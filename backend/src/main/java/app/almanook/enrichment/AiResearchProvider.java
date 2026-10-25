package app.almanook.enrichment;

/**
 * Port to an AI provider. Implementations must return real provider output or throw
 * {@link AiProviderException}; they must never fabricate a successful response.
 */
public interface AiResearchProvider {

    String name();

    String model();

    /** False when not configured (e.g. no API key); the app then runs with AI features disabled. */
    boolean isAvailable();

    /** Stage 1: grounded web research. Returns notes and the URLs that were actually retrieved. */
    ResearchNotes research(ResearchRequest request);

    /** Stage 2: structured extraction from the notes and the page, with no tools. */
    AiCardProposal extract(ResearchRequest request, ResearchNotes notes);
}
