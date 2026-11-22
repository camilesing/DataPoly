// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.core.util;

import cn.hutool.extra.spring.SpringUtil;
import org.junit.*;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.core.env.Environment;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

public class SqlTemplateGuardTest {

    private static final String KEY_DOLLAR_ENABLED = "datapoly.executor.sql.dollar-enabled";
    private static final String KEY_OPEN_DOLLAR_FORBIDDEN = "datapoly.executor.sql.open-dollar-forbidden";

    /** Mutable property source behind the stub Environment. */
    private static final Map<String, String> SPRING_PROPERTIES = new HashMap<>();

    @Before
    public void installEnvironment() {
        SPRING_PROPERTIES.clear();
        Environment environment = (Environment) Proxy.newProxyInstance(
                SqlTemplateGuardTest.class.getClassLoader(),
                new Class[]{Environment.class}, (proxy, method, args) -> {
                    if ("getProperty".equals(method.getName()) && args != null && args.length >= 1) {
                        String value = SPRING_PROPERTIES.get(String.valueOf(args[0]));
                        if (value != null) {
                            return value;
                        }
                        return args.length > 1 ? args[args.length - 1] : null;
                    }
                    if ("hashCode".equals(method.getName())) {
                        return System.identityHashCode(proxy);
                    }
                    if ("equals".equals(method.getName())) {
                        return proxy == args[0];
                    }
                    if ("toString".equals(method.getName())) {
                        return "stubEnvironment";
                    }
                    return null;
                });
        Object beanFactory = Proxy.newProxyInstance(SqlTemplateGuardTest.class.getClassLoader(),
                new Class[]{ConfigurableListableBeanFactory.class}, (proxy, method, args) -> {
                    if ("getBean".equals(method.getName()) && args != null && args.length == 1
                            && args[0] instanceof Class) {
                        return environment;
                    }
                    if ("hashCode".equals(method.getName())) {
                        return System.identityHashCode(proxy);
                    }
                    if ("equals".equals(method.getName())) {
                        return proxy == args[0];
                    }
                    if ("toString".equals(method.getName())) {
                        return "stubBeanFactory";
                    }
                    return null;
                });
        try {
            Field field = SpringUtil.class.getDeclaredField("beanFactory");
            field.setAccessible(true);
            field.set(null, beanFactory);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @After
    public void uninstallEnvironment() {
        try {
            Field field = SpringUtil.class.getDeclaredField("beanFactory");
            field.setAccessible(true);
            field.set(null, null);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    public void defaultsForbidDollarOnlyForOpenApis() {
        // Defaults (dollar-enabled=true, open-dollar-forbidden=true)
        Assert.assertTrue(SqlTemplateGuard.isDollarSubstitutionAllowed(Boolean.FALSE, true, true));
        Assert.assertTrue(SqlTemplateGuard.isDollarSubstitutionAllowed(null, true, true));
        Assert.assertFalse(SqlTemplateGuard.isDollarSubstitutionAllowed(Boolean.TRUE, true, true));
    }

    @Test
    public void globalSwitchDisablesAll() {
        Assert.assertFalse(SqlTemplateGuard.isDollarSubstitutionAllowed(Boolean.FALSE, false, true));
        Assert.assertFalse(SqlTemplateGuard.isDollarSubstitutionAllowed(Boolean.TRUE, false, true));
        Assert.assertFalse(SqlTemplateGuard.isDollarSubstitutionAllowed(null, false, false));
    }

    @Test
    public void openForbiddenSwitchCanBeRelaxed() {
        // With open enforcement disabled, public APIs are also allowed (escape hatch restoring the old unguarded behavior)
        Assert.assertTrue(SqlTemplateGuard.isDollarSubstitutionAllowed(Boolean.TRUE, true, false));
        Assert.assertTrue(SqlTemplateGuard.isDollarSubstitutionAllowed(Boolean.FALSE, true, false));
    }

    @Test
    public void noSpringContextFallsBackToSafeDefaults() {
        uninstallEnvironment();
        try {
            // Environment read failure falls back to defaults (true/true),
            // i.e. public APIs rejected, non-public APIs allowed
            Assert.assertFalse(SqlTemplateGuard.isDollarSubstitutionAllowed(Boolean.TRUE));
            Assert.assertTrue(SqlTemplateGuard.isDollarSubstitutionAllowed(Boolean.FALSE));
            Assert.assertTrue(SqlTemplateGuard.isDollarSubstitutionAllowed(null));
        } finally {
            installEnvironment();
        }
    }

    @Test
    public void missingOrEmptyPropertyValuesFallBackToDefaults() {
        // property absent from the environment
        Assert.assertFalse(SqlTemplateGuard.isDollarSubstitutionAllowed(Boolean.TRUE));
        Assert.assertTrue(SqlTemplateGuard.isDollarSubstitutionAllowed(Boolean.FALSE));
        // property present but empty
        SPRING_PROPERTIES.put(KEY_DOLLAR_ENABLED, "");
        Assert.assertFalse(SqlTemplateGuard.isDollarSubstitutionAllowed(Boolean.TRUE));
        Assert.assertTrue(SqlTemplateGuard.isDollarSubstitutionAllowed(Boolean.FALSE));
    }

    @Test
    public void propertyValuesAreTrimmedBeforeParsing() {
        SPRING_PROPERTIES.put(KEY_DOLLAR_ENABLED, " false ");
        Assert.assertFalse(SqlTemplateGuard.isDollarSubstitutionAllowed(Boolean.FALSE));

        SPRING_PROPERTIES.put(KEY_DOLLAR_ENABLED, "TRUE");
        Assert.assertTrue(SqlTemplateGuard.isDollarSubstitutionAllowed(Boolean.FALSE));
    }

    @Test
    public void nonBooleanValueDegradesToDefaultWithNoBehaviorChange() {
        // a typo must not widen or narrow the policy: default (true) stays in effect
        SPRING_PROPERTIES.put(KEY_DOLLAR_ENABLED, "yes");
        Assert.assertFalse(SqlTemplateGuard.isDollarSubstitutionAllowed(Boolean.TRUE));
        Assert.assertTrue(SqlTemplateGuard.isDollarSubstitutionAllowed(Boolean.FALSE));
    }

    @Test
    public void openForbiddenSwitchIsReadFromTheEnvironmentToo() {
        SPRING_PROPERTIES.put(KEY_OPEN_DOLLAR_FORBIDDEN, "false");
        // public APIs allowed once the forced prohibition is turned off
        Assert.assertTrue(SqlTemplateGuard.isDollarSubstitutionAllowed(Boolean.TRUE));

        SPRING_PROPERTIES.put(KEY_OPEN_DOLLAR_FORBIDDEN, "garbage");
        Assert.assertFalse(SqlTemplateGuard.isDollarSubstitutionAllowed(Boolean.TRUE));

        SPRING_PROPERTIES.put(KEY_OPEN_DOLLAR_FORBIDDEN, "false");
        SPRING_PROPERTIES.put(KEY_DOLLAR_ENABLED, "false");
        // but the global switch still wins
        Assert.assertFalse(SqlTemplateGuard.isDollarSubstitutionAllowed(Boolean.TRUE));
        Assert.assertFalse(SqlTemplateGuard.isDollarSubstitutionAllowed(Boolean.FALSE));
    }
}
