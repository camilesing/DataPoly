// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.core.util;

import java.util.*;
import java.util.function.Function;

public class ConvertUtils {

    public static Map<String, Object> to(Map<String, Object> row) {
        return to(row, null);
    }

    public static Map<String, Object> to(Map<String, Object> row, Function<String, String> converter) {
        if (null == converter) {
            return row;
        }
        if (null == row) {
            return null;
        }
        Map<String, Object> ret = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (value instanceof Map) {
                value = to((Map<String, Object>) value, converter);
            }
            ret.put(converter.apply(key), value);
        }
        return ret;
    }

}
