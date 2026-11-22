// Use of this source code is governed by a BSD-style license
package com.cs.common.util;

import org.junit.Assert;
import org.junit.Test;
import org.springframework.context.support.ResourceBundleMessageSource;

import java.util.Locale;

/**
 * Guards the {0} placeholder contract of message keys the permission system reports to users.
 * The companion trap this pins down: {@code I18nUtils.getMessage(key, stringArg)} binds to the
 * more specific {@code (key, defaultMessage, args)} overload, leaving {0} unfilled — callers
 * passing a formatted argument must pass an explicit {@code Object[]} (see PermissionService).
 */
public class I18nMessageFormatTest {

    @Test
    public void enUsPlaceholderFillsWithExplicitArgs() {
        ResourceBundleMessageSource source = new ResourceBundleMessageSource();
        source.setBasename("i18n/messages");
        source.setDefaultEncoding("UTF-8");
        String us = source.getMessage("auth.no.permission", new Object[]{"API_GROUP/C"}, Locale.US);
        Assert.assertEquals("No Permission to access: API_GROUP/C", us);
    }

    @Test
    public void zhPlaceholderFillsWithExplicitArgs() {
        ResourceBundleMessageSource source = new ResourceBundleMessageSource();
        source.setBasename("i18n/messages");
        source.setDefaultEncoding("UTF-8");
        String zh = source.getMessage("auth.no.permission", new Object[]{"数据源/C"}, Locale.SIMPLIFIED_CHINESE);
        Assert.assertTrue(zh, zh.contains("数据源/C") && !zh.contains("{0}"));
    }
}
