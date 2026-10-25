package app.almanook.enrichment;

import app.almanook.card.CardService;
import app.almanook.enrichment.CardDraft.Mode;
import app.almanook.identity.UserAccountService;
import app.almanook.imagery.CoverCandidate;
import app.almanook.imagery.CoverImageService;
import app.almanook.imagery.CoverQuery;
import app.almanook.shared.ApiException;
import app.almanook.sourcing.PageExtractor;
import app.almanook.sourcing.PageSnapshot;
import app.almanook.sourcing.SafeHttpFetcher;
import app.almanook.sourcing.SourceFetchException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Orchestrates draft generation: fetch the user's URL (if any), ground with web research, extract structured
 * data, assemble with the verification rules, and propose covers. Nothing is persisted here; the user saves
 * the edited draft through the ordinary card API.
 */
@Service
public class EnrichmentService {

    private static final Logger log = LoggerFactory.getLogger(EnrichmentService.class);

    public record Status(
            boolean aiAvailable, String provider, String model, List<String> imageProviders, String message) {}

    private final AiResearchProvider ai;
    private final SafeHttpFetcher fetcher;
    private final PageExtractor extractor;
    private final CoverImageService covers;
    private final CardService cards;
    private final UserAccountService users;
    private final JsonMapper json;
    private final Clock clock;

    EnrichmentService(
            AiResearchProvider ai,
            SafeHttpFetcher fetcher,
            PageExtractor extractor,
            CoverImageService covers,
            CardService cards,
            UserAccountService users,
            JsonMapper json,
            Clock clock) {
        this.ai = ai;
        this.fetcher = fetcher;
        this.extractor = extractor;
        this.covers = covers;
        this.cards = cards;
        this.users = users;
        this.json = json;
        this.clock = clock;
    }

    public Status status() {
        return new Status(
                ai.isAvailable(),
                ai.name(),
                ai.isAvailable() ? ai.model() : null,
                covers.enabledProviders(),
                ai.isAvailable()
                        ? "AI enrichment is available."
                        : "AI enrichment is not configured (set ANTHROPIC_API_KEY). You can still create cards"
                                + " manually, and importing a URL uses the page's own metadata.");
    }

    public CardDraft generate(UUID userId, DraftRequest request) {
        List<String> warnings = new ArrayList<>();
        LocalDate today = users.todayFor(userId);
        String url = firstUrl(request);

        PageSnapshot page = null;
        if (url != null) {
            try {
                page = extractor.extract(fetcher.fetch(url, SafeHttpFetcher.Kind.PAGE));
            } catch (SourceFetchException e) {
                warnings.add(e.getMessage());
            }
        }

        Mode mode;
        ResearchNotes notes = ResearchNotes.none();
        AiCardProposal proposal = null;
        if (ai.isAvailable()) {
            mode = Mode.AI;
            ResearchRequest researchRequest = new ResearchRequest(request.query(), url, request.notes(), today, page);
            notes = ai.research(researchRequest);
            if (page == null && notes.officialUrl() != null) {
                page = tryFetchOfficialPage(notes, warnings);
                researchRequest = new ResearchRequest(request.query(), url, request.notes(), today, page);
            }
            proposal = ai.extract(researchRequest, notes);
        } else if (page != null) {
            mode = Mode.BASIC;
            warnings.add(0, "AI enrichment is not configured, so this draft uses only the page's own metadata.");
        } else if (url != null) {
            throw new SourceFetchException("the page is unavailable and AI enrichment is not configured;"
                    + " create the card manually instead");
        } else {
            throw new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "ai_unavailable",
                    "AI enrichment is not configured. Create the card manually, or paste a URL to import its metadata.");
        }

        DraftAssembler.Assembled draft = DraftAssembler.assemble(request, page, notes, proposal);
        warnings.addAll(draft.warnings());

        List<CoverCandidate> coverCandidates = List.of();
        try {
            coverCandidates = covers.candidates(new CoverQuery(draft.imageSearchTerms(), draft.pageImageUrls()));
        } catch (RuntimeException e) {
            log.warn("Cover lookup failed", e);
            warnings.add("Cover suggestions are unavailable right now; you can add an image later.");
        }

        Map<String, Object> enrichment = new LinkedHashMap<>();
        enrichment.put("mode", mode.name());
        enrichment.put("provider", mode == Mode.AI ? ai.name() : null);
        enrichment.put("model", mode == Mode.AI ? ai.model() : null);
        enrichment.put("generatedAt", Instant.now(clock).toString());
        enrichment.put("sources", draft.sources());
        enrichment.put("fieldSources", draft.fieldSources());
        enrichment.put("unverifiedFields", draft.unverifiedFields());
        enrichment.put("confidence", draft.confidence());
        JsonNode enrichmentNode = json.valueToTree(enrichment);

        return new CardDraft(
                mode,
                draft.card(),
                draft.fieldSources(),
                draft.suggestions(),
                draft.confidence(),
                draft.unverifiedFields(),
                draft.missingFields(),
                draft.sources(),
                coverCandidates,
                cards.possibleDuplicates(
                        userId, draft.card().title(), draft.card().sourceUrl()),
                warnings.stream().distinct().toList(),
                request.originalInput(),
                enrichmentNode);
    }

    private PageSnapshot tryFetchOfficialPage(ResearchNotes notes, List<String> warnings) {
        boolean retrieved = notes.retrievedUrls().stream()
                .anyMatch(r -> DraftAssembler.canonical(r.url()).equals(DraftAssembler.canonical(notes.officialUrl())));
        if (!retrieved) {
            return null;
        }
        try {
            return extractor.extract(fetcher.fetch(notes.officialUrl(), SafeHttpFetcher.Kind.PAGE));
        } catch (RuntimeException e) {
            log.debug("Official page not retrievable: {}", e.getMessage());
            return null;
        }
    }

    private static String firstUrl(DraftRequest request) {
        if (request.url() != null && !request.url().isBlank()) {
            return request.url().strip();
        }
        if (request.query() != null && request.query().strip().matches("(?i)https?://\\S+")) {
            return request.query().strip();
        }
        return null;
    }
}
