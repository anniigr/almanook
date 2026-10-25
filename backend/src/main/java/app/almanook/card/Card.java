package app.almanook.card;

import app.almanook.expiration.FactSource;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Facts about a place, event or experience. Personal status lives in the collection and discovery modules. */
@Entity
@Table(name = "card")
public class Card {

    @Id
    private UUID id;

    @Column(name = "owner_id", nullable = false, updatable = false)
    private UUID ownerId;

    @Column(nullable = false)
    private String title;

    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "item_type", nullable = false)
    private ItemType itemType;

    @Column(name = "venue_name")
    private String venueName;

    private String address;
    private String city;
    private String country;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private String creator;

    @Column(name = "release_year")
    private Integer releaseYear;

    @Column(name = "source_url")
    private String sourceUrl;

    @Column(name = "canonical_url")
    private String canonicalUrl;

    @Column(name = "price_min")
    private BigDecimal priceMin;

    @Column(name = "price_max")
    private BigDecimal priceMax;

    private String currency;

    @Column(name = "price_note")
    private String priceNote;

    @Enumerated(EnumType.STRING)
    @Column(name = "price_source")
    private FactSource priceSource;

    @Column(name = "event_start_date")
    private LocalDate eventStartDate;

    @Column(name = "event_end_date")
    private LocalDate eventEndDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_dates_source")
    private FactSource eventDatesSource;

    @Column(name = "expires_on")
    private LocalDate expiresOn;

    @Enumerated(EnumType.STRING)
    @Column(name = "expiration_source")
    private FactSource expirationSource;

    @Column(name = "opening_hours_note")
    private String openingHoursNote;

    @Column(name = "cover_image_url")
    private String coverImageUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "cover_source")
    private CoverSource coverSource;

    @Column(name = "cover_attribution")
    private String coverAttribution;

    @Column(name = "cover_attribution_url")
    private String coverAttributionUrl;

    @Column(name = "cover_license")
    private String coverLicense;

    @Column(name = "cover_upload_id")
    private UUID coverUploadId;

    @Column(name = "original_input")
    private String originalInput;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "enrichment", columnDefinition = "jsonb")
    private String enrichment;

    @ElementCollection
    @CollectionTable(name = "card_season", joinColumns = @JoinColumn(name = "card_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "season", nullable = false)
    @BatchSize(size = 100)
    private Set<Season> seasons = new HashSet<>();

    @Version
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Card() {}

    Card(UUID ownerId, Instant now) {
        this.id = UUID.randomUUID();
        this.ownerId = ownerId;
        this.createdAt = now;
        this.updatedAt = now;
    }

    // --- mutation (package-private: only CardService changes cards) ---

    void setDescriptiveFields(CardInput in) {
        this.title = in.title().strip();
        this.description = blankToNull(in.description());
        this.itemType = in.itemType();
        this.venueName = blankToNull(in.venueName());
        this.address = blankToNull(in.address());
        this.city = blankToNull(in.city());
        this.country = blankToNull(in.country());
        this.latitude = in.latitude();
        this.longitude = in.longitude();
        this.creator = blankToNull(in.creator());
        this.releaseYear = in.releaseYear();
        this.openingHoursNote = blankToNull(in.openingHoursNote());
        this.seasons.clear();
        if (in.seasons() != null && !in.seasons().isEmpty()) {
            this.seasons.addAll(EnumSet.copyOf(in.seasons()));
        }
    }

    void setSource(String sourceUrl, String canonicalUrl) {
        this.sourceUrl = blankToNull(sourceUrl);
        this.canonicalUrl = canonicalUrl;
    }

    void setPrice(BigDecimal min, BigDecimal max, String currency, String note, FactSource source) {
        this.priceMin = min;
        this.priceMax = max;
        this.currency = min == null ? null : currency;
        this.priceNote = blankToNull(note);
        this.priceSource = min == null ? null : source;
    }

    void setEventDates(LocalDate start, LocalDate end, FactSource source) {
        this.eventStartDate = start;
        this.eventEndDate = end;
        this.eventDatesSource = (start == null && end == null) ? null : source;
    }

    void setExpiration(LocalDate expiresOn, FactSource source) {
        this.expiresOn = expiresOn;
        this.expirationSource = expiresOn == null ? null : source;
    }

    void setCover(
            CoverSource source, String url, UUID uploadId, String attribution, String attributionUrl, String license) {
        this.coverSource = source;
        this.coverImageUrl = source == null || source == CoverSource.UPLOAD ? null : url;
        this.coverUploadId = source == CoverSource.UPLOAD ? uploadId : null;
        this.coverAttribution = source == null ? null : blankToNull(attribution);
        this.coverAttributionUrl = source == null ? null : blankToNull(attributionUrl);
        this.coverLicense = source == null ? null : blankToNull(license);
    }

    void setProvenance(String originalInput, String enrichmentJson) {
        this.originalInput = blankToNull(originalInput);
        this.enrichment = enrichmentJson;
    }

    void touch(Instant now) {
        this.updatedAt = now;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }

    // --- accessors ---

    public UUID getId() {
        return id;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public ItemType getItemType() {
        return itemType;
    }

    public String getVenueName() {
        return venueName;
    }

    public String getAddress() {
        return address;
    }

    public String getCity() {
        return city;
    }

    public String getCountry() {
        return country;
    }

    public BigDecimal getLatitude() {
        return latitude;
    }

    public BigDecimal getLongitude() {
        return longitude;
    }

    public String getCreator() {
        return creator;
    }

    public Integer getReleaseYear() {
        return releaseYear;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }

    public String getCanonicalUrl() {
        return canonicalUrl;
    }

    public BigDecimal getPriceMin() {
        return priceMin;
    }

    public BigDecimal getPriceMax() {
        return priceMax;
    }

    public String getCurrency() {
        return currency;
    }

    public String getPriceNote() {
        return priceNote;
    }

    public FactSource getPriceSource() {
        return priceSource;
    }

    public LocalDate getEventStartDate() {
        return eventStartDate;
    }

    public LocalDate getEventEndDate() {
        return eventEndDate;
    }

    public FactSource getEventDatesSource() {
        return eventDatesSource;
    }

    public LocalDate getExpiresOn() {
        return expiresOn;
    }

    public FactSource getExpirationSource() {
        return expirationSource;
    }

    public String getOpeningHoursNote() {
        return openingHoursNote;
    }

    public String getCoverImageUrl() {
        return coverImageUrl;
    }

    public CoverSource getCoverSource() {
        return coverSource;
    }

    public String getCoverAttribution() {
        return coverAttribution;
    }

    public String getCoverAttributionUrl() {
        return coverAttributionUrl;
    }

    public String getCoverLicense() {
        return coverLicense;
    }

    public UUID getCoverUploadId() {
        return coverUploadId;
    }

    public String getOriginalInput() {
        return originalInput;
    }

    public String getEnrichment() {
        return enrichment;
    }

    public Set<Season> getSeasons() {
        return Set.copyOf(seasons);
    }

    public long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
