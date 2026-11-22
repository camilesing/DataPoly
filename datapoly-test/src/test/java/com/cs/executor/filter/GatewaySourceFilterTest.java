// Use of this source code is governed by a BSD-style license
package com.cs.executor.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.Before;
import org.junit.Test;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

/**
 * Tests for the gateway source check (S2): static address matching plus the {@code doFilter}
 * behavior — enable switch, trusted-address pass-through, optional shared-secret header with
 * constant-time comparison, and fail-closed 403 rejection.
 */
public class GatewaySourceFilterTest {

    private GatewaySourceFilter filter;
    private final AtomicInteger chainCalls = new AtomicInteger();
    private StringWriter responseBody;
    private HttpServletResponse response;
    private final Map<String, String> responseHeaders = new HashMap<>();
    private final Integer[] statusRef = new Integer[]{null};

    @Before
    public void setUp() {
        filter = new GatewaySourceFilter();
        chainCalls.set(0);
        responseBody = new StringWriter();
        responseHeaders.clear();
        statusRef[0] = null;
        response = recorderResponse();
    }

    private HttpServletResponse recorderResponse() {
        return (HttpServletResponse) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{HttpServletResponse.class},
                (p, m, a) -> {
                    switch (m.getName()) {
                        case "getWriter":
                            return new PrintWriter(responseBody, true);
                        case "setStatus":
                            statusRef[0] = (Integer) a[0];
                            return null;
                        case "getStatus":
                            return statusRef[0];
                        case "setContentType":
                            responseHeaders.put("Content-Type", String.valueOf(a[0]));
                            return null;
                        case "setCharacterEncoding":
                            responseHeaders.put("Character-Encoding", String.valueOf(a[0]));
                            return null;
                        default:
                            return null;
                    }
                });
    }

    private HttpServletRequest request(String remoteAddr, Map<String, String> headers) {
        return (HttpServletRequest) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{HttpServletRequest.class},
                (p, m, a) -> {
                    switch (m.getName()) {
                        case "getRemoteAddr":
                            return remoteAddr;
                        case "getMethod":
                            return "GET";
                        case "getRequestURI":
                            return "/api/demo";
                        case "getHeader":
                            return headers.get(String.valueOf(a[0]));
                        default:
                            return null;
                    }
                });
    }

    private FilterChain chain() {
        return (req, resp) -> chainCalls.incrementAndGet();
    }

    private void configure(boolean enabled, String cidrs, String authToken) throws Exception {
        set("enabled", enabled);
        set("trustedCidrs", cidrs);
        set("authToken", authToken);
    }

    private void set(String name, Object value) throws Exception {
        Field field = GatewaySourceFilter.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(filter, value);
    }

    /* ------------------------------ static matching ------------------------------ */

    @Test
    public void exactMatch() {
        assertTrue(GatewaySourceFilter.matches("172.28.0.40", "172.28.0.40"));
        assertTrue(GatewaySourceFilter.matches("::1", "::1"));
        assertFalse(GatewaySourceFilter.matches("172.28.0.40", "172.28.0.41"));
    }

    @Test
    public void loopbackAliases() {
        assertTrue(GatewaySourceFilter.matches("127.0.0.1", "0:0:0:0:0:0:0:1"));
        assertTrue(GatewaySourceFilter.matches("::1", "127.0.0.1"));
    }

    @Test
    public void cidrMatch() {
        assertTrue(GatewaySourceFilter.matches("172.28.0.0/24", "172.28.0.40"));
        assertFalse(GatewaySourceFilter.matches("172.28.0.0/24", "172.28.1.40"));
        assertTrue(GatewaySourceFilter.matches("10.0.0.0/8", "10.99.8.7"));
        assertTrue(GatewaySourceFilter.matches("192.168.1.5/32", "192.168.1.5"));
        assertFalse(GatewaySourceFilter.matches("192.168.1.5/32", "192.168.1.6"));
        assertTrue(GatewaySourceFilter.matches("0.0.0.0/0", "8.8.8.8"));
    }

    @Test
    public void invalidInputsNeverMatch() {
        assertFalse(GatewaySourceFilter.matches("", "1.2.3.4"));
        assertFalse(GatewaySourceFilter.matches("not-a-cidr/xx", "1.2.3.4"));
        assertFalse(GatewaySourceFilter.matches("172.28.0.0/24", "not-an-ip"));
        assertFalse(GatewaySourceFilter.matches("172.28.0.0/24", "::1"));
        assertFalse(GatewaySourceFilter.matches("172.28.0.0/33", "172.28.0.1"));
    }

    @Test
    public void malformedOrUnparsableEntriesFailClosed() {
        // no prefix length after the slash — previously an ArrayIndexOutOfBoundsException
        assertFalse(GatewaySourceFilter.matches("10.0.0.0/", "10.0.0.1"));
        // blank network base before the slash
        assertFalse(GatewaySourceFilter.matches("/24", "1.2.3.4"));
        // negative prefix length
        assertFalse(GatewaySourceFilter.matches("10.0.0.0/-1", "10.0.0.1"));
        // unparsable network base
        assertFalse(GatewaySourceFilter.matches("not-an-ip/8", "1.2.3.4"));
        // unparsable octet
        assertFalse(GatewaySourceFilter.matches("10.0.0.a/8", "10.0.0.1"));
        // negative and out-of-range octets in the network and in the address
        assertFalse(GatewaySourceFilter.matches("10.-1.0.1/24", "10.0.0.1"));
        assertFalse(GatewaySourceFilter.matches("10.0.0.300/24", "10.0.0.1"));
        assertFalse(GatewaySourceFilter.matches("10.0.0.0/8", "-1.2.3.4"));
        assertFalse(GatewaySourceFilter.matches("10.0.0.0/8", "300.2.3.4"));
        // blank address
        assertFalse(GatewaySourceFilter.matches("10.0.0.0/8", " "));
    }

    @Test
    public void ipv4MappedPrefixWithoutDotIsLeftUnchanged() {
        // "::ffff:abc" has the mapped prefix but no dotted quad — returned as-is, exact match applies
        assertTrue(GatewaySourceFilter.matches("::ffff:abc", "::ffff:abc"));
        assertFalse(GatewaySourceFilter.matches("127.0.0.1", "::ffff:abc"));
    }

    @Test
    public void ipv4MappedIpv6IsNormalizedToIpv4() {
        // dual-stack listeners report the remote as ::ffff:a.b.c.d — IPv4 rules must still apply
        assertTrue(GatewaySourceFilter.matches("127.0.0.1", "::ffff:127.0.0.1"));
        assertTrue(GatewaySourceFilter.matches("::ffff:127.0.0.1", "127.0.0.1"));
        assertTrue(GatewaySourceFilter.matches("172.28.0.0/24", "::ffff:172.28.0.40"));
        assertFalse(GatewaySourceFilter.matches("172.28.0.0/24", "::ffff:172.28.1.40"));
        assertFalse(GatewaySourceFilter.matches("172.28.0.40", "::ffff:172.28.0.41"));
        // prefix match is case-insensitive
        assertTrue(GatewaySourceFilter.matches("10.0.0.0/8", "::FFFF:10.99.8.7"));
        // a real (non-mapped) IPv6 address still fails IPv4-only rules — fail-closed
        assertFalse(GatewaySourceFilter.matches("172.28.0.0/24", "2001:db8::1"));
    }

    /* -------------------------------- doFilter -------------------------------- */

    @Test
    public void disabledFilterPassesEverythingThrough() throws Exception {
        configure(false, "127.0.0.1,::1", "");
        filter.doFilter(request("8.8.8.8", new HashMap<>()), response, chain());
        assertEquals(1, chainCalls.get());
        assertTrue(responseBody.toString().isEmpty());
    }

    @Test
    public void trustedLoopbackWithoutTokenPasses() throws Exception {
        configure(true, "127.0.0.1,::1", "");
        filter.doFilter(request("127.0.0.1", new HashMap<>()), response, chain());
        assertEquals(1, chainCalls.get());
    }

    @Test
    public void loopbackAliasIpv6FormPassesWithDefaults() throws Exception {
        configure(true, "127.0.0.1,::1", "");
        filter.doFilter(request("0:0:0:0:0:0:0:1", new HashMap<>()), response, chain());
        assertEquals(1, chainCalls.get());
    }

    @Test
    public void untrustedAddressIsRejectedWith403() throws Exception {
        configure(true, "127.0.0.1,::1", "");
        filter.doFilter(request("8.8.8.8", new HashMap<>()), response, chain());
        assertEquals("untrusted source must not reach the chain", 0, chainCalls.get());
        assertEquals(Integer.valueOf(403), statusRef[0]);
        assertEquals("application/json", responseHeaders.get("Content-Type"));
        assertEquals("UTF-8", responseHeaders.get("Character-Encoding"));
        assertTrue("403 body must be a ResultEntity JSON, was: " + responseBody,
                responseBody.toString().contains("\"code\":403"));
    }

    @Test
    public void blankRemoteAddrIsRejectedFailClosed() throws Exception {
        configure(true, "127.0.0.1,::1", "");
        filter.doFilter(request("", new HashMap<>()), response, chain());
        assertEquals(0, chainCalls.get());
        assertTrue(responseBody.toString().contains("\"code\":403"));
    }

    @Test
    public void customCidrListWithSpacesAndMultipleEntries() throws Exception {
        configure(true, "10.0.0.0/8, 192.168.0.0/16", "");
        filter.doFilter(request("10.1.2.3", new HashMap<>()), response, chain());
        assertEquals(1, chainCalls.get());
        filter.doFilter(request("192.168.5.6", new HashMap<>()), response, chain());
        assertEquals(2, chainCalls.get());
        filter.doFilter(request("11.0.0.1", new HashMap<>()), response, chain());
        assertEquals("address outside every configured CIDR is rejected", 2, chainCalls.get());
        assertTrue(responseBody.toString().contains("\"code\":403"));
    }

    @Test
    public void matchingGatewayTokenPasses() throws Exception {
        configure(true, "127.0.0.1,::1", "s3cr3t-token");
        Map<String, String> headers = new HashMap<>();
        headers.put(GatewaySourceFilter.GATEWAY_TOKEN_HEADER, "s3cr3t-token");
        filter.doFilter(request("127.0.0.1", headers), response, chain());
        assertEquals(1, chainCalls.get());
    }

    @Test
    public void missingGatewayTokenIsRejected() throws Exception {
        configure(true, "127.0.0.1,::1", "s3cr3t-token");
        filter.doFilter(request("127.0.0.1", new HashMap<>()), response, chain());
        assertEquals(0, chainCalls.get());
        assertTrue(responseBody.toString().contains("\"code\":403"));
    }

    @Test
    public void blankGatewayTokenIsRejected() throws Exception {
        configure(true, "127.0.0.1,::1", "s3cr3t-token");
        Map<String, String> headers = new HashMap<>();
        headers.put(GatewaySourceFilter.GATEWAY_TOKEN_HEADER, " ");
        filter.doFilter(request("127.0.0.1", headers), response, chain());
        assertEquals(0, chainCalls.get());
        assertTrue(responseBody.toString().contains("\"code\":403"));
    }

    @Test
    public void wrongGatewayTokenIsRejected() throws Exception {
        configure(true, "127.0.0.1,::1", "s3cr3t-token");
        Map<String, String> headers = new HashMap<>();
        headers.put(GatewaySourceFilter.GATEWAY_TOKEN_HEADER, "wrong-token");
        filter.doFilter(request("127.0.0.1", headers), response, chain());
        assertEquals(0, chainCalls.get());
        assertTrue(responseBody.toString().contains("\"code\":403"));
    }
}
