package app.almanook.enrichment.web;

import app.almanook.enrichment.CardDraft;
import app.almanook.enrichment.DraftRequest;
import app.almanook.enrichment.EnrichmentService;
import app.almanook.identity.AlmanookPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** AI generation lives apart from card CRUD so provider failures never affect the collection. */
@RestController
@RequestMapping("/api/v1/enrichment")
@Tag(name = "Enrichment")
class EnrichmentController {

    private final EnrichmentService enrichment;

    EnrichmentController(EnrichmentService enrichment) {
        this.enrichment = enrichment;
    }

    @GetMapping("/status")
    @Operation(summary = "Whether AI enrichment and which image sources are available")
    EnrichmentService.Status status() {
        return enrichment.status();
    }

    @PostMapping("/drafts")
    @Operation(summary = "Generate an editable card draft from a name, URL and/or note (nothing is saved)")
    CardDraft draft(@AuthenticationPrincipal AlmanookPrincipal principal, @Valid @RequestBody DraftRequest body) {
        return enrichment.generate(principal.userId(), body);
    }
}
