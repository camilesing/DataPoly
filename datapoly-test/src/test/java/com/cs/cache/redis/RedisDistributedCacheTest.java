// Use of this source code is governed by a BSD-style license
package com.cs.cache.redis;

import org.junit.Before;
import org.junit.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

public class RedisDistributedCacheTest {

    private RedisTestSupport.FakePool pool;

    @Before
    public void setUp() {
        pool = new RedisTestSupport.FakePool();
    }

    @Test
    public void testConstructorRejectsNulls() {
        try {
            new RedisDistributedCache(null, new JedisClient(pool));
            fail("null cache name must be rejected");
        } catch (NullPointerException expected) {
        }
        try {
            new RedisDistributedCache("name", null);
            fail("null client must be rejected");
        } catch (NullPointerException expected) {
        }
    }

    @Test
    public void testGetMissingKeyReturnsNull() {
        RedisDistributedCache cache = new RedisDistributedCache("demo", new JedisClient(pool));
        assertNull(cache.get("missing", String.class));
    }

    @Test
    public void testPutGetRoundTripUnderNamespacedKey() {
        RedisDistributedCache cache = new RedisDistributedCache("demo", new JedisClient(pool));
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("a", 1);
        cache.put("k", value, 0, TimeUnit.SECONDS);
        // value stored under "demo#k" as JSON
        assertTrue(pool.jedis.strings.containsKey("demo#k"));
        @SuppressWarnings("unchecked")
        Map<String, Object> read = (Map<String, Object>) cache.get("k", Map.class);
        assertEquals(1, read.get("a"));
        assertEquals("demo", cache.getName());
    }

    @Test
    public void testPositiveExpirySetsExpiration() {
        RedisDistributedCache cache = new RedisDistributedCache("demo", new JedisClient(pool));
        cache.put("k", "v", 30, TimeUnit.SECONDS);
        assertEquals(Collections.singletonList("demo#k:30"), pool.jedis.expireCalls);
    }

    @Test
    public void testEvictDeletesKey() {
        RedisDistributedCache cache = new RedisDistributedCache("demo", new JedisClient(pool));
        cache.put("k", "v", 0, TimeUnit.SECONDS);
        cache.evict("k");
        assertNull(cache.get("k", String.class));
    }
}
