package app.almanook.collection.web;

import app.almanook.card.CardService;
import app.almanook.card.CardView;
import app.almanook.collection.CollectionService;
import app.almanook.collection.CollectionService.SaveResult;
import app.almanook.identity.AlmanookPrincipal;
import app.almanook.identity.UserAccountService;
import app.almanook.shared.ApiException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

@RestController
@RequestMapping("/api/v1/collection")
@Tag(name = "Collection")
class CollectionController {

    private final CollectionService collection;
    private final CardService cards;
    private final UserAccountService users;

    CollectionController(CollectionService collection, CardService cards, UserAccountService users) {
        this.collection = collection;
        this.cards = cards;
        this.users = users;
    }

    @PutMapping("/{cardId}")
    @Operation(summary = "Save a card to the collection (idempotent)")
    ResponseEntity<CardView> save(@AuthenticationPrincipal AlmanookPrincipal principal, @PathVariable UUID cardId) {
        SaveResult result = collection.save(principal.userId(), cardId);
        CardView view = cards.view(principal.userId(), cardId, users.todayFor(principal.userId()));
        return ResponseEntity.status(result == SaveResult.ALREADY_SAVED ? HttpStatus.OK : HttpStatus.CREATED)
                .body(view);
    }

    @DeleteMapping("/{cardId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Remove from the collection (soft; save again to undo)")
    void remove(@AuthenticationPrincipal AlmanookPrincipal principal, @PathVariable UUID cardId) {
        collection.remove(principal.userId(), cardId);
    }

    @PatchMapping("/{cardId}")
    @Operation(
            summary = "Update visited/watched status and the personal note",
            description =
                    "Body: {\"visited\": true|false, \"personalNote\": \"...\"|null}; absent fields are unchanged")
    CardView update(
            @AuthenticationPrincipal AlmanookPrincipal principal,
            @PathVariable UUID cardId,
            @RequestBody JsonNode body) {
        Boolean visited = null;
        if (body.has("visited") && !body.get("visited").isNull()) {
            if (!body.get("visited").isBoolean()) {
                throw ApiException.badRequest("validation_failed", "visited must be a boolean");
            }
            visited = body.get("visited").booleanValue();
        }
        boolean noteSet = body.has("personalNote");
        String note = noteSet && !body.get("personalNote").isNull()
                ? body.get("personalNote").asString()
                : null;
        if (note != null && note.length() > 1000) {
            throw ApiException.badRequest("validation_failed", "personalNote must be at most 1000 characters");
        }
        collection.updatePersonal(principal.userId(), cardId, visited, note, noteSet);
        return cards.view(principal.userId(), cardId, users.todayFor(principal.userId()));
    }
}
