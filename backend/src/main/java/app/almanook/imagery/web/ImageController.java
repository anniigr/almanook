package app.almanook.imagery.web;

import app.almanook.identity.AlmanookPrincipal;
import app.almanook.imagery.CoverCandidate;
import app.almanook.imagery.CoverImageService;
import app.almanook.imagery.CoverQuery;
import app.almanook.imagery.ImageUploadService;
import app.almanook.imagery.provider.UnsplashProvider;
import app.almanook.shared.ApiException;
import app.almanook.sourcing.UrlSafetyPolicy;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/images")
@Tag(name = "Images")
class ImageController {

    record UrlCheckRequest(@NotBlank @Size(max = 2048) String url) {}

    record UrlCheckResponse(boolean usable) {}

    record CandidatesRequest(
            @NotBlank @Size(max = 200) String query,
            @Size(max = 2048) String sourceUrl) {}

    record UnsplashSelection(@NotBlank @Size(max = 2048) String downloadLocation) {}

    private final ImageUploadService uploads;
    private final CoverImageService covers;
    private final UnsplashProvider unsplash;
    private final UrlSafetyPolicy urlPolicy;

    ImageController(
            ImageUploadService uploads,
            CoverImageService covers,
            UnsplashProvider unsplash,
            UrlSafetyPolicy urlPolicy) {
        this.uploads = uploads;
        this.covers = covers;
        this.unsplash = unsplash;
        this.urlPolicy = urlPolicy;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Upload a JPEG, PNG or WebP cover (max 5 MiB)")
    Map<String, Object> upload(
            @AuthenticationPrincipal AlmanookPrincipal principal, @RequestParam("file") MultipartFile file)
            throws IOException {
        UUID id = uploads.store(principal.userId(), file.getBytes());
        return Map.of("id", id, "url", "/api/v1/images/" + id);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Download an uploaded image (owner only)")
    ResponseEntity<byte[]> download(@AuthenticationPrincipal AlmanookPrincipal principal, @PathVariable UUID id) {
        ImageUploadService.StoredImage image = uploads.load(principal.userId(), id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(image.contentType()))
                .cacheControl(CacheControl.maxAge(Duration.ofDays(1)).cachePrivate())
                .eTag(image.etag())
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Disposition", "inline")
                .body(image.data());
    }

    @PostMapping("/check-url")
    @Operation(summary = "Check that a pasted image URL is a reachable, safe https image")
    UrlCheckResponse checkUrl(@Valid @RequestBody UrlCheckRequest body) {
        if (!body.url().toLowerCase(java.util.Locale.ROOT).startsWith("https://")) {
            return new UrlCheckResponse(false);
        }
        urlPolicy.checkSyntax(body.url());
        return new UrlCheckResponse(covers.isReachableImage(body.url()));
    }

    @PostMapping("/candidates")
    @Operation(summary = "Cover suggestions for the cover picker")
    List<CoverCandidate> candidates(@Valid @RequestBody CandidatesRequest body) {
        return covers.candidates(new CoverQuery(body.query(), List.of()));
    }

    @PostMapping("/unsplash-selection")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Report that an Unsplash photo was selected (required by Unsplash API guidelines)")
    void unsplashSelected(@Valid @RequestBody UnsplashSelection body) {
        try {
            URI.create(body.downloadLocation());
            unsplash.trackDownload(body.downloadLocation());
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("validation_failed", "Not an Unsplash download location");
        }
    }
}
