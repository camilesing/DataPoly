// Use of this source code is governed by a BSD-style license
package com.cs.common.util;

import org.junit.Test;

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
}
