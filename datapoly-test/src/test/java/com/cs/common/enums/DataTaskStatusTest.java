// Use of this source code is governed by a BSD-style license
package com.cs.common.enums;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.*;

public class DataTaskStatusTest {

    @Test
    public void testFinalStatesReportedAsFinal() {
        assertTrue(DataTaskStatus.SUCCESS.isFinal());
        assertTrue(DataTaskStatus.FAILED.isFinal());
        assertTrue(DataTaskStatus.CANCELED.isFinal());
    }

    @Test
    public void testActiveStatesAreNotFinal() {
        // a queued job and a running job can still transition, so neither is final
        assertFalse(DataTaskStatus.PENDING.isFinal());
        assertFalse(DataTaskStatus.RUNNING.isFinal());
    }

    @Test
    public void testEveryConstantClassifiedExactlyOnce() {
        Set<DataTaskStatus> expectedFinal = new HashSet<>(Arrays.asList(
                DataTaskStatus.SUCCESS, DataTaskStatus.FAILED, DataTaskStatus.CANCELED));
        for (DataTaskStatus status : DataTaskStatus.values()) {
            assertEquals("isFinal drifted for " + status,
                    expectedFinal.contains(status), status.isFinal());
        }
    }

    @Test
    public void testEnumSurfaceAndValueOf() {
        assertEquals(5, DataTaskStatus.values().length);
        // valueOf round-trips every declared constant
        for (DataTaskStatus status : DataTaskStatus.values()) {
            assertSame(status, DataTaskStatus.valueOf(status.name()));
        }
    }
}
