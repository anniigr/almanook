package app.almanook.collection;

import java.util.UUID;

/** Published inside the saving transaction; the discovery module uses it to close a pending inbox entry. */
public record CardSavedToCollection(UUID userId, UUID cardId) {}
