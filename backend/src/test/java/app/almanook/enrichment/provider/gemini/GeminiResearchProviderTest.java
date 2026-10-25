package app.almanook.enrichment.provider.gemini;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.notContaining;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.almanook.card.ItemType;
import app.almanook.card.Season;
import app.almanook.enrichment.AiCardProposal;
import app.almanook.enrichment.AiProviderException;
import app.almanook.enrichment.ConfidenceLevel;
import app.almanook.enrichment.DraftAssembler;
import app.almanook.enrichment.DraftRequest;
import app.almanook.enrichment.ResearchNotes;
import app.almanook.enrichment.ResearchRequest;
import app.almanook.enrichment.SourceRef;
import app.almanook.enrichment.provider.AiProperties;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import jakarta.validation.Validation;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/** Contract test: the Gemini adapter against a WireMock stand-in for the Interactions API. */
class GeminiResearchProviderTest {

    static final String INTERACTIONS = "/v1beta/interactions";

    WireMockServer api;
    GeminiResearchProvider provider;
    final ResearchRequest request =
            new ResearchRequest("Ogród Botaniczny w Powsinie", null, null, LocalDate.of(2026, 10, 25), null);

    @BeforeEach
    void start() {
        api = new WireMockServer(options().dynamicPort());
        api.start();
        AiProperties.Gemini props = new AiProperties.Gemini(
                "gemini-test-key", api.baseUrl(), "gemini-3.8-flash", "low", Duration.ofSeconds(5));
        // The WireMock host stands in for Google's grounding redirect service.
        provider = new GeminiResearchProvider(
                props,
                JsonMapper.builder().build(),
                Validation.buildDefaultValidatorFactory().getValidator(),
                "localhost",
                Duration.ZERO);
        api.stubFor(get(urlEqualTo("/grounding-api-redirect/abc"))
                .willReturn(aResponse().withStatus(302).withHeader("Location", "https://www.ogrod-powsin.pl/")));
    }

    @AfterEach
    void stop() {
        api.stop();
    }

