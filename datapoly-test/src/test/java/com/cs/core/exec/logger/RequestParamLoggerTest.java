// Use of this source code is governed by a BSD-style license
package com.cs.core.exec.logger;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

public class RequestParamLoggerTest {

    @Test
    public void testGetAndClearReturnsOnce() {
        assertNull(RequestParamLogger.getAndClear());
        Map<String, Object> params = new HashMap<>();
        params.put("k", "v");
        RequestParamLogger.set(params);
        assertSame(params, RequestParamLogger.getAndClear());
        assertNull("second read after clear must be null", RequestParamLogger.getAndClear());
    }
}
