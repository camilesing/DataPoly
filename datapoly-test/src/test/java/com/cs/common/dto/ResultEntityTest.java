// Use of this source code is governed by a BSD-style license
package com.cs.common.dto;

import com.cs.common.exception.ResponseErrorCode;
import org.junit.Test;

import static org.junit.Assert.*;

public class ResultEntityTest {

    @Test
    public void testSuccessWithoutData() {
        ResultEntity<?> entity = ResultEntity.success();
        assertEquals(Integer.valueOf(ResponseErrorCode.SUCCESS.getCode()), entity.getCode());
        assertEquals("success", entity.getMessage());
        assertNull(entity.getData());
    }

    @Test
    public void testSuccessWithData() {
        ResultEntity<String> entity = ResultEntity.success("payload");
        assertEquals(Integer.valueOf(ResponseErrorCode.SUCCESS.getCode()), entity.getCode());
        assertEquals("payload", entity.getData());
    }

    @Test
    public void testFailedWithErrorCodeOnly() {
        ResultEntity<?> entity = ResultEntity.failed(ResponseErrorCode.ERROR_INVALID_ARGUMENT);
        assertEquals(Integer.valueOf(ResponseErrorCode.ERROR_INVALID_ARGUMENT.getCode()), entity.getCode());
        assertEquals(ResponseErrorCode.ERROR_INVALID_ARGUMENT.getMessage(), entity.getMessage());
    }

    @Test
    public void testFailedAppendsDetailMessage() {
        ResultEntity<?> entity = ResultEntity.failed(ResponseErrorCode.ERROR_INVALID_ARGUMENT, "extra detail");
        assertTrue(entity.getMessage().endsWith(",extra detail"));
    }

    @Test
    public void testFailedWithMessageOnlyUsesInternalErrorCode() {
        ResultEntity<?> entity = ResultEntity.failed("boom");
        assertEquals(Integer.valueOf(ResponseErrorCode.ERROR_INTERNAL_ERROR.getCode()), entity.getCode());
        assertEquals("boom", entity.getMessage());
    }
}
