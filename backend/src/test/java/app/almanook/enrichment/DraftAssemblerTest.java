package app.almanook.enrichment;

import static app.almanook.enrichment.Fixtures.BLOG;
import static app.almanook.enrichment.Fixtures.OFFICIAL;
import static app.almanook.enrichment.Fixtures.TICKETS;
import static app.almanook.enrichment.Fixtures.exhibitionItem;
import static app.almanook.enrichment.Fixtures.notes;
import static app.almanook.enrichment.Fixtures.page;
import static app.almanook.enrichment.Fixtures.proposal;
import static org.assertj.core.api.Assertions.assertThat;

import app.almanook.card.CardInput;
import app.almanook.card.ItemType;
import app.almanook.card.Season;
import app.almanook.expiration.FactSource;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.time.LocalDate;
import java.util.Set;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class DraftAssemblerTest {

    static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

    static DraftRequest typed(String query) {
        return new DraftRequest(query, null, null, null, null);
    }

    @Nested
    class UserInputIsNeverOverwritten {

        @Test
        void typedNameStaysTheTitleAndTheAiNameIsOnlySuggested() {
            var draft = DraftAssembler.assemble(
                    typed("Ogród Botaniczny w Powsinie"),
                    null,
                    notes(OFFICIAL),
                    proposal().build());
            assertThat(draft.card().title()).isEqualTo("Ogród Botaniczny w Powsinie");
            assertThat(draft.fieldSources()).containsEntry("title", FactSource.USER);
            assertThat(draft.suggestions()).containsEntry("title", "Ogród Botaniczny PAN w Powsinie");
        }

        @Test
        void userSeasonsAndExpiryWinOverAiValues() {
            DraftRequest req =
                    new DraftRequest("Christmas market", null, null, Set.of(Season.AUTUMN), LocalDate.of(2026, 12, 1));
            var draft = DraftAssembler.assemble(
                    req,
                    null,
                    notes(OFFICIAL),
                    proposal()
                            .type(ItemType.MARKET)
                            .seasons(Season.WINTER)
                            .dates("2026-11-20", "2026-12-23", OFFICIAL)
                            .build());
            assertThat(draft.card().seasons()).containsExactly(Season.AUTUMN);
            assertThat(draft.card().expiresOn()).isEqualTo(LocalDate.of(2026, 12, 1));
            assertThat(draft.card().expirationSource()).isEqualTo(FactSource.USER);
        }

        @Test
        void userUrlIsKeptAsTheSourceUrl() {
            DraftRequest req = new DraftRequest(null, "https://bilety.example/powsin", null, null, null);
            var draft = DraftAssembler.assemble(
                    req, null, notes(OFFICIAL), proposal().build());
            assertThat(draft.card().sourceUrl()).isEqualTo("https://bilety.example/powsin");
            assertThat(draft.card().title()).isEqualTo("Ogród Botaniczny PAN w Powsinie");
            assertThat(draft.fieldSources()).containsEntry("title", FactSource.AI);
        }
    }

    @Nested
    class OnlyCitedFactsSurvive {

        @Test
        void factsCitingRetrievedUrlsAreKeptAsSourceVerified() {
            var draft = DraftAssembler.assemble(
                    typed("Powsin"), null, notes(OFFICIAL, TICKETS), proposal().build());
            CardInput card = draft.card();
            assertThat(card.priceMin()).isEqualByComparingTo("15.00");
            assertThat(card.currency()).isEqualTo("PLN");
            assertThat(card.priceSource()).isEqualTo(FactSource.SOURCE);
            assertThat(card.address()).isEqualTo("ul. Prawdziwka 2");
            assertThat(card.openingHoursNote()).isEqualTo("Daily 10:00–20:00 in summer");
            assertThat(card.latitude()).isNotNull();
            assertThat(card.sourceUrl()).isEqualTo(OFFICIAL);
            assertThat(draft.unverifiedFields()).isEmpty();
            assertThat(draft.sources()).extracting(SourceRef::url).contains(OFFICIAL, TICKETS);
        }

        @Test
        void factsCitingUrlsThatWereNeverRetrievedAreDropped() {
            AiCardProposal ai = proposal()
                    .price("15", "PLN", "https://hallucinated.example/prices")
                    .address("ul. Prawdziwka 2", null)
                    .hours("Mon–Sun 9–17", BLOG)
                    .build();
            var draft = DraftAssembler.assemble(typed("Powsin"), null, notes(OFFICIAL), ai);
            assertThat(draft.card().priceMin()).isNull();
            assertThat(draft.card().currency()).isNull();
            assertThat(draft.card().address()).isNull();
            assertThat(draft.card().latitude()).isNull();
            assertThat(draft.card().openingHoursNote()).isNull();
            assertThat(draft.unverifiedFields()).contains("price", "address", "coordinates", "openingHoursNote");
            assertThat(draft.missingFields()).contains("price");
        }

        @Test
        void uncitedEventDatesAreDroppedAndCannotCreateAnExpiry() {
            AiCardProposal ai = proposal()
                    .type(ItemType.EXHIBITION)
                    .dates("2026-10-01", "2027-01-10", null)
                    .build();
            var draft = DraftAssembler.assemble(typed("Some exhibition"), null, notes(OFFICIAL), ai);
            assertThat(draft.card().eventEndDate()).isNull();
            assertThat(draft.card().expiresOn()).isNull();
            assertThat(draft.unverifiedFields()).contains("eventDates");
            assertThat(draft.missingFields()).contains("eventDates");
        }

        @Test
        void citationsMatchDespiteTrackingParametersOrTrailingSlashes() {
            AiCardProposal ai = proposal()
                    .price("15", "PLN", "https://ogrod-powsin.pl/bilety?utm_source=x")
                    .build();
            var draft = DraftAssembler.assemble(typed("Powsin"), null, notes(OFFICIAL, TICKETS), ai);
            assertThat(draft.card().priceMin()).isNotNull();
        }

        @Test
        void invalidCurrencyOrNegativePriceIsRejected() {
            var lowercase = DraftAssembler.assemble(
                    typed("x"),
                    null,
                    notes(TICKETS),
                    proposal().price("15", "zł", TICKETS).build());
            assertThat(lowercase.card().priceMin()).isNull();
            var negative = DraftAssembler.assemble(
                    typed("x"),
                    null,
                    notes(TICKETS),
                    proposal().price("-5", "PLN", TICKETS).build());
            assertThat(negative.card().priceMin()).isNull();
        }
    }

    @Nested
    class Expiration {

        @Test
        void verifiedEndDateOfATemporaryEventBecomesTheDefaultExpiry() {
            AiCardProposal ai = proposal()
                    .type(ItemType.EXHIBITION)
                    .dates("2026-10-01", "2027-01-10", OFFICIAL)
                    .build();
            var draft = DraftAssembler.assemble(typed("Exhibition"), null, notes(OFFICIAL), ai);
            assertThat(draft.card().eventEndDate()).isEqualTo(LocalDate.of(2027, 1, 10));
            assertThat(draft.card().expiresOn()).isEqualTo(LocalDate.of(2027, 1, 10));
            assertThat(draft.card().expirationSource()).isEqualTo(FactSource.SOURCE);
        }

        @Test
        void permanentPlacesFilmsAndRestaurantsNeverGetAnExpiry() {
            for (ItemType type : new ItemType[] {ItemType.PLACE, ItemType.MUSEUM, ItemType.FILM, ItemType.RESTAURANT}) {
                AiCardProposal ai = proposal()
                        .type(type)
                        .dates("2026-10-01", "2027-01-10", OFFICIAL)
                        .build();
                var draft = DraftAssembler.assemble(typed("x"), null, notes(OFFICIAL), ai);
                assertThat(draft.card().expiresOn()).as(type.name()).isNull();
            }
        }

        @Test
        void reversedDatesAreDiscarded() {
            AiCardProposal ai = proposal()
                    .type(ItemType.FESTIVAL)
                    .dates("2027-02-01", "2027-01-10", OFFICIAL)
                    .build();
            var draft = DraftAssembler.assemble(typed("x"), null, notes(OFFICIAL), ai);
            assertThat(draft.card().eventStartDate()).isNull();
            assertThat(draft.card().expiresOn()).isNull();
            assertThat(draft.warnings()).anyMatch(w -> w.contains("inconsistent"));
        }
    }

    @Nested
    class PageStructuredData {

        static final String MNW = "https://www.mnw.art.pl/wystawy/lempicka/";

        @Test
        void basicModeUsesPageFactsAsSourceVerified() {
            DraftRequest req = new DraftRequest(null, MNW, null, null, null);
            var draft = DraftAssembler.assemble(req, page(MNW, exhibitionItem()), null, null);
            CardInput card = draft.card();
            assertThat(card.title()).isEqualTo("Tamara Łempicka");
            assertThat(card.itemType()).isEqualTo(ItemType.EXHIBITION);
            assertThat(card.venueName()).isEqualTo("Muzeum Narodowe");
            assertThat(card.eventEndDate()).isEqualTo(LocalDate.of(2027, 1, 17));
            assertThat(card.expiresOn()).isEqualTo(LocalDate.of(2027, 1, 17));
            assertThat(card.priceMin()).isEqualByComparingTo("25.00");
            assertThat(card.priceMax()).isEqualByComparingTo("35.00");
            assertThat(card.seasons()).isEmpty();
            assertThat(draft.pageImageUrls()).containsExactly("https://cdn.example/cover.jpg");
            assertThat(VALIDATOR.validate(card)).isEmpty();
        }

        @Test
        void pageDatesWinOverConflictingAiDates() {
            DraftRequest req = new DraftRequest(null, MNW, null, null, null);
            AiCardProposal ai = proposal()
                    .type(ItemType.EXHIBITION)
                    .dates("2026-10-03", "2027-02-28", MNW)
                    .build();
            var draft = DraftAssembler.assemble(req, page(MNW, exhibitionItem()), notes(), ai);
            assertThat(draft.card().eventEndDate()).isEqualTo(LocalDate.of(2027, 1, 17));
            assertThat(draft.warnings()).anyMatch(w -> w.contains("disagree"));
        }

        @Test
        void thePageUrlItselfCountsAsEvidenceForAiCitations() {
            DraftRequest req = new DraftRequest(null, MNW, null, null, null);
            AiCardProposal ai = proposal().price("40", "PLN", MNW).build();
            var draft = DraftAssembler.assemble(req, page(MNW, null), notes(), ai);
            assertThat(draft.card().priceMin()).isEqualByComparingTo("40.00");
        }
    }

    @Test
    void lowConfidenceWhenTheAiCouldNotIdentifyTheItem() {
        var draft = DraftAssembler.assemble(
                typed("asdf"), null, notes(), proposal().notIdentified().build());
        assertThat(draft.confidence().identification()).isEqualTo(ConfidenceLevel.LOW);
        assertThat(draft.confidence().overall()).isEqualTo(ConfidenceLevel.LOW);
    }

    @Test
    void modelReportedUncertaintyIsSurfacedAsWarnings() {
        var draft = DraftAssembler.assemble(
                typed("x"),
                null,
                notes(OFFICIAL),
                proposal().unverified("winter opening hours").build());
        assertThat(draft.warnings()).contains("Not verified: winter opening hours");
    }

    @Test
    void descriptionsAreCappedAtThreeSentences() {
        assertThat(DraftAssembler.concise("One. Two! Three? Four. Five.")).isEqualTo("One. Two! Three?");
    }

    @Test
    void assembledCardsAlwaysPassCardValidation() {
        String longTitle = "T".repeat(500);
        AiCardProposal ai = proposal().title(longTitle).build();
        var draft =
                DraftAssembler.assemble(new DraftRequest(null, OFFICIAL, null, null, null), null, notes(OFFICIAL), ai);
        assertThat(VALIDATOR.validate(draft.card())).isEmpty();
    }
}
