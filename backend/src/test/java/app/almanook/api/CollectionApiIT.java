package app.almanook.api;

import static app.almanook.api.Cards.card;
import static org.assertj.core.api.Assertions.assertThat;

import app.almanook.support.AbstractIntegrationTest;
import app.almanook.support.ApiClient;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import tools.jackson.databind.JsonNode;

class CollectionApiIT extends AbstractIntegrationTest {

    @LocalServerPort
    int port;

    ApiClient ada;

    @BeforeEach
    void setUp() {
        resetDatabase();
        ada = new ApiClient(port).register("ada@example.com", "correct-horse-battery", "Ada");
    }

    String create(Cards c) {
        clock.set(clock.instant().plusSeconds(1)); // distinct saved_at values for RECENT ordering
        ApiClient.Response r = ada.post("/api/v1/cards", c.create("COLLECTION"));
        assertThat(r.status()).as(r.raw()).isEqualTo(201);
        return r.body().get("id").asString();
    }

    static List<String> titles(ApiClient.Response r) {
        List<String> titles = new ArrayList<>();
        r.body().get("items").forEach(i -> titles.add(i.get("title").asString()));
        return titles;
    }

    @Nested
    class Creation {

        @Test
        void manualCardIsCreatedWithUserProvenanceAndAppearsInCollection() {
            ApiClient.Response r = ada.post(
                    "/api/v1/cards",
                    card("Ogród Botaniczny PAN w Powsinie", "PLACE")
                            .with("city", "Warszawa")
                            .with("priceMin", 15)
                            .with("currency", "PLN")
                            .seasons("SPRING", "AUTUMN")
                            .create("COLLECTION"));
            assertThat(r.status()).isEqualTo(201);
            assertThat(r.http().headers().firstValue("Location")).isPresent();
            JsonNode body = r.body();
            assertThat(body.at("/price/source").asString()).isEqualTo("USER");
            assertThat(body.at("/collection/status").asString()).isEqualTo("ACTIVE");
            assertThat(body.get("expiresOn").isNull()).isTrue();
            assertThat(body.get("seasons").toString()).isEqualTo("[\"SPRING\",\"AUTUMN\"]");

            assertThat(titles(ada.get("/api/v1/collection"))).containsExactly("Ogród Botaniczny PAN w Powsinie");
        }

        @Test
        void invalidInputReturnsFieldErrors() {
            ApiClient.Response r = ada.post(
                    "/api/v1/cards",
                    card("  ", "PLACE")
                            .with("priceMin", 20)
                            .with("priceMax", 10)
                            .with("latitude", 52.1)
                            .with("sourceUrl", "javascript:alert(1)")
                            .create("COLLECTION"));
            assertThat(r.status()).isEqualTo(400);
            assertThat(r.body().get("code").asString()).isEqualTo("validation_failed");
            assertThat(r.body().get("errors").toString())
                    .contains("title", "sourceUrl", "coordinatePair", "priceRangeValid", "currencyPresentWithPrice");
        }

        @Test
        void sameSourceUrlCannotBeSavedTwice() {
            String first = create(card("Muzeum Narodowe", "MUSEUM").with("sourceUrl", "https://www.mnw.art.pl/"));
            ApiClient.Response dup = ada.post(
                    "/api/v1/cards",
                    card("MNW", "MUSEUM")
                            .with("sourceUrl", "http://mnw.art.pl?utm_source=instagram")
                            .create("COLLECTION"));
            assertThat(dup.status()).isEqualTo(409);
            assertThat(dup.body().get("code").asString()).isEqualTo("duplicate_card");
            assertThat(dup.body().get("existingCardId").asString()).isEqualTo(first);
        }

        @Test
        void permanentPlacesDoNotReceiveAnExpiryButVerifiedTemporaryEventsDo() {
            ApiClient.Response museum = ada.post(
                    "/api/v1/cards",
                    card("Museum", "MUSEUM")
                            .with("eventEndDate", "2026-12-24")
                            .with("eventDatesSource", "SOURCE")
                            .create("COLLECTION"));
            assertThat(museum.body().get("expiresOn").isNull()).isTrue();

            ApiClient.Response exhibition = ada.post(
                    "/api/v1/cards",
                    card("Exhibition", "EXHIBITION")
                            .with("eventStartDate", "2026-10-01")
                            .with("eventEndDate", "2026-12-24")
                            .with("eventDatesSource", "SOURCE")
                            .create("COLLECTION"));
            assertThat(exhibition.body().get("expiresOn").asString()).isEqualTo("2026-12-24");
            assertThat(exhibition.body().get("expirationSource").asString()).isEqualTo("SOURCE");

            ApiClient.Response unverified = ada.post(
                    "/api/v1/cards",
                    card("Rumoured festival", "FESTIVAL")
                            .with("eventEndDate", "2026-12-24")
                            .with("eventDatesSource", "AI")
                            .create("COLLECTION"));
            assertThat(unverified.body().get("expiresOn").isNull()).isTrue();
        }
    }

