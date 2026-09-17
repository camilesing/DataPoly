// Use of this source code is governed by a BSD-style license
package com.cs.common.enums;

import org.junit.Test;

import static org.junit.Assert.*;

public class HttpMethodEnumTest {

    @Test
    public void testBodyCapability() {
        assertFalse(HttpMethodEnum.GET.isHasBody());
        assertFalse(HttpMethodEnum.HEAD.isHasBody());
        assertTrue(HttpMethodEnum.POST.isHasBody());
        assertTrue(HttpMethodEnum.PUT.isHasBody());
        assertTrue(HttpMethodEnum.DELETE.isHasBody());
    }

    @Test
    public void testExistsIsCaseSensitiveOnEnumName() {
        assertTrue(HttpMethodEnum.exists("GET"));
        assertTrue(HttpMethodEnum.exists("POST"));
        assertFalse(HttpMethodEnum.exists("get"));
        assertFalse(HttpMethodEnum.exists("PATCH"));
        assertFalse(HttpMethodEnum.exists(null));
    }
}
