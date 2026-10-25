package app.almanook.api;

import static org.assertj.core.api.Assertions.assertThat;

import app.almanook.support.AbstractIntegrationTest;
import app.almanook.support.ApiClient;
import app.almanook.support.LocalSiteServer;
import java.net.http.HttpResponse;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import tools.jackson.databind.JsonNode;

/** No ANTHROPIC_API_KEY: the app still works, explains why AI is off, and imports page metadata only. */
@Import(LocalSiteServer.LoopbackFetchConfig.class)
class EnrichmentWithoutAiIT extends AbstractIntegrationTest {

    @LocalServerPort
    int port;

    ApiClient ada;

    @BeforeEach
    void setUp() {
        resetDatabase();
        ada = new ApiClient(port).register("ada@example.com", "correct-horse-battery", "Ada");
    }

    @Test
    void statusExplainsThatAiIsNotConfigured() {
        JsonNode status = ada.get("/api/v1/enrichment/status").body();
        assertThat(status.get("aiAvailable").asBoolean()).isFalse();
        assertThat(status.get("message").asString()).contains("ANTHROPIC_API_KEY");
    }

    @Test
    void nameOnlyGenerationIsUnavailableWithAClearMessage() {
        ApiClient.Response r = ada.post("/api/v1/enrichment/drafts", ApiClient.json(Map.of("query", "Opera Narodowa")));
        assertThat(r.status()).isEqualTo(503);
        assertThat(r.body().get("code").asString()).isEqualTo("ai_unavailable");
        assertThat(r.body().get("detail").asString()).contains("manually");
    }

    @Test
    void urlImportBuildsABasicDraftFromThePagesOwnStructuredData() {
        ApiClient.Response r = ada.post(
                "/api/v1/enrichment/drafts",
                ApiClient.json(Map.of("url", LocalSiteServer.url("/exhibition"), "notes", "with Ola")));
        assertThat(r.status()).as(r.raw()).isEqualTo(200);
        JsonNode draft = r.body();
        assertThat(draft.get("mode").asString()).isEqualTo("BASIC");
        assertThat(draft.at("/card/title").asString()).isEqualTo("Tamara Łempicka");
        assertThat(draft.at("/card/itemType").asString()).isEqualTo("EXHIBITION");
        assertThat(draft.at("/card/eventEndDate").asString()).isEqualTo("2027-01-17");
        assertThat(draft.at("/card/expiresOn").asString()).isEqualTo("2027-01-17");
        assertThat(draft.at("/card/expirationSource").asString()).isEqualTo("SOURCE");
        assertThat(draft.at("/card/priceMin").decimalValue()).isEqualByComparingTo("25.00");
        assertThat(draft.at("/originalInput").asString()).contains("Notes: with Ola");
        assertThat(draft.get("warnings").toString()).contains("not configured");

        // Saving the draft works through the ordinary card API and keeps provenance.
        ApiClient.Response saved = ada.post(
                "/api/v1/cards",
                ApiClient.json(Map.of(
                        "card", draft.get("card"),
                        "destination", "DISCOVER",
                        "originalInput", draft.get("originalInput").asString(),
                        "enrichment", draft.get("enrichment"))));
        assertThat(saved.status()).as(saved.raw()).isEqualTo(201);
        assertThat(saved.body().at("/enrichment/mode").asString()).isEqualTo("BASIC");
        assertThat(saved.body().at("/discovery/status").asString()).isEqualTo("PENDING");
        assertThat(saved.body().get("expirationSource").asString()).isEqualTo("SOURCE");
    }

    @Test
    void unsafeUrlsAreRejected() {
        ApiClient.Response r = ada.post(
                "/api/v1/enrichment/drafts", ApiClient.json(Map.of("url", "http://169.254.169.254/latest/meta-data/")));
        assertThat(r.status()).isEqualTo(400);
        assertThat(r.body().get("code").asString()).isEqualTo("unsafe_url");
    }

    @Test
    void uploadsAreVerifiedByContentAndPrivateToTheirOwner() {
        ApiClient.Response upload = ada.upload("/api/v1/images", "cover.png", "image/png", LocalSiteServer.TINY_PNG());
        assertThat(upload.status()).as(upload.raw()).isEqualTo(201);
        String id = upload.body().get("id").asString();

        HttpResponse<byte[]> image = ada.getBytes("/api/v1/images/" + id);
        assertThat(image.statusCode()).isEqualTo(200);
        assertThat(image.headers().firstValue("Content-Type")).contains("image/png");
        assertThat(image.body()).isEqualTo(LocalSiteServer.TINY_PNG());

        ApiClient.Response disguised =
                ada.upload("/api/v1/images", "evil.png", "image/png", "<script>alert(1)</script>".getBytes());
        assertThat(disguised.status()).isEqualTo(415);

        ApiClient eve = new ApiClient(port).register("eve@example.com", "correct-horse-battery", "Eve");
        assertThat(eve.getBytes("/api/v1/images/" + id).statusCode()).isEqualTo(404);
        ApiClient.Response stolenCover = eve.post(
                "/api/v1/cards",
                Cards.card("Eve's card", "PLACE")
                        .with("cover", Map.of("source", "UPLOAD", "uploadId", id))
                        .create("COLLECTION"));
        assertThat(stolenCover.status()).isEqualTo(400);

        ApiClient.Response ownCover = ada.post(
                "/api/v1/cards",
                Cards.card("Ada's card", "PLACE")
                        .with("cover", Map.of("source", "UPLOAD", "uploadId", id))
                        .create("COLLECTION"));
        assertThat(ownCover.status()).isEqualTo(201);
        assertThat(ownCover.body().at("/cover/url").asString()).isEqualTo("/api/v1/images/" + id);
    }

    @Test
    void pastedImageUrlsAreCheckedSafely() {
        assertThat(ada.post("/api/v1/images/check-url", ApiClient.json(Map.of("url", "https://169.254.169.254/x.png")))
                        .body()
                        .get("usable")
                        .asBoolean())
                .isFalse();
        assertThat(ada.post("/api/v1/images/check-url", ApiClient.json(Map.of("url", "http://example.com/x.png")))
                        .body()
                        .get("usable")
                        .asBoolean())
                .isFalse();
    }
}
