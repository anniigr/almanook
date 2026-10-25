package app.almanook.enrichment;

import app.almanook.card.ItemType;
import app.almanook.card.Season;
import app.almanook.sourcing.PageSnapshot;
import app.almanook.sourcing.PageSnapshot.StructuredItem;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Builders for AI proposals and pages used across enrichment tests. */
final class Fixtures {

    static final String OFFICIAL = "https://www.ogrod-powsin.pl/";
    static final String TICKETS = "https://www.ogrod-powsin.pl/bilety/";
    static final String BLOG = "https://some-blog.example/powsin-guide";

    private Fixtures() {}

    static AiCardProposal.Confidence confidence(ConfidenceLevel level) {
        return new AiCardProposal.Confidence(level, level, level, level);
    }

    static ProposalBuilder proposal() {
        return new ProposalBuilder();
    }

    static ResearchNotes notes(String... urls) {
        return new ResearchNotes(
                "notes",
                java.util.Arrays.stream(urls).map(u -> new SourceRef(u, "t")).toList(),
                urls.length > 0 ? urls[0] : null);
    }

    static PageSnapshot page(String url, StructuredItem item) {
        return new PageSnapshot(
                url,
                "Page title",
                "Page description.",
                "Site",
                "pl",
                List.of("https://cdn.example/cover.jpg"),
                item == null ? List.of() : List.of(item),
                "text");
    }

    static StructuredItem exhibitionItem() {
        return new StructuredItem(
                "ExhibitionEvent",
                "Tamara Łempicka",
                "Retrospective.",
                LocalDate.of(2026, 10, 3),
                LocalDate.of(2027, 1, 17),
                "Muzeum Narodowe",
                "Al. Jerozolimskie 3",
                "Warszawa",
                "Poland",
                new BigDecimal("52.2317"),
                new BigDecimal("21.0248"),
                new BigDecimal("25"),
                new BigDecimal("35"),
                "PLN",
                null,
                null);
    }

    static final class ProposalBuilder {
        boolean identified = true;
        String title = "Ogród Botaniczny PAN w Powsinie";
        String description =
                "A large botanical garden in southern Warsaw. Best in spring for magnolias and in autumn for colours.";
        ItemType type = ItemType.PLACE;
        String venue = "Ogród Botaniczny – CZRB PAN";
        String address = "ul. Prawdziwka 2";
        String addressUrl = OFFICIAL;
        String city = "Warszawa";
        String country = "Poland";
        BigDecimal lat = new BigDecimal("52.106");
        BigDecimal lon = new BigDecimal("21.094");
        String start;
        String end;
        String datesUrl;
        BigDecimal priceMin = new BigDecimal("15");
        BigDecimal priceMax = null;
        String currency = "PLN";
        String priceUrl = TICKETS;
        String hours = "Daily 10:00–20:00 in summer";
        String hoursUrl = OFFICIAL;
        String official = OFFICIAL;
        List<Season> seasons = List.of(Season.SPRING, Season.AUTUMN);
        List<String> unverified = List.of();

        ProposalBuilder type(ItemType t) {
            type = t;
            return this;
        }

        ProposalBuilder title(String t) {
            title = t;
            return this;
        }

        ProposalBuilder dates(String s, String e, String url) {
            start = s;
            end = e;
            datesUrl = url;
            return this;
        }

        ProposalBuilder price(String min, String cur, String url) {
            priceMin = min == null ? null : new BigDecimal(min);
            currency = cur;
            priceUrl = url;
            return this;
        }

        ProposalBuilder address(String a, String url) {
            address = a;
            addressUrl = url;
            return this;
        }

        ProposalBuilder hours(String h, String url) {
            hours = h;
            hoursUrl = url;
            return this;
        }

        ProposalBuilder seasons(Season... s) {
            seasons = List.of(s);
            return this;
        }

        ProposalBuilder unverified(String... u) {
            unverified = List.of(u);
            return this;
        }

        ProposalBuilder notIdentified() {
            identified = false;
            return this;
        }

        AiCardProposal build() {
            return new AiCardProposal(
                    identified,
                    title,
                    description,
                    type,
                    venue,
                    address,
                    addressUrl,
                    city,
                    country,
                    lat,
                    lon,
                    null,
                    null,
                    start,
                    end,
                    datesUrl,
                    priceMin,
                    priceMax,
                    currency,
                    "Reduced 10 PLN",
                    priceUrl,
                    hours,
                    hoursUrl,
                    official,
                    seasons,
                    "Ogród Botaniczny Powsin",
                    confidence(ConfidenceLevel.HIGH),
                    unverified);
        }
    }
}