    @Nested
    class Updates {

        @Test
        void editedFieldsBecomeUserProvidedAndStaleVersionsAreRejected() {
            ApiClient.Response created = ada.post(
                    "/api/v1/cards",
                    card("Exhibition", "EXHIBITION")
                            .with("priceMin", 30)
                            .with("currency", "PLN")
                            .with("priceSource", "SOURCE")
                            .with("eventEndDate", "2027-01-10")
                            .with("eventDatesSource", "SOURCE")
                            .create("COLLECTION"));
            String id = created.body().get("id").asString();
            long version = created.body().get("version").asLong();

            Map<String, Object> edit = card("Exhibition (edited)", "EXHIBITION")
                    .with("priceMin", 35)
                    .with("currency", "PLN")
                    .with("eventEndDate", "2027-01-10")
                    .with("expiresOn", "2027-01-10")
                    .with("version", version)
                    .fields();
            ApiClient.Response updated = ada.put("/api/v1/cards/" + id, ApiClient.json(edit));
            assertThat(updated.status()).as(updated.raw()).isEqualTo(200);
            assertThat(updated.body().at("/price/source").asString()).isEqualTo("USER");
            assertThat(updated.body().get("eventDatesSource").asString()).isEqualTo("SOURCE");
            assertThat(updated.body().get("expirationSource").asString()).isEqualTo("SOURCE");

            ApiClient.Response stale = ada.put("/api/v1/cards/" + id, ApiClient.json(edit));
            assertThat(stale.status()).isEqualTo(409);
            assertThat(stale.body().get("code").asString()).isEqualTo("stale_version");
        }
    }

    @Nested
    class Filtering {

        @BeforeEach
        void seed() {
            create(card("Botanical garden", "PLACE").seasons("SPRING", "AUTUMN"));
            create(card("Open-air cinema", "EVENT")
                    .seasons("SUMMER")
                    .with("eventStartDate", "2027-07-01")
                    .with("eventDatesSource", "USER"));
            create(card("Christmas market", "MARKET")
                    .seasons("WINTER")
                    .with("description", "Mulled wine on the Old Town square")
                    .with("eventStartDate", "2026-11-21")
                    .with("eventEndDate", "2026-12-23"));
            create(card("Museum of 100% Design", "MUSEUM"));
        }

        @Test
        void singleSeasonExcludesNonSeasonalCards() {
            assertThat(titles(ada.get("/api/v1/collection?season=SPRING"))).containsExactly("Botanical garden");
            assertThat(titles(ada.get("/api/v1/collection?season=AUTUMN"))).containsExactly("Botanical garden");
            assertThat(titles(ada.get("/api/v1/collection?season=WINTER"))).containsExactly("Christmas market");
        }

        @Test
        void allSeasonsIncludesNonSeasonalAndNonSeasonalFilterIsolatesThem() {
            assertThat(titles(ada.get("/api/v1/collection?season=ALL"))).hasSize(4);
            assertThat(titles(ada.get("/api/v1/collection"))).hasSize(4);
            assertThat(titles(ada.get("/api/v1/collection?season=NON_SEASONAL")))
                    .containsExactly("Museum of 100% Design");
        }

        @Test
        void searchMatchesTitleAndDescriptionCaseInsensitivelyAndEscapesWildcards() {
            assertThat(titles(ada.get("/api/v1/collection?q=GARDEN"))).containsExactly("Botanical garden");
            assertThat(titles(ada.get("/api/v1/collection?q=mulled"))).containsExactly("Christmas market");
            assertThat(titles(ada.get("/api/v1/collection?q=100%25"))).containsExactly("Museum of 100% Design");
            assertThat(titles(ada.get("/api/v1/collection?q=_"))).isEmpty();
            assertThat(titles(ada.get("/api/v1/collection?q=garden&season=WINTER")))
                    .isEmpty();
        }

        @Test
        void sortingOptions() {
            assertThat(titles(ada.get("/api/v1/collection?sort=RECENT")))
                    .containsExactly(
                            "Museum of 100% Design", "Christmas market", "Open-air cinema", "Botanical garden");
            assertThat(titles(ada.get("/api/v1/collection?sort=ALPHABETICAL")))
                    .containsExactly(
                            "Botanical garden", "Christmas market", "Museum of 100% Design", "Open-air cinema");
            assertThat(titles(ada.get("/api/v1/collection?sort=EVENT_DATE")))
                    .containsExactly(
                            "Christmas market", "Open-air cinema", "Botanical garden", "Museum of 100% Design");
        }

