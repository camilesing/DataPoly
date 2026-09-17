// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.common.util;

import cn.hutool.extra.spring.SpringUtil;
import org.junit.*;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.core.env.Environment;

import javax.servlet.http.HttpServletRequest;
import java.lang.reflect.*;
import java.util.*;

public class TokenUtilsTest {

    @After
    public void tearDown() throws Exception {
        installEnvironment(null);
    }

    private static HttpServletRequest request(Map<String, String> headers, Map<String, String> queryParams) {
        Map<String, String[]> params = new HashMap<>();
        queryParams.forEach((k, v) -> params.put(k, new String[]{v}));
        return (HttpServletRequest) Proxy.newProxyInstance(TokenUtilsTest.class.getClassLoader(),
                new Class[]{HttpServletRequest.class}, (proxy, method, args) -> {
                    String name = method.getName();
                    if ("getHeader".equals(name)) {
                        return headers.get((String) args[0]);
                    }
                    if ("getParameter".equals(name)) {
                        String[] values = params.get((String) args[0]);
                        return (null == values || 0 == values.length) ? null : values[0];
                    }
                    if ("hashCode".equals(name)) {
                        return System.identityHashCode(proxy);
                    }
                    if ("equals".equals(name)) {
                        return proxy == args[0];
                    }
                    if ("toString".equals(name)) {
                        return "stubHttpServletRequest";
                    }
                    return null;
                });
    }

    /**
     * Reflectively injects a stub beanFactory into hutool SpringUtil: getBean(Environment.class)
     * returns a stub whose getProperty always yields the given value; null clears it (restores
     * the no-container default path).
     */
    private static void installEnvironment(String propertyValue) throws Exception {
        Object beanFactory;
        if (null == propertyValue) {
            beanFactory = null;
        } else {
            Environment environment = (Environment) Proxy.newProxyInstance(TokenUtilsTest.class.getClassLoader(),
                    new Class[]{Environment.class}, (proxy, method, args) -> {
                        if ("getProperty".equals(method.getName())) {
                            return propertyValue;
                        }
                        if ("hashCode".equals(method.getName())) {
                            return System.identityHashCode(proxy);
                        }
                        if ("toString".equals(method.getName())) {
                            return "stubEnvironment";
                        }
                        return null;
                    });
            beanFactory = Proxy.newProxyInstance(TokenUtilsTest.class.getClassLoader(),
                    new Class[]{ConfigurableListableBeanFactory.class}, (proxy, method, args) -> {
                        if ("getBean".equals(method.getName()) && null != args && 1 == args.length
                                && args[0] instanceof Class) {
                            return environment;
                        }
                        if ("hashCode".equals(method.getName())) {
                            return System.identityHashCode(proxy);
                        }
                        if ("toString".equals(method.getName())) {
                            return "stubBeanFactory";
                        }
                        return null;
                    });
        }
        Field field = SpringUtil.class.getDeclaredField("beanFactory");
        field.setAccessible(true);
        field.set(null, beanFactory);
    }

    @Test
    public void bearerHeaderTokenWins() throws Exception {
        installEnvironment("true");
        HttpServletRequest request = request(
                Collections.singletonMap("Authorization", "Bearer header-token"),
                Collections.singletonMap("token", "query-token"));
        Assert.assertEquals("header-token", TokenUtils.getRequestToken(request));
    }

    @Test
    public void queryTokenRejectedWithoutSpringContext() {
        // No Spring container (plain unit test / uninitialized): falls back to the safe default, query token disabled
        HttpServletRequest request = request(Collections.emptyMap(), Collections.singletonMap("token", "query-token"));
        Assert.assertNull(TokenUtils.getRequestToken(request));
    }

    @Test
    public void queryTokenAcceptedWhenCompatibilitySwitchEnabled() throws Exception {
        installEnvironment("true");
        HttpServletRequest request = request(Collections.emptyMap(), Collections.singletonMap("token", "query-token"));
        Assert.assertEquals("query-token", TokenUtils.getRequestToken(request));
    }

    @Test
    public void queryTokenRejectedWhenSwitchExplicitlyDisabled() throws Exception {
        installEnvironment("false");
        HttpServletRequest request = request(Collections.emptyMap(), Collections.singletonMap("token", "query-token"));
        Assert.assertNull(TokenUtils.getRequestToken(request));
    }

    @Test
    public void malformedAuthorizationHeaderDoesNotFallBackToQuery() throws Exception {
        // A non-Bearer Authorization header does not trigger query fallback (existing behavior)
        installEnvironment("true");
        HttpServletRequest request = request(
                Collections.singletonMap("Authorization", "Basic abcdef"),
                Collections.singletonMap("token", "query-token"));
        Assert.assertNull(TokenUtils.getRequestToken(request));
    }

}
