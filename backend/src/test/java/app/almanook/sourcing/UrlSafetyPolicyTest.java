package app.almanook.sourcing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.InetAddress;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class UrlSafetyPolicyTest {

    final UrlSafetyPolicy policy = new UrlSafetyPolicy();

    @ParameterizedTest
    @ValueSource(
            strings = {
                "file:///etc/passwd",
                "ftp://example.com/x",
                "gopher://example.com",
                "jar:https://example.com/a.jar!/",
                "data:text/html,<script>",
                "javascript:alert(1)",
                "https://user:pass@example.com/",
                "https://example.com:8080/",
                "http://example.com:22/",
                "http://localhost/",
                "http://LOCALHOST./admin",
                "http://foo.localhost/",
                "http://metadata.google.internal/computeMetadata/v1/",
                "http://printer.local/",
                "http://intranet/",
                "http://2130706433/",
                "http://0177.0.0.1/",
                "http://0x7f.0.0.1/",
                "http://127.1/",
                "https://",
                " ",
            })
    void rejectsUnsafeUrlsBeforeAnyNetworkAccess(String url) {
        assertThatThrownBy(() -> policy.checkSyntax(url)).isInstanceOf(UnsafeUrlException.class);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "127.0.0.1",
                "127.255.255.254",
                "0.0.0.0",
                "10.1.2.3",
                "172.16.0.1",
                "172.31.255.255",
                "192.168.1.1",
                "169.254.169.254",
                "100.64.0.1",
                "198.18.0.1",
                "192.0.2.10",
                "224.0.0.1",
                "255.255.255.255",
                "::1",
                "::",
                "fe80::1",
                "fc00::1",
                "fd12:3456::1",
                "::ffff:127.0.0.1",
                "::ffff:169.254.169.254",
                "::ffff:10.0.0.1",
                "2001:db8::1",
                "64:ff9b::a9fe:a9fe",
                "2002:7f00:1::1",
            })
    void nonPublicAddressesAreRejected(String ip) throws Exception {
        InetAddress address = InetAddress.getByName(ip);
        assertThat(UrlSafetyPolicy.isPublicAddress(address)).as(ip).isFalse();
        assertThatThrownBy(() -> policy.resolve(ip)).isInstanceOf(UnsafeUrlException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"8.8.8.8", "1.1.1.1", "151.101.1.69", "2606:4700:4700::1111", "2a00:1450:4001::200e"})
    void publicAddressesAreAllowed(String ip) throws Exception {
        assertThat(UrlSafetyPolicy.isPublicAddress(InetAddress.getByName(ip)))
                .as(ip)
                .isTrue();
    }

    @Test
    void ipLiteralUrlsAreCheckedByAddress() throws Exception {
        policy.checkSyntax("http://169.254.169.254/latest/meta-data/"); // syntactically fine...
        assertThatThrownBy(() -> policy.resolve("169.254.169.254")) // ...but blocked by address
                .isInstanceOf(UnsafeUrlException.class);
        policy.checkSyntax("http://[::1]/");
        assertThatThrownBy(() -> policy.resolve("[::1]")).isInstanceOf(UnsafeUrlException.class);
    }

    @Test
    void ordinaryPublicUrlsPassSyntaxChecks() {
        assertThat(policy.checkSyntax("https://www.ogrod-powsin.pl/").getHost()).isEqualTo("www.ogrod-powsin.pl");
        assertThat(policy.checkSyntax("http://mnw.art.pl:80/wystawy?x=1").getPort())
                .isEqualTo(80);
    }
}
