package app.almanook.sourcing;

import java.net.InetAddress;
import java.net.UnknownHostException;
import org.apache.hc.client5.http.DnsResolver;

/**
 * DNS resolver for the outbound HTTP client. The connection is opened to exactly the addresses validated
 * here, which closes the DNS-rebinding gap between "check" and "connect".
 */
final class GuardedDnsResolver implements DnsResolver {

    private final UrlSafetyPolicy policy;

    GuardedDnsResolver(UrlSafetyPolicy policy) {
        this.policy = policy;
    }

    @Override
    public InetAddress[] resolve(String host) throws UnknownHostException {
        return policy.resolve(host);
    }

    @Override
    public String resolveCanonicalHostname(String host) throws UnknownHostException {
        return host;
    }
}
