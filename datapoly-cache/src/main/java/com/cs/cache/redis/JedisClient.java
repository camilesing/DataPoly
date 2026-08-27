// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.cache.redis;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.util.Pool;

import java.util.function.*;

public class JedisClient {

    private Pool<Jedis> jedisPool;

    public JedisClient(Pool<Jedis> jedisPool) {
        this.jedisPool = jedisPool;
    }

    private Jedis getJedis() {
        Jedis jedis = this.jedisPool.getResource();
        jedis.ping();
        return jedis;
    }

    public <T> T doAction(Function<Jedis, T> function) {
        Jedis jedis = getJedis();
        try {
            return function.apply(jedis);
        } finally {
            jedis.close();
        }
    }

    public void doConsume(Consumer<Jedis> action) {
        Jedis jedis = getJedis();
        try {
            action.accept(jedis);
        } finally {
            jedis.close();
        }
    }
}
