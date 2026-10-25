package app.almanook.collection;

import app.almanook.card.CardMembershipPort;
import app.almanook.card.CardService;
import app.almanook.card.CardView.CollectionInfo;
import app.almanook.card.Destination;
import app.almanook.shared.ApiException;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CollectionService implements CardMembershipPort {

    public enum SaveResult {
        CREATED,
        ALREADY_SAVED,
        REACTIVATED
    }

    private final CollectionEntryRepository entries;
    private final CardService cards;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    CollectionService(
            CollectionEntryRepository entries, CardService cards, ApplicationEventPublisher events, Clock clock) {
        this.entries = entries;
        this.cards = cards;
        this.events = events;
        this.clock = clock;
    }

    /**
     * Saves a card to the collection. Idempotent: an active entry is left alone, a removed one is re-activated,
     * and the unique (user, card) constraint guarantees there is never a second entry.
     */
    @Transactional
    public SaveResult save(UUID userId, UUID cardId) {
        cards.requireOwned(userId, cardId);
        Instant now = Instant.now(clock);
        SaveResult result;
        if (entries.insertIfAbsent(UUID.randomUUID(), userId, cardId, now) == 1) {
            result = SaveResult.CREATED;
        } else {
            CollectionEntry entry =
                    entries.findByUserIdAndCardId(userId, cardId).orElseThrow();
            result = entry.reactivate(now) ? SaveResult.REACTIVATED : SaveResult.ALREADY_SAVED;
        }
        events.publishEvent(new CardSavedToCollection(userId, cardId));
        return result;
    }

    /** Soft removal: the card and its data stay, so the client can offer "Undo". */
    @Transactional
    public void remove(UUID userId, UUID cardId) {
        cards.requireOwned(userId, cardId);
        CollectionEntry entry = entries.findByUserIdAndCardId(userId, cardId)
                .orElseThrow(() -> ApiException.notFound("Collection entry"));
        entry.remove(Instant.now(clock));
    }

    @Transactional
    public CollectionInfo updatePersonal(
            UUID userId, UUID cardId, Boolean visited, String personalNote, boolean noteSet) {
        cards.requireOwned(userId, cardId);
        CollectionEntry entry = entries.findByUserIdAndCardId(userId, cardId)
                .orElseThrow(() -> ApiException.notFound("Collection entry"));
        if (visited != null) {
            entry.setVisited(visited, Instant.now(clock));
        }
        if (noteSet) {
            entry.setPersonalNote(personalNote);
        }
        return entry.toInfo();
    }

    @Override
    public Destination destination() {
        return Destination.COLLECTION;
    }

    @Override
    @Transactional
    public void place(UUID userId, UUID cardId) {
        save(userId, cardId);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, CollectionInfo> collectionStatus(UUID userId, Collection<UUID> cardIds) {
        if (cardIds.isEmpty()) {
            return Map.of();
        }
        return entries.findAllByUserIdAndCardIdIn(userId, cardIds).stream()
                .collect(Collectors.toMap(CollectionEntry::getCardId, CollectionEntry::toInfo));
    }
}
