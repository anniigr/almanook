package app.almanook.imagery;

import app.almanook.card.CoverSource;
import app.almanook.sourcing.SafeHttpFetcher;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Proposes cover images in priority order: the source page's own image (verified reachable), then licensed
 * images from Wikimedia Commons and, if configured, Unsplash. Image URLs are never produced by the AI.
 */
@Service
public class CoverImageService {

    private static final Logger log = LoggerFactory.getLogger(CoverImageService.class);
    static final int MAX_CANDIDATES = 8;

    private final SafeHttpFetcher fetcher;
    private final List<ImageCandidateProvider> providers;

    CoverImageService(SafeHttpFetcher fetcher, List<ImageCandidateProvider> providers) {
        this.fetcher = fetcher;
        this.providers = providers;
    }

    public List<String> enabledProviders() {
        List<String> names = new ArrayList<>(List.of("PAGE"));
        providers.stream()
                .filter(ImageCandidateProvider::isEnabled)
                .map(ImageCandidateProvider::name)
                .forEach(names::add);
        return names;
    }

    public List<CoverCandidate> candidates(CoverQuery query) {
        Map<String, CoverCandidate> byUrl = new LinkedHashMap<>();
        query.pageImageUrls().stream()
                .limit(2)
                .filter(this::isReachableImage)
                .forEach(url ->
                        byUrl.putIfAbsent(url, new CoverCandidate(url, url, CoverSource.PAGE, null, null, null, null)));
        for (ImageCandidateProvider provider : providers) {
            if (byUrl.size() >= MAX_CANDIDATES) {
                break;
            }
            if (provider.isEnabled()) {
                provider.search(query.searchTerms(), 4).forEach(c -> byUrl.putIfAbsent(c.url(), c));
            }
        }
        return byUrl.values().stream().limit(MAX_CANDIDATES).toList();
    }

    /** Validates a user-pasted image URL with the same SSRF protections as page fetches. */
    public boolean isReachableImage(String url) {
        try {
            fetcher.fetch(url, SafeHttpFetcher.Kind.IMAGE);
            return true;
        } catch (RuntimeException e) {
            log.debug("Image not usable: {} ({})", url, e.getMessage());
            return false;
        }
    }
}
