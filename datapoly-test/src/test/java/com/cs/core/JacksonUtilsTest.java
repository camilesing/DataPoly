// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.core;

import com.cs.common.enums.DataTypeFormatEnum;
import com.cs.core.util.JacksonUtils;
import com.fasterxml.jackson.databind.*;
import org.junit.*;

import java.time.LocalDateTime;
import java.util.*;

public class JacksonUtilsTest {

    /**
     * Without a Spring container (plain unit test), falls back to the default timezone and serializes normally
     */
    @Test
    public void testToJsonStrWithoutFormatMap() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("user_name", "test");
        result.put("user_age", 23);
        result.put("user_sex", 1);
        String json = JacksonUtils.toJsonStr(result, Collections.emptyMap());
        Assert.assertEquals("{\"user_name\":\"test\",\"user_age\":23,\"user_sex\":1}", json);
    }

    @Test
    public void testToJsonStrWithDatetimeFormat() {
        Map<DataTypeFormatEnum, String> formatMap = new HashMap<>();
        formatMap.put(DataTypeFormatEnum.LOCAL_DATE_TIME, "yyyy/MM/dd HH:mm:ss");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("created", LocalDateTime.of(2026, 8, 25, 10, 15, 30));
        String json = JacksonUtils.toJsonStr(result, formatMap);
        Assert.assertEquals("{\"created\":\"2026/08/25 10:15:30\"}", json);
    }

    @Test
    public void testJsonStrToMap() throws Exception {
        String jsonString = "{\"user_sex\":1,\"user_name\":\"test\",\"user_age\":23}";

        ObjectMapper mapper = new ObjectMapper();
        JsonNode rootNode = mapper.readTree(jsonString);
        Map<String, Object> resultMap = new HashMap<>();
        rootNode.fields().forEachRemaining(entry -> resultMap.put(entry.getKey(),
                mapper.convertValue(entry.getValue(), Object.class)));
        Assert.assertEquals(3, resultMap.size());
        Assert.assertEquals("test", resultMap.get("user_name"));
        Assert.assertEquals(23, resultMap.get("user_age"));
        Assert.assertEquals(1, resultMap.get("user_sex"));
    }

    @Test
    public void testParseFieldTypesNullAndEmptyInputs() {
        Assert.assertTrue(JacksonUtils.parseFieldTypes(null).isEmpty());
        Assert.assertTrue(JacksonUtils.parseFieldTypes(new ArrayList<Object>()).isEmpty());
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("k", "v");
        Assert.assertFalse(JacksonUtils.parseFieldTypes(Collections.singletonList(row)).isEmpty());
    }

    @Test
    public void testParseFieldTypesScalarTypeDetection() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("bool", Boolean.TRUE);
        row.put("byte", (byte) 1);
        row.put("int", 1);
        row.put("long", 1L);
        row.put("bigint", new java.math.BigInteger("9"));
        row.put("dbl", 1.5d);
        row.put("time", new java.sql.Time(0));
        row.put("ts", new java.sql.Timestamp(0));
        row.put("ldt", LocalDateTime.of(2026, 1, 1, 0, 0));
        row.put("date", new java.util.Date(0));
        row.put("ld", java.time.LocalDate.of(2026, 1, 1));
        row.put("str", "s");

        List<com.cs.common.dto.OutParam> params = JacksonUtils.parseFieldTypes(row);
        Assert.assertEquals(row.size(), params.size());
        Map<String, com.cs.common.enums.ParamTypeEnum> byName = new HashMap<>();
        for (com.cs.common.dto.OutParam param : params) {
            byName.put(param.getName(), param.getType());
        }
        Assert.assertEquals(com.cs.common.enums.ParamTypeEnum.BOOLEAN, byName.get("bool"));
        Assert.assertEquals(com.cs.common.enums.ParamTypeEnum.BOOLEAN, byName.get("byte"));
        Assert.assertEquals(com.cs.common.enums.ParamTypeEnum.LONG, byName.get("int"));
        Assert.assertEquals(com.cs.common.enums.ParamTypeEnum.LONG, byName.get("long"));
        Assert.assertEquals(com.cs.common.enums.ParamTypeEnum.LONG, byName.get("bigint"));
        Assert.assertEquals(com.cs.common.enums.ParamTypeEnum.DOUBLE, byName.get("dbl"));
        Assert.assertEquals(com.cs.common.enums.ParamTypeEnum.TIME, byName.get("time"));
        Assert.assertEquals(com.cs.common.enums.ParamTypeEnum.TIME, byName.get("ts"));
        Assert.assertEquals(com.cs.common.enums.ParamTypeEnum.TIME, byName.get("ldt"));
        Assert.assertEquals(com.cs.common.enums.ParamTypeEnum.DATE, byName.get("date"));
        Assert.assertEquals(com.cs.common.enums.ParamTypeEnum.DATE, byName.get("ld"));
        Assert.assertEquals(com.cs.common.enums.ParamTypeEnum.STRING, byName.get("str"));
    }

    @Test
    public void testParseFieldTypesNestedObjectBecomesChildren() {
        Map<String, Object> inner = new LinkedHashMap<>();
        inner.put("inner_field", 1);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("outer", inner);

        List<com.cs.common.dto.OutParam> params = JacksonUtils.parseFieldTypes(row);
        Assert.assertEquals(1, params.size());
        com.cs.common.dto.OutParam outer = params.get(0);
        Assert.assertEquals(com.cs.common.enums.ParamTypeEnum.OBJECT, outer.getType());
        Assert.assertEquals(Boolean.FALSE, outer.getIsArray());
        Assert.assertEquals(1, outer.getChildren().size());
        Assert.assertEquals("inner_field", outer.getChildren().get(0).getName());
    }

    @Test
    public void testParseFieldTypesArraysTakeTypeOfFirstElement() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("ids", Arrays.asList(1, 2));
        row.put("empty", new ArrayList<Object>());

        List<com.cs.common.dto.OutParam> params = JacksonUtils.parseFieldTypes(row);
        Map<String, com.cs.common.dto.OutParam> byName = new HashMap<>();
        for (com.cs.common.dto.OutParam param : params) {
            byName.put(param.getName(), param);
        }
        Assert.assertEquals(com.cs.common.enums.ParamTypeEnum.LONG, byName.get("ids").getType());
        Assert.assertEquals(Boolean.TRUE, byName.get("ids").getIsArray());
        // empty arrays have no element type at all
        Assert.assertNull(byName.get("empty").getType());
    }

    @Test
    public void testParseFieldTypesDeduplicatesColumnNames() {
        Map<String, Object> first = new LinkedHashMap<>();
        first.put("name", "a");
        Map<String, Object> second = new LinkedHashMap<>();
        second.put("name", "b");

        List<com.cs.common.dto.OutParam> params =
                JacksonUtils.parseFieldTypes(Arrays.asList(first, second));
        Assert.assertEquals(1, params.size());
    }

    @Test
    public void testParseFieldTypesListInsideListInsideList() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("deep", 1);
        List<Object> innerList = new ArrayList<>();
        innerList.add(row);
        List<Object> outerList = new ArrayList<>();
        outerList.add(innerList);

        List<com.cs.common.dto.OutParam> params = JacksonUtils.parseFieldTypes(
                Collections.singletonList(outerList));
        Assert.assertEquals(1, params.size());
        Assert.assertEquals("deep", params.get(0).getName());
    }

    @Test
    public void testParseFiledTypesFillsNullTypeAsString() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("empty", new ArrayList<Object>());

        List<com.cs.common.dto.OutParam> params = JacksonUtils.parseFiledTypesAndFillNullAsString(row);
        Assert.assertEquals(com.cs.common.enums.ParamTypeEnum.STRING, params.get(0).getType());
    }

    @Test
    public void testToJsonStrWithTimeDateTimestampAndDecimalFormats() {
        long millis = java.time.ZonedDateTime.of(2026, 8, 25, 10, 15, 30, 0,
                java.time.ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli();

        Map<DataTypeFormatEnum, String> formatMap = new HashMap<>();
        formatMap.put(DataTypeFormatEnum.TIME, "HH:mm:ss");
        formatMap.put(DataTypeFormatEnum.DATE, "yyyy/MM/dd");
        formatMap.put(DataTypeFormatEnum.TIMESTAMP, "yyyy/MM/dd HH:mm:ss");
        formatMap.put(DataTypeFormatEnum.BIG_DECIMAL, "2");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("t", new java.sql.Time(millis));
        result.put("d", new java.sql.Date(millis));
        result.put("ts", new java.sql.Timestamp(millis));
        result.put("num", new java.math.BigDecimal("1.5"));

        String json = JacksonUtils.toJsonStr(result, formatMap);
        Assert.assertEquals(
                "{\"t\":\"10:15:30\",\"d\":\"2026/08/25\",\"ts\":\"2026/08/25 10:15:30\",\"num\":1.50}",
                json);
    }

    @Test
    public void testToJsonStrWithDefaultFormats() {
        long millis = java.time.ZonedDateTime.of(2026, 8, 25, 10, 15, 30, 0,
                java.time.ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ts", new java.sql.Timestamp(millis));
        // null format map: default pattern yyyy-MM-dd HH:mm:ss applies
        Assert.assertEquals("{\"ts\":\"2026-08-25 10:15:30\"}",
                JacksonUtils.toJsonStr(result, null));
    }

    @Test
    public void testGetTimezoneFallsBackToDefaultWithoutConfig() {
        Assert.assertEquals("Asia/Shanghai", JacksonUtils.getTimezone());
    }
}
