package app.almanook.api;

import static app.almanook.api.Cards.card;
import static org.assertj.core.api.Assertions.assertThat;

import app.almanook.support.AbstractIntegrationTest;
import app.almanook.support.ApiClient;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;

class DiscoverAndExpiryApiIT extends AbstractIntegrationTest {

    @LocalServerPort
    int port;

    ApiClient ada;

    @BeforeEach
    void setUp() {
        resetDatabase();
        ada = new ApiClient(port).register("ada@example.com", "correct-horse-battery", "Ada");
    }

    String create(Cards c, String destination) {
        ApiClient.Response r = ada.post("/api/v1/cards", c.create(destination));
        assertThat(r.status()).as(r.raw()).isEqualTo(201);
        return r.body().get("id").asString();
    }

    List<String> titles(String path) {
        List<String> titles = new ArrayList<>();
        ada.get(path).body().get("items").forEach(i -> titles.add(i.get("title").asString()));
        return titles;
    }

    @Nested
    class DiscoverInbox {

        @Test
        void inboxIdeasAreSeparateFromTheCollection() {
            create(card("Idea", "ACTIVITY"), "DISCOVER");
            assertThat(titles("/api/v1/discover")).containsExactly("Idea");
            assertThat(titles("/api/v1/collection")).isEmpty();
        }

        @Test
        void savingMovesTheIdeaWithoutDuplicates() {
            String id = create(card("Idea", "ACTIVITY"), "DISCOVER");
            ApiClient.Response saved = ada.post("/api/v1/discover/" + id + "/save", "");
            assertThat(saved.status()).isEqualTo(200);
            assertThat(saved.body().at("/collection/status").asString()).isEqualTo("ACTIVE");
            assertThat(saved.body().at("/discovery/status").asString()).isEqualTo("SAVED");

            assertThat(ada.post("/api/v1/discover/" + id + "/save", "").status())
                    .isEqualTo(200);
            assertThat(ada.put("/api/v1/collection/" + id, "").status()).isEqualTo(200);

            assertThat(titles("/api/v1/discover")).isEmpty();
            assertThat(titles("/api/v1/collection")).containsExactly("Idea");
            assertThat(jdbc.queryForObject("SELECT count(*) FROM collection_entry", Integer.class))
                    .isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM card", Integer.class))
                    .isEqualTo(1);
        }

        @Test
        void savingDirectlyToTheCollectionClosesThePendingInboxEntry() {
            String id = create(card("Idea", "ACTIVITY"), "DISCOVER");
            assertThat(ada.put("/api/v1/collection/" + id, "").status()).isEqualTo(201);
            assertThat(titles("/api/v1/discover")).isEmpty();
            assertThat(ada.get("/api/v1/cards/" + id)
                            .body()
                            .at("/discovery/status")
                            .asString())
                    .isEqualTo("SAVED");
        }

        @Test
        void dismissIsReversibleAndKeepsTheCard() {
            String id = create(card("Idea", "ACTIVITY"), "DISCOVER");
            assertThat(ada.post("/api/v1/discover/" + id + "/dismiss", "").status())
                    .isEqualTo(204);
            assertThat(titles("/api/v1/discover")).isEmpty();
            assertThat(titles("/api/v1/discover?status=DISMISSED")).containsExactly("Idea");
            assertThat(ada.get("/api/v1/cards/" + id).status()).isEqualTo(200);

            assertThat(ada.post("/api/v1/discover/" + id + "/restore", "").status())
                    .isEqualTo(204);
            assertThat(titles("/api/v1/discover")).containsExactly("Idea");
        }

        @Test
        void unknownInboxEntryIs404() {
            String id = create(card("Saved directly", "PLACE"), "COLLECTION");
            assertThat(ada.post("/api/v1/discover/" + id + "/dismiss", "").status())
                    .isEqualTo(404);
        }
    }

    @Nested
    class Expiry {

        String exhibition;

        @BeforeEach
        void seed() {
            exhibition = create(
                    card("Winter exhibition", "EXHIBITION")
                            .with("eventStartDate", "2026-10-01")
                            .with("eventEndDate", "2026-12-24")
                            .with("eventDatesSource", "SOURCE"),
                    "COLLECTION");
            create(card("Botanical garden", "PLACE").seasons("SPRING", "AUTUMN"), "COLLECTION");
        }

