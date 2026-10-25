package app.almanook.api;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

import app.almanook.support.AbstractIntegrationTest;
import app.almanook.support.ApiClient;
import com.github.tomakehurst.wiremock.WireMockServer;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;

/** Full draft flow with Gemini selected as the provider, pointed at a WireMock Interactions API. */
class EnrichmentWithGeminiIT extends AbstractIntegrationTest {

    static final WireMockServer GEMINI = new WireMockServer(options().dynamicPort());

    static {
        GEMINI.start();
    }

    @DynamicPropertySource
    static void provider(DynamicPropertyRegistry registry) {
        registry.add("almanook.ai.provider", () -> "gemini");
        registry.add("almanook.ai.gemini.api-key", () -> "gemini-test-key");
        registry.add("almanook.ai.gemini.base-url", GEMINI::baseUrl);
    }

    @AfterAll
    static void stopProvider() {
        GEMINI.stop();
    }

    @LocalServerPort
    int port;

    ApiClient ada;

    @BeforeEach
    void setUp() {
        resetDatabase();
        GEMINI.resetAll();
        ada = new ApiClient(port).register("ada@example.com", "correct-horse-battery", "Ada");
    }

    static String fixture(String name) {
        try (InputStream in = EnrichmentWithGeminiIT.class.getResourceAsStream("/gemini/" + name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("REDIRECT_BASE", GEMINI.baseUrl());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void generatesAVerifiedDraftWithGemini() {
        GEMINI.stubFor(post(urlEqualTo("/v1beta/interactions"))
                .withRequestBody(containing("google_search"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(fixture("research.json"))));
        GEMINI.stubFor(post(urlEqualTo("/v1beta/interactions"))
                .withRequestBody(containing("response_format"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(fixture("extraction.json"))));

        JsonNode status = ada.get("/api/v1/enrichment/status").body();
        assertThat(status.get("aiAvailable").asBoolean()).isTrue();
        assertThat(status.get("provider").asString()).isEqualTo("gemini");

        ApiClient.Response r =
                ada.post("/api/v1/enrichment/drafts", ApiClient.json(Map.of("query", "Ogród Botaniczny w Powsinie")));
        assertThat(r.status()).as(r.raw()).isEqualTo(200);
        JsonNode draft = r.body();
        assertThat(draft.get("mode").asString()).isEqualTo("AI");
        assertThat(draft.at("/card/priceMin").decimalValue()).isEqualByComparingTo("15.00");
        assertThat(draft.at("/card/priceSource").asString()).isEqualTo("SOURCE");
        assertThat(draft.at("/card/openingHoursNote").isNull()).isTrue();
        assertThat(draft.at("/enrichment/provider").asString()).isEqualTo("gemini");
        assertThat(draft.at("/enrichment/model").asString()).isEqualTo("gemini-3.5-flash-lite");
    }

    @Test
    void outageIsReportedWithoutLeakingProviderDetails() {
        GEMINI.stubFor(
                post(urlEqualTo("/v1beta/interactions"))
                        .willReturn(
                                aResponse()
                                        .withStatus(400)
                                        .withHeader("Content-Type", "application/json")
                                        .withBody(
                                                "{\"error\":{\"code\":400,\"message\":\"secret detail\",\"status\":\"INVALID_ARGUMENT\"}}")));

        ApiClient.Response r = ada.post("/api/v1/enrichment/drafts", ApiClient.json(Map.of("query", "Opera Narodowa")));
        assertThat(r.status()).isEqualTo(502);
        assertThat(r.body().get("code").asString()).isEqualTo("ai_provider_error");
        assertThat(r.raw()).doesNotContain("secret detail");
    }
}
