// Use of this source code is governed by a BSD-style license
package com.cs.core.exec.extractor;

import org.junit.Test;
import org.springframework.http.MediaType;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.Assert.*;

public class JsonHttpRequestBodyExtractorTest {

    private ServletInputStream stream(String body) {
        ByteArrayInputStream bytes = new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8));
        return new ServletInputStream() {
            @Override
            public int read() {
                return bytes.read();
            }

            @Override
            public boolean isFinished() {
                return bytes.available() == 0;
            }

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setReadListener(ReadListener readListener) {
            }
        };
    }

    @Test
    public void testSupportOnlyJson() {
        JsonHttpRequestBodyExtractor extractor = new JsonHttpRequestBodyExtractor();
        assertTrue(extractor.support(MediaType.APPLICATION_JSON));
        assertTrue(extractor.support(MediaType.parseMediaType("application/json;charset=UTF-8")));
        assertFalse(extractor.support(MediaType.TEXT_PLAIN));
        assertFalse(extractor.support(MediaType.APPLICATION_FORM_URLENCODED));
        assertFalse(extractor.support(null));
    }

    @Test
    public void testReadFlatObject() {
        Map<String, Object> result = new JsonHttpRequestBodyExtractor().read(
                StandardCharsets.UTF_8, stream("{\"a\":1,\"b\":\"x\",\"c\":true}"));
        assertEquals(3, result.size());
        // USE_LONG_FOR_INTS: integer literals become Long
        assertEquals(Long.valueOf(1), result.get("a"));
        assertEquals("x", result.get("b"));
        assertEquals(Boolean.TRUE, result.get("c"));
    }

    @Test
    public void testReadNestedStructures() {
        Map<String, Object> result = new JsonHttpRequestBodyExtractor().read(
                StandardCharsets.UTF_8, stream("{\"arr\":[1,2],\"obj\":{\"k\":\"v\"}}"));
        assertEquals(java.util.Arrays.asList(1L, 2L), result.get("arr"));
        @SuppressWarnings("unchecked")
        Map<String, Object> nested = (Map<String, Object>) result.get("obj");
        assertEquals("v", nested.get("k"));
    }

    @Test
    public void testReadBlankAndScalarBodiesYieldEmptyMap() {
        JsonHttpRequestBodyExtractor extractor = new JsonHttpRequestBodyExtractor();
        assertTrue(extractor.read(StandardCharsets.UTF_8, stream("   ")).isEmpty());
        assertTrue(extractor.read(StandardCharsets.UTF_8, stream("42")).isEmpty());
        assertTrue(extractor.read(StandardCharsets.UTF_8, stream("[1,2]")).isEmpty());
    }
}
