package app.almanook.imagery;

import java.util.List;

/**
 * What to look for. {@code pageImageUrls} come from the retrieved source page (og:image, schema.org image);
 * {@code searchTerms} is a short, human-readable query such as "Ogród Botaniczny Powsin".
 */
public record CoverQuery(String searchTerms, List<String> pageImageUrls) {

    public CoverQuery {
        pageImageUrls = pageImageUrls == null ? List.of() : List.copyOf(pageImageUrls);
    }
}
