package app.almanook.api;

import static org.assertj.core.api.Assertions.assertThat;

import app.almanook.support.AbstractIntegrationTest;
import app.almanook.support.ApiClient;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;

class AuthApiIT extends AbstractIntegrationTest {

    @LocalServerPort
    int port;

    @BeforeEach
    void clean() {
        resetDatabase();
    }

    @Test
    void protectedEndpointsRequireASession() {
        ApiClient.Response r = new ApiClient(port).get("/api/v1/me");
        assertThat(r.status()).isEqualTo(401);
        assertThat(r.body().get("code").asString()).isEqualTo("unauthenticated");
        assertThat(r.http().headers().firstValue("Content-Type"))
                .hasValueSatisfying(ct -> assertThat(ct).contains("application/problem+json"));
    }

    @Test
    void registerLoginLogoutRoundTrip() {
        ApiClient client = new ApiClient(port).register("Ada@Example.com", "correct-horse-battery", "Ada");
        ApiClient.Response me = client.get("/api/v1/me");
        assertThat(me.status()).isEqualTo(200);
        assertThat(me.body().get("email").asString()).isEqualTo("ada@example.com");
        assertThat(me.body().at("/preferences/timezone").asString()).isEqualTo("Europe/Warsaw");
        assertThat(me.body().at("/preferences/theme").asString()).isEqualTo("SYSTEM");

        assertThat(client.post("/api/v1/auth/logout", "").status()).isEqualTo(204);
        assertThat(client.get("/api/v1/me").status()).isEqualTo(401);

        ApiClient fresh = new ApiClient(port).refreshCsrf();
        ApiClient.Response bad = fresh.post(
                "/api/v1/auth/login", ApiClient.json(Map.of("email", "ada@example.com", "password", "wrong-password")));
        assertThat(bad.status()).isEqualTo(401);
        assertThat(bad.body().get("code").asString()).isEqualTo("bad_credentials");

        ApiClient.Response ok = fresh.post(
                "/api/v1/auth/login",
                ApiClient.json(Map.of("email", "ADA@example.com", "password", "correct-horse-battery")));
        assertThat(ok.status()).isEqualTo(200);
        assertThat(fresh.get("/api/v1/me").status()).isEqualTo(200);
    }

    @Test
    void duplicateEmailIsRejected() {
        new ApiClient(port).register("ada@example.com", "correct-horse-battery", "Ada");
        ApiClient other = new ApiClient(port).refreshCsrf();
        ApiClient.Response r = other.post(
                "/api/v1/auth/register",
                ApiClient.json(Map.of("email", "ada@example.com", "password", "another-password", "displayName", "X")));
        assertThat(r.status()).isEqualTo(409);
        assertThat(r.body().get("code").asString()).isEqualTo("email_taken");
    }

    @Test
    void shortPasswordsAreRejectedWithFieldErrors() {
        ApiClient client = new ApiClient(port).refreshCsrf();
        ApiClient.Response r = client.post(
                "/api/v1/auth/register",
                ApiClient.json(Map.of("email", "not-an-email", "password", "short", "displayName", "")));
        assertThat(r.status()).isEqualTo(400);
        assertThat(r.body().get("code").asString()).isEqualTo("validation_failed");
        assertThat(r.body().get("errors").size()).isEqualTo(3);
    }

    @Test
    void stateChangingRequestsWithoutCsrfTokenAreForbidden() {
        ApiClient client = new ApiClient(port).register("ada@example.com", "correct-horse-battery", "Ada");
        ApiClient.Response r = client.postWithoutCsrf(
                "/api/v1/cards", ApiClient.json(Map.of("card", Map.of("title", "X", "itemType", "PLACE"))));
        assertThat(r.status()).isEqualTo(403);
        assertThat(r.body().get("code").asString()).isEqualTo("csrf_invalid");
    }

    @Test
    void preferencesArePersistedAndValidated() {
        ApiClient client = new ApiClient(port).register("ada@example.com", "correct-horse-battery", "Ada");
        ApiClient.Response bad = client.put(
                "/api/v1/me/preferences",
                ApiClient.json(Map.of("theme", "DARK", "language", "pl", "timezone", "Mars/Olympus")));
        assertThat(bad.status()).isEqualTo(400);

        ApiClient.Response ok = client.put(
                "/api/v1/me/preferences",
                ApiClient.json(Map.of("theme", "LIGHT", "language", "pl", "timezone", "America/New_York")));
        assertThat(ok.status()).isEqualTo(200);
        assertThat(client.get("/api/v1/me").body().at("/preferences/theme").asString())
                .isEqualTo("LIGHT");
        assertThat(client.get("/api/v1/me").body().at("/preferences/timezone").asString())
                .isEqualTo("America/New_York");
    }

    @Test
    void healthIsPublicAndSecurityHeadersArePresent() {
        ApiClient.Response health = new ApiClient(port).get("/actuator/health");
        assertThat(health.status()).isEqualTo(200);
        assertThat(health.http().headers().firstValue("X-Frame-Options")).contains("DENY");
        assertThat(health.http().headers().firstValue("Content-Security-Policy"))
                .isPresent();
        assertThat(health.http().headers().firstValue("X-Content-Type-Options")).contains("nosniff");
    }
}
