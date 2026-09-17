// Use of this source code is governed by a BSD-style license
package com.cs.core.datatask;

import org.junit.Test;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

public class DataTaskWorkerTest {

    /** Engine fake: only the three entry points the worker touches. */
    private static class StubEngine extends DataTaskJobEngine {
        final Queue<Long> claims = new ConcurrentLinkedQueue<>();
        final List<Long> ran = new java.util.concurrent.CopyOnWriteArrayList<>();
        final List<String> reaped = new java.util.concurrent.CopyOnWriteArrayList<>();
        final AtomicInteger claimFailures = new AtomicInteger();
        volatile boolean failNextClaim;

        @Override
        public Long claimNext(String workerAddr) {
            if (failNextClaim) {
                failNextClaim = false;
                claimFailures.incrementAndGet();
                throw new IllegalStateException("claim exploded");
            }
            return claims.poll();
        }

        @Override
        public void run(Long jobId) {
            ran.add(jobId);
        }

        @Override
        public void reapLost(String message) {
            reaped.add(message);
        }
    }

    private static void awaitTrue(java.util.function.BooleanSupplier condition, long timeoutMs)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (!condition.getAsBoolean()) {
            if (System.currentTimeMillis() > deadline) {
                fail("condition not met within " + timeoutMs + "ms");
            }
            TimeUnit.MILLISECONDS.sleep(20);
        }
    }

    @Test
    public void testPollTickRunsClaimedJobsAndStopsOnEmptyQueue() throws Exception {
        StubEngine engine = new StubEngine();
        Collections.addAll(engine.claims, 101L, 102L);
        DataTaskWorker worker = new DataTaskWorker(2, "reap-msg");
        DataTaskTestSupport.setField(worker, "engine", engine);
        try {
            worker.pollTick();
            awaitTrue(() -> engine.ran.containsAll(java.util.Arrays.asList(101L, 102L)), 5000);
            // queue exhausted: nothing else runs
            assertEquals(2, engine.ran.size());
        } finally {
            worker.shutdown();
        }
    }

    @Test
    public void testPollTickSurvivesClaimFailure() throws Exception {
        StubEngine engine = new StubEngine();
        engine.failNextClaim = true;
        DataTaskWorker worker = new DataTaskWorker(1, "reap-msg");
        DataTaskTestSupport.setField(worker, "engine", engine);
        try {
            worker.pollTick();
            assertEquals(1, engine.claimFailures.get());
            assertTrue(engine.ran.isEmpty());

            // worker stays usable: a later tick with a claim runs the job
            engine.claims.add(201L);
            worker.pollTick();
            awaitTrue(() -> engine.ran.contains(201L), 5000);
        } finally {
            worker.shutdown();
        }
    }

    @Test
    public void testReapTickDelegatesWithConfiguredMessage() {
        StubEngine engine = new StubEngine();
        DataTaskWorker worker = new DataTaskWorker(1, "lease expired");
        DataTaskTestSupport.setField(worker, "engine", engine);
        try {
            worker.reapTick();
            assertEquals(Collections.singletonList("lease expired"), engine.reaped);
        } finally {
            worker.shutdown();
        }
    }
}
