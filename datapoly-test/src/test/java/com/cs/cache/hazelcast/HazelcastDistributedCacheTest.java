// Use of this source code is governed by a BSD-style license
package com.cs.cache.hazelcast;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

public class HazelcastDistributedCacheTest {

    /** Records operations on the fake IMap by method name and argument count. */
    static class RecordingMap {
        final List<String> ops = new ArrayList<>();
        final Map<Object, Object> store = new HashMap<>();

        IMap<Object, Object> proxy() {
            return (IMap<Object, Object>) Proxy.newProxyInstance(HazelcastDistributedCacheTest.class.getClassLoader(),
                    new Class<?>[]{IMap.class},
                    (p, m, a) -> {
                        if ("hashCode".equals(m.getName())) {
                            return System.identityHashCode(p);
                        }
                        if ("equals".equals(m.getName())) {
                            return p == a[0];
                        }
                        if ("toString".equals(m.getName())) {
                            return "fake-imap";
                        }
                        if ("get".equals(m.getName())) {
                            return store.get(a[0]);
                        }
                        if ("put".equals(m.getName()) && a.length == 2) {
                            ops.add("put");
                            return store.put(a[0], a[1]);
                        }
                        if ("put".equals(m.getName()) && a.length == 4) {
                            ops.add("put:" + a[2] + ":" + ((TimeUnit) a[3]).name());
                            return store.put(a[0], a[1]);
                        }
                        if ("evict".equals(m.getName())) {
                            ops.add("evict");
                            return store.remove(a[0]) != null;
                        }
                        return null;
                    });
        }
    }

    private HazelcastInstance instance(final IMap<Object, Object> map, final String[] nameSink) {
        return (HazelcastInstance) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{HazelcastInstance.class},
                (p, m, a) -> {
                    if ("getMap".equals(m.getName())) {
                        nameSink[0] = (String) a[0];
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
    }

    @Test
    public void testConstructorRejectsNulls() {
        try {
            new HazelcastDistributedCache(null, "name");
            fail("null instance must be rejected");
        } catch (NullPointerException expected) {
        }
        try {
            new HazelcastDistributedCache((HazelcastInstance) Proxy.newProxyInstance(
                    getClass().getClassLoader(), new Class<?>[]{HazelcastInstance.class},
                    (p, m, a) -> null), null);
            fail("null name must be rejected");
        } catch (NullPointerException expected) {
        }
    }

    @Test
    public void testGetCastsStoredValue() {
        RecordingMap recording = new RecordingMap();
        recording.store.put("k", "value");
        String[] mapName = new String[1];
        HazelcastDistributedCache cache = new HazelcastDistributedCache(instance(recording.proxy(), mapName), "cache");
        assertEquals("value", cache.get("k", String.class));
        assertEquals("cache", mapName[0]);
        assertNull(cache.get("missing", String.class));
    }

    @Test
    public void testPutWithAndWithoutExpiry() {
        RecordingMap recording = new RecordingMap();
        HazelcastDistributedCache cache = new HazelcastDistributedCache(
                instance(recording.proxy(), new String[1]), "cache");
        cache.put("k", "v", 0, TimeUnit.SECONDS);
        cache.put("k2", "v2", 30, TimeUnit.SECONDS);
        assertEquals(Arrays.asList("put", "put:30:SECONDS"), recording.ops);
        assertEquals("v", cache.get("k", String.class));
    }

    @Test
    public void testEvictRemovesKey() {
        RecordingMap recording = new RecordingMap();
        recording.store.put("k", "v");
        HazelcastDistributedCache cache = new HazelcastDistributedCache(
                instance(recording.proxy(), new String[1]), "cache");
        cache.evict("k");
        assertNull(cache.get("k", String.class));
        assertTrue(recording.ops.contains("evict"));
    }
}
