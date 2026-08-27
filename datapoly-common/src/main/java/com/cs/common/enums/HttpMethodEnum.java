// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.common.enums;

public enum HttpMethodEnum {
    GET(false), HEAD(false), PUT(true), POST(true), DELETE(true),
    ;

    private boolean hasBody;

    HttpMethodEnum(boolean hasBody) {
        this.hasBody = hasBody;
    }

    public boolean isHasBody() {
        return hasBody;
    }

    public static boolean exists(String method) {
        for (HttpMethodEnum methodEnum : values()) {
            if (methodEnum.name().equals(method)) {
                return true;
            }
        }
        return false;
    }
}
