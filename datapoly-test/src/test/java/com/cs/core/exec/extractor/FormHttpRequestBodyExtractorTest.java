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

public class FormHttpRequestBodyExtractorTest {

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
    public void testSupportFormUrlencodedAndUnknownTypes() {
        FormHttpRequestBodyExtractor extractor = new FormHttpRequestBodyExtractor();
        assertTrue(extractor.support(MediaType.APPLICATION_FORM_URLENCODED));
        // multipart types are explicitly skipped, but null falls back to supported
        assertFalse(extractor.support(MediaType.MULTIPART_FORM_DATA));
        assertFalse(extractor.support(MediaType.MULTIPART_MIXED));
        assertFalse(extractor.support(MediaType.APPLICATION_JSON));
        assertTrue(extractor.support(null));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testReadPlainPairs() {
        Map<String, Object> result = new FormHttpRequestBodyExtractor().read(
                StandardCharsets.UTF_8, stream("a=1&b=x"));
        assertEquals(java.util.Arrays.asList("1"), (java.util.List<String>) result.get("a"));
        assertEquals(java.util.Arrays.asList("x"), (java.util.List<String>) result.get("b"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testCommaSeparatedValuesSplitIntoList() {
        Map<String, Object> result = new FormHttpRequestBodyExtractor().read(
                StandardCharsets.UTF_8, stream("tags=x,y,z"));
        assertEquals(java.util.Arrays.asList("x", "y", "z"), (java.util.List<String>) result.get("tags"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testValuelessKeyMapsToNullEntry() {
        Map<String, Object> result = new FormHttpRequestBodyExtractor().read(
                StandardCharsets.UTF_8, stream("flag&k=v"));
        java.util.List<String> flag = (java.util.List<String>) result.get("flag");
        assertEquals(1, flag.size());
        assertNull(flag.get(0));
        assertEquals(java.util.Arrays.asList("v"), (java.util.List<String>) result.get("k"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testUrlDecodingAppliedToNamesAndCommaSegmentsOnly() {
        // Current behaviour: names are always URL-decoded, but simple values are stored raw —
        // only comma-separated segments go through URLDecoder (inconsistency, see run report)
        Map<String, Object> result = new FormHttpRequestBodyExtractor().read(
                StandardCharsets.UTF_8, stream("%E5%90%8D=%E5%80%BC,%E5%80%BC2"));
        assertTrue(result.containsKey("名"));
        assertEquals(java.util.Arrays.asList("值", "值2"), (java.util.List<String>) result.get("名"));

        Map<String, Object> simple = new FormHttpRequestBodyExtractor().read(
                StandardCharsets.UTF_8, stream("k=%E5%80%BC"));
        assertEquals(java.util.Arrays.asList("%E5%80%BC"), (java.util.List<String>) simple.get("k"));
    }
}
