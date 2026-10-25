package app.almanook.card;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class UrlCanonicalizerTest {

    @ParameterizedTest
    @CsvSource({
        "https://www.MNW.art.pl/wystawy/,                          https://mnw.art.pl/wystawy",
        "http://mnw.art.pl/wystawy#top,                            https://mnw.art.pl/wystawy",
        "https://mnw.art.pl:443/wystawy?utm_source=ig&id=7,        https://mnw.art.pl/wystawy?id=7",
        "https://mnw.art.pl/w?b=2&a=1&fbclid=x,                    https://mnw.art.pl/w?a=1&b=2",
        "https://example.com:8443/a/,                              https://example.com:8443/a"
    })
    void normalisesEquivalentUrls(String input, String expected) {
        assertThat(UrlCanonicalizer.canonicalize(input)).contains(expected);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"not a url", "mailto:someone@example.com", "/relative/path"})
    void rejectsNonAbsoluteUrls(String input) {
        assertThat(UrlCanonicalizer.canonicalize(input)).isEmpty();
    }
}
