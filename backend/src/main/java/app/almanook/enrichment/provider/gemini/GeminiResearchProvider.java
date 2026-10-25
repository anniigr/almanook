package app.almanook.enrichment.provider.gemini;

import app.almanook.enrichment.AiCardProposal;
import app.almanook.enrichment.AiProviderException;
import app.almanook.enrichment.AiResearchProvider;
import app.almanook.enrichment.ResearchNotes;
import app.almanook.enrichment.ResearchRequest;
import app.almanook.enrichment.SourceRef;
import app.almanook.enrichment.provider.AiProperties;
import app.almanook.enrichment.provider.Prompts;
import app.almanook.enrichment.provider.ProposalParser;
import app.almanook.enrichment.provider.ProposalSchema;
import jakarta.validation.Validator;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Google Gemini adapter over the Interactions API ({@code POST /v1beta/interactions}). Stage 1 runs with
 * the built-in {@code google_search} and {@code url_context} tools and collects the URLs the model cited or
 * actually read. Stage 2 asks for JSON constrained by the card schema with no tools. Failed, incomplete or
 * malformed responses become an {@link AiProviderException}; no fallback content is ever invented.
 *
 * <p>Interactions are sent with {@code store: false}, so Google does not keep them for later retrieval.
 */
public class GeminiResearchProvider implements AiResearchProvider {

    private static final Logger log = LoggerFactory.getLogger(GeminiResearchProvider.class);
    private static final Pattern OFFICIAL_URL = Pattern.compile("(?im)^\\s*OFFICIAL_URL:\\s*(https?://\\S+)\\s*$");
    private static final int MAX_ATTEMPTS = 3;
    private static final int MAX_SOURCES = 25;
    /** Grounding citations may point at Google's redirect service instead of the publisher's page. */
    static final String GROUNDING_REDIRECT_HOST = "vertexaisearch.cloud.google.com";

    private final AiProperties.Gemini properties;
    private final JsonMapper json;
    private final ProposalParser parser;
    private final HttpClient http;
    private final String redirectHost;
    private final Duration retryDelay;
    /** Turned off for the rest of the process once the key turns out to have no search quota. */
    private final AtomicBoolean searchAvailable = new AtomicBoolean(true);

    /** Gemini answered 429: a per-minute rate limit, or a quota the key's tier does not include. */
    static final class QuotaExceeded extends AiProviderException {
        final String detail;

        QuotaExceeded(String detail) {
            super("The AI provider's usage limit for this API key was reached. Try again later,"
                    + " or check the key's plan and billing in Google AI Studio.");
            this.detail = detail;
        }
    }

    public GeminiResearchProvider(AiProperties.Gemini properties, JsonMapper json, Validator validator) {
        this(properties, json, validator, GROUNDING_REDIRECT_HOST, Duration.ofSeconds(1));
    }

