package app.almanook.imagery;

import app.almanook.card.CoverSource;

/**
 * A possible cover image. {@code downloadLocation} is only set for Unsplash photos: their API guidelines
 * require calling it when a user selects the photo.
 */
public record CoverCandidate(
        String url,
        String thumbnailUrl,
        CoverSource source,
        String attribution,
        String attributionUrl,
        String license,
        String downloadLocation) {}
