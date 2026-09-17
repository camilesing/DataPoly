// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.core.util;

import org.junit.*;

import java.util.*;

public class SpelUtilsTest {

    @Test
    public void variableAccess() {
        Map<String, Object> params = new HashMap<>();
        params.put("deptNo", "d001");
        Assert.assertEquals("d001", SpelUtils.getExpressionValue("#deptNo", params));
    }

    @Test
    public void propertyAccessOnParamObject() {
        Map<String, Object> obj = new HashMap<>();
        obj.put("deptNo", "d002");
        Map<String, Object> params = new HashMap<>();
        params.put("user", obj);
        Assert.assertEquals("d002", SpelUtils.getExpressionValue("#user.deptNo", params));
    }

    @Test
    public void stringConcatenation() {
        Map<String, Object> params = new HashMap<>();
        params.put("a", "v1");
        params.put("b", "v2");
        Assert.assertEquals("v1-v2", SpelUtils.getExpressionValue("#a + '-' + #b", params));
    }

    @Test
    public void typeReferenceRejected() {
        Map<String, Object> params = new HashMap<>();
        String expr = "T(java.lang.Runtime).getRuntime().exec('id')";
        // SimpleEvaluationContext forbids type references; evaluation failure falls back to the original text
        Assert.assertEquals(expr, SpelUtils.getExpressionValue(expr, params));
    }

    @Test
    public void methodInvocationRejected() {
        Map<String, Object> params = new HashMap<>();
        params.put("a", "v1");
        String expr = "#a.toUpperCase()";
        // Read-only data binding does not resolve arbitrary method calls; falls back to the original text
        Assert.assertEquals(expr, SpelUtils.getExpressionValue(expr, params));
    }

    @Test
    public void constructorRejected() {
        Map<String, Object> params = new HashMap<>();
        String expr = "new java.io.File('/etc/passwd')";
        Assert.assertEquals(expr, SpelUtils.getExpressionValue(expr, params));
    }

    @Test
    public void malformedExpressionFallsBackToRawText() {
        Map<String, Object> params = new HashMap<>();
        String expr = "#a +";
        Assert.assertEquals(expr, SpelUtils.getExpressionValue(expr, params));
    }
}
