// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.cache.redis;

import cn.hutool.json.JSONUtil;

import java.util.*;

public class RedisCacheMap<V> implements Map<String, V> {

    private final String hashTableKey;
    private final JedisClient jedisClient;
    private final Class<V> valueClazz;

    public RedisCacheMap(String hashTableKey, JedisClient jedisClient, Class<V> clazz) {
        this.hashTableKey = hashTableKey;
        this.jedisClient = jedisClient;
        this.valueClazz = clazz;
    }

    @Override
    public int size() {
        Long size = jedisClient.doAction(
                jedis -> jedis.hlen(hashTableKey)
        );
        return null == size ? 0 : size.intValue();
    }

    @Override
    public boolean isEmpty() {
        return 0 == size();
    }

    @Override
    public boolean containsKey(Object o) {
        return jedisClient.doAction(
                jedis -> jedis.hexists(hashTableKey, o.toString())
        );
    }

    @Override
    public boolean containsValue(Object o) {
        return snapshot().containsValue(o);
    }

    @Override
    public V get(Object o) {
        return jedisClient.doAction(
                jedis -> {
                    String value = jedis.hget(hashTableKey, o.toString());
                    return JSONUtil.toBean(value, valueClazz, true);
                }
        );
    }

    @Override
    public V put(String k, V v) {
        return jedisClient.doAction(
                jedis -> {
                    String value = jedis.hget(hashTableKey, k);
                    jedis.hset(hashTableKey, k, JSONUtil.toJsonStr(v));
                    return JSONUtil.toBean(value, valueClazz, true);
                }
        );
    }

    @Override
    public V remove(Object o) {
        return jedisClient.doAction(
                jedis -> {
                    String value = jedis.hget(hashTableKey, o.toString());
                    jedis.hdel(hashTableKey, o.toString());
                    return JSONUtil.toBean(value, valueClazz, true);
                }
        );
    }

    @Override
    public void putAll(Map<? extends String, ? extends V> map) {
        if (null == map || map.isEmpty()) {
            // HMSET with zero pairs is a protocol error on the Redis side
            return;
        }
        Map<String, String> values = new HashMap<>();
        map.forEach((k, v) -> values.put(k, JSONUtil.toJsonStr(v)));
        jedisClient.doConsume(
                jedis -> jedis.hmset(hashTableKey, values)
        );
    }

    @Override
    public void clear() {
        jedisClient.doConsume(
                jedis -> jedis.del(hashTableKey)
        );
    }

    @Override
    public Set<String> keySet() {
        return jedisClient.doAction(
                jedis -> jedis.hkeys(hashTableKey)
        );
    }

    @Override
    public Collection<V> values() {
        return snapshot().values();
    }

    @Override
    public Set<Entry<String, V>> entrySet() {
        return snapshot().entrySet();
    }

    /**
     * Full in-memory copy of the hash — backs the value-view operations that have no Redis-side shortcut.
     */
    private Map<String, V> snapshot() {
        Map<String, String> raw = jedisClient.doAction(
                jedis -> jedis.hgetAll(hashTableKey)
        );
        Map<String, V> result = new LinkedHashMap<>(null == raw ? 4 : raw.size());
        if (null != raw) {
            raw.forEach((k, v) -> result.put(k, JSONUtil.toBean(v, valueClazz, true)));
        }
        return result;
    }
}
