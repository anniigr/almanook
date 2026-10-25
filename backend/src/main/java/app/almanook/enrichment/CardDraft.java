package app.almanook.enrichment;

import app.almanook.card.CardInput;
import app.almanook.card.CardService.DuplicateHint;
import app.almanook.expiration.FactSource;
import app.almanook.imagery.CoverCandidate;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.JsonNode;

/**
 * An unsaved card proposal. {@code card} is ready to be edited and posted to {@code POST /cards};
 * {@code enrichment} is the provenance record stored with it.
 */
public record CardDraft(
        Mode mode,
        CardInput card,
        Map<String, FactSource> fieldSources,
        Map<String, String> suggestions,
        Confidence confidence,
        List<String> unverifiedFields,
        List<String> missingFields,
        List<SourceRef> sources,
        List<CoverCandidate> coverCandidates,
        List<DuplicateHint> possibleDuplicates,
        List<String> warnings,
        String originalInput,
        JsonNode enrichment) {

    public enum Mode {
        /** AI research + extraction were used. */
        AI,
        /** No AI provider configured: only the page's own metadata was used. */
        BASIC
    }

    public record Confidence(
            ConfidenceLevel overall,
            ConfidenceLevel identification,
            ConfidenceLevel dates,
            ConfidenceLevel price,
            ConfidenceLevel location) {}
}
