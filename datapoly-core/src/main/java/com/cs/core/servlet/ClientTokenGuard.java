// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.core.servlet;

import com.cs.common.exception.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * /token/generate endpoint protection (S4): fixed-window rate limiting per clientId+IP plus consecutive-failure lockout.
 *
 * <p>In-memory implementation (no external dependencies), reset on process restart; lockout dimension is clientId+IP,
 * so an attacker rotating IPs cannot lock out a legitimate clientId (cross-IP distributed brute force is bounded only by per-IP rate limiting, see AGENTS.md).
 */
@Slf4j
@Service
public class ClientTokenGuard {

    private static final long WINDOW_MILLIS = 60 * 1000L;

    @Value("${datapoly.executor.token.rate-limit-per-minute:10}")
    int rateLimitPerMinute = 10;

    @Value("${datapoly.executor.token.fail-lock-threshold:5}")
    int failLockThreshold = 5;

    @Value("${datapoly.executor.token.fail-lock-seconds:300}")
    long failLockSeconds = 300;

    @Value("${datapoly.executor.token.max-track-size:10000}")
    int maxTrackSize = 10000;

    private final ConcurrentHashMap<String, RateWindow> rateWindows = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, FailState> failStates = new ConcurrentHashMap<>();

    /**
     * Throws {@link CommonException} (code=429) when the limit is exceeded.
     */
    public void checkAllowed(String clientId, String remoteAddr) {
        long now = System.currentTimeMillis();
        pruneStale(now);

        String key = buildKey(clientId, remoteAddr);
        FailState state = failStates.get(key);
        if (null != state && state.isLocked(now)) {
            throw new CommonException(ResponseErrorCode.ERROR_TOO_MANY_REQUESTS, "token.rate.limited");
        }

        RateWindow window = rateWindows.compute(key, (k, w) -> {
            if (null == w || !w.isInWindow(now)) {
                return new RateWindow(now);
            }
            return w;
        });
        if (window.incrementAndGet() > rateLimitPerMinute) {
            log.warn("Token generate rate limited, key:{}", key);
            throw new CommonException(ResponseErrorCode.ERROR_TOO_MANY_REQUESTS, "token.rate.limited");
        }
    }

    public void recordFailure(String clientId, String remoteAddr) {
        long now = System.currentTimeMillis();
        String key = buildKey(clientId, remoteAddr);
        FailState state = failStates.compute(key, (k, s) -> (null == s) ? new FailState() : s);
        int fails = state.incrementFails();
        if (fails >= failLockThreshold) {
            state.lockUntil(now + failLockSeconds * 1000L);
            state.resetFails();
            log.warn("Token generate locked for [{}] seconds, key:{}", failLockSeconds, key);
        }
    }

    public void recordSuccess(String clientId, String remoteAddr) {
        failStates.remove(buildKey(clientId, remoteAddr));
    }

    private String buildKey(String clientId, String remoteAddr) {
        return String.format("%s@%s",
                StringUtils.isBlank(clientId) ? "-" : clientId,
                StringUtils.isBlank(remoteAddr) ? "-" : remoteAddr);
    }

    private void pruneStale(long now) {
        if (rateWindows.size() <= maxTrackSize && failStates.size() <= maxTrackSize) {
            return;
        }
        for (Iterator<Map.Entry<String, RateWindow>> it = rateWindows.entrySet().iterator(); it.hasNext(); ) {
            if (!it.next().getValue().isInWindow(now)) {
                it.remove();
            }
        }
        for (Iterator<Map.Entry<String, FailState>> it = failStates.entrySet().iterator(); it.hasNext(); ) {
            if (it.next().getValue().isExpired(now)) {
                it.remove();
            }
        }
    }

    private static final class RateWindow {

        private final long windowStart;
        private final AtomicInteger count = new AtomicInteger(0);

        private RateWindow(long now) {
            this.windowStart = now;
        }

        private boolean isInWindow(long now) {
            return now - windowStart < WINDOW_MILLIS;
        }

        private int incrementAndGet() {
            return count.incrementAndGet();
        }
    }

    private static final class FailState {

        private final AtomicInteger fails = new AtomicInteger(0);
        private volatile long lockedUntilMs = 0L;

        private int incrementFails() {
            return fails.incrementAndGet();
        }

        private void resetFails() {
            fails.set(0);
        }

        private void lockUntil(long timestamp) {
            lockedUntilMs = timestamp;
        }

        private boolean isLocked(long now) {
            return now < lockedUntilMs;
        }

        private boolean isExpired(long now) {
            return now >= lockedUntilMs && 0 == fails.get();
        }
    }

}
