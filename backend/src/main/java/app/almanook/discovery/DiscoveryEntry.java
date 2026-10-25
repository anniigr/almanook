package app.almanook.discovery;

import app.almanook.card.CardView.DiscoveryInfo;
import app.almanook.card.CardView.DiscoveryStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** An unsaved idea in the user's Discover inbox. */
@Entity
@Table(name = "discovery_entry")
class DiscoveryEntry {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "card_id", nullable = false, updatable = false)
    private UUID cardId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DiscoveryStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "dismissed_at")
    private Instant dismissedAt;

    @Column(name = "saved_at")
    private Instant savedAt;

    protected DiscoveryEntry() {}

    DiscoveryEntry(UUID userId, UUID cardId, Instant now) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.cardId = cardId;
        this.status = DiscoveryStatus.PENDING;
        this.createdAt = now;
    }

    void dismiss(Instant now) {
        if (status == DiscoveryStatus.PENDING) {
            status = DiscoveryStatus.DISMISSED;
            dismissedAt = now;
        }
    }

    /** Back to the inbox; also used when a saved idea is re-proposed. */
    void restore() {
        status = DiscoveryStatus.PENDING;
        dismissedAt = null;
        savedAt = null;
    }

    void markSaved(Instant now) {
        status = DiscoveryStatus.SAVED;
        dismissedAt = null;
        savedAt = now;
    }

    UUID getCardId() {
        return cardId;
    }

    DiscoveryStatus getStatus() {
        return status;
    }

    DiscoveryInfo toInfo() {
        return new DiscoveryInfo(status, createdAt, dismissedAt, savedAt);
    }
}
