package app.almanook.card.web;

import app.almanook.card.CardInput;
import app.almanook.card.CardService;
import app.almanook.card.CardView;
import app.almanook.card.Destination;
import app.almanook.identity.AlmanookPrincipal;
import app.almanook.identity.UserAccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

@RestController
@RequestMapping("/api/v1/cards")
@Tag(name = "Cards")
class CardController {

    record CreateCardRequest(
            @NotNull @Valid CardInput card,
            Destination destination,
            @Size(max = 4000) String originalInput,
            JsonNode enrichment) {}

    record ExpirationRequest(LocalDate expiresOn) {}

    private final CardService cards;
    private final UserAccountService users;

    CardController(CardService cards, UserAccountService users) {
        this.cards = cards;
        this.users = users;
    }

    @PostMapping
    @Operation(summary = "Create a card (manually or from an accepted draft) and place it in a destination")
    ResponseEntity<CardView> create(
            @AuthenticationPrincipal AlmanookPrincipal principal, @Valid @RequestBody CreateCardRequest body) {
        UUID userId = principal.userId();
        Destination destination = body.destination() == null ? Destination.COLLECTION : body.destination();
        UUID id = cards.create(userId, body.card(), destination, body.originalInput(), body.enrichment())
                .getId();
        return ResponseEntity.created(URI.create("/api/v1/cards/" + id))
                .body(cards.view(userId, id, users.todayFor(userId)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "A card with the current user's collection and inbox status")
    CardView get(@AuthenticationPrincipal AlmanookPrincipal principal, @PathVariable UUID id) {
        return cards.view(principal.userId(), id, users.todayFor(principal.userId()));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Replace the card's metadata (optimistic locking via version)")
    CardView update(
            @AuthenticationPrincipal AlmanookPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody CardInput body) {
        cards.update(principal.userId(), id, body);
        return cards.view(principal.userId(), id, users.todayFor(principal.userId()));
    }

    @PatchMapping("/{id}/expiration")
    @Operation(summary = "Set or clear the expiry date; clearing or moving it to the future restores the card")
    CardView setExpiration(
            @AuthenticationPrincipal AlmanookPrincipal principal,
            @PathVariable UUID id,
            @RequestBody ExpirationRequest body) {
        cards.setExpiration(principal.userId(), id, body.expiresOn());
        return cards.view(principal.userId(), id, users.todayFor(principal.userId()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Permanently delete the card and its collection / inbox entries")
    void delete(@AuthenticationPrincipal AlmanookPrincipal principal, @PathVariable UUID id) {
        cards.delete(principal.userId(), id);
    }
}
