package app.almanook.collection;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

interface CollectionEntryRepository extends JpaRepository<CollectionEntry, UUID> {

    Optional<CollectionEntry> findByUserIdAndCardId(UUID userId, UUID cardId);

    List<CollectionEntry> findAllByUserIdAndCardIdIn(UUID userId, Collection<UUID> cardIds);

    /** Race-free insert: the unique (user_id, card_id) constraint decides, no exception is raised. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
            value = "INSERT INTO collection_entry (id, user_id, card_id, status, saved_at)"
                    + " VALUES (:id, :userId, :cardId, 'ACTIVE', :now) ON CONFLICT (user_id, card_id) DO NOTHING",
            nativeQuery = true)
    int insertIfAbsent(UUID id, UUID userId, UUID cardId, Instant now);
}
