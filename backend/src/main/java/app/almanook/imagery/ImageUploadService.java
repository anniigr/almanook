package app.almanook.imagery;

import app.almanook.card.CardService;
import app.almanook.shared.ApiException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Stores user-uploaded cover images in PostgreSQL after verifying the actual file format. */
@Service
public class ImageUploadService implements CardService.UploadOwnership {

    public static final int MAX_BYTES = 5 * 1024 * 1024;

    public record StoredImage(UUID id, String contentType, byte[] data, String etag) {}

    private final UploadedImageRepository images;
    private final Clock clock;

    ImageUploadService(UploadedImageRepository images, Clock clock) {
        this.images = images;
        this.clock = clock;
    }

    /** Identical bytes uploaded twice by the same user are stored once. */
    @Transactional
    public UUID store(UUID ownerId, byte[] data) {
        if (data == null || data.length == 0) {
            throw ApiException.badRequest("validation_failed", "The file is empty");
        }
        if (data.length > MAX_BYTES) {
            throw new ApiException(
                    HttpStatus.CONTENT_TOO_LARGE, "payload_too_large", "The upload exceeds the 5 MiB limit");
        }
        String type = sniff(data)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                        "unsupported_media_type",
                        "Only JPEG, PNG and WebP images are accepted"));
        String sha = sha256(data);
        return images.findFirstByOwnerIdAndSha256(ownerId, sha)
                .map(UploadedImage::getId)
                .orElseGet(() -> images.save(new UploadedImage(ownerId, type, data, sha, Instant.now(clock)))
                        .getId());
    }

    @Transactional(readOnly = true)
    public StoredImage load(UUID ownerId, UUID id) {
        UploadedImage image = images.findByIdAndOwnerId(id, ownerId).orElseThrow(() -> ApiException.notFound("Image"));
        return new StoredImage(image.getId(), image.getContentType(), image.getData(), image.getSha256());
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isOwnedBy(UUID uploadId, UUID userId) {
        return uploadId != null && images.existsByIdAndOwnerId(uploadId, userId);
    }

    /** Detects the format from magic bytes; the client's declared Content-Type is not trusted. */
    static Optional<String> sniff(byte[] d) {
        if (d.length >= 3 && (d[0] & 0xff) == 0xff && (d[1] & 0xff) == 0xd8 && (d[2] & 0xff) == 0xff) {
            return Optional.of("image/jpeg");
        }
        if (d.length >= 8
                && (d[0] & 0xff) == 0x89
                && d[1] == 'P'
                && d[2] == 'N'
                && d[3] == 'G'
                && d[4] == 0x0d
                && d[5] == 0x0a
                && d[6] == 0x1a
                && d[7] == 0x0a) {
            return Optional.of("image/png");
        }
        if (d.length >= 12
                && d[0] == 'R'
                && d[1] == 'I'
                && d[2] == 'F'
                && d[3] == 'F'
                && d[8] == 'W'
                && d[9] == 'E'
                && d[10] == 'B'
                && d[11] == 'P') {
            return Optional.of("image/webp");
        }
        return Optional.empty();
    }

    private static String sha256(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
