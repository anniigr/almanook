package app.almanook.imagery.provider;

import app.almanook.card.CoverSource;
import app.almanook.imagery.CoverCandidate;
import app.almanook.imagery.ImageCandidateProvider;
import app.almanook.imagery.ImageryProperties;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Openly licensed photos from Wikimedia Commons (no API key). Only free licences are kept, and author,
 * licence and file page are returned for attribution.
 */
@Component
class WikimediaCommonsProvider implements ImageCandidateProvider {

    private static final Logger log = LoggerFactory.getLogger(WikimediaCommonsProvider.class);
    private static final Pattern FREE_LICENSE =
            Pattern.compile("^(cc0|cc[ -]by([ -]sa)?\\b.*|public domain|pd\\b.*|pdm.*)", Pattern.CASE_INSENSITIVE);
    private static final Set<String> MIME = Set.of("image/jpeg", "image/png", "image/webp");

    private final ImageryProperties.Wikimedia properties;
    private final RestClient http;
    private final JsonMapper json = JsonMapper.builder().build();

    WikimediaCommonsProvider(ImageryProperties properties) {
        this.properties = properties.wikimedia();
        this.http = ProviderHttp.client(this.properties.apiUrl());
    }

    @Override
    public String name() {
        return "WIKIMEDIA";
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
                    .uri(u -> u.queryParam("action", "query")
                            .queryParam("format", "json")
                            .queryParam("formatversion", "2")
                            .queryParam("generator", "search")
                            .queryParam("gsrsearch", "filetype:bitmap " + terms.strip())
                            .queryParam("gsrnamespace", "6")
                            .queryParam("gsrlimit", String.valueOf(Math.min(limit * 3, 30)))
                            .queryParam("prop", "imageinfo")
                            .queryParam("iiprop", "url|extmetadata|mime|size")
                            .queryParam("iiurlwidth", "1280")
                            .queryParam("iiextmetadatafilter", "LicenseShortName|Artist|LicenseUrl")
                            .build())
                    .retrieve()
                    .body(String.class);
            return parse(body, limit);
        } catch (RestClientException | JacksonException e) {
            log.warn("Wikimedia Commons search failed: {}", e.getMessage());
            return List.of();
        }
    }

    List<CoverCandidate> parse(String body, int limit) {
        JsonNode pages = json.readTree(body == null ? "{}" : body).path("query").path("pages");
        List<JsonNode> sorted = new ArrayList<>();
        pages.forEach(sorted::add);
        sorted.sort(java.util.Comparator.comparingInt(p -> p.path("index").asInt(Integer.MAX_VALUE)));
        List<CoverCandidate> out = new ArrayList<>();
        for (JsonNode page : sorted) {
            JsonNode info = page.path("imageinfo").path(0);
            String mime = info.path("mime").asString("");
            String license = info.path("extmetadata")
                    .path("LicenseShortName")
                    .path("value")
                    .asString("");
            String url = info.path("thumburl").asString(info.path("url").asString(""));
            if (!MIME.contains(mime.toLowerCase(Locale.ROOT))
                    || !FREE_LICENSE.matcher(license.strip()).matches()
                    || !url.startsWith("https://")
                    || info.path("width").asInt(0) < 600) {
                continue;
            }
            String artist = Jsoup.parse(info.path("extmetadata")
                            .path("Artist")
                            .path("value")
                            .asString(""))
                    .text()
                    .strip();
            artist = collapseRepeat(artist);
            String attribution =
                    (artist.isEmpty() ? "Wikimedia Commons" : artist) + " / " + license + ", via Wikimedia Commons";
            out.add(new CoverCandidate(
                    url,
                    url,
                    CoverSource.WIKIMEDIA,
                    truncate(attribution, 300),
                    info.path("descriptionurl").asString(null),
                    truncate(license, 100),
                    null));
            if (out.size() >= limit) {
                break;
            }
        }
        return out;
    }

    /** Commons often renders "Unknown author" twice (text + link title); keep one copy. */
    static String collapseRepeat(String s) {
        int half = s.length() / 2;
        return s.length() % 2 == 0 && half > 0 && s.substring(0, half).equals(s.substring(half))
                ? s.substring(0, half)
                : s;
    }

    private static String truncate(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}
