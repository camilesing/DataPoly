// Use of this source code is governed by a BSD-style license
package com.cs.common.util;

import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

public class LambdaUtilsTest {

    private static class RecordedFailure extends Exception {
    }

    @Test
    public void testIfDoRunsActionOnlyWhenConditionTrue() {
        AtomicInteger counter = new AtomicInteger();
        LambdaUtils.ifDo(true, counter::incrementAndGet);
        assertEquals(1, counter.get());
        LambdaUtils.ifDo(false, counter::incrementAndGet);
        assertEquals(1, counter.get());
    }

    @Test
    public void testIfDoWrapsCheckedException() {
        try {
            LambdaUtils.ifDo(true, () -> {
                throw new RecordedFailure();
            });
            fail("expected RuntimeException wrapping the checked failure");
        } catch (RuntimeException e) {
            assertTrue(e.getCause() instanceof RecordedFailure);
        }
    }

    /**
     * Documents current behaviour: the condition argument is ignored and the action
     * always runs. Suspected bug kept as-is per repo policy; flagged in the report.
     */
    @Test
    public void testIfDoMayThrowRunsRegardlessOfCondition() throws Exception {
        AtomicInteger counter = new AtomicInteger();
        LambdaUtils.ifDoMayThrow(false, counter::incrementAndGet);
        assertEquals(1, counter.get());
        LambdaUtils.ifDoMayThrow(true, counter::incrementAndGet);
        assertEquals(2, counter.get());
    }

    @Test
    public void testIfDoIgnoreThrowSwallowsFailure() {
        AtomicInteger counter = new AtomicInteger();
        LambdaUtils.ifDoIgnoreThrow(true, () -> {
            counter.incrementAndGet();
            throw new RecordedFailure();
        });
        assertEquals(1, counter.get());
        LambdaUtils.ifDoIgnoreThrow(false, counter::incrementAndGet);
        assertEquals("condition is not honoured by ifDoIgnoreThrow either", 2, counter.get());
    }

    @Test
    public void testIfDoElseSelectsBranch() {
        AtomicInteger counter = new AtomicInteger();
        LambdaUtils.ifDoElse(true, () -> counter.set(10), () -> counter.set(20));
        assertEquals(10, counter.get());
        LambdaUtils.ifDoElse(false, () -> counter.set(10), () -> counter.set(20));
        assertEquals(20, counter.get());
    }

    @Test
    public void testIfDoElseWrapsThrowable() {
        try {
            LambdaUtils.ifDoElse(true, () -> {
                throw new RecordedFailure();
            }, () -> {
            });
            fail("expected RuntimeException wrapping the branch failure");
        } catch (RuntimeException e) {
            assertTrue(e.getCause() instanceof RecordedFailure);
        }
    }
}