        @Test
        void activeUntilTheEndOfTheLastLocalDayThenArchived() {
            clock.set(Instant.parse("2026-12-24T22:59:59Z")); // 23:59:59 in Warsaw
            assertThat(titles("/api/v1/collection")).contains("Winter exhibition");
            assertThat(titles("/api/v1/expired")).isEmpty();

            clock.set(Instant.parse("2026-12-24T23:00:00Z")); // 00:00 on the 25th in Warsaw
            assertThat(titles("/api/v1/collection")).containsExactly("Botanical garden");
            assertThat(titles("/api/v1/expired")).containsExactly("Winter exhibition");
            ApiClient.Response card = ada.get("/api/v1/cards/" + exhibition);
            assertThat(card.body().get("expired").asBoolean()).isTrue();
            assertThat(card.body().at("/collection/status").asString()).isEqualTo("ACTIVE");
        }

        @Test
        void userTimezoneDecides() {
            clock.set(Instant.parse("2026-12-24T23:30:00Z"));
            assertThat(titles("/api/v1/expired")).containsExactly("Winter exhibition");

            ada.put(
                    "/api/v1/me/preferences",
                    ApiClient.json(Map.of("theme", "DARK", "language", "en", "timezone", "America/Los_Angeles")));
            assertThat(titles("/api/v1/expired")).isEmpty();
            assertThat(titles("/api/v1/collection")).contains("Winter exhibition");
        }

        @Test
        void editingTheDateToTheFutureOrClearingItRestoresTheCard() {
            clock.set(Instant.parse("2027-01-15T12:00:00Z"));
            assertThat(titles("/api/v1/expired")).containsExactly("Winter exhibition");

            ApiClient.Response extended =
                    ada.patch("/api/v1/cards/" + exhibition + "/expiration", "{\"expiresOn\":\"2027-02-28\"}");
            assertThat(extended.status()).isEqualTo(200);
            assertThat(extended.body().get("expirationSource").asString()).isEqualTo("USER");
            assertThat(titles("/api/v1/expired")).isEmpty();
            assertThat(titles("/api/v1/collection")).contains("Winter exhibition");

            ada.patch("/api/v1/cards/" + exhibition + "/expiration", "{\"expiresOn\":\"2027-01-01\"}");
            assertThat(titles("/api/v1/expired")).containsExactly("Winter exhibition");

            ApiClient.Response cleared =
                    ada.patch("/api/v1/cards/" + exhibition + "/expiration", "{\"expiresOn\":null}");
            assertThat(cleared.body().get("expiresOn").isNull()).isTrue();
            assertThat(cleared.body().get("expirationSource").isNull()).isTrue();
            assertThat(titles("/api/v1/expired")).isEmpty();
        }

        @Test
        void expiredCardsCanStillBeMarkedVisitedAndAreNeverDeleted() {
            clock.set(Instant.parse("2027-06-01T12:00:00Z"));
            ApiClient.Response r = ada.patch("/api/v1/collection/" + exhibition, "{\"visited\":true}");
            assertThat(r.status()).isEqualTo(200);
            assertThat(r.body().get("expired").asBoolean()).isTrue();
            assertThat(r.body().at("/collection/visitedAt").isNull()).isFalse();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM card", Integer.class))
                    .isEqualTo(2);
        }

        @Test
        void expiredPendingIdeasAppearInTheArchiveNotTheInbox() {
            create(
                    card("Autumn festival idea", "FESTIVAL")
                            .with("eventEndDate", "2026-11-02")
                            .with("eventDatesSource", "USER"),
                    "DISCOVER");
            assertThat(titles("/api/v1/discover")).containsExactly("Autumn festival idea");
            clock.set(Instant.parse("2026-11-03T12:00:00Z"));
            assertThat(titles("/api/v1/discover")).isEmpty();
            assertThat(titles("/api/v1/expired")).containsExactly("Autumn festival idea");
        }

        @Test
        void removedCardsDoNotAppearInTheArchive() {
            ada.delete("/api/v1/collection/" + exhibition);
            clock.set(Instant.parse("2027-01-15T12:00:00Z"));
            assertThat(titles("/api/v1/expired")).isEmpty();
        }
    }
}
