// Use of this source code is governed by a BSD-style license
package com.cs.cache.redis;

import org.apache.commons.pool2.BasePooledObjectFactory;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.impl.DefaultPooledObject;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.util.Pool;

import java.net.InetAddress;
import java.net.ServerSocket;
import java.util.*;

/**
 * Hand-written jedis fakes: an in-memory Jedis subclass plus a Pool that hands it out,
 * so RedisCacheMap / RedisDistributedCache run their real lambda bodies without a server.
 * A loopback ServerSocket backs the Jedis constructor because Jedis 4+ may establish the
 * TCP connection eagerly; no Redis protocol is ever spoken (all commands are overridden).
 */
public final class RedisTestSupport {

    private static final ServerSocket DUMMY_SERVER = createDummyServer();

    private RedisTestSupport() {
    }

    private static ServerSocket createDummyServer() {
        try {
            return new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
        } catch (Exception e) {
            throw new IllegalStateException("Cannot open dummy server socket for jedis fake", e);
        }
    }

    public static class FakeJedis extends Jedis {
        public final Map<String, Map<String, String>> hashes = new HashMap<>();
        public final Map<String, String> strings = new HashMap<>();
        public final List<String> expireCalls = new ArrayList<>();

        public FakeJedis() {
            super("localhost", DUMMY_SERVER.getLocalPort());
        }

        @Override
        public String ping() {
            return "PONG";
        }

        @Override
        public void close() {
            // never touch the (dummy) connection
        }

        @Override
        public long hlen(String key) {
            Map<String, String> hash = hashes.get(key);
            return null == hash ? 0L : (long) hash.size();
        }

        @Override
        public boolean hexists(String key, String field) {
            Map<String, String> hash = hashes.get(key);
            return null != hash && hash.containsKey(field);
        }

        @Override
        public String hget(String key, String field) {
            Map<String, String> hash = hashes.get(key);
            return null == hash ? null : hash.get(field);
        }

        @Override
        public long hset(String key, String field, String value) {
            hashes.computeIfAbsent(key, k -> new HashMap<String, String>()).put(field, value);
            return 1L;
        }

        @Override
        public long hdel(String key, String... fields) {
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
        public long expire(String key, long seconds) {
            expireCalls.add(key + ":" + seconds);
            return 1L;
        }

        @Override
        public long del(String key) {
            return del(new String[]{key});
        }

        @Override
        public long del(String... keys) {
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
            // Jedis 6 的 Pool 直接继承 GenericObjectPool，构造必须给工厂；
            // getResource 已覆写为直接返回 fake，工厂永远不会被实际调用
            super(new BasePooledObjectFactory<Jedis>() {
                @Override
                public Jedis create() {
                    return null;
                }

                @Override
                public PooledObject<Jedis> wrap(Jedis value) {
                    return new DefaultPooledObject<>(value);
                }
            });
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
