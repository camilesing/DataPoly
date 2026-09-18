// Use of this source code is governed by a BSD-style license
package com.cs.common.enums;

import org.junit.Test;

import static org.junit.Assert.*;

public class DataTypeFormatEnumTest {

    @Test
    public void testDateAndTimePatterns() {
        assertEquals("yyyy-MM-dd", DataTypeFormatEnum.DATE.getDefaultPattern());
        assertEquals("HH:mm:ss", DataTypeFormatEnum.TIME.getDefaultPattern());
        assertEquals("yyyy-MM-dd HH:mm:ss", DataTypeFormatEnum.LOCAL_DATE_TIME.getDefaultPattern());
        assertEquals(java.sql.Timestamp.class.getName(), DataTypeFormatEnum.TIMESTAMP.getClassName());
    }

    @Test
    public void testBigDecimalScale() {
        assertEquals(6, DataTypeFormatEnum.BIG_DECIMAL.getNumberScale());
        assertEquals("6", DataTypeFormatEnum.BIG_DECIMAL.getDefault());
    }

    @Test
    public void testRemarkUsesI18nKeyWhenPresent() {
        // no Spring context in unit tests: the key itself is the fallback message
        assertEquals("format.use.system.response", DataTypeFormatEnum.USE_SYSTEM_RESPONSE_FORMAT.getRemark());
        assertEquals(java.sql.Date.class.getName(), DataTypeFormatEnum.DATE.getRemark());
    }

    @Test
    public void testDefaultReturnsPatternWhenNoScale() {
        assertEquals("yyyy-MM-dd", DataTypeFormatEnum.DATE.getDefault());
    }
}
