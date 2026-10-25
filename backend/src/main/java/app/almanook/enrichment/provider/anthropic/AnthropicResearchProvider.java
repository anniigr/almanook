package app.almanook.enrichment.provider.anthropic;

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
import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.JsonValue;
import com.anthropic.errors.AnthropicException;
import com.anthropic.errors.AnthropicIoException;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.errors.PermissionDeniedException;
import com.anthropic.errors.RateLimitException;
import com.anthropic.errors.UnauthorizedException;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.JsonOutputFormat;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.TextBlock;
import com.anthropic.models.messages.UserLocation;
import com.anthropic.models.messages.WebSearchResultBlock;
import com.anthropic.models.messages.WebSearchTool20260209;
import jakarta.validation.Validator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.json.JsonMapper;

/**
 * Claude adapter. Stage 1 asks the model to research with the server-side web search tool and collects the
 * URLs of every search result it saw. Stage 2 asks for schema-constrained JSON with no tools. Any API
 * error, refusal, truncation or invalid JSON becomes an {@link AiProviderException}; no fallback content
 * is ever invented.
 */
public class AnthropicResearchProvider implements AiResearchProvider {

    private static final Logger log = LoggerFactory.getLogger(AnthropicResearchProvider.class);
    private static final Pattern OFFICIAL_URL = Pattern.compile("(?im)^\\s*OFFICIAL_URL:\\s*(https?://\\S+)\\s*$");
    private static final int MAX_PAUSE_CONTINUATIONS = 3;
    static final String FALLBACK_BETA = "server-side-fallback-2026-07-01";

    private final AiProperties.Anthropic properties;
    private final AnthropicClient client;
    private final ProposalParser parser;
    private final Map<String, Object> schema = ProposalSchema.schema();

    public AnthropicResearchProvider(AiProperties.Anthropic properties, JsonMapper json, Validator validator) {
        this.properties = properties;
        this.parser = new ProposalParser(json, validator);
        this.client = AnthropicOkHttpClient.builder()
                .apiKey(properties.apiKey())
                .baseUrl(properties.baseUrl())
                .timeout(properties.timeout())
                .maxRetries(2)
                .build();
    }

    @Override
    public String name() {
        return "anthropic";
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
        MessageCreateParams.Builder params = base().maxTokens(8_000)
                .system(Prompts.researchSystem(request))
                .addUserMessage(Prompts.researchUser(request))
                .addTool(WebSearchTool20260209.builder()
                        .maxUses(properties.maxSearches())
                        .userLocation(UserLocation.builder()
                                .country("PL")
                                .timezone("Europe/Warsaw")
                                .build())
                        .build());

        Map<String, SourceRef> retrieved = new LinkedHashMap<>();
        StringBuilder notes = new StringBuilder();
        Message response = call(params.build());
        for (int i = 0; ; i++) {
            collect(response, retrieved, notes);
            if (!StopReason.PAUSE_TURN.equals(response.stopReason().orElse(null)) || i >= MAX_PAUSE_CONTINUATIONS) {
                break;
            }
            // The server-side tool loop paused; resend with the partial assistant turn to let it resume.
            params.addMessage(response);
            response = call(params.build());
        }
        requireUsableStop(response, false);
        String text = notes.toString().strip();
        if (text.isEmpty() && retrieved.isEmpty()) {
            throw new AiProviderException(
                    "The AI research step returned nothing usable. Try again or create the card manually.");
        }
        Matcher m = OFFICIAL_URL.matcher(text);
        String official = null;
        while (m.find()) {
            official = m.group(1);
        }
        return new ResearchNotes(text, List.copyOf(retrieved.values()), official);
    }

    @Override
    public AiCardProposal extract(ResearchRequest request, ResearchNotes notes) {
        JsonOutputFormat.Schema.Builder schemaBuilder = JsonOutputFormat.Schema.builder();
        schema.forEach((k, v) -> schemaBuilder.putAdditionalProperty(k, JsonValue.from(v)));
        MessageCreateParams params = base().maxTokens(4_000)
                .system(Prompts.extractionSystem(request))
                .addUserMessage(Prompts.extractionUser(request, notes))
                .outputConfig(OutputConfig.builder()
                        .effort(effort())
                        .format(JsonOutputFormat.builder()
                                .schema(schemaBuilder.build())
                                .build())
                        .build())
                .build();
        Message response = call(params);
        requireUsableStop(response, true);
        String text = response.content().stream()
                .flatMap(b -> b.text().stream())
                .map(TextBlock::text)
                .collect(Collectors.joining());
        return parseProposal(text);
    }

    /** Visible for tests in other packages. */
    public AiCardProposal parseProposalForTest(String text) {
        return parseProposal(text);
    }

    AiCardProposal parseProposal(String text) {
        return parser.parse(text);
    }

    private MessageCreateParams.Builder base() {
        MessageCreateParams.Builder b = MessageCreateParams.builder().model(properties.model());
        if (properties.refusalFallback()) {
            b.putAdditionalHeader("anthropic-beta", FALLBACK_BETA)
                    .putAdditionalBodyProperty("fallbacks", JsonValue.from("default"));
        }
        return b;
    }

    private OutputConfig.Effort effort() {
        return OutputConfig.Effort.of(properties.effort());
    }

    private Message call(MessageCreateParams params) {
        try {
            return client.messages().create(params);
        } catch (UnauthorizedException | PermissionDeniedException e) {
            log.error("Anthropic rejected the API key or permissions (status {})", e.statusCode());
            throw new AiProviderException("The AI provider rejected the configured API key.");
        } catch (RateLimitException e) {
            throw new AiProviderException("The AI provider is busy (rate limited). Try again in a minute.");
        } catch (AnthropicServiceException e) {
            log.warn("Anthropic API error {}: {}", e.statusCode(), e.getMessage());
            throw new AiProviderException(
                    e.statusCode() >= 500
                            ? "The AI provider is temporarily unavailable. Try again later."
                            : "The AI provider could not process this request.");
        } catch (AnthropicIoException e) {
            log.warn("Anthropic connection problem: {}", e.getMessage());
            throw new AiProviderException("The AI provider could not be reached. Try again later.");
        } catch (AnthropicException e) {
            log.warn("Anthropic SDK error: {}", e.getMessage());
            throw new AiProviderException("The AI provider returned an unexpected response.");
        }
    }

    private static void requireUsableStop(Message response, boolean structured) {
        StopReason stop = response.stopReason().orElse(null);
        if (StopReason.REFUSAL.equals(stop)) {
            throw new AiProviderException("The AI declined this request. Create the card manually instead.");
        }
        if (StopReason.MAX_TOKENS.equals(stop) && structured) {
            throw new AiProviderException("The AI response was cut off. Try again with a shorter input.");
        }
    }

    private static void collect(Message response, Map<String, SourceRef> retrieved, StringBuilder notes) {
        for (ContentBlock block : response.content()) {
            block.webSearchToolResult()
                    .ifPresent(result -> result.content()
                            .resultBlocks()
                            .ifPresent(results -> results.forEach(r -> add(retrieved, r))));
            block.text().ifPresent(text -> {
                notes.append(text.text());
                text.citations()
                        .ifPresent(cs -> cs.forEach(c -> c.webSearchResultLocation()
                                .ifPresent(loc -> retrieved.putIfAbsent(
                                        loc.url(),
                                        new SourceRef(loc.url(), loc.title().orElse(null))))));
            });
        }
    }

    private static void add(Map<String, SourceRef> retrieved, WebSearchResultBlock r) {
        retrieved.putIfAbsent(r.url(), new SourceRef(r.url(), r.title()));
    }
}
