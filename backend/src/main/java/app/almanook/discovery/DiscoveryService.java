package app.almanook.discovery;

import app.almanook.card.CardMembershipPort;
import app.almanook.card.CardService;
import app.almanook.card.CardView.CollectionStatus;
import app.almanook.card.CardView.DiscoveryInfo;
import app.almanook.card.CardView.DiscoveryStatus;
import app.almanook.card.Destination;
import app.almanook.collection.CardSavedToCollection;
import app.almanook.collection.CollectionService;
import app.almanook.shared.ApiException;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DiscoveryService implements CardMembershipPort {

    private final DiscoveryEntryRepository entries;
    private final CardService cards;
    private final CollectionService collection;
    private final Clock clock;

    DiscoveryService(DiscoveryEntryRepository entries, CardService cards, CollectionService collection, Clock clock) {
        this.entries = entries;
        this.cards = cards;
        this.collection = collection;
        this.clock = clock;
    }

    /**
     * Proposes a card in the inbox. A card that is already in the active collection is not proposed again, so
     * the inbox never shadows saved items.
     */
    @Override
    @Transactional
    public void place(UUID userId, UUID cardId) {
        boolean inCollection = collection.collectionStatus(userId, List.of(cardId)).values().stream()
                .anyMatch(info -> info.status() == CollectionStatus.ACTIVE);
        if (inCollection) {
            throw ApiException.conflict("already_in_collection", "This card is already in your collection");
        }
        Instant now = Instant.now(clock);
        if (entries.insertIfAbsent(UUID.randomUUID(), userId, cardId, now) == 0) {
            entries.findByUserIdAndCardId(userId, cardId).orElseThrow().restore();
        }
    }

    /** Save from the inbox: the collection entry is created (or re-activated), never duplicated. */
    @Transactional
    public void save(UUID userId, UUID cardId) {
        requireEntry(userId, cardId);
        collection.save(userId, cardId); // publishes CardSavedToCollection, which marks the entry SAVED
    }

    /** Dismiss keeps the card and the entry so the idea can be restored. */
    @Transactional
    public void dismiss(UUID userId, UUID cardId) {
        requireEntry(userId, cardId).dismiss(Instant.now(clock));
    }

    @Transactional
    public void restore(UUID userId, UUID cardId) {
        DiscoveryEntry entry = requireEntry(userId, cardId);
        if (entry.getStatus() == DiscoveryStatus.DISMISSED) {
            entry.restore();
        }
    }

    @EventListener
    void onSaved(CardSavedToCollection event) {
        entries.findByUserIdAndCardId(event.userId(), event.cardId())
                .filter(e -> e.getStatus() != DiscoveryStatus.SAVED)
                .ifPresent(e -> e.markSaved(Instant.now(clock)));
    }

    @Override
    public Destination destination() {
        return Destination.DISCOVER;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, DiscoveryInfo> discoveryStatus(UUID userId, Collection<UUID> cardIds) {
        if (cardIds.isEmpty()) {
            return Map.of();
        }
        return entries.findAllByUserIdAndCardIdIn(userId, cardIds).stream()
                .collect(Collectors.toMap(DiscoveryEntry::getCardId, DiscoveryEntry::toInfo));
    }

    private DiscoveryEntry requireEntry(UUID userId, UUID cardId) {
        cards.requireOwned(userId, cardId);
        return entries.findByUserIdAndCardId(userId, cardId).orElseThrow(() -> ApiException.notFound("Inbox entry"));
    }
}
