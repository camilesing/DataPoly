// Use of this source code is governed by a BSD-style license
package com.cs.core.util;

import com.cs.common.exception.CommonException;
import com.cs.common.exception.ResponseErrorCode;
import org.apache.commons.lang3.StringUtils;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.net.URI;
import java.util.Arrays;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Host allowlist for the alarm webhook endpoint (SSRF guard): the endpoint is operator
 * configured and the client performs a real outbound POST, so loopback, RFC1918 private,
 * shared (CGN 100.64/10), link-local (cloud metadata among them), multicast, reserved and
 * unspecified address literals are rejected — in every notation the JDK can parse
 * (dotted quad, decimal/hex integer, per-component shorthand, IPv4-compatible/-mapped IPv6).
 *
 * <p>Address literals are normalized through {@link InetAddress} before judging, so
 * {@code ::127.0.0.1}, {@code 0:0:0:0:0:ffff:a00:1} or {@code 0x7f.0.0.1} cannot smuggle a
 * private target past a literal-string deny list. A public hostname that resolves to a
 * private address (DNS rebinding) remains a documented residual limitation — no resolution
 * is performed for plain hostnames.
 */
public final class AlarmEndpointGuard {

    private static final Pattern IPV4_LITERAL = Pattern.compile("[0-9]{1,3}(\\.[0-9]{1,3}){3}");

    /**
     * Hostnames made only of hex chars, digits, dots or the 0x prefix are overwhelmingly
     * alternate IPv4 notations the JDK would happily parse ("127.1", "0x7f.0.0.1",
     * "2130706433"); they must never fall through to the hostname branch.
     */
    private static final Pattern NUMERIC_ISH = Pattern.compile("[0-9a-fx]+(\\.[0-9a-fx]+)*");

    private AlarmEndpointGuard() {
    }

    /**
     * Throws {@link CommonException} when the endpoint is not a parsable URI, has no host,
     * or its host is a private/loopback/link-local address literal.
     */
    public static void checkEndpointAllowed(String endpoint) {
        URI uri;
        try {
            uri = URI.create(endpoint);
        } catch (IllegalArgumentException e) {
            throw new CommonException(ResponseErrorCode.ERROR_INVALID_ARGUMENT,
                    "alarm endpoint must be a parsable URI: " + endpoint);
        }
        String host = uri.getHost();
        if (StringUtils.isBlank(host)) {
            throw new CommonException(ResponseErrorCode.ERROR_INVALID_ARGUMENT,
                    "alarm endpoint must contain a host: " + endpoint);
        }
        String bare = StringUtils.strip(host, "[]");
        if (!isHostAllowed(bare)) {
            throw new CommonException(ResponseErrorCode.ERROR_INVALID_ARGUMENT,
                    "alarm endpoint host is not allowed (loopback/private/link-local addresses are rejected): " + bare);
        }
    }

    static boolean isHostAllowed(String host) {
        String normalized = host.toLowerCase(Locale.ROOT);
        if (isLoopbackName(normalized)) {
            return false;
        }
        if (normalized.contains(":")) {
            return isIpv6Allowed(normalized);
        }
        if (IPV4_LITERAL.matcher(normalized).matches()) {
            return isIpv4Allowed(normalized);
        }
        if (isNumericShorthandHost(normalized)) {
            return isNumericShorthandAllowed(normalized);
        }
        // not an address literal: hostname, resolution deliberately out of scope
        return true;
    }

    private static boolean isLoopbackName(String host) {
        return "localhost".equals(host) || host.endsWith(".localhost");
    }

    private static boolean isNumericShorthandHost(String host) {
        if (!NUMERIC_ISH.matcher(host).matches()) {
            return false;
        }
        // require at least one digit so pure-word hex-looking names ("beef") stay hostnames
        return host.chars().anyMatch(c -> c >= '0' && c <= '9');
    }

    /**
     * Decimal/hex/shorthand IPv4 notations: let the JDK parse them (literal parsing, no
     * hostname semantics) and judge the resulting address; unparsable forms are denied.
     */
    private static boolean isNumericShorthandAllowed(String host) {
        try {
            InetAddress[] addresses = InetAddress.getAllByName(host);
            if (0 == addresses.length) {
                return false;
            }
            for (InetAddress address : addresses) {
                if (!isAddressAllowed(address)) {
                    return false;
                }
            }
            return true;
        } catch (UnknownHostException e) {
            return false;
        }
    }

