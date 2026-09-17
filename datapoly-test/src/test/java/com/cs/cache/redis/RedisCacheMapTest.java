// Use of this source code is governed by a BSD-style license
package com.cs.cache.redis;

import org.junit.Before;
import org.junit.Test;

import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

public class RedisCacheMapTest {

    private RedisTestSupport.FakePool pool;
    private RedisCacheMap<Map<String, Object>> map;

    @Before
    public void setUp() {
        pool = new RedisTestSupport.FakePool();
        map = new RedisCacheMap<>("ht", new JedisClient(pool),
                (Class<Map<String, Object>>) (Class<?>) Map.class);
    }

    private Map<String, Object> row(String key, int value) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put(key, value);
        return row;
    }

    @Test
    public void testSizeAndEmptinessTrackBackingHash() {
        assertTrue(map.isEmpty());
        map.put("a", row("v", 1));
        assertEquals(1, map.size());
        assertFalse(map.isEmpty());
    }

    @Test
    public void testPutReturnsPreviousValueAndRoundTripsJson() {
        assertNull(map.put("a", row("v", 1)));
        assertEquals(row("v", 1), map.get("a"));
        assertEquals(row("v", 1), map.put("a", row("v", 2)));
        assertEquals(row("v", 2), map.get("a"));
    }

    @Test
    public void testContainsKeyAndContainsValue() {
        map.put("a", row("v", 1));
        assertTrue(map.containsKey("a"));
        assertFalse(map.containsKey("b"));
        assertTrue(map.containsValue(row("v", 1)));
        assertFalse(map.containsValue(row("v", 9)));
    }

    @Test
    public void testRemoveReturnsPreviousValue() {
        map.put("a", row("v", 1));
        assertEquals(row("v", 1), map.remove("a"));
        assertNull(map.get("a"));
        assertNull(map.remove("a"));
    }

    @Test
    public void testPutAllWritesThroughAndIgnoresEmptyInput() {
        Map<String, Map<String, Object>> batch = new LinkedHashMap<>();
        batch.put("a", row("v", 1));
        batch.put("b", row("v", 2));
        map.putAll(batch);
        assertEquals(2, map.size());

        map.putAll(null);
        map.putAll(new HashMap<String, Map<String, Object>>());
        assertEquals(2, map.size());
    }

    @Test
    public void testKeySetValuesEntrySetFromSnapshot() {
        map.put("a", row("v", 1));
        map.put("b", row("v", 2));
        assertEquals(new HashSet<>(Arrays.asList("a", "b")), map.keySet());
        assertEquals(2, map.values().size());
        assertTrue(map.values().contains(row("v", 1)));
        assertEquals(2, map.entrySet().size());
    }

    @Test
    public void testClearDropsWholeHash() {
        map.put("a", row("v", 1));
        map.clear();
        assertTrue(map.isEmpty());
        assertFalse(pool.jedis.hashes.containsKey("ht"));
    }
}
