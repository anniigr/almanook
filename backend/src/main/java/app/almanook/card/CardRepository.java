package app.almanook.card;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface CardRepository extends JpaRepository<Card, UUID> {

    Optional<Card> findByIdAndOwnerId(UUID id, UUID ownerId);

    Optional<Card> findByOwnerIdAndCanonicalUrl(UUID ownerId, String canonicalUrl);

    List<Card> findAllByOwnerIdAndIdIn(UUID ownerId, Collection<UUID> ids);

    @Query("select c from Card c where c.ownerId = :ownerId and lower(c.title) = lower(:title)")
    List<Card> findSameTitle(UUID ownerId, String title);
}
