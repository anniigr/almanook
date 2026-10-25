package app.almanook.sourcing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Exercises the fetcher against an in-process server. The test policy allows loopback (where the server
 * lives) but keeps every other non-public range blocked, so redirect-based SSRF is tested for real.
 */
class SafeHttpFetcherTest {

    HttpServer server;
    SafeHttpFetcher fetcher;
    String base;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(
                "/page", ex -> respond(ex, 200, "text/html; charset=utf-8", "<html><title>Hi</title></html>"));
        server.createContext("/redirect-chain/", ex -> {
            int n = Integer.parseInt(ex.getRequestURI().getPath().substring("/redirect-chain/".length()));
            redirect(ex, n == 0 ? "/page" : "/redirect-chain/" + (n - 1));
        });
        server.createContext("/to-metadata", ex -> redirect(ex, "http://169.254.169.254/latest/meta-data/"));
        server.createContext("/to-private", ex -> redirect(ex, "http://10.0.0.5/admin"));
        server.createContext("/to-file", ex -> redirect(ex, "file:///etc/passwd"));
        server.createContext("/json", ex -> respond(ex, 200, "application/json", "{}"));
        server.createContext("/huge", ex -> {
            ex.getResponseHeaders().add("Content-Type", "text/html");
            ex.sendResponseHeaders(200, 0); // chunked, no Content-Length to trust
            try (OutputStream out = ex.getResponseBody()) {
                byte[] chunk = new byte[64 * 1024];
                for (int i = 0; i < 64; i++) {
                    out.write(chunk);
                }
            } catch (IOException ignored) {
                // client aborted, as expected
            }
        });
        server.createContext("/slow", ex -> {
            try {
                Thread.sleep(3_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            respond(ex, 200, "text/html", "late");
        });
        server.createContext("/missing", ex -> respond(ex, 404, "text/html", "nope"));
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
        int port = server.getAddress().getPort();
        base = "http://127.0.0.1:" + port;
        fetcher = new SafeHttpFetcher(
                new UrlSafetyPolicy(Set.of(80, 443, port), true), Duration.ofSeconds(1), Duration.ofSeconds(1));
    }

    @AfterEach
    void stop() throws IOException {
        fetcher.close();
        server.stop(0);
    }

    @Test
    void fetchesAnAllowedPage() {
        FetchedResource r = fetcher.fetch(base + "/page", SafeHttpFetcher.Kind.PAGE);
        assertThat(r.contentType()).isEqualTo("text/html");
        assertThat(r.charset()).isEqualTo(StandardCharsets.UTF_8);
        assertThat(new String(r.body(), StandardCharsets.UTF_8)).contains("<title>Hi</title>");
    }

    @Test
    void followsUpToFiveRedirectsAndReportsTheFinalUrl() {
        FetchedResource r = fetcher.fetch(base + "/redirect-chain/4", SafeHttpFetcher.Kind.PAGE);
        assertThat(r.finalUri().getPath()).isEqualTo("/page");
        assertThatThrownBy(() -> fetcher.fetch(base + "/redirect-chain/5", SafeHttpFetcher.Kind.PAGE))
                .isInstanceOf(SourceFetchException.class)
                .hasMessageContaining("too many redirects");
    }

    @Test
    void redirectsToMetadataPrivateOrFileTargetsAreBlocked() {
        assertThatThrownBy(() -> fetcher.fetch(base + "/to-metadata", SafeHttpFetcher.Kind.PAGE))
                .isInstanceOf(UnsafeUrlException.class);
        assertThatThrownBy(() -> fetcher.fetch(base + "/to-private", SafeHttpFetcher.Kind.PAGE))
                .isInstanceOf(UnsafeUrlException.class);
        assertThatThrownBy(() -> fetcher.fetch(base + "/to-file", SafeHttpFetcher.Kind.PAGE))
                .isInstanceOf(UnsafeUrlException.class);
    }

    @Test
    void productionPolicyRejectsTheLoopbackServerItself() throws IOException {
        try (SafeHttpFetcher production = new SafeHttpFetcher(
                new UrlSafetyPolicy(Set.of(server.getAddress().getPort()), false))) {
            assertThatThrownBy(() -> production.fetch(base + "/page", SafeHttpFetcher.Kind.PAGE))
                    .isInstanceOf(UnsafeUrlException.class);
        }
    }

    @Test
    void enforcesContentTypeSizeAndTime() {
        assertThatThrownBy(() -> fetcher.fetch(base + "/json", SafeHttpFetcher.Kind.PAGE))
                .isInstanceOf(SourceFetchException.class)
                .hasMessageContaining("application/json");
        assertThatThrownBy(() -> fetcher.fetch(base + "/page", SafeHttpFetcher.Kind.IMAGE))
                .isInstanceOf(SourceFetchException.class);
        assertThatThrownBy(() -> fetcher.fetch(base + "/huge", SafeHttpFetcher.Kind.PAGE))
                .isInstanceOf(SourceFetchException.class)
                .hasMessageContaining("larger than 2 MiB");
        assertThatThrownBy(() -> fetcher.fetch(base + "/slow", SafeHttpFetcher.Kind.PAGE))
                .isInstanceOf(SourceFetchException.class)
                .hasMessageContaining("timed out");
        assertThatThrownBy(() -> fetcher.fetch(base + "/missing", SafeHttpFetcher.Kind.PAGE))
                .isInstanceOf(SourceFetchException.class)
                .hasMessageContaining("HTTP 404");
    }

    private static void redirect(HttpExchange ex, String location) throws IOException {
        ex.getResponseHeaders().add("Location", location);
        ex.sendResponseHeaders(302, -1);
        ex.close();
    }

    private static void respond(HttpExchange ex, int status, String type, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", type);
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = ex.getResponseBody()) {
            out.write(bytes);
        }
    }
}
