package app.almanook.imagery.provider;

import app.almanook.card.CoverSource;
import app.almanook.imagery.CoverCandidate;
import app.almanook.imagery.ImageCandidateProvider;
import app.almanook.imagery.ImageryProperties;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Unsplash search, enabled only when {@code UNSPLASH_ACCESS_KEY} is set. Follows the API guidelines:
 * hotlinked image URLs, "Photo by X on Unsplash" attribution with referral links, and a download-event call
 * when the user selects a photo.
 */
@Component
public class UnsplashProvider implements ImageCandidateProvider {

    private static final Logger log = LoggerFactory.getLogger(UnsplashProvider.class);

    private final ImageryProperties.Unsplash properties;
    private final RestClient http;
    private final JsonMapper json = JsonMapper.builder().build();

    UnsplashProvider(ImageryProperties properties) {
        this.properties = properties.unsplash();
        this.http = ProviderHttp.client(this.properties.apiUrl());
    }

    @Override
    public String name() {
        return "UNSPLASH";
    }

    @Override
    public boolean isEnabled() {
        return properties.enabled();
    }

    @Override
    public List<CoverCandidate> search(String terms, int limit) {
        if (!isEnabled() || terms == null || terms.isBlank()) {
            return List.of();
        }
        try {
            String body = http.get()
                    .uri(u -> u.path("/search/photos")
                            .queryParam("query", terms.strip())
                            .queryParam("per_page", String.valueOf(Math.min(limit, 10)))
                            .queryParam("orientation", "landscape")
                            .queryParam("content_filter", "high")
                            .build())
                    .header("Authorization", "Client-ID " + properties.accessKey())
                    .header("Accept-Version", "v1")
                    .retrieve()
                    .body(String.class);
            return parse(body, limit);
        } catch (RestClientException | JacksonException e) {
            log.warn("Unsplash search failed: {}", e.getMessage());
            return List.of();
        }
    }

    List<CoverCandidate> parse(String body, int limit) {
        List<CoverCandidate> out = new ArrayList<>();
        String utm = "?utm_source=" + properties.appName() + "&utm_medium=referral";
        for (JsonNode photo : json.readTree(body == null ? "{}" : body).path("results")) {
            String url = photo.path("urls").path("regular").asString("");
            if (!url.startsWith("https://")) {
                continue;
            }
            String name = photo.path("user").path("name").asString("Unknown photographer");
            String profile = photo.path("user").path("links").path("html").asString("https://unsplash.com");
            out.add(new CoverCandidate(
                    url,
                    photo.path("urls").path("small").asString(url),
                    CoverSource.UNSPLASH,
                    "Photo by " + name + " on Unsplash",
                    profile + utm,
                    "Unsplash License",
                    photo.path("links").path("download_location").asString(null)));
            if (out.size() >= limit) {
                break;
            }
        }
        return out;
    }

    /** Reports a selection to Unsplash. Only Unsplash API URLs are accepted. */
    public void trackDownload(String downloadLocation) {
        if (!isEnabled() || downloadLocation == null) {
            return;
        }
        URI uri = URI.create(downloadLocation);
        URI api = URI.create(properties.apiUrl());
        if (!api.getHost().equalsIgnoreCase(uri.getHost()) || !uri.getPath().startsWith("/photos/")) {
            throw new IllegalArgumentException("Not an Unsplash download location");
        }
        try {
            http.get()
                    .uri(uri)
                    .header("Authorization", "Client-ID " + properties.accessKey())
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            log.warn("Unsplash download tracking failed: {}", e.getMessage());
        }
    }
}
