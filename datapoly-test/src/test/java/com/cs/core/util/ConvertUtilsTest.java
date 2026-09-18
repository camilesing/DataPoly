// Use of this source code is governed by a BSD-style license
package com.cs.core.util;

import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

import static org.junit.Assert.*;

public class ConvertUtilsTest {

    @Test
    public void testNullConverterReturnsSameRow() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("A", 1);
        assertSame(row, ConvertUtils.to(row, null));
        assertSame(row, ConvertUtils.to(row));
    }

    @Test
    public void testNullRowConvertedToNull() {
        assertNull(ConvertUtils.to(null, String::toLowerCase));
    }

    @Test
    public void testConvertsKeysRecursivelyLeavingValuesUntouched() {
        Map<String, Object> inner = new LinkedHashMap<>();
        inner.put("NESTED_KEY", 1);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("OUTER_KEY", "v");
        row.put("INNER_MAP", inner);

        Map<String, Object> converted = ConvertUtils.to(row, String::toLowerCase);
        assertEquals("v", converted.get("outer_key"));
        @SuppressWarnings("unchecked")
        Map<String, Object> convertedInner = (Map<String, Object>) converted.get("inner_map");
        assertEquals(1, convertedInner.get("nested_key"));
        // the original map is not mutated
        assertTrue(row.containsKey("OUTER_KEY"));
        assertTrue(inner.containsKey("NESTED_KEY"));
    }

    @Test
    public void testWorksWithNamingStrategyFunctions() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("userName", 1);
        Map<String, Object> snake = ConvertUtils.to(row, com.cs.common.enums.NamingStrategyEnum.SNAKE_CASE.getFunction());
        assertTrue(snake.containsKey("user_name"));

        Map<String, Object> camel = ConvertUtils.to(new LinkedHashMap<>(row), com.cs.common.enums.NamingStrategyEnum.CAMEL_CASE.getFunction());
        assertTrue(camel.containsKey("userName"));
    }
}
