package app.almanook.search.web;

import app.almanook.card.CardView;
import app.almanook.identity.AlmanookPrincipal;
import app.almanook.identity.UserAccountService;
import app.almanook.search.CardSearchService;
import app.almanook.search.Scope;
import app.almanook.search.SearchCriteria;
import app.almanook.search.SeasonFilter;
import app.almanook.search.SortOrder;
import app.almanook.shared.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** All card listings: collection, Discover inbox, Expired archive, and every card. */
@RestController
@RequestMapping("/api/v1")
@Validated
@Tag(name = "Listings")
class ListingController {

    enum InboxStatus {
        PENDING,
        DISMISSED
    }

    private final CardSearchService search;
    private final UserAccountService users;

    ListingController(CardSearchService search, UserAccountService users) {
        this.search = search;
        this.users = users;
    }

    @GetMapping("/collection")
    @Operation(summary = "Active collection (not removed, not expired)")
    PageResponse<CardView> collection(
            @AuthenticationPrincipal AlmanookPrincipal principal,
            @RequestParam(required = false) @Size(max = 200) String q,
            @RequestParam(defaultValue = "ALL") SeasonFilter season,
            @RequestParam(defaultValue = "RECENT") SortOrder sort,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "24") @Min(1) @Max(100) int size) {
        return run(principal, Scope.COLLECTION, q, season, sort, page, size);
    }

    @GetMapping("/discover")
    @Operation(summary = "Discover inbox: pending (default) or dismissed ideas")
    PageResponse<CardView> discover(
            @AuthenticationPrincipal AlmanookPrincipal principal,
            @RequestParam(defaultValue = "PENDING") InboxStatus status,
            @RequestParam(required = false) @Size(max = 200) String q,
            @RequestParam(defaultValue = "ALL") SeasonFilter season,
            @RequestParam(defaultValue = "RECENT") SortOrder sort,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "24") @Min(1) @Max(100) int size) {
        Scope scope = status == InboxStatus.DISMISSED ? Scope.DISCOVER_DISMISSED : Scope.DISCOVER_PENDING;
        return run(principal, scope, q, season, sort, page, size);
    }

    @GetMapping("/expired")
    @Operation(summary = "Expired archive: saved or pending cards whose expiry date has passed in your timezone")
    PageResponse<CardView> expired(
            @AuthenticationPrincipal AlmanookPrincipal principal,
            @RequestParam(required = false) @Size(max = 200) String q,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "24") @Min(1) @Max(100) int size) {
        return run(principal, Scope.EXPIRED, q, SeasonFilter.ALL, SortOrder.RECENT, page, size);
    }

    @GetMapping("/cards")
    @Operation(summary = "Every card you own, regardless of status")
    PageResponse<CardView> allCards(
            @AuthenticationPrincipal AlmanookPrincipal principal,
            @RequestParam(required = false) @Size(max = 200) String q,
            @RequestParam(defaultValue = "ALL") SeasonFilter season,
            @RequestParam(defaultValue = "RECENT") SortOrder sort,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "24") @Min(1) @Max(100) int size) {
        return run(principal, Scope.ALL_CARDS, q, season, sort, page, size);
    }

    private PageResponse<CardView> run(
            AlmanookPrincipal principal,
            Scope scope,
            String q,
            SeasonFilter season,
            SortOrder sort,
            int page,
            int size) {
        return search.search(new SearchCriteria(
                principal.userId(), scope, q, season, sort, users.todayFor(principal.userId()), page, size));
    }
}
