// Use of this source code is governed by a BSD-style license
package com.cs.core.exec.logger;

import com.cs.common.service.DisplayRecord;
import org.junit.After;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.*;

public class DebugExecuteLoggerTest {

    @After
    public void cleanUp() {
        DebugExecuteLogger.clear();
    }

    @Test
    public void testWithoutInitAddsAreIgnored() {
        DebugExecuteLogger.add("sql", Collections.emptyList(), 1L);
        DebugExecuteLogger.add("text");
        assertTrue(DebugExecuteLogger.get().isEmpty());
    }

    @Test
    public void testRecordsSqlAndScriptEntriesInOrder() {
        DebugExecuteLogger.init();
        DebugExecuteLogger.add("SELECT 1", Arrays.asList("p1", 2), 42L);
        DebugExecuteLogger.add("script output");
        assertEquals(2, DebugExecuteLogger.get().size());
        DisplayRecord first = DebugExecuteLogger.get().get(0);
        assertTrue(first instanceof com.cs.core.dto.ExecuteSqlRecord);
        DisplayRecord second = DebugExecuteLogger.get().get(1);
        assertTrue(second instanceof com.cs.core.dto.ScripDebugRecord);
    }

    @Test
    public void testClearResetsThreadLocal() {
        DebugExecuteLogger.init();
        DebugExecuteLogger.add("SELECT 1", Collections.emptyList(), 1L);
        DebugExecuteLogger.clear();
        assertTrue(DebugExecuteLogger.get().isEmpty());
        // after clear, adds are ignored again until re-init
        DebugExecuteLogger.add("SELECT 2", Collections.emptyList(), 1L);
        assertTrue(DebugExecuteLogger.get().isEmpty());
    }
}
