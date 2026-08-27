// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.core.exec.logger;

import com.cs.common.service.DisplayRecord;
import com.cs.core.dto.*;

import java.util.*;

public final class DebugExecuteLogger {

    private static final ThreadLocal<List<DisplayRecord>> threadLocal = new ThreadLocal<>();

    public static void init() {
        threadLocal.set(new ArrayList<>());
    }

    public static void add(String sql, List parameters, Long costs) {
        List<DisplayRecord> list = threadLocal.get();
        if (null != list) {
            list.add(new ExecuteSqlRecord(sql, parameters, costs));
        }
    }

    public static void add(String text) {
        List<DisplayRecord> list = threadLocal.get();
        if (null != list) {
            list.add(new ScripDebugRecord(text));
        }
    }

    public static List<DisplayRecord> get() {
        List<DisplayRecord> list = threadLocal.get();
        if (null == list) {
            return Collections.emptyList();
        }
        return list;
    }

    public static void clear() {
        threadLocal.remove();
    }
}
