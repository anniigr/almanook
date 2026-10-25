package app.almanook.card;

import app.almanook.expiration.FactSource;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

/**
 * Editable card fields, used for manual creation, updates and generated drafts. The {@code *Source} hints
 * let a draft carry provenance; anything the user edits becomes {@link FactSource#USER}.
 */
public record CardInput(
        @NotBlank @Size(max = 200) String title,
        @Size(max = 1200) String description,
        @NotNull ItemType itemType,
        @Size(max = 200) String venueName,
        @Size(max = 300) String address,
        @Size(max = 120) String city,
        @Size(max = 80) String country,

        @DecimalMin("-90") @DecimalMax("90") @Digits(integer = 3, fraction = 6)
        BigDecimal latitude,

        @DecimalMin("-180") @DecimalMax("180") @Digits(integer = 3, fraction = 6)
        BigDecimal longitude,

        @Size(max = 200) String creator,
        @Min(1870) @Max(2200) Integer releaseYear,

        @Size(max = 2048) @Pattern(regexp = "(?i)https?://\\S+", message = "must be an http(s) URL")
        String sourceUrl,

        @DecimalMin("0") @Digits(integer = 8, fraction = 2) BigDecimal priceMin,
        @DecimalMin("0") @Digits(integer = 8, fraction = 2) BigDecimal priceMax,

        @Pattern(regexp = "[A-Z]{3}", message = "must be an ISO 4217 code such as PLN")
        String currency,

        @Size(max = 200) String priceNote,
        FactSource priceSource,
        LocalDate eventStartDate,
        LocalDate eventEndDate,
        FactSource eventDatesSource,
        LocalDate expiresOn,
        FactSource expirationSource,
        @Size(max = 300) String openingHoursNote,
        @Size(max = 4) Set<Season> seasons,
        @Valid CoverInput cover,
        Long version) {

    public record CoverInput(
            @NotNull CoverSource source,

            @Size(max = 2048) @Pattern(regexp = "(?i)https://\\S+", message = "must be an https URL")
            String url,

            UUID uploadId,
            @Size(max = 300) String attribution,
            @Size(max = 2048) String attributionUrl,
            @Size(max = 100) String license) {

        @JsonIgnore
        @AssertTrue(message = "an upload cover needs uploadId; other covers need url")
        public boolean isConsistent() {
            return source == CoverSource.UPLOAD ? uploadId != null : url != null && !url.isBlank();
        }
    }

    @JsonIgnore
    @AssertTrue(message = "latitude and longitude must be given together")
    public boolean isCoordinatePair() {
        return (latitude == null) == (longitude == null);
    }

    @JsonIgnore
    @AssertTrue(message = "priceMax must be greater than or equal to priceMin, and needs priceMin")
    public boolean isPriceRangeValid() {
        return priceMax == null || (priceMin != null && priceMax.compareTo(priceMin) >= 0);
    }

    @JsonIgnore
    @AssertTrue(message = "currency is required when a price is given")
    public boolean isCurrencyPresentWithPrice() {
        return priceMin == null || currency != null;
    }

    @JsonIgnore
    @AssertTrue(message = "eventEndDate must not be before eventStartDate")
    public boolean isEventDateOrderValid() {
        return eventStartDate == null || eventEndDate == null || !eventEndDate.isBefore(eventStartDate);
    }
}
