// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.common.util;

import com.google.common.cache.*;

import java.util.*;
import java.util.concurrent.TimeUnit;

public final class CacheUtils {

    // Cache duration: 2 hours
    public static final long CACHE_DURATION_SECONDS = 7200L;

    private static Cache<String, Object> loadingCache = CacheBuilder.newBuilder()
            /* Initial capacity of the cache container: 10 */
            .initialCapacity(10)
            /* Maximum size of the cache container: 1000 */
            .maximumSize(1000)
            /* Record cache hit rate */
            .recordStats()
            /* Concurrency level 8 */
            .concurrencyLevel(8)
            /* Expiration time: 15 minutes */
            .expireAfterAccess(CACHE_DURATION_SECONDS, TimeUnit.SECONDS)
            .build();

    public static void put(String key, Object value) {
        loadingCache.put(key, value);
    }

    public static Object get(String key) {
        return loadingCache.getIfPresent(key);
    }

    public static void remove(String key) {
        loadingCache.invalidate(key);
    }

    public static void clear() {
        loadingCache.invalidateAll();
    }

    public static Map<String, Object> getAll() {
        return loadingCache.asMap();
    }

    public static Collection<Object> getAllValue() {
        return loadingCache.asMap().values();
    }

}