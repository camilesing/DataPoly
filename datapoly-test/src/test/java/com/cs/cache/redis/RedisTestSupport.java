// Use of this source code is governed by a BSD-style license
package com.cs.cache.redis;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.util.Pool;

import java.util.*;

/**
 * Hand-written jedis fakes: an in-memory Jedis subclass plus a Pool that hands it out,
 * so RedisCacheMap / RedisDistributedCache run their real lambda bodies without a server.
 */
public final class RedisTestSupport {

    private RedisTestSupport() {
    }

    public static class FakeJedis extends Jedis {
        public final Map<String, Map<String, String>> hashes = new HashMap<>();
        public final Map<String, String> strings = new HashMap<>();
        public final List<String> expireCalls = new ArrayList<>();

        public FakeJedis() {
            super("localhost", 1);
        }

        @Override
        public String ping() {
            return "PONG";
        }

        @Override
        public void close() {
            // never touch the (nonexistent) connection
        }

        @Override
        public Long hlen(String key) {
            Map<String, String> hash = hashes.get(key);
            return null == hash ? 0L : (long) hash.size();
        }

        @Override
        public Boolean hexists(String key, String field) {
            Map<String, String> hash = hashes.get(key);
            return null != hash && hash.containsKey(field);
        }

        @Override
        public String hget(String key, String field) {
            Map<String, String> hash = hashes.get(key);
            return null == hash ? null : hash.get(field);
        }

        @Override
        public Long hset(String key, String field, String value) {
            hashes.computeIfAbsent(key, k -> new HashMap<String, String>()).put(field, value);
            return 1L;
        }

        @Override
        public Long hdel(String key, String... fields) {
            Map<String, String> hash = hashes.get(key);
            if (null == hash) {
                return 0L;
            }
            long removed = 0;
            for (String field : fields) {
                if (null != hash.remove(field)) {
                    removed++;
                }
            }
            return removed;
        }

        @Override
        public String hmset(String key, Map<String, String> map) {
            hashes.computeIfAbsent(key, k -> new HashMap<String, String>()).putAll(map);
            return "OK";
        }

        @Override
        public Set<String> hkeys(String key) {
            Map<String, String> hash = hashes.get(key);
            return null == hash ? new HashSet<String>() : new HashSet<>(hash.keySet());
        }

        @Override
        public Map<String, String> hgetAll(String key) {
            Map<String, String> hash = hashes.get(key);
            return null == hash ? new HashMap<String, String>() : new HashMap<>(hash);
        }

        @Override
        public String get(String key) {
            return strings.get(key);
        }

        @Override
        public String set(String key, String value) {
            strings.put(key, value);
            return "OK";
        }

        @Override
        public Long expire(String key, long seconds) {
            expireCalls.add(key + ":" + seconds);
            return 1L;
        }

        @Override
        public Long del(String key) {
            return del(new String[]{key});
        }

        @Override
        public Long del(String... keys) {
            long removed = 0;
            for (String key : keys) {
                if (null != hashes.remove(key)) {
                    removed++;
                }
                if (null != strings.remove(key)) {
                    removed++;
                }
            }
            return removed;
        }
    }

    public static class FakePool extends Pool<Jedis> {
        public final FakeJedis jedis = new FakeJedis();

        public FakePool() {
            super();
        }

        @Override
        public Jedis getResource() {
            return jedis;
        }

        @Override
        public void close() {
            // pool holds no real resources
        }

        @Override
        public void destroy() {
            // pool holds no real resources
        }
    }
}
