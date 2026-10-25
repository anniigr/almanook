package app.almanook.card;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Normalises source URLs so that the same page saved twice is detected as a duplicate: lower-case scheme
 * and host, no default port, no fragment, no tracking parameters, no trailing slash.
 */
public final class UrlCanonicalizer {

    private static final Set<String> TRACKING_PARAMS =
            Set.of("fbclid", "gclid", "dclid", "msclkid", "mc_cid", "mc_eid", "igshid", "_ga", "ref_src");

    private UrlCanonicalizer() {}

    public static Optional<String> canonicalize(String url) {
        if (url == null || url.isBlank()) {
            return Optional.empty();
        }
        try {
            URI uri = new URI(url.strip());
            if (uri.getScheme() == null || uri.getHost() == null) {
                return Optional.empty();
            }
            String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
            String host = uri.getHost().toLowerCase(Locale.ROOT);
            if (host.startsWith("www.")) {
                host = host.substring(4);
            }
            int port = uri.getPort();
            boolean defaultPort =
                    port == -1 || (scheme.equals("http") && port == 80) || (scheme.equals("https") && port == 443);
            String path = uri.getRawPath() == null ? "" : uri.getRawPath();
            while (path.endsWith("/")) {
                path = path.substring(0, path.length() - 1);
            }
            String query = uri.getRawQuery() == null
                    ? null
                    : Arrays.stream(uri.getRawQuery().split("&"))
                            .filter(p -> !p.isEmpty())
                            .filter(p -> {
                                String name = p.split("=", 2)[0].toLowerCase(Locale.ROOT);
                                return !name.startsWith("utm_") && !TRACKING_PARAMS.contains(name);
                            })
                            .sorted()
                            .collect(Collectors.joining("&"));
            StringBuilder sb = new StringBuilder(scheme.equals("http") ? "https" : scheme)
                    .append("://")
                    .append(host);
            if (!defaultPort) {
                sb.append(':').append(port);
            }
            sb.append(path);
            if (query != null && !query.isEmpty()) {
                sb.append('?').append(query);
            }
            return Optional.of(sb.toString());
        } catch (URISyntaxException e) {
            return Optional.empty();
        }
    }
}
