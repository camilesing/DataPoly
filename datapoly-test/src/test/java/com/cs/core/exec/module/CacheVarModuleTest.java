// Use of this source code is governed by a BSD-style license
package com.cs.core.exec.module;

import com.cs.cache.CacheFactory;
import com.cs.cache.DistributedCache;
import com.cs.common.consts.Constants;
import com.cs.core.datatask.DataTaskTestSupport;
import org.junit.Before;
import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

/**
 * The script-facing {@code cache} module maps get/put/evict onto the API-var distributed
 * cache — one fixed cache name, keys and TTL passed through verbatim.
 */
public class CacheVarModuleTest {

    private static class FakeCache implements DistributedCache {
        final Map<String, Object> store = new LinkedHashMap<>();
        Long putTtl;
        TimeUnit putUnit;
        int evictCalls;

        @Override
        public String getName() {
            return Constants.CACHE_NAME_API_VAR;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T get(String key, Class<T> type) {
            return (T) store.get(key);
        }

        @Override
        public void put(String key, Object value, long expire, TimeUnit unit) {
            putTtl = expire;
            putUnit = unit;
            store.put(key, value);
        }

        @Override
        public void evict(String key) {
            evictCalls++;
            store.remove(key);
        }
    }

    private FakeCache cache;
    private CacheVarModule module;

    @Before
    public void setUp() {
        cache = new FakeCache();
        module = new CacheVarModule();
        DataTaskTestSupport.setField(module, "cacheFactory", new CacheFactory() {
            @Override
            public <T> Map<String, T> getCacheMap(String key, Class<T> clazz) {
                throw new UnsupportedOperationException("not used by CacheVarModule");
            }

            @Override
            public DistributedCache getDistributedCache(String name) {
                return Constants.CACHE_NAME_API_VAR.equals(name) ? cache : null;
            }
        });
    }

    @Test
    public void varModuleNameIsCache() {
        assertEquals("cache", module.getVarModuleName());
    }

    @Test
    public void getReturnsCachedString() {
        cache.store.put("greeting", "hello");
        assertEquals("hello", module.get("greeting"));
        assertNull(module.get("missing"));
    }

    @Test
    public void putStoresValueWithSecondBasedTtl() {
        module.put("greeting", "hello", 120);
        assertEquals("hello", cache.store.get("greeting"));
        assertEquals(Long.valueOf(120), cache.putTtl);
        assertEquals(TimeUnit.SECONDS, cache.putUnit);
        // round trip through the same module
        assertEquals("hello", module.get("greeting"));
    }

    @Test
    public void evictRemovesTheKey() {
        module.put("greeting", "hello", 60);
        module.evict("greeting");
        assertEquals(1, cache.evictCalls);
        assertTrue(cache.store.isEmpty());
        assertNull(module.get("greeting"));
    }
}
