// Use of this source code is governed by a BSD-style license
package com.cs.common.enums;

import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.*;

public class ParamTypeEnumTest {

    @Test
    public void testLongConverterParsesAndRejectsBlank() {
        assertEquals(Long.valueOf(123L), ParamTypeEnum.LONG.getConverter().apply("123"));
        assertEquals(Long.valueOf(-5L), ParamTypeEnum.LONG.getConverter().apply("-5"));
        assertNull("blank input must convert to null", ParamTypeEnum.LONG.getConverter().apply(""));
        assertNull("whitespace input must convert to null", ParamTypeEnum.LONG.getConverter().apply("   "));
        assertNull("null input must convert to null", ParamTypeEnum.LONG.getConverter().apply(null));
    }

    @Test
    public void testDoubleConverterParsesAndRejectsBlank() {
        assertEquals(Double.valueOf(1.5D), ParamTypeEnum.DOUBLE.getConverter().apply("1.5"));
        assertNull(ParamTypeEnum.DOUBLE.getConverter().apply(""));
        assertNull(ParamTypeEnum.DOUBLE.getConverter().apply(null));
    }

    @Test
    public void testStringFamilyIsIdentity() {
        assertSame("abc", ParamTypeEnum.STRING.getConverter().apply("abc"));
        assertNull(ParamTypeEnum.STRING.getConverter().apply(null));
        assertSame("2024-01-02", ParamTypeEnum.DATE.getConverter().apply("2024-01-02"));
        assertSame("12:30:00", ParamTypeEnum.TIME.getConverter().apply("12:30:00"));
    }

    @Test
    public void testBooleanConverterParsesLenientlyAndRejectsBlank() {
        assertEquals(Boolean.TRUE, ParamTypeEnum.BOOLEAN.getConverter().apply("true"));
        assertEquals(Boolean.FALSE, ParamTypeEnum.BOOLEAN.getConverter().apply("anything-not-true"));
        assertNull(ParamTypeEnum.BOOLEAN.getConverter().apply(""));
        assertNull(ParamTypeEnum.BOOLEAN.getConverter().apply(null));
    }

    @Test
    public void testObjectConverterParsesJsonAndRejectsBlank() {
        Object parsed = ParamTypeEnum.OBJECT.getConverter().apply("{\"a\":1}");
        assertTrue("json object must become a Map", parsed instanceof Map);
        assertEquals(1, ((Map<?, ?>) parsed).size());
        assertNull(ParamTypeEnum.OBJECT.getConverter().apply(""));
        assertNull(ParamTypeEnum.OBJECT.getConverter().apply(null));
    }

    @Test
    public void testIsObjectOnlyForObject() {
        assertTrue(ParamTypeEnum.OBJECT.isObject());
        for (ParamTypeEnum type : ParamTypeEnum.values()) {
            assertEquals(type == ParamTypeEnum.OBJECT, type.isObject());
        }
    }

    @Test
    public void testSwaggerMetadataPerType() {
        // jsType/example/clazz back the swagger schema generation and must stay in sync
        assertEquals("number", ParamTypeEnum.LONG.getJsType());
        assertEquals("number", ParamTypeEnum.DOUBLE.getJsType());
        assertEquals("string", ParamTypeEnum.STRING.getJsType());
        assertEquals("boolean", ParamTypeEnum.BOOLEAN.getJsType());
        assertEquals("object", ParamTypeEnum.OBJECT.getJsType());

        assertEquals(0L, ParamTypeEnum.LONG.getExample());
        assertEquals(0D, ParamTypeEnum.DOUBLE.getExample());
        assertEquals("true", ParamTypeEnum.BOOLEAN.getExample());
        assertEquals("{}", ParamTypeEnum.OBJECT.getExample());

        assertEquals(Long.class, ParamTypeEnum.LONG.getClazz());
        assertEquals(Double.class, ParamTypeEnum.DOUBLE.getClazz());
        assertEquals(String.class, ParamTypeEnum.STRING.getClazz());
        assertEquals(Boolean.class, ParamTypeEnum.BOOLEAN.getClazz());
        assertEquals(Map.class, ParamTypeEnum.OBJECT.getClazz());
    }
}
