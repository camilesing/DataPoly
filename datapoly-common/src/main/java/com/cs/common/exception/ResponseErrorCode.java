// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.common.exception;

import com.cs.common.util.I18nUtils;
import lombok.*;

@Getter
@AllArgsConstructor
public enum ResponseErrorCode {

    SUCCESS(0, 200),
    ERROR_INTERNAL_ERROR(1, 500),
    ERROR_INVALID_ARGUMENT(2, 400),
    ERROR_RESOURCE_NOT_EXISTS(3, 404),
    ERROR_RESOURCE_ALREADY_EXISTS(4, 200),
    ERROR_RESOURCE_ALREADY_USED(5, 200),
    ERROR_RESOURCE_NOT_ONLINE(6, 200),
    ERROR_USER_NOT_EXISTS(7, 200),
    ERROR_USER_PASSWORD_WRONG(8, 200),
    ERROR_INVALID_JDBC_URL(9, 200),
    ERROR_CANNOT_CONNECT_REMOTE(10, 200),
    ERROR_EDIT_ALREADY_PUBLISHED(11, 200),

    ERROR_CLIENT_FORBIDDEN(403, 403),
    ERROR_ACCESS_FORBIDDEN(403, 403),
    ERROR_TOKEN_EXPIRED(401, 401),
    ERROR_PATH_NOT_EXISTS(404, 404),

    ERROR_TOO_MANY_REQUESTS(429, 429),
    ;

    private int code;

    /**
     * HTTP status for this error code; business codes (4-11) stay at 200 and differ only by body code
     */
    private int httpStatus;

    public String getMessage() {
        String prefix = "exception.";
        return I18nUtils.getMessage(prefix + this.name());
    }
}
