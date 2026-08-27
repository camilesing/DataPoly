// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.cache.redis;

import com.cs.cache.*;

import javax.annotation.Resource;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class RedisCacheFactory implements CacheFactory {

    private Map<String, RedisDistributedCache> cacheMap = new ConcurrentHashMap<>();

    @Resource
    private JedisClient jedisClient;

    @Override
    public <T> Map<String, T> getCacheMap(String key, Class<T> clazz) {
        return new RedisCacheMap<>(key, jedisClient, clazz);
    }

    @Override
    public DistributedCache getDistributedCache(String name) {
        return cacheMap.computeIfAbsent(name, key -> new RedisDistributedCache(key, jedisClient));
    }
}
