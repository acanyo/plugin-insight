package com.xhhao.insight.net;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Locale;
import org.springframework.stereotype.Component;

/** Central SSRF boundary for HTTP clients that contact user- or extension-provided URLs. */
@Component
public final class OutboundDestinationPolicy {

    private final HostResolver hostResolver;

    public OutboundDestinationPolicy() {
        this(InetAddress::getAllByName);
    }

    public OutboundDestinationPolicy(HostResolver hostResolver) {
        this.hostResolver = hostResolver;
    }

    public URI requireHttpsPublic(URI uri) {
        return validate(uri, false, "Destination");
    }

    public URI validate(URI uri, boolean allowPrivateNetwork, String subject) {
        if (uri == null || !"https".equalsIgnoreCase(uri.getScheme())) {
            throw new DestinationRejectedException(subject + " must use HTTPS");
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new DestinationRejectedException(subject + " must contain a valid host");
        }
        if (uri.getRawUserInfo() != null) {
            throw new DestinationRejectedException(subject + " must not contain user credentials");
        }
        if (uri.getRawFragment() != null) {
            throw new DestinationRejectedException(subject + " must not contain a fragment");
        }

        InetAddress[] addresses = resolve(uri.getHost(), subject);
        if (addresses.length == 0) {
            throw new DestinationRejectedException(subject + " host did not resolve to an address");
        }
        if (Arrays.stream(addresses).anyMatch(OutboundDestinationPolicy::isUnusableAddress)) {
            throw new DestinationRejectedException(
                subject + " host resolves to an unusable network address");
        }
        if (!allowPrivateNetwork
            && Arrays.stream(addresses).anyMatch(address -> !isPublicAddress(address))) {
            throw new DestinationRejectedException(
                subject + " host resolves to a private or reserved network address");
        }
        return uri;
    }

    private InetAddress[] resolve(String host, String subject) {
        try {
            return hostResolver.resolve(host.toLowerCase(Locale.ROOT));
        } catch (UnknownHostException error) {
            throw new DestinationRejectedException(subject + " host could not be resolved");
        }
    }

    static boolean isPublicAddress(InetAddress address) {
        if (isUnusableAddress(address) || address.isLoopbackAddress()
            || address.isLinkLocalAddress() || address.isSiteLocalAddress()) {
            return false;
        }
        if (address instanceof Inet4Address) {
            return isPublicIpv4(address.getAddress());
        }
        if (address instanceof Inet6Address) {
            return isPublicIpv6(address.getAddress());
        }
        return false;
    }

    private static boolean isUnusableAddress(InetAddress address) {
        return address == null || address.isAnyLocalAddress() || address.isMulticastAddress();
    }

    private static boolean isPublicIpv4(byte[] value) {
        int first = Byte.toUnsignedInt(value[0]);
        int second = Byte.toUnsignedInt(value[1]);
        int third = Byte.toUnsignedInt(value[2]);

        if (first == 0 || first == 10 || first == 127 || first >= 224) {
            return false;
        }
        if (first == 100 && second >= 64 && second <= 127) {
            return false;
        }
        if (first == 169 && second == 254) {
            return false;
        }
        if (first == 172 && second >= 16 && second <= 31) {
            return false;
        }
        if (first == 192 && (second == 168 || second == 0 && third <= 2
            || second == 88 && third == 99)) {
            return false;
        }
        if (first == 198 && (second == 18 || second == 19 || second == 51 && third == 100)) {
            return false;
        }
        return !(first == 203 && second == 0 && third == 113);
    }

    private static boolean isPublicIpv6(byte[] value) {
        int first = Byte.toUnsignedInt(value[0]);
        int second = Byte.toUnsignedInt(value[1]);

        if ((first & 0xE0) != 0x20) {
            return false;
        }
        if (first == 0x20 && second == 0x01) {
            int third = Byte.toUnsignedInt(value[2]);
            int fourth = Byte.toUnsignedInt(value[3]);
            if (third == 0x0d && fourth == 0xb8) {
                return false;
            }
            if (third == 0x00 || third == 0x10) {
                return false;
            }
        }
        return !(first == 0x20 && second == 0x02);
    }

    @FunctionalInterface
    public interface HostResolver {

        InetAddress[] resolve(String host) throws UnknownHostException;
    }

    public static final class DestinationRejectedException extends IllegalArgumentException {

        public DestinationRejectedException(String message) {
            super(message);
        }
    }
}
