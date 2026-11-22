// Use of this source code is governed by a BSD-style license
package com.cs.core.util;

import org.junit.After;
import org.junit.Test;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

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

    /** Same fake, but every header lookup blows up: getIpAddr must fall back to 0.0.0.0. */
    private HttpServletRequest throwingHeaderRequest(String remoteAddr) {
        return (HttpServletRequest) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{HttpServletRequest.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getRemoteAddr":
                            return remoteAddr;
                        case "getHeader":
                            throw new IllegalStateException("header access exploded");
                        default:
                            return null;
                    }
                });
    }

    private void bind(HttpServletRequest request) {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    private static Map<String, String> headers(String... pairs) {
        Map<String, String> map = new HashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put(pairs[i], pairs[i + 1]);
        }
        return map;
    }

    @After
    public void unbind() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    public void testCurrentRequestResolvedFromHolder() {
        HttpServletRequest request = fakeRequest("http://h/x", "/x", null, new HashMap<String, String>());
        bind(request);
        assertSame(request, ServletUtils.getHttpServletRequest());
    }

    @Test
    public void testDomainOriginPathAndUserAgent() {
        Map<String, String> headers = new HashMap<>();
        headers.put("Origin", "http://front");
        headers.put("user-agent", "ua-1");
        bind(fakeRequest("http://api.example.com/svc/a", "/svc/a", null, headers));
        assertEquals("http://api.example.com", ServletUtils.getDomain());
        assertEquals("http://front", ServletUtils.getOrigin());
        assertEquals("/svc/a", ServletUtils.getPathUri());
        assertEquals("ua-1", ServletUtils.getUserAgent());
    }

    @Test
    public void testIpAddrHeaderFallbackChain() {
        Map<String, String> headers = new HashMap<>();
        headers.put("x-forwarded-for", "unknown");
        headers.put("WL-Proxy-Client-IP", "8.8.4.4");
        bind(fakeRequest("http://h/x", "/x", "10.0.0.1", headers));
        assertEquals("8.8.4.4", ServletUtils.getIpAddr());
    }

    @Test
    public void testIpAddrFallsBackToRemoteAddr() {
        bind(fakeRequest("http://h/x", "/x", "10.0.0.2", new HashMap<String, String>()));
        assertEquals("10.0.0.2", ServletUtils.getIpAddr());
    }

    @Test
    public void testFirstValidHeaderWins() {
        // a real x-forwarded-for short-circuits the whole chain
        bind(fakeRequest("http://h/x", "/x", "10.0.0.3", headers("x-forwarded-for", "1.2.3.4")));
        assertEquals("1.2.3.4", ServletUtils.getIpAddr());

        // empty x-forwarded-for falls through to Proxy-Client-IP, which then wins
        bind(fakeRequest("http://h/x", "/x", "10.0.0.3", headers("x-forwarded-for", "", "Proxy-Client-IP", "5.6.7.8")));
        assertEquals("5.6.7.8", ServletUtils.getIpAddr());
    }

    @Test
    public void testUnknownProxyClientIpFallsThroughToWlProxy() {
        bind(fakeRequest("http://h/x", "/x", "10.0.0.4", headers(
                "x-forwarded-for", "unknown",
                "Proxy-Client-IP", "unknown",
                "WL-Proxy-Client-IP", "7.7.7.7")));
        assertEquals("7.7.7.7", ServletUtils.getIpAddr());
    }

    @Test
    public void testHttpClientIpHeaderUsedWhenEarlierOnesUnknown() {
        bind(fakeRequest("http://h/x", "/x", "10.0.0.5", headers(
                "x-forwarded-for", "unknown",
                "Proxy-Client-IP", "unknown",
                "WL-Proxy-Client-IP", "unknown",
                "HTTP_CLIENT_IP", "9.9.9.9")));
        assertEquals("9.9.9.9", ServletUtils.getIpAddr());
    }

    @Test
    public void testHttpXForwardedForHeaderUsedLast() {
        bind(fakeRequest("http://h/x", "/x", "10.0.0.6", headers(
                "x-forwarded-for", "unknown",
                "Proxy-Client-IP", "unknown",
                "WL-Proxy-Client-IP", "unknown",
                "HTTP_CLIENT_IP", "unknown",
                "HTTP_X_FORWARDED_FOR", "11.11.11.11")));
        assertEquals("11.11.11.11", ServletUtils.getIpAddr());
    }

    @Test
    public void testAllHeadersUnknownFallsBackToRemoteAddr() {
        bind(fakeRequest("http://h/x", "/x", "10.0.0.7", headers(
                "x-forwarded-for", "unknown",
                "Proxy-Client-IP", "unknown",
                "WL-Proxy-Client-IP", "unknown",
                "HTTP_CLIENT_IP", "unknown",
                "HTTP_X_FORWARDED_FOR", "unknown")));
        assertEquals("10.0.0.7", ServletUtils.getIpAddr());
    }

    @Test
    public void testHeaderExplosionYieldsDefaultIp() {
        bind(throwingHeaderRequest("10.0.0.8"));
        assertEquals("0.0.0.0", ServletUtils.getIpAddr());
    }
}
