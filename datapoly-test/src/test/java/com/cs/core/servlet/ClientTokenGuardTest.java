// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.core.servlet;

import com.cs.common.exception.*;
import org.junit.*;

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
}
