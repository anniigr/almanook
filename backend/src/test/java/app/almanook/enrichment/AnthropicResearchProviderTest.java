package app.almanook.enrichment;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.almanook.card.ItemType;
import app.almanook.card.Season;
import app.almanook.enrichment.provider.AiProperties;
import app.almanook.enrichment.provider.anthropic.AnthropicResearchProvider;
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

/** Contract test: the real Anthropic Java SDK against a WireMock stand-in for the Messages API. */
class AnthropicResearchProviderTest {

    WireMockServer api;
    AnthropicResearchProvider provider;
    final ResearchRequest request =
            new ResearchRequest("Ogród Botaniczny w Powsinie", null, null, LocalDate.of(2026, 10, 25), null);

    @BeforeEach
    void start() {
        api = new WireMockServer(options().dynamicPort());
        api.start();
        provider = providerWith(true);
    }

    @AfterEach
    void stop() {
        api.stop();
    }

    AnthropicResearchProvider providerWith(boolean fallback) {
        AiProperties.Anthropic props = new AiProperties.Anthropic(
                "sk-ant-api03-test", api.baseUrl(), "claude-opus-5-5", "low", 4, fallback, Duration.ofSeconds(5));
        return new AnthropicResearchProvider(
                props,
                JsonMapper.builder().build(),
                Validation.buildDefaultValidatorFactory().getValidator());
    }

