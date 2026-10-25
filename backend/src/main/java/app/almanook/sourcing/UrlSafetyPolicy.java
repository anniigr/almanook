package app.almanook.sourcing;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;

/**
 * Decides whether the server may contact a URL. Applied to the initial URL, to every redirect target and,
 * through {@link GuardedDnsResolver}, to the exact addresses a connection is opened to (so a second DNS
 * answer cannot rebind the host to an internal address).
 */
public class UrlSafetyPolicy {

    private static final Set<String> BLOCKED_HOSTS = Set.of(
            "localhost",
            "localhost.localdomain",
            "ip6-localhost",
            "ip6-loopback",
            "metadata",
            "metadata.google.internal",
            "metadata.goog",
            "instance-data",
            "instance-data.ec2.internal");

    private static final Set<String> BLOCKED_SUFFIXES =
            Set.of(".localhost", ".local", ".internal", ".intranet", ".lan", ".home.arpa", ".corp");

    private final Set<Integer> allowedPorts;
    private final boolean allowLoopback;

    /** Production policy: ports 80/443 only, no loopback. */
    public UrlSafetyPolicy() {
        this(Set.of(80, 443), false);
    }

    /**
     * @param allowLoopback only for tests that run an in-process HTTP server on 127.0.0.1; every other
     *     non-public range stays blocked
     */
    public UrlSafetyPolicy(Set<Integer> allowedPorts, boolean allowLoopback) {
        this.allowedPorts = Set.copyOf(allowedPorts);
        this.allowLoopback = allowLoopback;
    }

    /** Syntactic checks that need no DNS: scheme, credentials, port, host name. */
    public URI checkSyntax(String url) {
        if (url == null || url.isBlank() || url.length() > 2048) {
            throw new UnsafeUrlException("missing or too long");
        }
        URI uri;
        try {
            uri = new URI(url.strip()).normalize();
        } catch (URISyntaxException e) {
            throw new UnsafeUrlException("malformed URL");
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) {
            throw new UnsafeUrlException("only http and https are supported");
        }
        if (uri.getRawUserInfo() != null) {
            throw new UnsafeUrlException("credentials in URLs are not allowed");
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new UnsafeUrlException("missing host");
        }
        int port = uri.getPort() == -1 ? (scheme.equals("https") ? 443 : 80) : uri.getPort();
        if (!allowedPorts.contains(port)) {
            throw new UnsafeUrlException("port " + port + " is not allowed");
        }
        String normalizedHost = host.toLowerCase(Locale.ROOT);
        if (normalizedHost.endsWith(".")) {
            normalizedHost = normalizedHost.substring(0, normalizedHost.length() - 1);
        }
        if (!allowLoopback && (BLOCKED_HOSTS.contains(normalizedHost) || hasBlockedSuffix(normalizedHost))) {
            throw new UnsafeUrlException("internal host names are not allowed");
        }
        if (!normalizedHost.startsWith("[") && !normalizedHost.contains(".") && !isNumericHost(normalizedHost)) {
            throw new UnsafeUrlException("single-label host names are not allowed");
        }
        if (isNumericHost(normalizedHost) && !normalizedHost.matches("\\d{1,3}(\\.\\d{1,3}){3}")) {
            // decimal (2130706433), octal (0177.0.0.1) or hex (0x7f.1) encodings: reject outright
            throw new UnsafeUrlException("non-canonical IP address notation");
        }
        return uri;
    }

    /** Resolves the host and requires every address to be public. */
    public InetAddress[] resolve(String host) throws UnknownHostException {
        String h = host.startsWith("[") && host.endsWith("]") ? host.substring(1, host.length() - 1) : host;
        InetAddress[] addresses = InetAddress.getAllByName(h);
        if (addresses.length == 0) {
            throw new UnknownHostException(host);
        }
        for (InetAddress address : addresses) {
            if (!isAllowedAddress(address)) {
                throw new UnsafeUrlException("the host resolves to a non-public address");
            }
        }
        return addresses;
    }

    public boolean isAllowedAddress(InetAddress address) {
        if (allowLoopback && address.isLoopbackAddress()) {
            return true;
        }
        return isPublicAddress(address);
    }

    /** True only for globally routable unicast addresses. */
    public static boolean isPublicAddress(InetAddress address) {
        if (address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            return false;
        }
        byte[] b = address.getAddress();
        if (address instanceof Inet6Address) {
            if (isIpv4Mapped(b) || isIpv4Compatible(b)) {
                return isPublicIpv4(Arrays.copyOfRange(b, 12, 16));
            }
            int first = b[0] & 0xff;
            int second = b[1] & 0xff;
            if ((first & 0xfe) == 0xfc) {
                return false; // fc00::/7 unique local
            }
            if (first == 0xfe && (second & 0xc0) == 0xc0) {
                return false; // fec0::/10 deprecated site-local
            }
            if (first == 0x20 && second == 0x01 && (b[2] & 0xff) == 0x0d && (b[3] & 0xff) == 0xb8) {
                return false; // 2001:db8::/32 documentation
            }
            if (first == 0x00 && second == 0x64 && (b[2] & 0xff) == 0xff && (b[3] & 0xff) == 0x9b) {
                return isPublicIpv4(Arrays.copyOfRange(b, 12, 16)); // 64:ff9b::/96 NAT64
            }
            if (first == 0x20 && second == 0x02) {
                return isPublicIpv4(Arrays.copyOfRange(b, 2, 6)); // 2002::/16 6to4 embeds an IPv4
            }
            return true;
        }
        return address instanceof Inet4Address && isPublicIpv4(b);
    }

    private static boolean isPublicIpv4(byte[] b) {
        int a0 = b[0] & 0xff;
        int a1 = b[1] & 0xff;
        int a2 = b[2] & 0xff;
        return !(a0 == 0 // 0.0.0.0/8
                || a0 == 10 // private
                || a0 == 127 // loopback
                || (a0 == 100 && a1 >= 64 && a1 <= 127) // 100.64/10 carrier-grade NAT
                || (a0 == 169 && a1 == 254) // link-local incl. cloud metadata 169.254.169.254
                || (a0 == 172 && a1 >= 16 && a1 <= 31) // private
                || (a0 == 192 && a1 == 0 && a2 == 0) // IETF protocol assignments
                || (a0 == 192 && a1 == 0 && a2 == 2) // TEST-NET-1
                || (a0 == 192 && a1 == 88 && a2 == 99) // 6to4 relay anycast
                || (a0 == 192 && a1 == 168) // private
                || (a0 == 198 && (a1 == 18 || a1 == 19)) // benchmarking
                || (a0 == 198 && a1 == 51 && a2 == 100) // TEST-NET-2
                || (a0 == 203 && a1 == 0 && a2 == 113) // TEST-NET-3
                || a0 >= 224); // multicast, reserved, broadcast
    }

    private static boolean isIpv4Mapped(byte[] b) {
        for (int i = 0; i < 10; i++) {
            if (b[i] != 0) {
                return false;
            }
        }
        return (b[10] & 0xff) == 0xff && (b[11] & 0xff) == 0xff;
    }

    private static boolean isIpv4Compatible(byte[] b) {
        for (int i = 0; i < 12; i++) {
            if (b[i] != 0) {
                return false;
            }
        }
        return true;
    }

    private static boolean hasBlockedSuffix(String host) {
        return BLOCKED_SUFFIXES.stream().anyMatch(host::endsWith);
    }

    private static boolean isNumericHost(String host) {
        return host.matches("(0x[0-9a-f]+|\\d+)(\\.(0x[0-9a-f]+|\\d+)){0,3}");
    }
}
