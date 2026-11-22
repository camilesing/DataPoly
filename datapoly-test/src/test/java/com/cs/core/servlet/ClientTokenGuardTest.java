// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.core.servlet;

import com.cs.common.exception.*;
import org.junit.*;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

public class ClientTokenGuardTest {

    private ClientTokenGuard guard;

    @Before
    public void setUp() {
        guard = new ClientTokenGuard();
    }

    @Test
    public void checkAllowedUnderLimit() {
        for (int i = 0; i < 10; i++) {
            guard.checkAllowed("clientA", "10.0.0.1");
        }
    }

    @Test
    public void rateLimitedBeyondLimitPerMinute() {
        guard.rateLimitPerMinute = 3;
        guard.checkAllowed("clientA", "10.0.0.1");
        guard.checkAllowed("clientA", "10.0.0.1");
        guard.checkAllowed("clientA", "10.0.0.1");
        try {
            guard.checkAllowed("clientA", "10.0.0.1");
            Assert.fail("expected rate limited exception");
        } catch (CommonException e) {
            Assert.assertEquals(ResponseErrorCode.ERROR_TOO_MANY_REQUESTS, e.getCode());
        }
    }

    @Test
    public void lockedAfterConsecutiveFailures() {
        guard.failLockThreshold = 3;
        for (int i = 0; i < 3; i++) {
            guard.recordFailure("clientA", "10.0.0.1");
        }
        try {
            guard.checkAllowed("clientA", "10.0.0.1");
            Assert.fail("expected locked exception");
        } catch (CommonException e) {
            Assert.assertEquals(ResponseErrorCode.ERROR_TOO_MANY_REQUESTS, e.getCode());
        }
    }

    @Test
    public void recordSuccessClearsLock() {
        guard.failLockThreshold = 2;
        guard.recordFailure("clientA", "10.0.0.1");
        guard.recordFailure("clientA", "10.0.0.1");
        guard.recordSuccess("clientA", "10.0.0.1");
        guard.checkAllowed("clientA", "10.0.0.1");
    }

    @Test
    public void differentKeysAreIndependent() {
        guard.failLockThreshold = 2;
        guard.recordFailure("clientA", "10.0.0.1");
        guard.recordFailure("clientA", "10.0.0.1");
        // clientA being locked out does not affect clientB
        guard.checkAllowed("clientB", "10.0.0.1");
        try {
            guard.checkAllowed("clientA", "10.0.0.1");
            Assert.fail("expected locked exception");
        } catch (CommonException e) {
            Assert.assertEquals(ResponseErrorCode.ERROR_TOO_MANY_REQUESTS, e.getCode());
        }
    }

    @Test
    public void subThresholdFailureDoesNotLock() {
        // one recorded failure leaves a non-null FailState that is simply not locked:
        // further checks pass through it (isLocked == false branch)
        guard.failLockThreshold = 5;
        guard.recordFailure("clientA", "10.0.0.1");
        guard.checkAllowed("clientA", "10.0.0.1");
        guard.checkAllowed("clientA", "10.0.0.1");
    }

    @Test
    public void blankKeyPartsNormalizeToDashAndShareOneState() {
        // buildKey folds null/blank clientId and remoteAddr to "-": a lock recorded under
        // (null, null) must also hit a caller passing blanks
        guard.failLockThreshold = 1;
        guard.recordFailure(null, null);
        try {
            guard.checkAllowed(" ", "");
            Assert.fail("expected locked exception for the normalized blank key");
        } catch (CommonException e) {
            Assert.assertEquals(ResponseErrorCode.ERROR_TOO_MANY_REQUESTS, e.getCode());
        }
    }

    @Test
    public void blankRemoteAddrAloneSharesStateWithEmptyRemoteAddr() {
        guard.failLockThreshold = 1;
        guard.recordFailure("clientA", "");
        try {
            guard.checkAllowed("clientA", null);
            Assert.fail("expected locked exception for the normalized blank remote addr");
        } catch (CommonException e) {
            Assert.assertEquals(ResponseErrorCode.ERROR_TOO_MANY_REQUESTS, e.getCode());
        }
    }

