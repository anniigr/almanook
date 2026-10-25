package app.almanook.card;

import app.almanook.card.CardInput.CoverInput;
import app.almanook.card.CardView.CollectionInfo;
import app.almanook.card.CardView.DiscoveryInfo;
import app.almanook.expiration.ExpirationPolicy;
import app.almanook.expiration.FactSource;
import app.almanook.shared.ApiException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Service
public class CardService {

    /** Ownership check for cover uploads, implemented by the imagery module. */
    public interface UploadOwnership {
        boolean isOwnedBy(UUID uploadId, UUID userId);
    }

    public record DuplicateHint(UUID id, String title) {}

    private final CardRepository cards;
    private final ObjectProvider<CardMembershipPort> memberships;
    private final ObjectProvider<UploadOwnership> uploadOwnership;
    private final JsonMapper json;
    private final Clock clock;

    CardService(
            CardRepository cards,
            ObjectProvider<CardMembershipPort> memberships,
            ObjectProvider<UploadOwnership> uploadOwnership,
            JsonMapper json,
            Clock clock) {
        this.cards = cards;
        this.memberships = memberships;
        this.uploadOwnership = uploadOwnership;
        this.json = json;
        this.clock = clock;
    }

    /**
     * Creates a card from a manual form or an accepted draft and places it into the requested destination in
     * the same transaction.
     */
    @Transactional
    public Card create(UUID ownerId, CardInput in, Destination destination, String originalInput, JsonNode enrichment) {
        Optional<String> canonical = UrlCanonicalizer.canonicalize(in.sourceUrl());
        canonical
                .flatMap(url -> cards.findByOwnerIdAndCanonicalUrl(ownerId, url))
                .ifPresent(existing -> {
                    throw duplicate(existing);
                });
        Instant now = Instant.now(clock);
        Card card = new Card(ownerId, now);
        card.setDescriptiveFields(in);
        card.setSource(in.sourceUrl(), canonical.orElse(null));
        card.setPrice(in.priceMin(), in.priceMax(), in.currency(), in.priceNote(), draftSource(in.priceSource()));
        FactSource datesSource = draftSource(in.eventDatesSource());
        card.setEventDates(in.eventStartDate(), in.eventEndDate(), datesSource);
        if (in.expiresOn() != null) {
            card.setExpiration(in.expiresOn(), draftSource(in.expirationSource()));
        } else {
            ExpirationPolicy.defaultExpiry(in.itemType().isTemporary(), in.eventEndDate(), card.getEventDatesSource())
                    .ifPresent(d -> card.setExpiration(d, card.getEventDatesSource()));
        }
        applyCover(ownerId, card, in.cover());
        card.setProvenance(
                originalInput, enrichment == null || enrichment.isNull() ? null : json.writeValueAsString(enrichment));
        // A concurrent create of the same URL is stopped by the partial unique index (answered with 409).
        Card saved = cards.saveAndFlush(card);
        if (destination != null && destination != Destination.NONE) {
            port(destination).place(ownerId, saved.getId());
        }
        return saved;
    }

