package app.almanook.collection;

import app.almanook.card.CardView.CollectionInfo;
import app.almanook.card.CardView.CollectionStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** A card in a user's collection, with the personal state that is independent of the card's facts. */
@Entity
@Table(name = "collection_entry")
class CollectionEntry {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "card_id", nullable = false, updatable = false)
    private UUID cardId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CollectionStatus status;

    @Column(name = "personal_note")
    private String personalNote;

    @Column(name = "saved_at", nullable = false)
    private Instant savedAt;

    @Column(name = "visited_at")
    private Instant visitedAt;

    @Column(name = "removed_at")
    private Instant removedAt;

    protected CollectionEntry() {}

    CollectionEntry(UUID userId, UUID cardId, Instant now) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.cardId = cardId;
        this.status = CollectionStatus.ACTIVE;
        this.savedAt = now;
    }

    /** @return true if the entry changed (was removed and is active again). */
    boolean reactivate(Instant now) {
        if (status == CollectionStatus.ACTIVE) {
            return false;
        }
        status = CollectionStatus.ACTIVE;
        removedAt = null;
        savedAt = now;
        return true;
    }

    void remove(Instant now) {
        if (status == CollectionStatus.ACTIVE) {
            status = CollectionStatus.REMOVED;
            removedAt = now;
        }
    }

    /** Visited is independent of expiry: an expired exhibition can still be marked as visited. */
    void setVisited(boolean visited, Instant now) {
        if (visited && visitedAt == null) {
            visitedAt = now;
        } else if (!visited) {
            visitedAt = null;
        }
    }

    void setPersonalNote(String note) {
        this.personalNote = note == null || note.isBlank() ? null : note.strip();
    }

    UUID getCardId() {
        return cardId;
    }

    CollectionStatus getStatus() {
        return status;
    }

    CollectionInfo toInfo() {
        return new CollectionInfo(status, savedAt, visitedAt, removedAt, personalNote);
    }
}
