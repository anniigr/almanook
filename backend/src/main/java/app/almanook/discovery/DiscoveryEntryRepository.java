package app.almanook.discovery;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

interface DiscoveryEntryRepository extends JpaRepository<DiscoveryEntry, UUID> {

    Optional<DiscoveryEntry> findByUserIdAndCardId(UUID userId, UUID cardId);

    List<DiscoveryEntry> findAllByUserIdAndCardIdIn(UUID userId, Collection<UUID> cardIds);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
            value = "INSERT INTO discovery_entry (id, user_id, card_id, status, created_at)"
                    + " VALUES (:id, :userId, :cardId, 'PENDING', :now) ON CONFLICT (user_id, card_id) DO NOTHING",
            nativeQuery = true)
    int insertIfAbsent(UUID id, UUID userId, UUID cardId, Instant now);
}