    /**
     * Full update from the edit form. Any value that differs from the stored one becomes user-provided; values
     * left untouched keep their provenance.
     */
    @Transactional
    public Card update(UUID ownerId, UUID cardId, CardInput in) {
        Card card = require(ownerId, cardId);
        if (in.version() == null || in.version() != card.getVersion()) {
            throw ApiException.conflict("stale_version", "The card was changed elsewhere; reload it and try again");
        }
        Optional<String> canonical = UrlCanonicalizer.canonicalize(in.sourceUrl());
        canonical
                .flatMap(url -> cards.findByOwnerIdAndCanonicalUrl(ownerId, url))
                .filter(other -> !other.getId().equals(cardId))
                .ifPresent(other -> {
                    throw duplicate(other);
                });

        boolean priceChanged = !sameDecimal(card.getPriceMin(), in.priceMin())
                || !sameDecimal(card.getPriceMax(), in.priceMax())
                || !Objects.equals(card.getCurrency(), in.priceMin() == null ? null : in.currency());
        boolean datesChanged = !Objects.equals(card.getEventStartDate(), in.eventStartDate())
                || !Objects.equals(card.getEventEndDate(), in.eventEndDate());
        boolean expiryChanged = !Objects.equals(card.getExpiresOn(), in.expiresOn());

        card.setDescriptiveFields(in);
        card.setSource(in.sourceUrl(), canonical.orElse(null));
        card.setPrice(
                in.priceMin(),
                in.priceMax(),
                in.currency(),
                in.priceNote(),
                priceChanged ? FactSource.USER : card.getPriceSource());
        card.setEventDates(
                in.eventStartDate(), in.eventEndDate(), datesChanged ? FactSource.USER : card.getEventDatesSource());
        card.setExpiration(in.expiresOn(), expiryChanged ? FactSource.USER : card.getExpirationSource());
        applyCover(ownerId, card, in.cover());
        card.touch(Instant.now(clock));
        return cards.saveAndFlush(card);
    }

    /** Sets or clears the expiry. Clearing it, or moving it into the future, restores an archived card. */
    @Transactional
    public Card setExpiration(UUID ownerId, UUID cardId, LocalDate expiresOn) {
        Card card = require(ownerId, cardId);
        card.setExpiration(expiresOn, FactSource.USER);
        card.touch(Instant.now(clock));
        return cards.saveAndFlush(card);
    }

    @Transactional
    public void delete(UUID ownerId, UUID cardId) {
        cards.delete(require(ownerId, cardId));
    }

    @Transactional(readOnly = true)
    public Card get(UUID ownerId, UUID cardId) {
        return require(ownerId, cardId);
    }

    /** Throws 404 unless the card exists and belongs to the user. */
    @Transactional(readOnly = true)
    public void requireOwned(UUID ownerId, UUID cardId) {
        require(ownerId, cardId);
    }

    @Transactional(readOnly = true)
    public List<DuplicateHint> possibleDuplicates(UUID ownerId, String title, String sourceUrl) {
        Map<UUID, DuplicateHint> hints = new LinkedHashMap<>();
        UrlCanonicalizer.canonicalize(sourceUrl)
                .flatMap(url -> cards.findByOwnerIdAndCanonicalUrl(ownerId, url))
                .ifPresent(c -> hints.put(c.getId(), new DuplicateHint(c.getId(), c.getTitle())));
        if (title != null && !title.isBlank()) {
            cards.findSameTitle(ownerId, title.strip())
                    .forEach(c -> hints.putIfAbsent(c.getId(), new DuplicateHint(c.getId(), c.getTitle())));
        }
        return List.copyOf(hints.values());
    }

    @Transactional(readOnly = true)
    public CardView view(UUID ownerId, UUID cardId, LocalDate today) {
        return views(ownerId, List.of(cardId), today).stream()
                .findFirst()
                .orElseThrow(() -> ApiException.notFound("Card"));
    }

    /** Builds views for the given ids, preserving their order; ids not owned by the user are skipped. */
    @Transactional(readOnly = true)
    public List<CardView> views(UUID ownerId, List<UUID> cardIds, LocalDate today) {
        if (cardIds.isEmpty()) {
            return List.of();
        }
        Map<UUID, Card> byId = cards.findAllByOwnerIdAndIdIn(ownerId, cardIds).stream()
                .collect(Collectors.toMap(Card::getId, Function.identity()));
        Map<UUID, CollectionInfo> collection = new HashMap<>();
        Map<UUID, DiscoveryInfo> discovery = new HashMap<>();
        for (CardMembershipPort port : memberships.orderedStream().toList()) {
            collection.putAll(port.collectionStatus(ownerId, byId.keySet()));
            discovery.putAll(port.discoveryStatus(ownerId, byId.keySet()));
        }
        return cardIds.stream()
                .map(byId::get)
                .filter(Objects::nonNull)
                .map(c -> toView(c, collection.get(c.getId()), discovery.get(c.getId()), today))
                .toList();
    }