    String fixture(String name) {
        try (InputStream in = getClass().getResourceAsStream("/gemini/" + name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("REDIRECT_BASE", api.baseUrl());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    void stubInteractions(int status, String body) {
        api.stubFor(post(urlEqualTo(INTERACTIONS))
                .willReturn(aResponse()
                        .withStatus(status)
                        .withHeader("Content-Type", "application/json")
                        .withBody(body)));
    }

    @Test
    void researchUsesSearchAndUrlContextAndKeepsOnlySuccessfullyRetrievedUrls() {
        stubInteractions(200, fixture("research.json"));

        ResearchNotes notes = provider.research(request);

        assertThat(notes.retrievedUrls())
                .extracting(SourceRef::url)
                .containsExactly("https://www.ogrod-powsin.pl/bilety/", "https://www.ogrod-powsin.pl/")
                .doesNotContain("https://www.ogrod-powsin.pl/godziny/");
        assertThat(notes.officialUrl()).isEqualTo("https://www.ogrod-powsin.pl/");
        assertThat(notes.notes()).contains("15 PLN").doesNotContain("Looking for");
        api.verify(postRequestedFor(urlEqualTo(INTERACTIONS))
                .withHeader("x-goog-api-key", equalTo("gemini-test-key"))
                .withRequestBody(matchingJsonPath("$.model", equalTo("gemini-3.8-flash")))
                .withRequestBody(matchingJsonPath("$.store", equalTo("false")))
                .withRequestBody(matchingJsonPath("$.tools[0].type", equalTo("google_search")))
                .withRequestBody(matchingJsonPath("$.tools[1].type", equalTo("url_context")))
                .withRequestBody(matchingJsonPath("$.generation_config.thinking_level", equalTo("low")))
                .withRequestBody(matchingJsonPath("$.system_instruction", containing("Never rely on memory"))));
    }

    @Test
    void extractionUsesSchemaConstrainedJsonWithoutTools() {
        stubInteractions(200, fixture("extraction.json"));

        AiCardProposal p = provider.extract(
                request, new ResearchNotes("price: 15 PLN", java.util.List.of(), "https://www.ogrod-powsin.pl/"));

        assertThat(p.title()).isEqualTo("Ogród Botaniczny PAN w Powsinie");
        assertThat(p.itemType()).isEqualTo(ItemType.PLACE);
        assertThat(p.seasons()).containsExactly(Season.SPRING, Season.SUMMER, Season.AUTUMN);
        assertThat(p.confidence().price()).isEqualTo(ConfidenceLevel.HIGH);
        api.verify(postRequestedFor(urlEqualTo(INTERACTIONS))
                .withRequestBody(matchingJsonPath("$.response_format.mime_type", equalTo("application/json")))
                .withRequestBody(matchingJsonPath("$.response_format.schema.additionalProperties", equalTo("false")))
                .withRequestBody(matchingJsonPath("$.input", containing("<research_notes>")))
                .withRequestBody(matchingJsonPath("$[?(!@.tools)]")));
    }

    @Test
    void endToEndFactsWithoutARetrievedSourceAreDropped() {
        stubInteractions(200, fixture("research.json"));
        ResearchNotes notes = provider.research(request);
        api.resetMappings();
        stubInteractions(200, fixture("extraction.json"));
        AiCardProposal proposal = provider.extract(request, notes);

        var draft = DraftAssembler.assemble(
                new DraftRequest("Ogród Botaniczny w Powsinie", null, null, null, null), null, notes, proposal);

        assertThat(draft.card().priceMin()).isEqualByComparingTo("15.00");
        // Backed by the grounding link that resolved to the official site.
        assertThat(draft.card().address()).isEqualTo("ul. Prawdziwka 2");
        // The hours page failed to load, so the hours stay empty and are flagged.
        assertThat(draft.card().openingHoursNote()).isNull();
        assertThat(draft.unverifiedFields()).containsExactly("openingHoursNote");
    }

    @Test
    void unresolvableGroundingLinksAreDropped() {
        api.resetMappings();
        stubInteractions(200, fixture("research.json"));

        ResearchNotes notes = provider.research(request);

        assertThat(notes.retrievedUrls())
                .extracting(SourceRef::url)
                .containsExactly("https://www.ogrod-powsin.pl/bilety/");
    }

    @Test
    void incompleteExtractionIsRejectedAsCutOff() {
        stubInteractions(200, fixture("extraction.json").replace("\"completed\"", "\"incomplete\""));
        assertThatThrownBy(() -> provider.extract(request, ResearchNotes.none()))
                .isInstanceOf(AiProviderException.class)
                .hasMessageContaining("cut off");
    }

    @Test
    void failedInteractionAndEmptyOutputAreNeverTurnedIntoAFakeDraft() {
        stubInteractions(
                200,
                "{\"id\":\"v1_x\",\"status\":\"failed\",\"steps\":[],"
                        + "\"errors\":[{\"code\":\"internal\",\"message\":\"boom\"}]}");
        assertThatThrownBy(() -> provider.research(request)).isInstanceOf(AiProviderException.class);

        api.resetMappings();
        stubInteractions(200, "{\"id\":\"v1_y\",\"status\":\"completed\",\"steps\":[]}");
        assertThatThrownBy(() -> provider.extract(request, ResearchNotes.none()))
                .isInstanceOf(AiProviderException.class)
                .hasMessageContaining("declined");
    }

    @Test
    void invalidJsonIsRejected() {
        stubInteractions(
                200,
                "{\"status\":\"completed\",\"steps\":[{\"type\":\"model_output\",\"content\":"
                        + "[{\"type\":\"text\",\"text\":\"{\\\"title\\\": \\\"x\\\"\"}]}]}");
        assertThatThrownBy(() -> provider.extract(request, ResearchNotes.none()))
                .isInstanceOf(AiProviderException.class)
                .hasMessageContaining("unexpected format");
    }

    @Test
    void invalidKeyRateLimitAndServerErrorsBecomeProviderErrors() {
        stubInteractions(
                400,
                "{\"error\":{\"code\":400,\"message\":\"API key not valid. Please pass a valid API key.\","
                        + "\"status\":\"INVALID_ARGUMENT\",\"details\":[{\"reason\":\"API_KEY_INVALID\"}]}}");
        assertThatThrownBy(() -> provider.research(request))
                .isInstanceOf(AiProviderException.class)
                .hasMessageContaining("API key");

        api.resetAll();
        stubInteractions(
                429, "{\"error\":{\"code\":\"too_many_requests\",\"message\":\"You exceeded your current quota\"}}");
        assertThatThrownBy(() -> provider.extract(request, ResearchNotes.none()))
                .isInstanceOf(AiProviderException.class)
                .hasMessageContaining("usage limit");
        // Quota errors are not retried.
        api.verify(1, postRequestedFor(urlEqualTo(INTERACTIONS)));

        api.resetAll();
        stubInteractions(503, "{\"error\":{\"code\":503,\"status\":\"UNAVAILABLE\"}}");
        assertThatThrownBy(() -> provider.research(request))
                .isInstanceOf(AiProviderException.class)
                .hasMessageContaining("temporarily unavailable");
    }

    @Test
    void withoutSearchQuotaResearchContinuesWithUrlContextOnly() {
        api.stubFor(post(urlEqualTo(INTERACTIONS))
                .withRequestBody(containing("google_search"))
                .willReturn(aResponse()
                        .withStatus(429)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"error\":{\"code\":\"too_many_requests\","
                                + "\"message\":\"You exceeded your current quota, please check your plan\"}}")));
        api.stubFor(post(urlEqualTo(INTERACTIONS))
                .withRequestBody(notContaining("google_search"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(fixture("research.json"))));

        assertThat(provider.research(request).notes()).contains("15 PLN");
        api.verify(postRequestedFor(urlEqualTo(INTERACTIONS))
                .withRequestBody(matchingJsonPath("$.tools[0].type", equalTo("url_context")))
                .withRequestBody(matchingJsonPath("$.system_instruction", containing("Web search is not available"))));

        // Once the key is known to lack search quota, later requests skip search straight away.
        api.resetRequests();
        provider.research(request);
        api.verify(1, postRequestedFor(urlEqualTo(INTERACTIONS)));
        api.verify(0, postRequestedFor(urlEqualTo(INTERACTIONS)).withRequestBody(containing("google_search")));
    }

    @Test
    void transientServerErrorIsRetried() {
        api.stubFor(post(urlEqualTo(INTERACTIONS))
                .inScenario("retry")
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse().withStatus(503))
                .willSetStateTo("ok"));
        api.stubFor(post(urlEqualTo(INTERACTIONS))
                .inScenario("retry")
                .whenScenarioStateIs("ok")
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(fixture("research.json"))));

        assertThat(provider.research(request).notes()).contains("15 PLN");
        api.verify(2, postRequestedFor(urlEqualTo(INTERACTIONS)));
    }
}
