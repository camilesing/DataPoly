// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.manager.config;

import com.cs.common.exception.*;
import org.junit.*;

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
}
