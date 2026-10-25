package app.almanook.card;

import app.almanook.expiration.FactSource;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

/** API representation of a card together with the current user's collection and inbox status. */
public record CardView(
        UUID id,
        long version,
        String title,
        String description,
        ItemType itemType,
        String venueName,
        String address,
        String city,
        String country,
        BigDecimal latitude,
        BigDecimal longitude,
        String creator,
        Integer releaseYear,
        String sourceUrl,
        Price price,
        LocalDate eventStartDate,
        LocalDate eventEndDate,
        FactSource eventDatesSource,
        LocalDate expiresOn,
        FactSource expirationSource,
        boolean expired,
        String openingHoursNote,
        List<Season> seasons,
        Cover cover,
        JsonNode enrichment,
        String originalInput,
        CollectionInfo collection,
        DiscoveryInfo discovery,
        Instant createdAt,
        Instant updatedAt) {

    public record Price(BigDecimal min, BigDecimal max, String currency, String note, FactSource source) {}

    public record Cover(
            String url, CoverSource source, String attribution, String attributionUrl, String license, UUID uploadId) {}

    public enum CollectionStatus {
        ACTIVE,
        REMOVED
    }

    public enum DiscoveryStatus {
        PENDING,
        SAVED,
        DISMISSED
    }

    public record CollectionInfo(
            CollectionStatus status, Instant savedAt, Instant visitedAt, Instant removedAt, String personalNote) {}

    public record DiscoveryInfo(DiscoveryStatus status, Instant createdAt, Instant dismissedAt, Instant savedAt) {}
}
