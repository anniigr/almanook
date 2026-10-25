package app.almanook.enrichment.provider;

import app.almanook.enrichment.AiCardProposal;
import app.almanook.enrichment.AiProviderException;
import app.almanook.enrichment.AiResearchProvider;
import app.almanook.enrichment.ResearchNotes;
import app.almanook.enrichment.ResearchRequest;

/** Used when no provider is configured. Reports itself unavailable and refuses to produce output. */
final class DisabledAiProvider implements AiResearchProvider {

    @Override
    public String name() {
        return "none";
    }

    @Override
    public String model() {
        return null;
    }

    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public ResearchNotes research(ResearchRequest request) {
        throw AiProviderException.unavailable("AI enrichment is not configured");
    }

    @Override
    public AiCardProposal extract(ResearchRequest request, ResearchNotes notes) {
        throw AiProviderException.unavailable("AI enrichment is not configured");
    }
}
