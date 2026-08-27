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
}
