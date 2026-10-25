package app.almanook.sourcing;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.UnsupportedCharsetException;
import java.time.Duration;
import java.util.Locale;
import java.util.Set;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.util.Timeout;

/**
 * The only way the backend retrieves user-supplied URLs. Enforces {@link UrlSafetyPolicy} on every redirect
 * hop and on the connected address, plus timeouts, a streaming size cap and a content-type allow-list.
 */
public class SafeHttpFetcher implements AutoCloseable {

    public enum Kind {
        PAGE(2 * 1024 * 1024, Set.of("text/html", "application/xhtml+xml")),
        IMAGE(5 * 1024 * 1024, Set.of("image/jpeg", "image/png", "image/webp", "image/gif"));

        final int maxBytes;
        final Set<String> contentTypes;

        Kind(int maxBytes, Set<String> contentTypes) {
            this.maxBytes = maxBytes;
            this.contentTypes = contentTypes;
        }
    }

    public static final int MAX_REDIRECTS = 5;
    static final String USER_AGENT =
            "AlmanookBot/1.0 (+https://github.com/anniigr/almanook; personal bookmarking, one request per user action)";

    private final UrlSafetyPolicy policy;
    private final CloseableHttpClient client;
    private final Duration totalTimeout;

    public SafeHttpFetcher(UrlSafetyPolicy policy) {
        this(policy, Duration.ofSeconds(4), Duration.ofSeconds(8));
    }

    public SafeHttpFetcher(UrlSafetyPolicy policy, Duration connectTimeout, Duration totalTimeout) {
        this.policy = policy;
        this.totalTimeout = totalTimeout;
        this.client = HttpClients.custom()
                .setConnectionManager(PoolingHttpClientConnectionManagerBuilder.create()
                        .setDnsResolver(new GuardedDnsResolver(policy))
                        .setDefaultConnectionConfig(ConnectionConfig.custom()
                                .setConnectTimeout(Timeout.of(connectTimeout))
                                .setSocketTimeout(Timeout.of(totalTimeout))
                                .build())
                        .setMaxConnTotal(20)
                        .setMaxConnPerRoute(4)
                        .build())
                .setDefaultRequestConfig(RequestConfig.custom()
                        .setResponseTimeout(Timeout.of(totalTimeout))
                        .setRedirectsEnabled(false)
                        .build())
                .disableRedirectHandling()
                .disableCookieManagement()
                .disableAuthCaching()
                .setUserAgent(USER_AGENT)
                .build();
    }

    public FetchedResource fetch(String url, Kind kind) {
        long deadline = System.nanoTime() + totalTimeout.toNanos();
        URI current = policy.checkSyntax(url);
        for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
            HttpGet get = new HttpGet(current);
            get.setHeader("Accept", kind == Kind.PAGE ? "text/html,application/xhtml+xml;q=0.9" : "image/*");
            get.setHeader("Accept-Language", "pl,en;q=0.8");
            try (ClassicHttpResponse response = client.executeOpen(null, get, null)) {
                int status = response.getCode();
                if (status >= 300 && status < 400 && status != 304) {
                    Header location = response.getFirstHeader("Location");
                    if (location == null) {
                        throw new SourceFetchException("redirect without a target");
                    }
                    URI next = resolveRedirect(current, location.getValue());
                    current = policy.checkSyntax(next.toString());
                    continue;
                }
                if (status != 200) {
                    throw new SourceFetchException("the server answered HTTP " + status);
                }
                return read(current, response.getEntity(), kind, deadline);
            } catch (UnknownHostException e) {
                throw new SourceFetchException("the host could not be found");
            } catch (InterruptedIOException e) {
                throw new SourceFetchException("the request timed out");
            } catch (IOException e) {
                throw new SourceFetchException("the connection failed");
            }
        }
        throw new SourceFetchException("too many redirects");
    }

    private URI resolveRedirect(URI current, String location) {
        URI next;
        try {
            next = current.resolve(location.strip());
        } catch (IllegalArgumentException e) {
            throw new UnsafeUrlException("malformed redirect target");
        }
        boolean downgrade = "https".equalsIgnoreCase(current.getScheme()) && "http".equalsIgnoreCase(next.getScheme());
        if (downgrade && !current.getHost().equalsIgnoreCase(next.getHost())) {
            throw new UnsafeUrlException("redirect from https to plain http on another host");
        }
        return next;
    }

    private FetchedResource read(URI uri, HttpEntity entity, Kind kind, long deadline) throws IOException {
        if (entity == null) {
            throw new SourceFetchException("empty response");
        }
        ContentType type = parseContentType(entity.getContentType());
        String mime = type == null ? "" : type.getMimeType().toLowerCase(Locale.ROOT);
        if (!kind.contentTypes.contains(mime)) {
            throw new SourceFetchException("unexpected content type " + (mime.isEmpty() ? "(none)" : mime));
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream(16 * 1024);
        try (InputStream in = entity.getContent()) {
            byte[] buffer = new byte[16 * 1024];
            int n;
            while ((n = in.read(buffer)) != -1) {
                if (out.size() + n > kind.maxBytes) {
                    throw new SourceFetchException(
                            "the response is larger than " + kind.maxBytes / (1024 * 1024) + " MiB");
                }
                if (System.nanoTime() > deadline) {
                    throw new SourceFetchException("the request timed out");
                }
                out.write(buffer, 0, n);
            }
        }
        return new FetchedResource(uri, mime, charsetOf(type), out.toByteArray());
    }

    private static ContentType parseContentType(String header) {
        if (header == null || header.isBlank()) {
            return null;
        }
        try {
            return ContentType.parseLenient(header);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static Charset charsetOf(ContentType type) {
        try {
            return type == null ? null : type.getCharset();
        } catch (UnsupportedCharsetException | IllegalCharsetNameException e) {
            return null;
        }
    }

    @Override
    public void close() throws IOException {
        client.close();
    }
}
