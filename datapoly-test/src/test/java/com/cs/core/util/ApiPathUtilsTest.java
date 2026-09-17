// Use of this source code is governed by a BSD-style license
package com.cs.core.util;

import org.junit.Test;

import static org.junit.Assert.*;

public class ApiPathUtilsTest {

    @Test
    public void testLeadingSlashIsNormalised() {
        assertEquals("/api/user/list", ApiPathUtils.getFullPath("/user/list"));
        assertEquals("/api/user/list", ApiPathUtils.getFullPath("user/list"));
    }

    @Test
    public void testPrefixAlwaysPresent() {
        assertTrue(ApiPathUtils.getFullPath("x").startsWith("/api/"));
    }
}