    static String fixture(String name) {
        try (InputStream in = AnthropicResearchProviderTest.class.getResourceAsStream("/anthropic/" + name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    void stubMessages(int status, String body) {
        api.stubFor(post(urlEqualTo("/v1/messages"))
                .willReturn(aResponse()
                        .withStatus(status)
                        .withHeader("Content-Type", "application/json")
                        .withBody(body)));
    }

    @Test
    void researchSendsWebSearchToolAndCollectsEveryRetrievedUrl() {
        stubMessages(200, fixture("research.json"));

        ResearchNotes notes = provider.research(request);

        assertThat(notes.retrievedUrls())
                .extracting(SourceRef::url)
                .containsExactly("https://www.ogrod-powsin.pl/", "https://www.ogrod-powsin.pl/bilety/");
        assertThat(notes.officialUrl()).isEqualTo("https://www.ogrod-powsin.pl/");
        assertThat(notes.notes()).contains("15 PLN");
        api.verify(postRequestedFor(urlEqualTo("/v1/messages"))
                .withHeader(
                        "Authorization",
                        equalTo("Bearer sk-ant-api03-test")) // API version 2026-10-21 sends keys as Bearer
                .withHeader("anthropic-version", equalTo("2026-10-21"))
                .withHeader("anthropic-beta", containing("server-side-fallback-2026-07-01"))
                .withRequestBody(matchingJsonPath("$.model", equalTo("claude-opus-5-5")))
                .withRequestBody(matchingJsonPath("$.tools[0].type", equalTo("web_search_20260209")))
                .withRequestBody(matchingJsonPath("$.tools[0].max_uses", equalTo("4")))
                .withRequestBody(matchingJsonPath("$.fallbacks", equalTo("default")))
                .withRequestBody(matchingJsonPath("$.system", containing("Never rely on memory"))));
    }

    @Test
    void extractionUsesStructuredOutputWithoutToolsAndParsesTheProposal() {
        stubMessages(200, fixture("extraction.json"));

        AiCardProposal p = provider.extract(request, Fixtures.notes("https://www.ogrod-powsin.pl/"));

        assertThat(p.title()).isEqualTo("Ogród Botaniczny PAN w Powsinie");
        assertThat(p.itemType()).isEqualTo(ItemType.PLACE);
        assertThat(p.seasons()).containsExactly(Season.SPRING, Season.SUMMER, Season.AUTUMN);
        assertThat(p.priceMin()).isEqualByComparingTo("15");
        assertThat(p.confidence().price()).isEqualTo(ConfidenceLevel.HIGH);
        api.verify(postRequestedFor(urlEqualTo("/v1/messages"))
                .withRequestBody(matchingJsonPath("$.output_config.format.type", equalTo("json_schema")))
                .withRequestBody(
                        matchingJsonPath("$.output_config.format.schema.additionalProperties", equalTo("false")))
                .withRequestBody(matchingJsonPath("$.output_config.effort", equalTo("low")))
                .withRequestBody(matchingJsonPath("$.messages[0].content", containing("<research_notes>")))
                .withRequestBody(matchingJsonPath("$[?(!@.tools)]")));
    }

    @Test
    void endToEndTheUncitedOpeningHoursAreDroppedByTheAssembler() {
        stubMessages(200, fixture("research.json"));
        ResearchNotes notes = provider.research(request);
        stubMessages(200, fixture("extraction.json"));
        AiCardProposal proposal = provider.extract(request, notes);

        var draft = DraftAssembler.assemble(
                new DraftRequest("Ogród Botaniczny w Powsinie", null, null, null, null), null, notes, proposal);

        assertThat(draft.card().priceMin()).isEqualByComparingTo("15.00");
        assertThat(draft.card().address()).isEqualTo("ul. Prawdziwka 2");
        assertThat(draft.card().openingHoursNote()).isNull();
        assertThat(draft.unverifiedFields()).containsExactly("openingHoursNote");
        assertThat(draft.card().expiresOn()).isNull();
        assertThat(draft.card().sourceUrl()).isEqualTo("https://www.ogrod-powsin.pl/");
    }

    @Test
    void fallbackParameterCanBeDisabled() {
        stubMessages(200, fixture("research.json"));
        providerWith(false).research(request);
        api.verify(
                postRequestedFor(urlEqualTo("/v1/messages")).withRequestBody(matchingJsonPath("$[?(!@.fallbacks)]")));
    }

    @Test
    void pauseTurnIsResumedWithThePartialAssistantTurn() {
        String paused = fixture("research.json").replace("\"end_turn\"", "\"pause_turn\"");
        api.stubFor(post(urlEqualTo("/v1/messages"))
                .inScenario("pause")
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(paused))
                .willSetStateTo("resumed"));
        api.stubFor(post(urlEqualTo("/v1/messages"))
                .inScenario("pause")
                .whenScenarioStateIs("resumed")
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(fixture("research.json"))));

        provider.research(request);

        api.verify(2, postRequestedFor(urlEqualTo("/v1/messages")));
        api.verify(postRequestedFor(urlEqualTo("/v1/messages"))
                .withRequestBody(matchingJsonPath("$.messages[1].role", equalTo("assistant"))));
    }

    @Test
    void refusalIsReportedAndNeverTurnedIntoAFakeDraft() {
        stubMessages(200, fixture("extraction.json").replace("\"end_turn\"", "\"refusal\""));
        assertThatThrownBy(() -> provider.extract(request, ResearchNotes.none()))
                .isInstanceOf(AiProviderException.class)
                .hasMessageContaining("declined");
    }

    @Test
    void truncatedStructuredOutputIsRejected() {
        stubMessages(200, fixture("extraction.json").replace("\"end_turn\"", "\"max_tokens\""));
        assertThatThrownBy(() -> provider.extract(request, ResearchNotes.none()))
                .isInstanceOf(AiProviderException.class)
                .hasMessageContaining("cut off");
    }

    @Test
    void malformedOrInvalidJsonIsRejected() {
        stubMessages(
                200,
                fixture("extraction.json")
                        .replaceFirst("\\\\\"title\\\\\": \\\\\"[^\\\\]*\\\\\"", "\\\\\"title\\\\\": \\\\\"\\\\\""));
        assertThatThrownBy(() -> provider.extract(request, ResearchNotes.none()))
                .isInstanceOf(AiProviderException.class);

        assertThatThrownBy(() -> provider.parseProposalForTest("{\"title\": \"x\""))
                .isInstanceOf(AiProviderException.class)
                .hasMessageContaining("unexpected format");
    }

    @Test
    void authenticationAndServerErrorsBecomeProviderErrors() {
        stubMessages(
                401,
                "{\"type\":\"error\",\"error\":{\"type\":\"authentication_error\",\"message\":\"invalid x-api-key\"}}");
        assertThatThrownBy(() -> provider.research(request))
                .isInstanceOf(AiProviderException.class)
                .hasMessageContaining("API key");

        api.resetAll();
        stubMessages(529, "{\"type\":\"error\",\"error\":{\"type\":\"overloaded_error\",\"message\":\"Overloaded\"}}");
        assertThatThrownBy(() -> provider.research(request))
                .isInstanceOf(AiProviderException.class)
                .hasMessageContaining("temporarily unavailable");
    }
}
