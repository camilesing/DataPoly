// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.common.exception;

import org.junit.*;

public class ResponseErrorCodeTest {

    @Test
    public void authErrorCodesMapToRealHttpStatus() {
        Assert.assertEquals(401, ResponseErrorCode.ERROR_TOKEN_EXPIRED.getHttpStatus());
        Assert.assertEquals(403, ResponseErrorCode.ERROR_CLIENT_FORBIDDEN.getHttpStatus());
        Assert.assertEquals(403, ResponseErrorCode.ERROR_ACCESS_FORBIDDEN.getHttpStatus());
        Assert.assertEquals(404, ResponseErrorCode.ERROR_PATH_NOT_EXISTS.getHttpStatus());
        Assert.assertEquals(429, ResponseErrorCode.ERROR_TOO_MANY_REQUESTS.getHttpStatus());
    }

    @Test
    public void commonErrorCodesMapToClientOrServerStatus() {
        Assert.assertEquals(200, ResponseErrorCode.SUCCESS.getHttpStatus());
        Assert.assertEquals(500, ResponseErrorCode.ERROR_INTERNAL_ERROR.getHttpStatus());
        Assert.assertEquals(400, ResponseErrorCode.ERROR_INVALID_ARGUMENT.getHttpStatus());
        Assert.assertEquals(404, ResponseErrorCode.ERROR_RESOURCE_NOT_EXISTS.getHttpStatus());
    }

    @Test
    public void businessErrorCodesStayHttp200() {
        for (ResponseErrorCode code : new ResponseErrorCode[]{
                ResponseErrorCode.ERROR_RESOURCE_ALREADY_EXISTS,
                ResponseErrorCode.ERROR_RESOURCE_ALREADY_USED,
                ResponseErrorCode.ERROR_RESOURCE_NOT_ONLINE,
                ResponseErrorCode.ERROR_USER_NOT_EXISTS,
                ResponseErrorCode.ERROR_USER_PASSWORD_WRONG,
                ResponseErrorCode.ERROR_INVALID_JDBC_URL,
                ResponseErrorCode.ERROR_CANNOT_CONNECT_REMOTE,
                ResponseErrorCode.ERROR_EDIT_ALREADY_PUBLISHED}) {
            Assert.assertEquals(code.name(), 200, code.getHttpStatus());
        }
    }

    @Test
    public void bodyCodesUnchanged() {
        // Body code semantics unchanged (HTTP status is only an added dimension)
        Assert.assertEquals(0, ResponseErrorCode.SUCCESS.getCode());
        Assert.assertEquals(1, ResponseErrorCode.ERROR_INTERNAL_ERROR.getCode());
        Assert.assertEquals(2, ResponseErrorCode.ERROR_INVALID_ARGUMENT.getCode());
        Assert.assertEquals(3, ResponseErrorCode.ERROR_RESOURCE_NOT_EXISTS.getCode());
        Assert.assertEquals(401, ResponseErrorCode.ERROR_TOKEN_EXPIRED.getCode());
        Assert.assertEquals(429, ResponseErrorCode.ERROR_TOO_MANY_REQUESTS.getCode());
    }
}
