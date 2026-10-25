package app.almanook.imagery;

import java.util.List;

/** A licensed image search source. Implementations must never throw for "no results" or provider outages. */
public interface ImageCandidateProvider {

    String name();

    boolean isEnabled();

    List<CoverCandidate> search(String terms, int limit);
}
