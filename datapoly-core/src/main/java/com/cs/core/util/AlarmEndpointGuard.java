// Use of this source code is governed by a BSD-style license
package com.cs.core.util;

import com.cs.common.exception.CommonException;
import com.cs.common.exception.ResponseErrorCode;
import org.apache.commons.lang3.StringUtils;

import java.net.URI;
import java.util.regex.Pattern;

/**
 * Host allowlist for the alarm webhook endpoint (SSRF guard): the endpoint is operator
 * configured and the client performs a real outbound POST, so loopback, RFC1918 private,
 * link-local (cloud metadata among them) and unspecified address literals are rejected.
 *
 * <p>Only address <em>literals</em> are inspected; a public hostname that resolves to a
 * private address (DNS rebinding) is a documented residual limitation — no resolution is
 * performed here.
 */
public final class AlarmEndpointGuard {

    private static final Pattern IPV4_LITERAL = Pattern.compile("[0-9]{1,3}(\\.[0-9]{1,3}){3}");

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
        String normalized = host.toLowerCase();
        if (isLoopbackName(normalized)) {
            return false;
        }
        if (normalized.contains(":")) {
            return isIpv6Allowed(normalized);
        }
        if (IPV4_LITERAL.matcher(normalized).matches()) {
            return isIpv4Allowed(normalized);
        }
        // not an address literal: hostname, resolution deliberately out of scope
        return true;
    }

    private static boolean isLoopbackName(String host) {
        return "localhost".equals(host) || host.endsWith(".localhost");
    }

    private static boolean isIpv4Allowed(String ip) {
        long bits = ipv4ToLong(ip);
        if (bits < 0) {
            return false;
        }
        return !inCidr(bits, 0x0A000000L, 8)    // 10.0.0.0/8
                && !inCidr(bits, 0xAC100000L, 12) // 172.16.0.0/12
                && !inCidr(bits, 0xC0A80000L, 16) // 192.168.0.0/16
                && !inCidr(bits, 0x7F000000L, 8)  // 127.0.0.0/8
                && !inCidr(bits, 0xA9FE0000L, 16) // 169.254.0.0/16 (incl. cloud metadata)
                && !inCidr(bits, 0x00000000L, 8); // 0.0.0.0/8
    }

    private static boolean isIpv6Allowed(String ip) {
        if ("::".equals(ip) || "::1".equals(ip) || "0:0:0:0:0:0:0:1".equals(ip)) {
            return false;
        }
        // IPv4-mapped: judge by the embedded IPv4 address
        int mapped = ip.indexOf("::ffff:");
        if (0 == mapped) {
            String embedded = ip.substring(7);
            if (IPV4_LITERAL.matcher(embedded).matches()) {
                return isIpv4Allowed(embedded);
            }
        }
        if (ip.startsWith("::")) {
            // no leading hextet (e.g. ::2) — not one of the rejected special forms above
            return true;
        }
        int colon = ip.indexOf(':');
        String firstGroup = colon > 0 ? ip.substring(0, colon) : ip;
        if (!firstGroup.matches("[0-9a-f]{1,4}")) {
            return false;
        }
        int first = Integer.parseInt(firstGroup, 16);
        // fc00::/7 unique-local, fe80::/10 link-local
        return (first & 0xFE00) != 0xFC00 && (first & 0xFFC0) != 0xFE80;
    }

    private static boolean inCidr(long bits, long network, int prefix) {
        long mask = prefix == 0 ? 0L : (0xFFFFFFFFL << (32 - prefix)) & 0xFFFFFFFFL;
        return (bits & mask) == (network & mask);
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