    @Test
    public void staleWindowIsReplacedByAFreshOne() throws Exception {
        // a rate window older than WINDOW_MILLIS is no longer usable: the compute lambda
        // must swap in a new window instead of incrementing the expired counter
        guard.rateLimitPerMinute = 1;
        Map<String, Object> rateWindows = internalMap("rateWindows");
        Class<?> rateWindowClass = Class.forName("com.cs.core.servlet.ClientTokenGuard$RateWindow");
        Constructor<?> ctor = rateWindowClass.getDeclaredConstructor(long.class);
        ctor.setAccessible(true);
        rateWindows.put("clientA@10.0.0.1", ctor.newInstance(System.currentTimeMillis() - 120_000L));

        guard.checkAllowed("clientA", "10.0.0.1"); // fresh window: counter starts at 1, allowed
        try {
            guard.checkAllowed("clientA", "10.0.0.1"); // second hit inside the new window
            Assert.fail("expected rate limited exception");
        } catch (CommonException e) {
            Assert.assertEquals(ResponseErrorCode.ERROR_TOO_MANY_REQUESTS, e.getCode());
        }
        // the window in the map is the fresh one now
        Object current = rateWindows.get("clientA@10.0.0.1");
        Field start = rateWindowClass.getDeclaredField("windowStart");
        start.setAccessible(true);
        Assert.assertTrue(start.getLong(current) > System.currentTimeMillis() - 60_000L);
    }

    @Test
    public void pruneRemovesStaleWindowsAndExpiredFailStatesOnly() throws Exception {
        long now = System.currentTimeMillis();
        Map<String, Object> rateWindows = internalMap("rateWindows");
        Map<String, Object> failStates = internalMap("failStates");
        Class<?> rateWindowClass = Class.forName("com.cs.core.servlet.ClientTokenGuard$RateWindow");
        Constructor<?> rwCtor = rateWindowClass.getDeclaredConstructor(long.class);
        rwCtor.setAccessible(true);
        rateWindows.put("stale@ip", rwCtor.newInstance(now - 120_000L));
        rateWindows.put("fresh@ip", rwCtor.newInstance(now));

        failStates.put("expired@ip", failState(0L, now - 400_000L));
        failStates.put("recent@ip", failState(0L, now));
        failStates.put("locked@ip", failState(now + 60_000L, now));

        // maxTrackSize 0 forces pruneStale on the next check
        guard.maxTrackSize = 0;
        guard.checkAllowed("fresh", "ip");

        Assert.assertFalse("stale window must be pruned", rateWindows.containsKey("stale@ip"));
        Assert.assertTrue("fresh window must survive", rateWindows.containsKey("fresh@ip"));
        Assert.assertFalse("expired fail state must be pruned", failStates.containsKey("expired@ip"));
        Assert.assertTrue("recent fail state must survive", failStates.containsKey("recent@ip"));
        Assert.assertTrue("locked fail state must survive", failStates.containsKey("locked@ip"));
    }

    @Test
    public void pruneRunsWhenOnlyFailStatesExceedTheLimit() throws Exception {
        // rateWindows within the limit but failStates over it: prune still walks both maps
        long now = System.currentTimeMillis();
        Map<String, Object> failStates = internalMap("failStates");
        failStates.put("expired@ip", failState(0L, now - 400_000L));

        guard.maxTrackSize = 0;
        guard.checkAllowed("clientA", "10.0.0.1");

        Assert.assertFalse(failStates.containsKey("expired@ip"));
    }

    @Test
    public void concurrentFirstFailuresDoNotLoseCounts() throws Exception {
        // several threads record failures concurrently; a threshold of 4 with 4 concurrent
        // failures must end locked (single compute keeps counts consistent)
        guard.failLockThreshold = 4;
        int threads = 4;
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        for (int i = 0; i < threads; i++) {
            new Thread(() -> {
                try {
                    start.await();
                    guard.recordFailure("clientA", "10.0.0.1");
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            }).start();
        }
        start.countDown();
        Assert.assertTrue(done.await(10, java.util.concurrent.TimeUnit.SECONDS));
        try {
            guard.checkAllowed("clientA", "10.0.0.1");
            Assert.fail("expected locked exception after concurrent failures");
        } catch (CommonException e) {
            Assert.assertEquals(ResponseErrorCode.ERROR_TOO_MANY_REQUESTS, e.getCode());
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> internalMap(String name) throws Exception {
        Field field = ClientTokenGuard.class.getDeclaredField(name);
        field.setAccessible(true);
        return (Map<String, Object>) field.get(guard);
    }

    /**
     * Crafted FailState: no real failure recorded, only the volatile fields positioned to
     * exercise isExpired's branches (lockedUntilMs / lastFailAtMs).
     */
    private Object failState(long lockedUntilMs, long lastFailAtMs) throws Exception {
        Class<?> clazz = Class.forName("com.cs.core.servlet.ClientTokenGuard$FailState");
        Constructor<?> ctor = clazz.getDeclaredConstructor();
        ctor.setAccessible(true);
        Object state = ctor.newInstance();
        setLong(clazz, state, "lockedUntilMs", lockedUntilMs);
        setLong(clazz, state, "lastFailAtMs", lastFailAtMs);
        return state;
    }

    private static void setLong(Class<?> clazz, Object target, String field, long value) throws Exception {
        Field f = clazz.getDeclaredField(field);
        f.setAccessible(true);
        f.setLong(target, value);
    }
}
