package app.almanook.sourcing;

import static org.assertj.core.api.Assertions.assertThat;

import app.almanook.sourcing.PageSnapshot.StructuredItem;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class PageExtractorTest {

    final PageExtractor extractor = new PageExtractor();

    static FetchedResource fixture(String name, String url) throws IOException {
        try (InputStream in = PageExtractorTest.class.getResourceAsStream("/pages/" + name)) {
            return new FetchedResource(URI.create(url), "text/html", StandardCharsets.UTF_8, in.readAllBytes());
        }
    }

    @Test
    void extractsOpenGraphAndJsonLdEventFacts() throws IOException {
        PageSnapshot page = extractor.extract(fixture("exhibition.html", "https://www.mnw.art.pl/wystawy/lempicka/"));

        assertThat(page.title()).isEqualTo("Tamara Łempicka – wystawa");
        assertThat(page.description()).isEqualTo("Retrospektywa artystki w Muzeum Narodowym.");
        assertThat(page.siteName()).isEqualTo("Muzeum Narodowe w Warszawie");
        assertThat(page.language()).isEqualTo("pl");
        assertThat(page.imageUrls())
                .containsExactly("https://www.mnw.art.pl/media/lempicka.jpg", "https://example.org/a.jpg");

        StructuredItem event = page.primaryItem().orElseThrow();
        assertThat(event.type()).isEqualTo("ExhibitionEvent");
        assertThat(event.name()).isEqualTo("Tamara Łempicka");
        assertThat(event.description()).isEqualTo("Retrospektywa & obrazy");
        assertThat(event.startDate()).isEqualTo(LocalDate.of(2026, 10, 3));
        assertThat(event.endDate()).isEqualTo(LocalDate.of(2027, 1, 17));
        assertThat(event.venueName()).isEqualTo("Muzeum Narodowe w Warszawie");
        assertThat(event.streetAddress()).isEqualTo("Al. Jerozolimskie 3");
        assertThat(event.locality()).isEqualTo("Warszawa");
        assertThat(event.country()).isEqualTo("Poland");
        assertThat(event.latitude()).isEqualByComparingTo("52.2317");
        assertThat(event.longitude()).isEqualByComparingTo("21.0248");
        assertThat(event.priceMin()).isEqualByComparingTo(new BigDecimal("25.00"));
        assertThat(event.priceMax()).isEqualByComparingTo(new BigDecimal("35.00"));
        assertThat(event.currency()).isEqualTo("PLN");
    }

    @Test
    void readableTextSkipsNavigationAndKeepsMainContentAsPlainText() throws IOException {
        PageSnapshot page = extractor.extract(fixture("exhibition.html", "https://www.mnw.art.pl/"));
        assertThat(page.text())
                .contains("Bilety: 35 zł.")
                .doesNotContain("Menu menu")
                .doesNotContain("Stopka");
    }

    @Test
    void doubleEncodedMetaEntitiesAreDecoded() {
        FetchedResource r = new FetchedResource(
                URI.create("https://neonmuzeum.org/"),
                "text/html",
                StandardCharsets.UTF_8,
                "<html><head><meta property=\"og:title\" content=\"muzeum neon&amp;oacute;w\"></head></html>"
                        .getBytes(StandardCharsets.UTF_8));
        assertThat(extractor.extract(r).title()).isEqualTo("muzeum neonów");
    }

    @Test
    void pagesWithoutStructuredDataStillYieldTitle() {
        FetchedResource r = new FetchedResource(
                URI.create("https://example.com/"),
                "text/html",
                null,
                "<html><head><title>  Plain   page </title></head><body>Hello</body></html>"
                        .getBytes(StandardCharsets.UTF_8));
        PageSnapshot page = extractor.extract(r);
        assertThat(page.title()).isEqualTo("Plain page");
        assertThat(page.items()).isEmpty();
        assertThat(page.primaryItem()).isEmpty();
        assertThat(page.imageUrls()).isEmpty();
    }
}
