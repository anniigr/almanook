package app.almanook.card;

import app.almanook.card.CardView.CollectionInfo;
import app.almanook.card.CardView.DiscoveryInfo;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * Implemented by the collection and discovery modules. Lets the card module place a new card and show a
 * card's personal status without depending on those modules (which themselves depend on cards).
 */
public interface CardMembershipPort {

    /** The destination this implementation handles. */
    Destination destination();

    /** Adds the (already persisted, user-owned) card to this destination. */
    void place(UUID userId, UUID cardId);

    default Map<UUID, CollectionInfo> collectionStatus(UUID userId, Collection<UUID> cardIds) {
        return Map.of();
    }

    default Map<UUID, DiscoveryInfo> discoveryStatus(UUID userId, Collection<UUID> cardIds) {
        return Map.of();
    }
}