    private CardView toView(Card c, CollectionInfo collection, DiscoveryInfo discovery, LocalDate today) {
        CardView.Price price = c.getPriceMin() == null
                ? null
                : new CardView.Price(
                        c.getPriceMin(), c.getPriceMax(), c.getCurrency(), c.getPriceNote(), c.getPriceSource());
        CardView.Cover cover = c.getCoverSource() == null
                ? null
                : new CardView.Cover(
                        c.getCoverSource() == CoverSource.UPLOAD
                                ? "/api/v1/images/" + c.getCoverUploadId()
                                : c.getCoverImageUrl(),
                        c.getCoverSource(),
                        c.getCoverAttribution(),
                        c.getCoverAttributionUrl(),
                        c.getCoverLicense(),
                        c.getCoverUploadId());
        return new CardView(
                c.getId(),
                c.getVersion(),
                c.getTitle(),
                c.getDescription(),
                c.getItemType(),
                c.getVenueName(),
                c.getAddress(),
                c.getCity(),
                c.getCountry(),
                c.getLatitude(),
                c.getLongitude(),
                c.getCreator(),
                c.getReleaseYear(),
                c.getSourceUrl(),
                price,
                c.getEventStartDate(),
                c.getEventEndDate(),
                c.getEventDatesSource(),
                c.getExpiresOn(),
                c.getExpirationSource(),
                ExpirationPolicy.isExpired(c.getExpiresOn(), today),
                c.getOpeningHoursNote(),
                c.getSeasons().stream().sorted(Comparator.naturalOrder()).toList(),
                cover,
                c.getEnrichment() == null ? null : json.readTree(c.getEnrichment()),
                c.getOriginalInput(),
                collection,
                discovery,
                c.getCreatedAt(),
                c.getUpdatedAt());
    }

    private void applyCover(UUID ownerId, Card card, CoverInput cover) {
        if (cover == null) {
            card.setCover(null, null, null, null, null, null);
            return;
        }
        if (cover.source() == CoverSource.UPLOAD && !isOwnedUpload(cover.uploadId(), ownerId)) {
            throw ApiException.badRequest("validation_failed", "Unknown cover upload");
        }
        card.setCover(
                cover.source(),
                cover.url(),
                cover.uploadId(),
                cover.attribution(),
                cover.attributionUrl(),
                cover.license());
    }

    private boolean isOwnedUpload(UUID uploadId, UUID ownerId) {
        UploadOwnership ownership = uploadOwnership.getIfAvailable();
        return ownership != null && ownership.isOwnedBy(uploadId, ownerId);
    }

    private CardMembershipPort port(Destination destination) {
        return memberships
                .orderedStream()
                .filter(p -> p.destination() == destination)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No handler for " + destination));
    }

    private Card require(UUID ownerId, UUID cardId) {
        return cards.findByIdAndOwnerId(cardId, ownerId).orElseThrow(() -> ApiException.notFound("Card"));
    }

    private ApiException duplicate(Card existing) {
        return new ApiException(
                HttpStatus.CONFLICT,
                "duplicate_card",
                "A card for this page already exists: " + existing.getTitle(),
                Map.of("existingCardId", existing.getId()));
    }

    /** Drafts may only claim SOURCE or AI; anything else (including absent) is the user's own input. */
    private static FactSource draftSource(FactSource hint) {
        return hint == FactSource.SOURCE || hint == FactSource.AI ? hint : FactSource.USER;
    }

    private static boolean sameDecimal(BigDecimal a, BigDecimal b) {
        return a == null ? b == null : b != null && a.compareTo(b) == 0;
    }
}
