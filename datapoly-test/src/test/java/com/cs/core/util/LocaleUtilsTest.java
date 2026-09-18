// Use of this source code is governed by a BSD-style license
package com.cs.core.util;

import org.junit.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;

import java.lang.reflect.Proxy;
import java.util.Locale;

import static org.junit.Assert.*;

public class LocaleUtilsTest {

    private ServerHttpRequest requestWithAcceptLanguage(String header) {
        HttpHeaders headers = new HttpHeaders();
        if (header != null) {
            headers.add("Accept-Language", header);
        }
        return (ServerHttpRequest) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{ServerHttpRequest.class},
                (p, method, args) -> {
                    if ("getHeaders".equals(method.getName())) {
                        return headers;
                    }
                    if ("hashCode".equals(method.getName())) {
                        return System.identityHashCode(p);
                    }
                    if ("equals".equals(method.getName())) {
                        return p == args[0];
                    }
                    return null;
                });
    }

    @Test
    public void testUnderscoreTagNormalisedToLocale() {
        assertEquals(Locale.SIMPLIFIED_CHINESE, LocaleUtils.resolveLocale(requestWithAcceptLanguage("zh_CN")));
    }

    @Test
    public void testPlainTagResolved() {
        assertEquals(Locale.ENGLISH, LocaleUtils.resolveLocale(requestWithAcceptLanguage("en")));
    }

    @Test
    public void testMissingHeaderFallsBackToUsEnglish() {
        assertEquals(Locale.US, LocaleUtils.resolveLocale(requestWithAcceptLanguage(null)));
    }
}
