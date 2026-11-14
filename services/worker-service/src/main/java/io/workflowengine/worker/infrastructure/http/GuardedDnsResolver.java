package io.workflowengine.worker.infrastructure.http;

import java.net.InetAddress;
import java.net.UnknownHostException;
import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.SystemDefaultDnsResolver;

/**
 * Resolves host names and rejects any result that the {@link SsrfPolicy} blocks. Because the HTTP client connects to
 * exactly the addresses returned here, a DNS-rebinding answer cannot slip past an earlier check.
 */
public class GuardedDnsResolver implements DnsResolver {

    private final SsrfPolicy policy;
    private final DnsResolver delegate = SystemDefaultDnsResolver.INSTANCE;

    public GuardedDnsResolver(SsrfPolicy policy) {
        this.policy = policy;
    }

    @Override
    public InetAddress[] resolve(String host) throws UnknownHostException {
        InetAddress[] addresses = delegate.resolve(host);
        if (policy.isAllowListed(host)) {
            return addresses;
        }
        for (InetAddress address : addresses) {
            if (policy.isBlocked(address)) {
                throw new DestinationBlockedException(host, address);
            }
        }
        return addresses;
    }

    @Override
    public String resolveCanonicalHostname(String host) throws UnknownHostException {
        return delegate.resolveCanonicalHostname(host);
    }

    /** Raised when a destination resolves to a forbidden address. */
    public static class DestinationBlockedException extends UnknownHostException {
        public DestinationBlockedException(String host, InetAddress address) {
            super("Destination " + host + " resolves to a blocked address (" + address.getHostAddress() + ")");
        }
    }
}
