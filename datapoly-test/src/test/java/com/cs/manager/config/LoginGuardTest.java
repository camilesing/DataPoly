// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.manager.config;

import com.cs.common.exception.*;
import org.junit.*;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class LoginGuardTest {

    private LoginGuard guard;

    @Before
    public void setUp() {
        guard = new LoginGuard();
    }

    @Test
    public void checkAllowedUnderLimit() {
        for (int i = 0; i < 10; i++) {
            guard.checkAllowed("admin", "10.0.0.1");
        }
    }

    @Test
    public void rateLimitedBeyondLimitPerMinute() {
        guard.rateLimitPerMinute = 3;
        guard.checkAllowed("admin", "10.0.0.1");
        guard.checkAllowed("admin", "10.0.0.1");
        guard.checkAllowed("admin", "10.0.0.1");
        try {
            guard.checkAllowed("admin", "10.0.0.1");
            Assert.fail("expected rate limited exception");
        } catch (CommonException e) {
            Assert.assertEquals(ResponseErrorCode.ERROR_TOO_MANY_REQUESTS, e.getCode());
        }
    }

    @Test
    public void lockedAfterConsecutiveFailures() {
        guard.failLockThreshold = 3;
        for (int i = 0; i < 3; i++) {
            guard.recordFailure("admin", "10.0.0.1");
        }
        try {
            guard.checkAllowed("admin", "10.0.0.1");
            Assert.fail("expected locked exception");
        } catch (CommonException e) {
            Assert.assertEquals(ResponseErrorCode.ERROR_TOO_MANY_REQUESTS, e.getCode());
        }
    }

    @Test
    public void recordSuccessClearsLock() {
        guard.failLockThreshold = 2;
        guard.recordFailure("admin", "10.0.0.1");
        guard.recordFailure("admin", "10.0.0.1");
        guard.recordSuccess("admin", "10.0.0.1");
        guard.checkAllowed("admin", "10.0.0.1");
    }

    /**
     * Regression: pruning must delete EXPIRED entries and keep ACTIVE lockouts.
     * The old inverted condition (!isExpired) let an attacker flush live locks by
     * pushing the tracking maps over max-track-size with junk usernames.
     */
    @Test
    public void pruneKeepsActiveLocks() {
        guard.failLockThreshold = 3;
        guard.maxTrackSize = 2;
        for (int i = 0; i < 3; i++) {
            guard.recordFailure("victim", "10.0.0.1");
        }
        // junk keys push failStates over maxTrackSize so the next checkAllowed triggers pruning
        for (int i = 0; i < 5; i++) {
            guard.recordFailure("junk" + i, "10.0.0.2");
        }
        try {
            guard.checkAllowed("victim", "10.0.0.1");
            Assert.fail("active lock must survive pruning");
        } catch (CommonException e) {
            Assert.assertEquals(ResponseErrorCode.ERROR_TOO_MANY_REQUESTS, e.getCode());
        }
    }

    @Test
    public void subThresholdFailuresDoNotLock() {
        guard.failLockThreshold = 3;
        guard.recordFailure("admin", "10.0.0.1");
        guard.recordFailure("admin", "10.0.0.1");
        // recorded failures below the threshold exist in failStates, but the user is not locked
        guard.checkAllowed("admin", "10.0.0.1");
    }

    @Test
    public void blankIdentityFallsBackToTheDashBucket() {
        // null/blank username and address collapse to "-@-": one shared bucket, still enforced
        guard.checkAllowed(null, null);
        guard.checkAllowed("  ", null);

        guard.failLockThreshold = 1;
        guard.recordFailure(null, null);
        try {
            guard.checkAllowed(null, null);
            Assert.fail("expected locked exception in dash bucket");
        } catch (CommonException e) {
            Assert.assertEquals(ResponseErrorCode.ERROR_TOO_MANY_REQUESTS, e.getCode());
        }
        // recordSuccess clears that bucket again
        guard.recordSuccess(null, null);
        guard.checkAllowed(null, null);
    }

    /**
     * The prune pass sweeps rate windows too: windows older than the 60s window go, fresh ones
     * stay; and when rateWindows itself is the map over the limit the sweep still runs.
     */
    @Test
    public void pruneSweepsStaleRateWindowsAndExpiredFailStates() throws Exception {
        guard.maxTrackSize = 0; // any entry count exceeds the limit: every checkAllowed prunes
        guard.failLockThreshold = 2;
        guard.failLockSeconds = 1; // expiry quiet-window of one second

        Map<String, Object> rateWindows = stateMap("rateWindows");
        Map<String, Object> failStates = stateMap("failStates");

        long now = System.currentTimeMillis();
        rateWindows.put("stale@1.1.1.1", newRateWindow(now - 120_000L));
        rateWindows.put("fresh@1.1.1.1", newRateWindow(now));

        guard.recordFailure("quiet", "2.2.2.2"); // one failure, below threshold, then silence
        Object quiet = failStates.get("quiet@2.2.2.2");
        Field lastFailAt = quiet.getClass().getDeclaredField("lastFailAtMs");
        lastFailAt.setAccessible(true);
        lastFailAt.set(quiet, now - 10_000L); // quiet longer than the lock window: expired

        guard.recordFailure("victim", "3.3.3.3"); // locked → must survive the sweep
        guard.recordFailure("victim", "3.3.3.3");

        guard.checkAllowed("other", "4.4.4.4"); // triggers the prune pass

        Assert.assertFalse("stale rate window must be swept", rateWindows.containsKey("stale@1.1.1.1"));
        Assert.assertTrue("fresh rate window must stay", rateWindows.containsKey("fresh@1.1.1.1"));
        Assert.assertFalse("expired fail state must be swept", failStates.containsKey("quiet@2.2.2.2"));
        Assert.assertTrue("locked fail state must stay", failStates.containsKey("victim@3.3.3.3"));
    }

    @Test
    public void windowOlderThanAMinuteRestartsTheCount() throws Exception {
        Map<String, Object> rateWindows = stateMap("rateWindows");
        rateWindows.put("admin@10.0.0.1", newRateWindow(System.currentTimeMillis() - 120_000L));
        guard.rateLimitPerMinute = 1;
        // the stored window is past 60s: this call opens a fresh window (count=1, allowed)
        // instead of incrementing the stale one to 2 and rejecting
        guard.checkAllowed("admin", "10.0.0.1");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> stateMap(String name) throws Exception {
        Field field = LoginGuard.class.getDeclaredField(name);
        field.setAccessible(true);
        return (Map<String, Object>) field.get(guard);
    }

    private static Object newRateWindow(long windowStart) throws Exception {
        Class<?> type = Class.forName("com.cs.manager.config.LoginGuard$RateWindow");
        Constructor<?> constructor = type.getDeclaredConstructor(long.class);
        constructor.setAccessible(true);
        return constructor.newInstance(windowStart);
    }
}
