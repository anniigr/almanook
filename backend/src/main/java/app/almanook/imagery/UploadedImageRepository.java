package app.almanook.imagery;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface UploadedImageRepository extends JpaRepository<UploadedImage, UUID> {

    Optional<UploadedImage> findByIdAndOwnerId(UUID id, UUID ownerId);

    boolean existsByIdAndOwnerId(UUID id, UUID ownerId);

    Optional<UploadedImage> findFirstByOwnerIdAndSha256(UUID ownerId, String sha256);
}
