// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.common.exception;

import com.cs.common.util.I18nUtils;
import lombok.Data;

@Data
public class CommonException extends RuntimeException {

    private ResponseErrorCode code;

    public CommonException(ResponseErrorCode code, String message) {
        super(I18nUtils.getMessage(message));
        this.code = code;
    }

    public CommonException(ResponseErrorCode code, String messageKey, Object... args) {
        super(I18nUtils.getMessage(messageKey, args));
        this.code = code;
    }

    public CommonException(ResponseErrorCode code, Throwable cause) {
        super(cause);
        this.code = code;
    }
}
