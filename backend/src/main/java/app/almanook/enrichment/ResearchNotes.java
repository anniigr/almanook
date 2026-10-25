package app.almanook.enrichment;

import java.util.List;

/**
 * Output of the grounding stage: free-text notes plus the URLs that the provider actually retrieved (search
 * results, fetched pages). Only URLs in {@link #retrievedUrls()} can back a verified fact.
 */
public record ResearchNotes(String notes, List<SourceRef> retrievedUrls, String officialUrl) {

    public ResearchNotes {
        retrievedUrls = retrievedUrls == null ? List.of() : List.copyOf(retrievedUrls);
    }

    public static ResearchNotes none() {
        return new ResearchNotes(null, List.of(), null);
    }
}
