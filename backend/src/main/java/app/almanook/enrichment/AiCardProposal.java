package app.almanook.enrichment;

import app.almanook.card.ItemType;
import app.almanook.card.Season;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

/**
 * The structured output the AI must return (its JSON schema is derived in the provider adapter). Every
 * time-sensitive or location fact carries the URL it was taken from; the assembler drops facts whose URL
 * was not actually retrieved.
 */
public record AiCardProposal(
        boolean identified,
        @NotBlank @Size(max = 200) String title,
        @Size(max = 1200) String shortDescription,
        @NotNull ItemType itemType,
        @Size(max = 200) String venueName,
        @Size(max = 300) String address,
        @Size(max = 2048) String addressSourceUrl,
        @Size(max = 120) String city,
        @Size(max = 80) String country,
        BigDecimal latitude,
        BigDecimal longitude,
        @Size(max = 200) String creator,
        Integer releaseYear,
        @Size(max = 10) String eventStartDate,
        @Size(max = 10) String eventEndDate,
        @Size(max = 2048) String datesSourceUrl,
        BigDecimal priceMin,
        BigDecimal priceMax,
        @Size(max = 3) String currency,
        @Size(max = 200) String priceNote,
        @Size(max = 2048) String priceSourceUrl,
        @Size(max = 300) String openingHoursNote,
        @Size(max = 2048) String openingHoursSourceUrl,
        @Size(max = 2048) String officialUrl,
        @NotNull @Size(max = 4) List<Season> seasons,
        @Size(max = 200) String imageSearchTerms,
        @NotNull @Valid Confidence confidence,
        @NotNull @Size(max = 20) List<@Size(max = 300) String> unverified) {

    public record Confidence(
            @NotNull ConfidenceLevel identification,
            @NotNull ConfidenceLevel dates,
            @NotNull ConfidenceLevel price,
            @NotNull ConfidenceLevel location) {}
}
