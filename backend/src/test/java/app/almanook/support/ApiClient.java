package app.almanook.support;

import java.io.IOException;
import java.net.CookieManager;
import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * A real HTTP client with its own cookie jar, so integration tests exercise the same session + CSRF cookie
 * flow as the browser.
 */
public class ApiClient {

    public record Response(int status, JsonNode body, String raw, HttpResponse<String> http) {}

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final String baseUrl;
    private final CookieManager cookies = new CookieManager();
    private final HttpClient http;

    public ApiClient(int port) {
        this.baseUrl = "http://localhost:" + port;
        this.http = HttpClient.newBuilder()
                .cookieHandler(cookies)
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    public static String json(Object value) {
        return JSON.writeValueAsString(value);
    }

    public ApiClient refreshCsrf() {
        get("/api/v1/auth/csrf");
        return this;
    }

    public ApiClient register(String email, String password, String name) {
        refreshCsrf();
        Response r = post(
                "/api/v1/auth/register",
                json(java.util.Map.of("email", email, "password", password, "displayName", name)));
        if (r.status() != 201) {
            throw new IllegalStateException("register failed: " + r.raw());
        }
        return refreshCsrf();
    }

    public Response get(String path) {
        return send(HttpRequest.newBuilder(uri(path)).GET(), false);
    }

    public Response post(String path, String body) {
        return send(withBody(path, "POST", body), true);
    }

    public Response put(String path, String body) {
        return send(withBody(path, "PUT", body), true);
    }

    public Response patch(String path, String body) {
        return send(withBody(path, "PATCH", body), true);
    }

    public Response delete(String path) {
        return send(HttpRequest.newBuilder(uri(path)).DELETE(), true);
    }

    public Response upload(String path, String filename, String contentType, byte[] data) {
        String boundary = "----almanook" + System.nanoTime();
        java.io.ByteArrayOutputStream body = new java.io.ByteArrayOutputStream();
        String head = "--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"" + filename
                + "\"\r\nContent-Type: " + contentType + "\r\n\r\n";
        body.writeBytes(head.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        body.writeBytes(data);
        body.writeBytes(("\r\n--" + boundary + "--\r\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return send(
                HttpRequest.newBuilder(uri(path))
                        .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                        .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray())),
                true);
    }

    public HttpResponse<byte[]> getBytes(String path) {
        try {
            return http.send(HttpRequest.newBuilder(uri(path)).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    /** Sends without the CSRF header, to prove protection is active. */
    public Response postWithoutCsrf(String path, String body) {
        return send(withBody(path, "POST", body), false);
    }

    public Response send(HttpRequest.Builder builder, boolean withCsrf) {
        if (withCsrf) {
            csrfToken().ifPresent(token -> builder.header("X-XSRF-TOKEN", token));
        }
        try {
            HttpResponse<String> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            JsonNode body = response.body() == null || response.body().isBlank()
                    ? null
                    : response.headers().firstValue("Content-Type").orElse("").contains("json")
                            ? JSON.readTree(response.body())
                            : null;
            return new Response(response.statusCode(), body, response.body(), response);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    public String baseUrl() {
        return baseUrl;
    }

    private HttpRequest.Builder withBody(String path, String method, String body) {
        return HttpRequest.newBuilder(uri(path))
                .header("Content-Type", "application/json")
                .method(method, HttpRequest.BodyPublishers.ofString(body == null ? "" : body));
    }

    private java.util.Optional<String> csrfToken() {
        return cookies.getCookieStore().getCookies().stream()
                .filter(c -> c.getName().equals("XSRF-TOKEN"))
                .map(HttpCookie::getValue)
                .findFirst();
    }

    private URI uri(String path) {
        return URI.create(baseUrl + path);
    }
}