    GeminiResearchProvider(
            AiProperties.Gemini properties,
            JsonMapper json,
            Validator validator,
            String redirectHost,
            Duration retryDelay) {
        this.properties = properties;
        this.json = json;
        this.parser = new ProposalParser(json, validator);
        this.redirectHost = redirectHost;
        this.retryDelay = retryDelay;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    @Override
    public String name() {
        return "gemini";
    }

    @Override
    public String model() {
        return properties.model();
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public ResearchNotes research(ResearchRequest request) {
        JsonNode interaction;
        if (searchAvailable.get()) {
            try {
                interaction = call(researchBody(request, true));
            } catch (QuotaExceeded e) {
                // Free-tier keys have no Google Search grounding quota; URL context is still free.
                searchAvailable.set(false);
                log.warn(
                        "Gemini refused Google Search grounding ({}). Continuing with URL context only;"
                                + " enable billing on the key to restore search.",
                        e.detail);
                interaction = call(researchBody(request, false));
            }
        } else {
            interaction = call(researchBody(request, false));
        }
        String status = interaction.path("status").asString("");
        if (!"completed".equals(status) && !"incomplete".equals(status)) {
            throw failed(interaction);
        }
        Map<String, SourceRef> retrieved = new LinkedHashMap<>();
        String text = collect(interaction, retrieved).strip();
        if (text.isEmpty() && retrieved.isEmpty()) {
            throw new AiProviderException(
                    "The AI research step returned nothing usable. Try again or create the card manually.");
        }
        Matcher m = OFFICIAL_URL.matcher(text);
        String official = null;
        while (m.find()) {
            official = m.group(1);
        }
        return new ResearchNotes(text, resolveRedirects(retrieved), official);
    }

    private Map<String, Object> researchBody(ResearchRequest request, boolean withSearch) {
        String system = Prompts.researchSystem(request);
        if (!withSearch) {
            system += "\n\nWeb search is not available in this session. Open pages directly by URL instead:"
                    + " the URL the user supplied, the official website of the venue or organiser, and its"
                    + " ticket, programme or opening-hours pages. Only facts read from a page that loaded count.";
        }
        Map<String, Object> body = base(system, Prompts.researchUser(request), 8_000);
        body.put(
                "tools",
                withSearch
                        ? List.of(Map.of("type", "google_search"), Map.of("type", "url_context"))
                        : List.of(Map.of("type", "url_context")));
        return body;
    }

    @Override
    public AiCardProposal extract(ResearchRequest request, ResearchNotes notes) {
        Map<String, Object> body =
                base(Prompts.extractionSystem(request), Prompts.extractionUser(request, notes), 4_000);
        body.put(
                "response_format",
                Map.of("type", "text", "mime_type", "application/json", "schema", ProposalSchema.schema()));

        JsonNode interaction = call(body);
        String status = interaction.path("status").asString("");
        if ("incomplete".equals(status)) {
            throw new AiProviderException("The AI response was cut off. Try again with a shorter input.");
        }
        if (!"completed".equals(status)) {
            throw failed(interaction);
        }
        String text = collect(interaction, new LinkedHashMap<>()).strip();
        if (text.isEmpty()) {
            // Gemini returns no output text when a safety filter blocks the answer.
            throw new AiProviderException("The AI declined this request. Create the card manually instead.");
        }
        return parser.parse(text);
    }

    private Map<String, Object> base(String system, String input, int maxOutputTokens) {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("max_output_tokens", maxOutputTokens);
        if (properties.thinkingLevel() != null && !properties.thinkingLevel().isBlank()) {
            config.put("thinking_level", properties.thinkingLevel().strip().toLowerCase(Locale.ROOT));
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", properties.model());
        body.put("system_instruction", system);
        body.put("input", input);
        body.put("generation_config", config);
        body.put("store", false);
        return body;
    }

    private JsonNode call(Map<String, Object> body) {
        HttpRequest request = HttpRequest.newBuilder(
                        URI.create(trimSlash(properties.baseUrl()) + "/v1beta/interactions"))
                .timeout(properties.timeout())
                .header("Content-Type", "application/json")
                .header("x-goog-api-key", properties.apiKey())
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
                .build();

        for (int attempt = 1; ; attempt++) {
            HttpResponse<String> response;
            try {
                response = http.send(request, HttpResponse.BodyHandlers.ofString());
            } catch (HttpTimeoutException e) {
                // A slow model will not get faster on retry; fail within the request budget instead.
                log.warn("Gemini did not answer within {}", properties.timeout());
                throw new AiProviderException("The AI provider took too long to answer. Try again later,"
                        + " or choose a faster model (ALMANOOK_GEMINI_MODEL).");
            } catch (IOException e) {
                if (attempt < MAX_ATTEMPTS) {
                    pause(attempt);
                    continue;
                }
                log.warn("Gemini connection problem: {}", e.getMessage());
                throw new AiProviderException("The AI provider could not be reached. Try again later.");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AiProviderException("The AI provider could not be reached. Try again later.");
            }
            int code = response.statusCode();
            if (code >= 200 && code < 300) {
                try {
                    return json.readTree(response.body());
                } catch (JacksonException e) {
                    log.warn("Gemini returned a body that is not JSON");
                    throw new AiProviderException("The AI provider returned an unexpected response.");
                }
            }
            // 429 is not retried: Gemini uses it for exhausted quotas, where retrying only burns requests.
            if (code >= 500 && attempt < MAX_ATTEMPTS) {
                pause(attempt);
                continue;
            }
            throw httpError(code, response.body());
        }
    }

    private AiProviderException httpError(int code, String body) {
        JsonNode error = readQuietly(body).path("error");
        String message = error.path("message").asString("");
        String reasons = error.path("details").toString();
        if (code == 401
                || code == 403
                || reasons.contains("API_KEY_INVALID")
                || message.contains("API key not valid")) {
            log.error("Gemini rejected the API key or permissions (status {}): {}", code, message);
            return new AiProviderException("The AI provider rejected the configured API key.");
        }
        if (code == 429) {
            log.warn("Gemini quota or rate limit (429): {}", message);
            return new QuotaExceeded(message);
        }
        log.warn("Gemini API error {}: {}", code, message);
        return new AiProviderException(
                code >= 500
                        ? "The AI provider is temporarily unavailable. Try again later."
                        : "The AI provider could not process this request.");
    }

    private static AiProviderException failed(JsonNode interaction) {
        log.warn(
                "Gemini interaction ended with status '{}': {}",
                interaction.path("status").asString(""),
                interaction.path("errors"));
        return new AiProviderException("The AI provider could not process this request.");
    }

    /** Concatenates model output text and records cited, searched and fetched URLs. */
    private static String collect(JsonNode interaction, Map<String, SourceRef> retrieved) {
        StringBuilder text = new StringBuilder();
        for (JsonNode step : interaction.path("steps")) {
            switch (step.path("type").asString("")) {
                case "model_output" -> {
                    for (JsonNode block : step.path("content")) {
                        if (!"text".equals(block.path("type").asString(""))) {
                            continue;
                        }
                        text.append(block.path("text").asString(""));
                        for (JsonNode a : block.path("annotations")) {
                            if ("url_citation".equals(a.path("type").asString(""))) {
                                add(retrieved, a.path("url"), a.path("title"));
                            }
                        }
                    }
                }
                case "google_search_result" -> collectLinks(step.path("result"), retrieved, false);
                case "url_context_result" -> collectLinks(step.path("result"), retrieved, true);
                default -> {}
            }
        }
        return text.toString();
    }

    /**
     * Walks a tool result and records objects that carry a URL. For URL context only successful retrievals
     * count; a URL that failed to load or was flagged unsafe is not evidence.
     */
    private static void collectLinks(JsonNode node, Map<String, SourceRef> retrieved, boolean requireSuccess) {
        if (node.isArray()) {
            node.forEach(child -> collectLinks(child, retrieved, requireSuccess));
            return;
        }
        if (!node.isObject()) {
            return;
        }
        JsonNode url = firstPresent(node, "url", "retrieved_url", "uri");
        if (url != null) {
            String status = node.path("status").asString("");
            boolean ok = !requireSuccess || status.toLowerCase(Locale.ROOT).contains("success");
            if (ok) {
                add(retrieved, url, node.path("title"));
            }
            return;
        }
        for (JsonNode child : node) {
            collectLinks(child, retrieved, requireSuccess);
        }
    }

    private static JsonNode firstPresent(JsonNode node, String... names) {
        for (String name : names) {
            JsonNode v = node.get(name);
            if (v != null && v.isString() && !v.asString().isBlank()) {
                return v;
            }
        }
        return null;
    }

    private static void add(Map<String, SourceRef> retrieved, JsonNode url, JsonNode title) {
        String u = url.asString("");
        if (!u.startsWith("http://") && !u.startsWith("https://")) {
            return;
        }
        String t = title.isString() && !title.asString().isBlank() ? title.asString() : null;
        retrieved.putIfAbsent(u, new SourceRef(u, t));
    }

    /**
     * Replaces Google grounding redirect links with the publisher URL they point to, by reading the
     * {@code Location} header of a single request to Google's redirect host. The target page itself is never
     * fetched here, and links that cannot be resolved are dropped because they cannot back a fact.
     */
    private List<SourceRef> resolveRedirects(Map<String, SourceRef> retrieved) {
        List<SourceRef> refs = retrieved.values().stream().limit(MAX_SOURCES).toList();
        if (refs.stream().noneMatch(this::isRedirect)) {
            return refs;
        }
        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<Optional<SourceRef>>> futures = new ArrayList<>();
            for (SourceRef ref : refs) {
                futures.add(pool.submit(() -> isRedirect(ref) ? resolve(ref) : Optional.of(ref)));
            }
            Map<String, SourceRef> resolved = new LinkedHashMap<>();
            for (Future<Optional<SourceRef>> f : futures) {
                try {
                    f.get().ifPresent(r -> resolved.putIfAbsent(r.url(), r));
                } catch (Exception e) {
                    log.debug("Could not resolve a grounding link", e);
                }
            }
            return List.copyOf(resolved.values());
        }
    }

    private boolean isRedirect(SourceRef ref) {
        try {
            return redirectHost.equalsIgnoreCase(URI.create(ref.url()).getHost());
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private Optional<SourceRef> resolve(SourceRef ref) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(ref.url()))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();
        HttpResponse<Void> response = http.send(request, HttpResponse.BodyHandlers.discarding());
        if (response.statusCode() / 100 != 3) {
            return Optional.empty();
        }
        return response.headers()
                .firstValue("Location")
                .filter(l -> l.startsWith("https://") || l.startsWith("http://"))
                .map(l -> new SourceRef(l, ref.title()));
    }

    private JsonNode readQuietly(String body) {
        try {
            return json.readTree(body == null || body.isBlank() ? "{}" : body);
        } catch (JacksonException e) {
            return json.createObjectNode();
        }
    }

    private void pause(int attempt) {
        try {
            Thread.sleep(retryDelay.multipliedBy(attempt));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static String trimSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
