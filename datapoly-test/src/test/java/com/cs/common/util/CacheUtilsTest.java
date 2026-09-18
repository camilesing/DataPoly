// Use of this source code is governed by a BSD-style license
package com.cs.common.util;

import org.junit.Test;

import static org.junit.Assert.*;

public class CacheUtilsTest {

    @Test
    public void testPutGetRemoveAndClearLifecycle() {
        CacheUtils.clear();
        assertNull(CacheUtils.get("missing"));

        CacheUtils.put("a", "value-a");
        CacheUtils.put("b", "value-b");
        assertEquals("value-a", CacheUtils.get("a"));

        CacheUtils.remove("a");
        assertNull(CacheUtils.get("a"));
        assertEquals(1, CacheUtils.getAll().size());
        assertTrue(CacheUtils.getAllValue().contains("value-b"));

        CacheUtils.clear();
        assertNull(CacheUtils.get("b"));
        assertTrue(CacheUtils.getAll().isEmpty());
    }
}
