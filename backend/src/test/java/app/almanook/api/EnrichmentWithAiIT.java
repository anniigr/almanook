package app.almanook.api;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
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

/** Full draft flow through the real Anthropic SDK and Wikimedia adapter, both pointed at WireMock. */
class EnrichmentWithAiIT extends AbstractIntegrationTest {

    static final WireMockServer PROVIDERS = new WireMockServer(options().dynamicPort());

    static {
        PROVIDERS.start();
    }

    @DynamicPropertySource
    static void providers(DynamicPropertyRegistry registry) {
        registry.add("almanook.ai.anthropic.api-key", () -> "sk-ant-api03-test");
        registry.add("almanook.ai.anthropic.base-url", PROVIDERS::baseUrl);
        registry.add("almanook.images.wikimedia.enabled", () -> "true");
        registry.add("almanook.images.wikimedia.api-url", () -> PROVIDERS.baseUrl() + "/w/api.php");
    }

    @AfterAll
    static void stopProviders() {
        PROVIDERS.stop();
    }

    @LocalServerPort
    int port;

    ApiClient ada;

    @BeforeEach
    void setUp() {
        resetDatabase();
        PROVIDERS.resetAll();
        ada = new ApiClient(port).register("ada@example.com", "correct-horse-battery", "Ada");
    }

    static String fixture(String name) {
        try (InputStream in = EnrichmentWithAiIT.class.getResourceAsStream("/anthropic/" + name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    void stubHappyPath() {
        PROVIDERS.stubFor(post(urlEqualTo("/v1/messages"))
                .withRequestBody(containing("web_search_20260209"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(fixture("research.json"))));
        PROVIDERS.stubFor(post(urlEqualTo("/v1/messages"))
                .withRequestBody(containing("json_schema"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(fixture("extraction.json"))));
        PROVIDERS.stubFor(get(urlPathEqualTo("/w/api.php"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(fixture("wikimedia.json"))));
    }

    @Test
    void generatesAVerifiedDraftWithLicensedCoversAndSavesIt() {
        stubHappyPath();
        assertThat(ada.get("/api/v1/enrichment/status")
                        .body()
                        .get("aiAvailable")
                        .asBoolean())
                .isTrue();

        ApiClient.Response r = ada.post(
                "/api/v1/enrichment/drafts",
                ApiClient.json(Map.of("query", "Ogród Botaniczny w Powsinie", "seasons", java.util.List.of("SPRING"))));
        assertThat(r.status()).as(r.raw()).isEqualTo(200);
        JsonNode draft = r.body();

        assertThat(draft.get("mode").asString()).isEqualTo("AI");
        assertThat(draft.at("/card/title").asString()).isEqualTo("Ogród Botaniczny w Powsinie");
        assertThat(draft.at("/suggestions/title").asString()).isEqualTo("Ogród Botaniczny PAN w Powsinie");
        assertThat(draft.at("/card/seasons").toString()).isEqualTo("[\"SPRING\"]");
        assertThat(draft.at("/fieldSources/seasons").asString()).isEqualTo("USER");
        assertThat(draft.at("/card/priceMin").decimalValue()).isEqualByComparingTo("15.00");
        assertThat(draft.at("/card/priceSource").asString()).isEqualTo("SOURCE");
        assertThat(draft.at("/card/openingHoursNote").isNull()).isTrue();
        assertThat(draft.get("unverifiedFields").toString()).contains("openingHoursNote");
        assertThat(draft.at("/card/expiresOn").isNull()).isTrue();
        assertThat(draft.at("/card/sourceUrl").asString()).isEqualTo("https://www.ogrod-powsin.pl/");
        assertThat(draft.get("sources").toString()).contains("https://www.ogrod-powsin.pl/bilety/");

        JsonNode covers = draft.get("coverCandidates");
        assertThat(covers.size()).isEqualTo(1); // the "Fair use" file is excluded
        assertThat(covers.get(0).get("source").asString()).isEqualTo("WIKIMEDIA");
        assertThat(covers.get(0).get("attribution").asString())
                .isEqualTo("Aneta Leszkiewicz Inetta / CC BY 3.0, via Wikimedia Commons");

        JsonNode cover = covers.get(0);
        ApiClient.Response saved = ada.post(
                "/api/v1/cards",
                ApiClient.json(Map.of(
                        "card",
                        Map.of(
                                "title", draft.at("/card/title").asString(),
                                "itemType", draft.at("/card/itemType").asString(),
                                "priceMin", draft.at("/card/priceMin").decimalValue(),
                                "currency", draft.at("/card/currency").asString(),
                                "priceSource", "SOURCE",
                                "seasons", java.util.List.of("SPRING"),
                                "cover",
                                        Map.of(
                                                "source", "WIKIMEDIA",
                                                "url", cover.get("url").asString(),
                                                "attribution",
                                                        cover.get("attribution").asString(),
                                                "attributionUrl",
                                                        cover.get("attributionUrl")
                                                                .asString(),
                                                "license", cover.get("license").asString())),
                        "destination",
                        "COLLECTION",
                        "originalInput",
                        draft.get("originalInput").asString(),
                        "enrichment",
                        draft.get("enrichment"))));
        assertThat(saved.status()).as(saved.raw()).isEqualTo(201);
        assertThat(saved.body().at("/enrichment/model").asString()).isEqualTo("claude-opus-5-5");
        assertThat(saved.body().at("/cover/license").asString()).isEqualTo("CC BY 3.0");
        assertThat(saved.body().get("originalInput").asString()).isEqualTo("Ogród Botaniczny w Powsinie");
    }

    @Test
    void providerOutageIsReportedAndDoesNotAffectTheCollection() {
        PROVIDERS.stubFor(post(urlEqualTo("/v1/messages"))
                .willReturn(aResponse()
                        .withStatus(500)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"type\":\"error\",\"error\":{\"type\":\"api_error\",\"message\":\"boom\"}}")));

        ApiClient.Response r = ada.post("/api/v1/enrichment/drafts", ApiClient.json(Map.of("query", "Opera Narodowa")));
        assertThat(r.status()).isEqualTo(502);
        assertThat(r.body().get("code").asString()).isEqualTo("ai_provider_error");
        assertThat(r.raw()).doesNotContain("boom");

        assertThat(ada.post(
                                "/api/v1/cards",
                                Cards.card("Opera Narodowa", "PERFORMANCE").create("COLLECTION"))
                        .status())
                .isEqualTo(201);
    }
}
