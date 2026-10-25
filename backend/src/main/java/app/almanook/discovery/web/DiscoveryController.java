package app.almanook.discovery.web;

import app.almanook.card.CardService;
import app.almanook.card.CardView;
import app.almanook.discovery.DiscoveryService;
import app.almanook.identity.AlmanookPrincipal;
import app.almanook.identity.UserAccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/discover/{cardId}")
@Tag(name = "Discover inbox")
class DiscoveryController {

    private final DiscoveryService discovery;
    private final CardService cards;
    private final UserAccountService users;

    DiscoveryController(DiscoveryService discovery, CardService cards, UserAccountService users) {
        this.discovery = discovery;
        this.cards = cards;
        this.users = users;
    }

    @PostMapping("/save")
    @Operation(summary = "Move an inbox idea into the collection without creating a duplicate")
    CardView save(@AuthenticationPrincipal AlmanookPrincipal principal, @PathVariable UUID cardId) {
        discovery.save(principal.userId(), cardId);
        return cards.view(principal.userId(), cardId, users.todayFor(principal.userId()));
    }

    @PostMapping("/dismiss")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Dismiss an idea (reversible; the card is kept)")
    void dismiss(@AuthenticationPrincipal AlmanookPrincipal principal, @PathVariable UUID cardId) {
        discovery.dismiss(principal.userId(), cardId);
    }

    @PostMapping("/restore")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Bring a dismissed idea back into the inbox")
    void restore(@AuthenticationPrincipal AlmanookPrincipal principal, @PathVariable UUID cardId) {
        discovery.restore(principal.userId(), cardId);
    }
}
