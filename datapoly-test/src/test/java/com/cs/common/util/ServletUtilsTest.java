// Use of this source code is governed by a BSD-style license
package com.cs.common.util;

import org.junit.Test;

import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

public class ServletUtilsTest {

    private HttpServletRequest fakeRequest(String url, String uri, String remoteAddr, Map<String, String> headers) {
        return (HttpServletRequest) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{HttpServletRequest.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getRequestURL":
                            return new StringBuffer(url);
                        case "getRequestURI":
                            return uri;
                        case "getRemoteAddr":
                            return remoteAddr;
                        case "getHeader":
                            return headers.get(String.valueOf(args[0]));
                        default:
                            return null;
                    }
                });
    }

    @Test
    public void testGetDomainStripsRequestUri() {
        HttpServletRequest request = fakeRequest("http://api.example.com:8090/svc/api",
                "/svc/api", null, new HashMap<>());
        assertEquals("http://api.example.com:8090", ServletUtils.getDomain(request));
    }

    @Test
    public void testHeaderAccessors() {
        Map<String, String> headers = new HashMap<>();
        headers.put("Origin", "http://front.example.com");
        headers.put("user-agent", "DataPoly-Test/1.0");
        HttpServletRequest request = fakeRequest("http://x/y", "/y", null, headers);
        assertEquals("http://front.example.com", ServletUtils.getOrigin(request));
        assertEquals("DataPoly-Test/1.0", ServletUtils.getUserAgent(request));
        assertEquals("/y", ServletUtils.getPathUri(request));
    }

    @Test
    public void testIpAddrPrefersForwardedForHeader() {
        Map<String, String> headers = new HashMap<>();
        headers.put("x-forwarded-for", "1.2.3.4, 5.6.7.8");
        HttpServletRequest request = fakeRequest("http://x/y", "/y", "10.0.0.1", headers);
        // current implementation returns the raw header value without splitting the chain
        assertEquals("1.2.3.4, 5.6.7.8", ServletUtils.getIpAddr(request));
    }

    @Test
    public void testIpAddrFallsThroughUnknownHeaders() {
        Map<String, String> headers = new HashMap<>();
        headers.put("x-forwarded-for", "unknown");
        headers.put("Proxy-Client-IP", "9.9.9.9");
        HttpServletRequest request = fakeRequest("http://x/y", "/y", "10.0.0.1", headers);
        assertEquals("9.9.9.9", ServletUtils.getIpAddr(request));
    }

    @Test
    public void testIpAddrFallsBackToRemoteAddr() {
        HttpServletRequest request = fakeRequest("http://x/y", "/y", "10.0.0.1", new HashMap<>());
        assertEquals("10.0.0.1", ServletUtils.getIpAddr(request));
    }

    @Test
    public void testIpAddrWalksEveryProxyHeaderInOrder() {
        // each proxy header wins when all previous ones are absent or "unknown"
        assertEquals("8.8.4.4", ServletUtils.getIpAddr(requestWithHeaders("WL-Proxy-Client-IP", "8.8.4.4")));
        assertEquals("7.7.7.7", ServletUtils.getIpAddr(requestWithHeaders("HTTP_CLIENT_IP", "7.7.7.7")));
        assertEquals("6.6.6.6", ServletUtils.getIpAddr(requestWithHeaders("HTTP_X_FORWARDED_FOR", "6.6.6.6")));
    }

    @Test
    public void testIpAddrTreatsBlankHeaderValuesAsAbsent() {
        // empty-string and "unknown" (case-insensitive) headers fall through the whole walk
        Map<String, String> headers = new HashMap<>();
        headers.put("x-forwarded-for", "");
        headers.put("Proxy-Client-IP", "");
        headers.put("WL-Proxy-Client-IP", "unknown");
        headers.put("HTTP_CLIENT_IP", "");
        headers.put("HTTP_X_FORWARDED_FOR", "UNKNOWN");
        HttpServletRequest request = fakeRequest("http://x/y", "/y", "10.0.0.2", headers);
        assertEquals("10.0.0.2", ServletUtils.getIpAddr(request));
    }

    @Test
    public void testIpAddrUnknownProxyHeaderFallsThroughToLaterHeader() {
        Map<String, String> headers = new HashMap<>();
        headers.put("x-forwarded-for", "unknown");
        headers.put("Proxy-Client-IP", "unknown");
        headers.put("WL-Proxy-Client-IP", "5.5.5.5");
        HttpServletRequest request = fakeRequest("http://x/y", "/y", "10.0.0.1", headers);
        assertEquals("5.5.5.5", ServletUtils.getIpAddr(request));
    }

    @Test
    public void testIpAddrHeaderReadFailureReturnsInitialAddress() {
        HttpServletRequest request = (HttpServletRequest) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{HttpServletRequest.class},
                (proxy, method, args) -> {
                    if ("getHeader".equals(method.getName())) {
                        throw new IllegalStateException("header access broken");
                    }
                    if ("getRemoteAddr".equals(method.getName())) {
                        return "10.9.9.9";
                    }
                    return null;
                });
        // the guard value survives when header access blows up
        assertEquals("0.0.0.0", ServletUtils.getIpAddr(request));
    }

    @Test
    public void testIpAddrSkipsUnknownHttpClientIpHeader() {
        Map<String, String> headers = new HashMap<>();
        headers.put("x-forwarded-for", "");
        headers.put("Proxy-Client-IP", "");
        headers.put("WL-Proxy-Client-IP", "");
        headers.put("HTTP_CLIENT_IP", "unknown");
        headers.put("HTTP_X_FORWARDED_FOR", "9.8.7.6");
        HttpServletRequest request = fakeRequest("http://x/y", "/y", "10.0.0.1", headers);
        assertEquals("9.8.7.6", ServletUtils.getIpAddr(request));
    }

    @Test
    public void testUtilityConstructorIsCallable() throws Exception {
        // implicit default constructor of the final utility class: instantiate once
        assertNotNull(ServletUtils.class.getDeclaredConstructor().newInstance());
    }

    private HttpServletRequest requestWithHeaders(String name, String value) {
        Map<String, String> headers = new HashMap<>();
        headers.put(name, value);
        return fakeRequest("http://x/y", "/y", "10.0.0.1", headers);
    }
}
