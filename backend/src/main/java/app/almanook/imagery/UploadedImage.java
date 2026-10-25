package app.almanook.imagery;

import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "image_upload")
class UploadedImage {

    @Id
    private UUID id;

    @Column(name = "owner_id", nullable = false, updatable = false)
    private UUID ownerId;

    @Column(name = "content_type", nullable = false)
    private String contentType;

    @Basic(fetch = FetchType.LAZY)
    @Column(nullable = false)
    private byte[] data;

    @Column(name = "size_bytes", nullable = false)
    private int sizeBytes;

    @Column(nullable = false)
    private String sha256;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected UploadedImage() {}

    UploadedImage(UUID ownerId, String contentType, byte[] data, String sha256, Instant now) {
        this.id = UUID.randomUUID();
        this.ownerId = ownerId;
        this.contentType = contentType;
        this.data = data;
        this.sizeBytes = data.length;
        this.sha256 = sha256;
        this.createdAt = now;
    }

    UUID getId() {
        return id;
    }

    UUID getOwnerId() {
        return ownerId;
    }

    String getContentType() {
        return contentType;
    }

    byte[] getData() {
        return data;
    }

    String getSha256() {
        return sha256;
    }
}
