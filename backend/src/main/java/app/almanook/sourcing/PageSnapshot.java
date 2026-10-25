package app.almanook.sourcing;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Facts extracted from one retrieved page. All of it is untrusted third-party data: it is shown to the
 * user as a suggestion and passed to the AI only as delimited data, never as instructions.
 */
public record PageSnapshot(
        String url,
        String title,
        String description,
        String siteName,
        String language,
        List<String> imageUrls,
        List<StructuredItem> items,
        String text) {

    /** One schema.org entity (Event, Place, Museum, Restaurant, Movie...) found in JSON-LD. */
    public record StructuredItem(
            String type,
            String name,
            String description,
            LocalDate startDate,
            LocalDate endDate,
            String venueName,
            String streetAddress,
            String locality,
            String country,
            BigDecimal latitude,
            BigDecimal longitude,
            BigDecimal priceMin,
            BigDecimal priceMax,
            String currency,
            String imageUrl,
            String url) {}

    /** The most informative structured item, preferring events over places. */
    public java.util.Optional<StructuredItem> primaryItem() {
        return items.stream().max(java.util.Comparator.comparingInt(PageSnapshot::score));
    }

    private static int score(StructuredItem i) {
        int s = 0;
        if (i.type() != null && i.type().toLowerCase(java.util.Locale.ROOT).contains("event")) {
            s += 4;
        }
        if (i.startDate() != null || i.endDate() != null) {
            s += 3;
        }
        if (i.name() != null) {
            s += 2;
        }
        if (i.venueName() != null || i.locality() != null) {
            s += 1;
        }
        if (i.priceMin() != null) {
            s += 1;
        }
        return s;
    }
}
