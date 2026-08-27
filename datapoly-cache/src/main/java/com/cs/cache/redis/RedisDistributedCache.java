// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.cache.redis;

import cn.hutool.json.JSONUtil;
import com.cs.cache.DistributedCache;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

public class RedisDistributedCache implements DistributedCache {

    private static final String FORMAT = "%s#%s";

    private final String name;
    private final JedisClient client;

    public RedisDistributedCache(String name, JedisClient client) {
        this.name = Objects.requireNonNull(name, "cache name must not be null");
        this.client = Objects.requireNonNull(client, "client must not be null");
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public <T> T get(String key, Class<T> type) {
        String redisKey = String.format(FORMAT, name, key);
        String redisVal = client.doAction(jedis -> jedis.get(redisKey));
        if (null == redisVal) {
            return null;
        }
        return JSONUtil.toBean(redisVal, type, true);
    }

    @Override
    public void put(String key, Object value, long expire, TimeUnit unit) {
        String redisKey = String.format(FORMAT, name, key);
        String redisVal = JSONUtil.toJsonStr(value);
        client.doConsume(jedis -> {
            jedis.set(redisKey, redisVal);
            if (expire > 0) {
                jedis.expire(redisKey, unit.toSeconds(expire));
            }
        });
    }

    @Override
    public void evict(String key) {
        String redisKey = String.format(FORMAT, name, key);
        client.doConsume(jedis -> {
            jedis.del(redisKey);
        });
    }
}
