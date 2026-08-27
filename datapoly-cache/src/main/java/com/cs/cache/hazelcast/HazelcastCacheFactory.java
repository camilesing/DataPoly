// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.cache.hazelcast;

import com.cs.cache.*;
import com.hazelcast.core.HazelcastInstance;

import javax.annotation.Resource;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class HazelcastCacheFactory implements CacheFactory {

    private Map<String, HazelcastDistributedCache> cacheMap = new ConcurrentHashMap<>();

    @Resource
    private HazelcastInstance hazelcastInstance;

    @Override
    public <T> Map<String, T> getCacheMap(String key, Class<T> clazz) {
        return hazelcastInstance.getMap(key);
    }

    @Override
    public DistributedCache getDistributedCache(String name) {
        return cacheMap.computeIfAbsent(name, key -> new HazelcastDistributedCache(hazelcastInstance, key));
    }
}
