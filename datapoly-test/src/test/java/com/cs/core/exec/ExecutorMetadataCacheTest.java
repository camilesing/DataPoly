// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.core.exec;

import com.cs.common.enums.HttpMethodEnum;
import com.cs.persistence.entity.*;
import com.google.common.base.Ticker;
import org.junit.*;

import java.util.concurrent.atomic.*;

import static org.junit.Assert.*;

/**
 * Local short-TTL metadata cache test (A2): driven by a fake Ticker, verifying cache hits skip the source, negative-result caching,
 * TTL-expiry reload, switch pass-through, and the independent auth-group switch.
 */
public class ExecutorMetadataCacheTest {

    private static final long TTL_SECONDS = 10L;

    private FakeTicker ticker;
    private ExecutorMetadataCache cache;

    @Before
    public void setUp() {
        ticker = new FakeTicker();
        cache = new ExecutorMetadataCache();
        cache.rebuild(TTL_SECONDS, 100, ticker);
    }

    @Test
    public void apiAssignmentHitSkipsLoader() {
        ApiAssignmentEntity entity = new ApiAssignmentEntity();
        AtomicInteger loads = new AtomicInteger();
        ApiAssignmentEntity first = cache.getApiAssignment(HttpMethodEnum.GET, "/demo",
                () -> {
                    loads.incrementAndGet();
                    return entity;
                });
        ApiAssignmentEntity second = cache.getApiAssignment(HttpMethodEnum.GET, "/demo",
                () -> {
                    loads.incrementAndGet();
                    return new ApiAssignmentEntity();
                });
        assertSame(entity, first);
        assertSame(entity, second);
        assertEquals(1, loads.get());
    }

    @Test
    public void apiAssignmentNegativeResultIsCached() {
        AtomicInteger loads = new AtomicInteger();
        assertNull(cache.getApiAssignment(HttpMethodEnum.GET, "/missing", () -> {
            loads.incrementAndGet();
            return null;
        }));
        assertNull(cache.getApiAssignment(HttpMethodEnum.GET, "/missing", () -> {
            loads.incrementAndGet();
            return new ApiAssignmentEntity();
        }));
        assertEquals(1, loads.get());
    }

    @Test
    public void apiAssignmentDifferentKeyDifferentEntry() {
        ApiAssignmentEntity get = new ApiAssignmentEntity();
        ApiAssignmentEntity post = new ApiAssignmentEntity();
        assertSame(get, cache.getApiAssignment(HttpMethodEnum.GET, "/demo", () -> get));
        assertSame(post, cache.getApiAssignment(HttpMethodEnum.POST, "/demo", () -> post));
    }

    @Test
    public void expiredEntryReloads() {
        ApiAssignmentEntity old = new ApiAssignmentEntity();
        ApiAssignmentEntity fresh = new ApiAssignmentEntity();
        AtomicInteger loads = new AtomicInteger();
        assertSame(old, cache.getApiAssignment(HttpMethodEnum.GET, "/demo", () -> {
            loads.incrementAndGet();
            return old;
        }));
        ticker.advanceMillis(TTL_SECONDS * 1000L + 1L);
        assertSame(fresh, cache.getApiAssignment(HttpMethodEnum.GET, "/demo", () -> {
            loads.incrementAndGet();
            return fresh;
        }));
        assertEquals(2, loads.get());
    }

    @Test
    public void dataSourceCachedById() {
        DataSourceEntity entity = new DataSourceEntity();
        AtomicInteger loads = new AtomicInteger();
        assertSame(entity, cache.getDataSource(1L, () -> {
            loads.incrementAndGet();
            return entity;
        }));
        assertNull(cache.getDataSource(2L, () -> null));
        assertNull(cache.getDataSource(2L, () -> {
            loads.incrementAndGet();
            return null;
        }));
        assertSame(entity, cache.getDataSource(1L, () -> {
            loads.incrementAndGet();
            return new DataSourceEntity();
        }));
        assertEquals(1, loads.get());
    }

    @Test
    public void disabledBypassesCache() {
        cache.enabled = false;
        AtomicInteger loads = new AtomicInteger();
        cache.getDataSource(1L, () -> {
            loads.incrementAndGet();
            return null;
        });
        cache.getDataSource(1L, () -> {
            loads.incrementAndGet();
            return null;
        });
        assertEquals(2, loads.get());
    }

    @Test
    public void authGroupResultCached() {
        AtomicInteger loads = new AtomicInteger();
        assertTrue(cache.getAuthGroup("app", 1L, () -> {
            loads.incrementAndGet();
            return true;
        }));
        assertTrue(cache.getAuthGroup("app", 1L, () -> {
            loads.incrementAndGet();
            return false;
        }));
        assertEquals(1, loads.get());
    }

    @Test
    public void authGroupNullTreatedAsFalse() {
        assertFalse(cache.getAuthGroup("app", 1L, () -> null));
    }

    @Test
    public void authGroupDisabledBypassesCache() {
        cache.authGroupEnabled = false;
        AtomicInteger loads = new AtomicInteger();
        cache.getAuthGroup("app", 1L, () -> {
            loads.incrementAndGet();
            return true;
        });
        cache.getAuthGroup("app", 1L, () -> {
            loads.incrementAndGet();
            return true;
        });
        assertEquals(2, loads.get());
    }

    private static final class FakeTicker extends Ticker {

        private final AtomicLong nanos = new AtomicLong();

        @Override
        public long read() {
            return nanos.get();
        }

        private void advanceMillis(long millis) {
            nanos.addAndGet(millis * 1000_000L);
        }
    }

}