    private static boolean isIpv4Allowed(String ip) {
        long bits = ipv4ToLong(ip);
        if (bits < 0) {
            return false;
        }
        return isIpv4BitsAllowed(bits);
    }

    private static boolean isIpv4BitsAllowed(long bits) {
        return !inCidr(bits, 0x0A000000L, 8)    // 10.0.0.0/8
                && !inCidr(bits, 0xAC100000L, 12) // 172.16.0.0/12
                && !inCidr(bits, 0xC0A80000L, 16) // 192.168.0.0/16
                && !inCidr(bits, 0x7F000000L, 8)  // 127.0.0.0/8
                && !inCidr(bits, 0xA9FE0000L, 16) // 169.254.0.0/16 (incl. cloud metadata)
                && !inCidr(bits, 0x64400000L, 10) // 100.64.0.0/10 shared/CGN
                && !inCidr(bits, 0xE0000000L, 4)  // 224.0.0.0/4 multicast
                && !inCidr(bits, 0xF0000000L, 4)  // 240.0.0.0/4 reserved
                && !inCidr(bits, 0x00000000L, 8)  // 0.0.0.0/8
                && bits != 0xFFFFFFFFL;           // 255.255.255.255
    }

    private static boolean isIpv6Allowed(String ip) {
        final InetAddress address;
        try {
            // literal parsing for bracket-stripped IPv6 text; unknown forms are denied
            address = InetAddress.getByName(ip);
        } catch (UnknownHostException e) {
            return false;
        }
        return isAddressAllowed(address);
    }

    /**
     * Policy on a parsed address: JDK helpers cover any/loopback/link-local/site-local/
     * multicast; IPv4 additionally rejects CGN/reserved/broadcast; IPv6 additionally
     * rejects unique-local fc00::/7 and any IPv4-compatible/-mapped embedding of a
     * disallowed IPv4 address.
     */
    private static boolean isAddressAllowed(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress()
                || address.isLinkLocalAddress() || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            return false;
        }
        if (address instanceof Inet4Address) {
            return isIpv4BitsAllowed(ipv4BytesToLong(address.getAddress()));
        }
        if (address instanceof Inet6Address) {
            byte[] bytes = address.getAddress();
            boolean v4Compatible = true;
            for (int i = 0; i < 12; i++) {
                if (0 != bytes[i]) {
                    v4Compatible = false;
                    break;
                }
            }
            boolean v4Mapped = !v4Compatible
                    && (bytes[10] & 0xFF) == 0xFF && (bytes[11] & 0xFF) == 0xFF
                    && bytes[0] == 0 && bytes[1] == 0 && bytes[2] == 0 && bytes[3] == 0
                    && bytes[4] == 0 && bytes[5] == 0 && bytes[6] == 0 && bytes[7] == 0
                    && bytes[8] == 0 && bytes[9] == 0;
            if (v4Compatible || v4Mapped) {
                // embedded IPv4 (e.g. ::127.0.0.1, ::ffff:a00:1): judged by the IPv4 policy
                return isIpv4BitsAllowed(ipv4BytesToLong(Arrays.copyOfRange(bytes, 12, 16)));
            }
            // unique-local fc00::/7 has no InetAddress helper
            return (bytes[0] & 0xFE) != 0xFC;
        }
        return false;
    }

    private static boolean inCidr(long bits, long network, int prefix) {
        long mask = prefix == 0 ? 0L : (0xFFFFFFFFL << (32 - prefix)) & 0xFFFFFFFFL;
        return (bits & mask) == (network & mask);
    }

    private static long ipv4BytesToLong(byte[] bytes) {
        long value = 0L;
        for (byte b : bytes) {
            value = (value << 8) | (b & 0xFFL);
        }
        return value;
    }

    private static long ipv4ToLong(String ip) {
        String[] parts = ip.split("\\.");
        if (4 != parts.length) {
            return -1L;
        }
        long value = 0L;
        for (String part : parts) {
            int octet = Integer.parseInt(part);
            if (octet < 0 || octet > 255) {
                return -1L;
            }
            value = (value << 8) | octet;
        }
        return value;
    }
}
