// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.common.exception;

public class UnPermissionException extends RuntimeException {

    public UnPermissionException(String message) {
        super(message);
    }

    public UnPermissionException(String message, Throwable cause) {
        super(message, cause);
    }
}
