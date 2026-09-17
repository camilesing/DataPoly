// Use of this source code is governed by a BSD-style license
package com.cs.persistence.util;

import com.fasterxml.jackson.core.type.TypeReference;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

public class JsonUtilsTest {

    public static class BrokenBean {
        @SuppressWarnings("unused")
        public String getBoom() {
            throw new IllegalStateException("kaboom");
        }
    }

    public static class SampleBean {
        private String name;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    @Test
    public void testToJsonStringNullSafe() {
        assertNull(JsonUtils.toJsonString(null));
        Map<String, Integer> single = new LinkedHashMap<>();
        single.put("a", 1);
        assertEquals("{\"a\":1}", JsonUtils.toJsonString(single));
    }

    @Test
    public void testToJsonStringFailureSurfacesAsRuntimeException() {
        try {
            JsonUtils.toJsonString(new BrokenBean());
            fail("broken bean must surface as RuntimeException");
        } catch (RuntimeException expected) {
            assertTrue(expected.getMessage().contains("convert object to json string error"));
        }
    }

    @Test
    public void testToBeanObject() {
        assertNull(JsonUtils.toBeanObject(null, SampleBean.class));
        SampleBean bean = JsonUtils.toBeanObject("{\"name\":\"x\"}", SampleBean.class);
        assertEquals("x", bean.getName());
        try {
            JsonUtils.toBeanObject("not-json", SampleBean.class);
            fail("invalid json must surface as RuntimeException");
        } catch (RuntimeException expected) {
            assertTrue(expected.getMessage().contains("parse json string to object error"));
        }
    }

    @Test
    public void testToBeanType() {
        assertNull(JsonUtils.toBeanType(null, new TypeReference<Map<String, Integer>>() {
        }));
        Map<String, Integer> map = JsonUtils.toBeanType("{\"a\":1}", new TypeReference<Map<String, Integer>>() {
        });
        assertEquals(Integer.valueOf(1), map.get("a"));
        try {
            JsonUtils.toBeanType("not-json", new TypeReference<Map<String, Integer>>() {
            });
            fail("invalid json must surface as RuntimeException");
        } catch (RuntimeException expected) {
            assertTrue(expected.getMessage().contains("parse json string to object error"));
        }
    }

    @Test
    public void testToBeanList() {
        assertTrue(JsonUtils.toBeanList(null, Object.class).isEmpty());
        List<SampleBean> beans = JsonUtils.toBeanList(
                "[{\"name\":\"a\"},{\"name\":\"b\"}]", SampleBean.class);
        assertEquals(2, beans.size());
        assertEquals("a", beans.get(0).getName());
        assertEquals("b", beans.get(1).getName());
    }

    @Test
    public void testRoundTripKeepsKeyOrderForLinkedMaps() {
        Map<String, Object> ordered = new LinkedHashMap<>();
        ordered.put("z", 1);
        ordered.put("a", 2);
        assertEquals("{\"z\":1,\"a\":2}", JsonUtils.toJsonString(ordered));
        LinkedHashMap<String, Object> parsed = JsonUtils.toBeanType("{\"z\":1,\"a\":2}",
                new TypeReference<LinkedHashMap<String, Object>>() {
                });
        assertEquals(Arrays.asList("z", "a"), new ArrayList<>(parsed.keySet()));
    }
}
