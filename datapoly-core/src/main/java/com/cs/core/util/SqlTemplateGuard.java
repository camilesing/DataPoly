// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.core.util;

import cn.hutool.extra.spring.SpringUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;

/**
 * SQL template ${} literal substitution policy (S3):
 * <ul>
 *   <li>{@code datapoly.executor.sql.dollar-enabled} (default true): global switch; when disabled, SQL templates of all APIs
 *       forbid ${} (the escape/compat path is to switch to #{} parameterization);</li>
 *   <li>{@code datapoly.executor.sql.open-dollar-forbidden} (default true): open=true public APIs
 *       forcibly forbid ${} — their request parameters come from end users, and ${} concatenation is an injection surface.</li>
 * </ul>
 * The engine is not a Spring bean; config is read lazily from the Environment (same pattern as PageSizeUtils).
 */
@Slf4j
public final class SqlTemplateGuard {

    private static final String KEY_DOLLAR_ENABLED = "datapoly.executor.sql.dollar-enabled";
    private static final String KEY_OPEN_DOLLAR_FORBIDDEN = "datapoly.executor.sql.open-dollar-forbidden";
    private static final boolean DEFAULT_DOLLAR_ENABLED = true;
    private static final boolean DEFAULT_OPEN_DOLLAR_FORBIDDEN = true;

    private SqlTemplateGuard() {
    }

    public static boolean isDollarSubstitutionAllowed(Boolean openApi) {
        return isDollarSubstitutionAllowed(openApi, getProperty(KEY_DOLLAR_ENABLED, DEFAULT_DOLLAR_ENABLED),
                getProperty(KEY_OPEN_DOLLAR_FORBIDDEN, DEFAULT_OPEN_DOLLAR_FORBIDDEN));
    }

    /**
     * Pure decision function (package-visible for unit tests): allowed iff the global switch is on and (the API is not public, or public APIs are not forcibly forbidden).
     */
    static boolean isDollarSubstitutionAllowed(Boolean openApi, boolean dollarEnabled,
                                               boolean openDollarForbidden) {
        boolean open = Boolean.TRUE.equals(openApi);
        return dollarEnabled && !(open && openDollarForbidden);
    }

    private static boolean getProperty(String key, boolean defaultValue) {
        try {
            Environment environment = SpringUtil.getBean(Environment.class);
            String value = environment.getProperty(key);
            return (null == value || value.isEmpty()) ? defaultValue : Boolean.parseBoolean(value.trim());
        } catch (Exception e) {
            return defaultValue;
        }
    }

}
