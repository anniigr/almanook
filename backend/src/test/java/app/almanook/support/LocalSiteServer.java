package app.almanook.support;

import app.almanook.sourcing.SafeHttpFetcher;
import app.almanook.sourcing.UrlSafetyPolicy;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.util.Set;
import java.util.concurrent.Executors;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * A tiny "venue website" on loopback for integration tests, plus a fetcher whose policy admits loopback on
 * that port only (every other private range stays blocked).
 */
public final class LocalSiteServer {

    public static final HttpServer SERVER;
    public static final int PORT;

    static {
        try {
            SERVER = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        SERVER.createContext(
                "/exhibition", ex -> serve(ex, "text/html; charset=utf-8", resource("/pages/exhibition.html")));
        SERVER.createContext("/cover.png", ex -> serve(ex, "image/png", TINY_PNG()));
        SERVER.setExecutor(Executors.newCachedThreadPool());
        SERVER.start();
        PORT = SERVER.getAddress().getPort();
    }

    private LocalSiteServer() {}

    public static String url(String path) {
        return "http://127.0.0.1:" + PORT + path;
    }

    /** 1x1 transparent PNG. */
    public static byte[] TINY_PNG() {
        return java.util.Base64.getDecoder()
                .decode(
                        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==");
    }

    private static byte[] resource(String name) {
        try (InputStream in = LocalSiteServer.class.getResourceAsStream(name)) {
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void serve(com.sun.net.httpserver.HttpExchange ex, String type, byte[] body) throws IOException {
        ex.getResponseHeaders().add("Content-Type", type);
        ex.sendResponseHeaders(200, body.length);
        try (OutputStream out = ex.getResponseBody()) {
            out.write(body);
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    public static class LoopbackFetchConfig {
        @Bean
        @Primary
        UrlSafetyPolicy loopbackPolicy() {
            return new UrlSafetyPolicy(Set.of(80, 443, PORT), true);
        }

        @Bean(destroyMethod = "close")
        @Primary
        SafeHttpFetcher loopbackFetcher(UrlSafetyPolicy loopbackPolicy) {
            return new SafeHttpFetcher(loopbackPolicy);
        }
    }
}
