package io.workflowengine.worker.infrastructure.http;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.Locale;
import java.util.Set;

/**
 * Destination policy for HTTP Request nodes. Blocks loopback, private, link-local (including cloud metadata
 * 169.254.169.254), CGNAT, unique-local IPv6, multicast and reserved ranges. Hosts explicitly allow-listed by an
 * administrator (configuration, not workflow input) bypass the address check.
 */
public class SsrfPolicy {

    private final Set<String> allowedHosts;

    public SsrfPolicy(Set<String> allowedHosts) {
        this.allowedHosts = allowedHosts.stream().map(h -> h.toLowerCase(Locale.ROOT)).collect(java.util.stream.Collectors.toSet());
    }

    public boolean isAllowListed(String host) {
        return host != null && allowedHosts.contains(host.toLowerCase(Locale.ROOT));
    }

    public boolean isBlocked(InetAddress address) {
        if (address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            return true;
        }
        byte[] b = address.getAddress();
        if (address instanceof Inet4Address) {
            int b0 = b[0] & 0xff;
            int b1 = b[1] & 0xff;
            int b2 = b[2] & 0xff;
            return b0 == 0 // 0.0.0.0/8
                    || (b0 == 100 && (b1 & 0xC0) == 64) // 100.64.0.0/10 carrier-grade NAT
                    || (b0 == 192 && b1 == 0 && b2 == 0) // 192.0.0.0/24 IETF protocol assignments
                    || (b0 == 198 && (b1 == 18 || b1 == 19)) // benchmarking
                    || b0 >= 240; // reserved and broadcast
        }
        if (address instanceof Inet6Address) {
            if ((b[0] & 0xFE) == 0xFC) {
                return true; // fc00::/7 unique local
            }
            if (isEmbeddedIpv4(b)) {
                try {
                    return isBlocked(InetAddress.getByAddress(java.util.Arrays.copyOfRange(b, 12, 16)));
                } catch (java.net.UnknownHostException e) {
                    return true;
                }
            }
        }
        return false;
    }

    /** IPv4-mapped (::ffff:0:0/96), IPv4-compatible (::/96) and NAT64 (64:ff9b::/96) addresses. */
    private static boolean isEmbeddedIpv4(byte[] b) {
        boolean firstTenZero = true;
        for (int i = 0; i < 10; i++) {
            firstTenZero &= b[i] == 0;
        }
        boolean mapped = firstTenZero && (b[10] & 0xff) == 0xff && (b[11] & 0xff) == 0xff;
        boolean compatible = firstTenZero && b[10] == 0 && b[11] == 0;
        boolean nat64 = (b[0] & 0xff) == 0x00 && (b[1] & 0xff) == 0x64 && (b[2] & 0xff) == 0xff && (b[3] & 0xff) == 0x9b;
        return mapped || compatible || nat64;
    }
}
