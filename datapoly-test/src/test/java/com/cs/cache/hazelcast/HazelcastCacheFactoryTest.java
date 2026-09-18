// Use of this source code is governed by a BSD-style license
package com.cs.cache.hazelcast;

import com.cs.cache.DistributedCache;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

public class HazelcastCacheFactoryTest {

    private HazelcastCacheFactory factory;
    private Map<Object, Object> backingStore;

    @Before
    public void setUp() {
        backingStore = new HashMap<>();
        IMap<Object, Object> map = (IMap<Object, Object>) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{IMap.class},
                (p, m, a) -> {
                    if ("get".equals(m.getName())) {
                        return backingStore.get(a[0]);
                    }
                    if ("put".equals(m.getName())) {
                        return backingStore.put(a[0], a[1]);
                    }
                    if ("hashCode".equals(m.getName())) {
                        return System.identityHashCode(p);
                    }
                    if ("equals".equals(m.getName())) {
                        return p == a[0];
                    }
                    return null;
                });
        HazelcastInstance hazelcast = (HazelcastInstance) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{HazelcastInstance.class},
                (p, m, a) -> {
                    if ("getMap".equals(m.getName())) {
                        return map;
                    }
                    if ("hashCode".equals(m.getName())) {
                        return System.identityHashCode(p);
                    }
                    if ("equals".equals(m.getName())) {
                        return p == a[0];
                    }
                    return null;
                });
        factory = new HazelcastCacheFactory();
        com.cs.core.datatask.DataTaskTestSupport.setField(factory, "hazelcastInstance", hazelcast);
    }

    @Test
    public void testCacheMapDelegatesToHazelcastMap() {
        Map<String, String> cacheMap = factory.getCacheMap("m", String.class);
        cacheMap.put("k", "v");
        assertEquals("v", backingStore.get("k"));
        assertEquals("v", cacheMap.get("k"));
    }

    @Test
    public void testDistributedCacheCachedByName() {
        DistributedCache first = factory.getDistributedCache("cache-a");
        assertSame(first, factory.getDistributedCache("cache-a"));
        assertNotSame(first, factory.getDistributedCache("cache-b"));
        assertEquals("cache-a", first.getName());
        first.put("k", "v", 0, java.util.concurrent.TimeUnit.SECONDS);
        assertEquals("v", backingStore.get("k"));
    }
}
