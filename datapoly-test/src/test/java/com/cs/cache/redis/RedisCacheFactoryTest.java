// Use of this source code is governed by a BSD-style license
package com.cs.cache.redis;

import com.cs.cache.DistributedCache;
import com.cs.core.datatask.DataTaskTestSupport;
import org.junit.Before;
import org.junit.Test;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

public class RedisCacheFactoryTest {

    private RedisCacheFactory factory;
    private RedisTestSupport.FakePool pool;

    @Before
    public void setUp() {
        pool = new RedisTestSupport.FakePool();
        factory = new RedisCacheFactory();
        DataTaskTestSupport.setField(factory, "jedisClient", new JedisClient(pool));
    }

    @Test
    public void testCacheMapWritesThroughToJedis() {
        // NOTE: bean-like value types round-trip; plain String values do not survive
        // JSONUtil.toBean on a bare JSON string (main-code limitation, see run report)
        Map<String, Map<String, Object>> cacheMap =
                factory.getCacheMap("ht", (Class<Map<String, Object>>) (Class<?>) Map.class);
        Map<String, Object> value = new java.util.LinkedHashMap<>();
        value.put("f", 1);
        cacheMap.put("k", value);
        assertEquals("{\"f\":1}", pool.jedis.hashes.get("ht").get("k"));
        assertEquals(value, cacheMap.get("k"));
    }

    @Test
    public void testDistributedCacheCachedByName() {
        DistributedCache first = factory.getDistributedCache("cache-a");
        DistributedCache second = factory.getDistributedCache("cache-a");
        assertSame(first, second);
        assertNotSame(first, factory.getDistributedCache("cache-b"));
        assertEquals("cache-a", first.getName());

        first.put("k", "v", 5, TimeUnit.SECONDS);
        assertTrue(pool.jedis.strings.containsKey("cache-a#k"));
    }
}
