// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.core.util;

import org.junit.*;

import java.util.*;

public class ParamMaskUtilsTest {

    @Test
    public void masksSensitiveKeysCaseInsensitive() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("user_password", "plain-1");
        params.put("api_token", "plain-2");
        params.put("Authorization", "Bearer x");
        params.put("appSecret", "plain-3");

        Map<String, Object> masked = ParamMaskUtils.mask(params);
        Assert.assertEquals(ParamMaskUtils.MASK, masked.get("user_password"));
        Assert.assertEquals(ParamMaskUtils.MASK, masked.get("api_token"));
        Assert.assertEquals(ParamMaskUtils.MASK, masked.get("Authorization"));
        Assert.assertEquals(ParamMaskUtils.MASK, masked.get("appSecret"));
        // The original map is not mutated (the execution path keeps using the original values)
        Assert.assertEquals("plain-1", params.get("user_password"));
    }

    @Test
    public void hyphenKeyNormalized() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("api-key", "plain");
        Assert.assertEquals(ParamMaskUtils.MASK, ParamMaskUtils.mask(params).get("api-key"));
    }

    @Test
    public void normalKeysUntouched() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("username", "tom");
        params.put("dept_no", "d001");
        params.put("pageSize", 20);

        Map<String, Object> masked = ParamMaskUtils.mask(params);
        Assert.assertEquals("tom", masked.get("username"));
        Assert.assertEquals("d001", masked.get("dept_no"));
        Assert.assertEquals(20, masked.get("pageSize"));
    }

    @Test
    public void nullAndEmptySafe() {
        Assert.assertNull(ParamMaskUtils.mask(null));
        Map<String, Object> empty = new HashMap<>();
        Assert.assertTrue(ParamMaskUtils.mask(empty).isEmpty());
    }

    @Test
    public void emptyValueStillMasked() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("password", "");
        Assert.assertEquals(ParamMaskUtils.MASK, ParamMaskUtils.mask(params).get("password"));
    }
}
