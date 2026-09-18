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
}
