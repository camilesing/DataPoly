// Use of this source code is governed by a BSD-style license
package com.cs.common.enums;

import org.junit.Test;

import static org.junit.Assert.*;

public class ParamLocationEnumTest {

    @Test
    public void testLocationCapabilities() {
        assertTrue(ParamLocationEnum.REQUEST_HEADER.isParameter());
        assertTrue(ParamLocationEnum.REQUEST_HEADER.isHeader());
        assertFalse(ParamLocationEnum.REQUEST_HEADER.isRequestBody());

        assertTrue(ParamLocationEnum.REQUEST_FORM.isParameter());
        assertFalse(ParamLocationEnum.REQUEST_FORM.isHeader());
        assertFalse(ParamLocationEnum.REQUEST_FORM.isRequestBody());

        assertFalse(ParamLocationEnum.REQUEST_BODY.isParameter());
        assertTrue(ParamLocationEnum.REQUEST_BODY.isRequestBody());
    }

    @Test
    public void testSwaggerInNames() {
        assertEquals("header", ParamLocationEnum.REQUEST_HEADER.getIn());
        assertEquals("query", ParamLocationEnum.REQUEST_FORM.getIn());
        assertEquals("body", ParamLocationEnum.REQUEST_BODY.getIn());
    }

    @Test
    public void testDisplayNames() {
        assertEquals("请求头", ParamLocationEnum.REQUEST_HEADER.getName());
        assertEquals("表单数据", ParamLocationEnum.REQUEST_FORM.getName());
        assertEquals("请求体", ParamLocationEnum.REQUEST_BODY.getName());
    }
}