        @Test
        void pagination() {
            ApiClient.Response first = ada.get("/api/v1/collection?sort=ALPHABETICAL&size=3&page=0");
            assertThat(first.body().get("totalItems").asLong()).isEqualTo(4);
            assertThat(first.body().get("totalPages").asInt()).isEqualTo(2);
            assertThat(first.body().get("hasNext").asBoolean()).isTrue();
            ApiClient.Response second = ada.get("/api/v1/collection?sort=ALPHABETICAL&size=3&page=1");
            assertThat(titles(second)).containsExactly("Open-air cinema");
            assertThat(second.body().get("hasNext").asBoolean()).isFalse();
            assertThat(ada.get("/api/v1/collection?size=500").status()).isEqualTo(400);
            assertThat(ada.get("/api/v1/collection?season=MONSOON").status()).isEqualTo(400);
        }
    }

    @Nested
    class Membership {

        @Test
        void removeIsSoftAndUndoableAndSavingIsIdempotent() {
            String id = create(card("Opera", "PERFORMANCE"));
            assertThat(ada.delete("/api/v1/collection/" + id).status()).isEqualTo(204);
            assertThat(titles(ada.get("/api/v1/collection"))).isEmpty();
            ApiClient.Response card = ada.get("/api/v1/cards/" + id);
            assertThat(card.status()).isEqualTo(200);
            assertThat(card.body().at("/collection/status").asString()).isEqualTo("REMOVED");

            assertThat(ada.put("/api/v1/collection/" + id, "").status()).isEqualTo(201);
            assertThat(ada.put("/api/v1/collection/" + id, "").status()).isEqualTo(200);
            assertThat(titles(ada.get("/api/v1/collection"))).containsExactly("Opera");
            Integer rows = jdbc.queryForObject("SELECT count(*) FROM collection_entry", Integer.class);
            assertThat(rows).isEqualTo(1);
        }

        @Test
        void visitedAndPersonalNoteAreIndependentOfCardFacts() {
            String id = create(card("Film", "FILM").with("releaseYear", 2023).with("creator", "Wim Wenders"));
            ApiClient.Response r = ada.patch(
                    "/api/v1/collection/" + id, ApiClient.json(Map.of("visited", true, "personalNote", "with Ola")));
            assertThat(r.status()).isEqualTo(200);
            assertThat(r.body().at("/collection/visitedAt").isNull()).isFalse();
            assertThat(r.body().at("/collection/personalNote").asString()).isEqualTo("with Ola");
            assertThat(r.body().get("expiresOn").isNull()).isTrue();

            ApiClient.Response unvisit = ada.patch("/api/v1/collection/" + id, "{\"visited\":false}");
            assertThat(unvisit.body().at("/collection/visitedAt").isNull()).isTrue();
            assertThat(unvisit.body().at("/collection/personalNote").asString()).isEqualTo("with Ola");
        }

        @Test
        void permanentDeleteRemovesCardAndEntries() {
            String id = create(card("Temporary", "EVENT"));
            assertThat(ada.delete("/api/v1/cards/" + id).status()).isEqualTo(204);
            assertThat(ada.get("/api/v1/cards/" + id).status()).isEqualTo(404);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM collection_entry", Integer.class))
                    .isZero();
        }
    }

    @Nested
    class Isolation {

        @Test
        void usersCannotSeeOrTouchEachOthersCards() {
            String id = create(card("Ada's secret garden", "PLACE"));
            ApiClient eve = new ApiClient(port).register("eve@example.com", "correct-horse-battery", "Eve");

            assertThat(eve.get("/api/v1/cards/" + id).status()).isEqualTo(404);
            assertThat(eve.put(
                                    "/api/v1/cards/" + id,
                                    ApiClient.json(card("pwned", "PLACE")
                                            .with("version", 0)
                                            .fields()))
                            .status())
                    .isEqualTo(404);
            assertThat(eve.delete("/api/v1/cards/" + id).status()).isEqualTo(404);
            assertThat(eve.put("/api/v1/collection/" + id, "").status()).isEqualTo(404);
            assertThat(eve.delete("/api/v1/collection/" + id).status()).isEqualTo(404);
            assertThat(eve.patch("/api/v1/cards/" + id + "/expiration", "{\"expiresOn\":null}")
                            .status())
                    .isEqualTo(404);
            assertThat(eve.post("/api/v1/discover/" + id + "/dismiss", "").status())
                    .isEqualTo(404);
            assertThat(titles(eve.get("/api/v1/collection"))).isEmpty();
            assertThat(titles(eve.get("/api/v1/cards"))).isEmpty();

            assertThat(titles(ada.get("/api/v1/collection"))).containsExactly("Ada's secret garden");
        }
    }
}
