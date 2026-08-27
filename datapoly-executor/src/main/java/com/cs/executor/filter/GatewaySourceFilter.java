// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.executor.filter;

import com.cs.common.exception.ResponseErrorCode;
import com.cs.core.util.ResponseWriteUtils;
import com.google.common.base.Charsets;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.servlet.*;
import javax.servlet.http.*;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.Objects;

/**
 * Executor gateway source check filter (S2): rejects direct access that bypasses the gateway.
 *
 * <p>Two-layer validation:
 * <ol>
 *   <li>The TCP remote address must match the CIDR whitelist
 *       {@code datapoly.executor.gateway.trusted-cidrs} (IPv4 CIDR only; IPv6 exact match);</li>
 *   <li>Optional shared-secret header {@code X-DATAPOLY-Gateway-Token}: when
 *       {@code datapoly.executor.gateway.auth-token} is configured, it must match in constant time.</li>
 * </ol>
 * Note: no forwarded headers (X-Forwarded-For etc.) are trusted; only {@code getRemoteAddr()} is used.
 * Escape hatch: {@code datapoly.executor.gateway.enabled=false}.
 */
@Slf4j
@Component
public class GatewaySourceFilter implements Filter {

    public static final String GATEWAY_TOKEN_HEADER = "X-DATAPOLY-Gateway-Token";

    @Value("${datapoly.executor.gateway.enabled:true}")
    private boolean enabled;

    @Value("${datapoly.executor.gateway.trusted-cidrs:127.0.0.1,::1}")
    private String trustedCidrs;

    @Value("${datapoly.executor.gateway.auth-token:}")
    private String authToken;

    @Override
    public void doFilter(ServletRequest req, ServletResponse resp, FilterChain chain)
            throws IOException, ServletException {
        if (!enabled) {
            chain.doFilter(req, resp);
            return;
        }

        HttpServletRequest request = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) resp;
        String remoteAddr = request.getRemoteAddr();
        if (!isTrustedAddress(remoteAddr)) {
            reject(response, remoteAddr, "untrusted source address");
            return;
        }

        if (StringUtils.isNotBlank(authToken)) {
            String gatewayToken = request.getHeader(GATEWAY_TOKEN_HEADER);
            if (!isTokenMatched(gatewayToken)) {
                reject(response, remoteAddr, "missing or invalid gateway token");
                return;
            }
        }

        chain.doFilter(request, response);
    }

    private void reject(HttpServletResponse response, String remoteAddr, String reason)
            throws IOException {
        log.warn("Rejected direct access to executor from [{}]: {}", remoteAddr, reason);
        ResponseWriteUtils.writeError(response, HttpServletResponse.SC_FORBIDDEN,
                ResponseErrorCode.ERROR_ACCESS_FORBIDDEN, "Access denied");
    }

    private boolean isTokenMatched(String gatewayToken) {
        if (StringUtils.isBlank(gatewayToken)) {
            return false;
        }
        return MessageDigest.isEqual(
                authToken.getBytes(Charsets.UTF_8),
                gatewayToken.getBytes(Charsets.UTF_8));
    }

    private boolean isTrustedAddress(String remoteAddr) {
        if (StringUtils.isBlank(remoteAddr)) {
            return false;
        }
        for (String pattern : trustedCidrs.split(",")) {
            if (matches(pattern.trim(), remoteAddr)) {
                return true;
            }
        }
        return false;
    }

    static boolean matches(String pattern, String addr) {
        if (StringUtils.isBlank(pattern) || StringUtils.isBlank(addr)) {
            return false;
        }
        if (pattern.equals(addr)) {
            return true;
        }
        if (isLoopback(pattern) && isLoopback(addr)) {
            return true;
        }
        if (pattern.contains("/")) {
            String[] parts = pattern.split("/");
            int prefix;
            try {
                prefix = Integer.parseInt(parts[1]);
            } catch (NumberFormatException e) {
                return false;
            }
            if (prefix < 0 || prefix > 32) {
                return false;
            }
            return isIPv4InCidr(parts[0], addr, prefix);
        }
        return false;
    }

    private static boolean isIPv4InCidr(String network, String addr, int prefix) {
        long networkBits = ipv4ToLong(network);
        long addrBits = ipv4ToLong(addr);
        if (networkBits < 0 || addrBits < 0) {
            return false;
        }
        if (0 == prefix) {
            return true;
        }
        long mask = (32 == prefix) ? 0xFFFFFFFFL : (0xFFFFFFFFL << (32 - prefix)) & 0xFFFFFFFFL;
        return (networkBits & mask) == (addrBits & mask);
    }

    private static long ipv4ToLong(String ip) {
        String[] parts = ip.split("\\.");
        if (4 != parts.length) {
            return -1L;
        }
        long value = 0L;
        for (String part : parts) {
            try {
                int octet = Integer.parseInt(part);
                if (octet < 0 || octet > 255) {
                    return -1L;
                }
                value = (value << 8) | octet;
            } catch (NumberFormatException e) {
                return -1L;
            }
        }
        return value;
    }

    private static boolean isLoopback(String addr) {
        return Objects.equals("127.0.0.1", addr)
                || Objects.equals("::1", addr)
                || Objects.equals("0:0:0:0:0:0:0:1", addr);
    }

}
