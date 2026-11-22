// Use of this source code is governed by a BSD-style license
package com.cs.common.exception;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Constructor coverage for the exception family: message/cause wiring must survive
 * (they are what the global handler reports), with the code attached verbatim.
 */
public class CommonExceptionVariantsTest {

    @Test
    public void testVarargsConstructorKeepsCodeAndResolvedMessage() {
        // without a Spring container the i18n lookup degrades to the key itself
        CommonException exception = new CommonException(ResponseErrorCode.ERROR_INVALID_ARGUMENT,
                "sql.template.invalid", "detail");
        assertSame(ResponseErrorCode.ERROR_INVALID_ARGUMENT, exception.getCode());
        assertEquals("sql.template.invalid", exception.getMessage());
    }

    @Test
    public void testCauseConstructorKeepsCodeAndCause() {
        IllegalStateException cause = new IllegalStateException("boom");
        CommonException exception = new CommonException(ResponseErrorCode.ERROR_INTERNAL_ERROR, cause);
        assertSame(ResponseErrorCode.ERROR_INTERNAL_ERROR, exception.getCode());
        assertSame(cause, exception.getCause());
        assertEquals(cause.toString(), exception.getMessage());
    }

    @Test
    public void testPlainMessageConstructor() {
        CommonException exception = new CommonException(ResponseErrorCode.ERROR_INTERNAL_ERROR, "plain.msg");
        assertSame(ResponseErrorCode.ERROR_INTERNAL_ERROR, exception.getCode());
        assertEquals("plain.msg", exception.getMessage());
    }

    @Test
    public void testUnPermissionExceptionCarriesMessageAndCause() {
        IllegalStateException cause = new IllegalStateException("nope");
        assertEquals("forbidden", new UnPermissionException("forbidden").getMessage());
        UnPermissionException withCause = new UnPermissionException("forbidden", cause);
        assertEquals("forbidden", withCause.getMessage());
        assertSame(cause, withCause.getCause());
    }

    @Test
    public void testUnAuthorizedExceptionCarriesMessageAndCause() {
        IllegalStateException cause = new IllegalStateException("expired");
        assertEquals("unauthorized", new UnAuthorizedException("unauthorized").getMessage());
        UnAuthorizedException withCause = new UnAuthorizedException("unauthorized", cause);
        assertEquals("unauthorized", withCause.getMessage());
        assertSame(cause, withCause.getCause());
    }
}
