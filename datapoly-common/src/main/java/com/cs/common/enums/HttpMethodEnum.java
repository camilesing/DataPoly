// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.common.enums;

import java.util.Map;
import java.util.HashMap;

public enum HttpMethodEnum {
    GET(false), HEAD(false), PUT(true), POST(true), DELETE(true),
    ;

    /**
     * Name lookup on the request hot path: a map instead of a values() scan (which also
     * clones the enum array per call).
     */
    private static final Map<String, HttpMethodEnum> LOOKUP = new HashMap<>();

    static {
        for (HttpMethodEnum methodEnum : values()) {
            LOOKUP.put(methodEnum.name(), methodEnum);
        }
    }

    private boolean hasBody;

    HttpMethodEnum(boolean hasBody) {
        this.hasBody = hasBody;
    }

    public boolean isHasBody() {
        return hasBody;
    }

    public static boolean exists(String method) {
        return LOOKUP.containsKey(method);
    }

    /**
     * @return the constant for {@code method}, or null when the name is unknown
     *         (callers decide the fallback, e.g. treating unknown methods as GET).
     */
    public static HttpMethodEnum of(String method) {
        return LOOKUP.get(method);
    }
}
