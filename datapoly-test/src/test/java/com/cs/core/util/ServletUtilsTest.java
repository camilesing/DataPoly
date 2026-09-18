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

    private void bind(HttpServletRequest request) {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
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
}
