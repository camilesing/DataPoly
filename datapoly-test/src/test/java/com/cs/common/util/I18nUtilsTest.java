// Use of this source code is governed by a BSD-style license
package com.cs.common.util;

import cn.hutool.extra.spring.SpringUtil;
import org.junit.After;
import org.junit.Test;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.MessageSource;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.Locale;

import static org.junit.Assert.*;

public class I18nUtilsTest {

    /**
     * Without a Spring container the resolver stays null, the lookup path fails and
     * every getMessage variant must degrade gracefully to its fallback.
     */
    @Test
    public void testMessagesFallBackWithoutSpringContext() {
        assertEquals("some.missing.key", I18nUtils.getMessage("some.missing.key"));
        assertEquals("default msg", I18nUtils.getMessage("some.missing.key", "default msg"));
        assertEquals("some.missing.key", I18nUtils.getMessage("some.missing.key", Locale.ENGLISH));
    }

    /**
     * With a MessageSource resolvable through SpringUtil the lookups go through it, and the
     * resolver is cached after the first successful lookup.
     */
    @Test
    public void testMessagesResolveThroughSpringMessageSourceAndCacheIt() throws Exception {
        MessageSource source = (MessageSource) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{MessageSource.class},
                (proxy, method, args) -> {
                    if ("getMessage".equals(method.getName()) && null != args && args.length > 0) {
                        return "translated:" + args[0];
                    }
                    if ("hashCode".equals(method.getName())) {
                        return System.identityHashCode(proxy);
                    }
                    if ("toString".equals(method.getName())) {
                        return "stubMessageSource";
                    }
                    return null;
                });
        Object beanFactory = Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{ConfigurableListableBeanFactory.class},
                (proxy, method, args) -> {
                    if ("getBean".equals(method.getName()) && null != args && 1 == args.length
                            && args[0] instanceof Class) {
                        return source;
                    }
                    if ("hashCode".equals(method.getName())) {
                        return System.identityHashCode(proxy);
                    }
                    if ("toString".equals(method.getName())) {
                        return "stubBeanFactory";
                    }
                    return null;
                });

        Field factoryField = SpringUtil.class.getDeclaredField("beanFactory");
        factoryField.setAccessible(true);
        Object previousFactory = factoryField.get(null);
        factoryField.set(null, beanFactory);
        try {
            // first call resolves the bean, second call must serve from the cached field
            assertEquals("translated:known.key", I18nUtils.getMessage("known.key"));
            assertEquals("translated:known.key", I18nUtils.getMessage("known.key"));
            assertEquals("translated:known.key", I18nUtils.getMessage("known.key", "ignored default"));
            assertEquals("translated:known.key", I18nUtils.getMessage("known.key", Locale.ENGLISH, "arg"));
        } finally {
            factoryField.set(null, previousFactory);
            // drop the cached resolver so other tests keep the no-container fallback
            Field cachedField = I18nUtils.class.getDeclaredField("messageSource");
            cachedField.setAccessible(true);
            cachedField.set(null, null);
        }
    }

    @After
    public void resetCachedMessageSource() throws Exception {
        // defensive: never leak the stub resolver into other tests, even on failure paths
        Field cachedField = I18nUtils.class.getDeclaredField("messageSource");
        cachedField.setAccessible(true);
        cachedField.set(null, null);
        Field factoryField = SpringUtil.class.getDeclaredField("beanFactory");
        factoryField.setAccessible(true);
        factoryField.set(null, null);
    }
}
