// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.core.exec.logger;

import java.util.Map;

public class RequestParamLogger {

    private static final ThreadLocal<Map<String, Object>> threadLocal = new ThreadLocal<>();

    public static void set(Map<String, Object> requestParams) {
        threadLocal.set(requestParams);
    }

    public static Map<String, Object> getAndClear() {
        Map<String, Object> r = threadLocal.get();
        threadLocal.remove();
        return r;
    }

}
