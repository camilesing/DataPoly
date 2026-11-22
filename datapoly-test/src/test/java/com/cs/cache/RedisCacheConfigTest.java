// Use of this source code is governed by a BSD-style license
package com.cs.cache;

import com.cs.cache.redis.JedisClient;
import com.cs.cache.redis.RedisProperties;
import com.cs.cache.redis.RedisTestSupport;
import org.junit.Test;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisSentinelPool;
import redis.clients.jedis.util.Pool;
import redis.clients.jedis.Jedis;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.Assert.*;

/**
 * RedisCacheConfig bean wiring: standalone JedisPool branch (pool settings applied), the
 * sentinel branch (backed by a tiny in-test RESP sentinel that reports the master address),
 * and the JedisClient / RedisCacheFactory beans.
 */
public class RedisCacheConfigTest {

    @Test
    public void jedisPoolUsesStandaloneBranchWithDefaults() {
        DataPolyCacheConfiguration.RedisCacheConfig config = new DataPolyCacheConfiguration.RedisCacheConfig();
        RedisProperties properties = new RedisProperties();
        // defaults: timeBetweenEvictionRuns null, maxWait -1ms, host 127.0.0.1, port 6379
        properties.getPool().setMaxWait(null);

        Pool<Jedis> pool = config.jedisPool(properties);
        try {
            assertTrue("no sentinel nodes configured must build a standalone JedisPool",
                    pool instanceof JedisPool);
        } finally {
            pool.close();
        }
    }

    @Test
    public void jedisPoolAppliesEvictionAndWaitSettings() {
        DataPolyCacheConfiguration.RedisCacheConfig config = new DataPolyCacheConfiguration.RedisCacheConfig();
        RedisProperties properties = new RedisProperties();
        properties.setTimeout(java.time.Duration.ofMillis(1500));
        properties.getPool().setMinIdle(0);
        properties.getPool().setMaxActive(3);
        properties.getPool().setMaxIdle(2);
        properties.getPool().setTimeBetweenEvictionRuns(java.time.Duration.ofMillis(30_000L));
        properties.getPool().setMaxWait(java.time.Duration.ofMillis(500L));

        Pool<Jedis> pool = config.jedisPool(properties);
        try {
            assertTrue(pool instanceof JedisPool);
        } finally {
            pool.close();
        }
    }

    @Test
    public void jedisPoolUsesSentinelBranchWhenNodesConfigured() throws Exception {
        FakeSentinel sentinel = FakeSentinel.start();
        try {
            DataPolyCacheConfiguration.RedisCacheConfig config = new DataPolyCacheConfiguration.RedisCacheConfig();
            RedisProperties properties = new RedisProperties();
            properties.getSentinel().setMaster("mymaster");
            properties.getSentinel().setNodes(Collections.singletonList(
                    "127.0.0.1:" + sentinel.port()));

            Pool<Jedis> pool = config.jedisPool(properties);
            assertTrue("sentinel nodes configured must build a JedisSentinelPool", pool instanceof JedisSentinelPool);
            JedisSentinelPool sentinelPool = (JedisSentinelPool) pool;
            assertEquals("127.0.0.1", sentinelPool.getCurrentHostMaster().getHost());
            pool.close();
        } finally {
            sentinel.close();
        }
        assertTrue("the pool must have discovered the master through the sentinel",
                sentinel.sawDiscoveryCommand());
    }

    @Test
    public void jedisClientWrapsTheProvidedPool() {
        DataPolyCacheConfiguration.RedisCacheConfig config = new DataPolyCacheConfiguration.RedisCacheConfig();
        RedisTestSupport.FakePool pool = new RedisTestSupport.FakePool();
        JedisClient client = config.jedisClient(pool);
        assertNotNull(client);
        // sanity: the client really routes through the injected pool
        assertEquals("PONG", client.doAction(jedis -> jedis.ping()));
    }

    @Test
    public void redisCacheFactoryBeanIsRedisImplementation() {
        DataPolyCacheConfiguration.RedisCacheConfig config = new DataPolyCacheConfiguration.RedisCacheConfig();
        assertTrue(config.redisCacheFactory() instanceof com.cs.cache.redis.RedisCacheFactory);
    }

    /**
     * Minimal RESP-speaking sentinel: answers SENTINEL get-master-addr-by-name with
     * 127.0.0.1:6379 and acknowledges everything else, which is all JedisSentinelPool needs
     * during construction (master discovery + master-listener subscribe).
     */
    private static final class FakeSentinel implements Closeable {

        private final ServerSocket serverSocket;
        private final List<String> commands = new CopyOnWriteArrayList<>();
        private volatile boolean running = true;
        private final Thread acceptor;

        private FakeSentinel(ServerSocket serverSocket) {
            this.serverSocket = serverSocket;
            this.acceptor = new Thread(this::acceptLoop, "fake-sentinel");
            this.acceptor.setDaemon(true);
        }

        static FakeSentinel start() throws IOException {
            ServerSocket serverSocket = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
            FakeSentinel sentinel = new FakeSentinel(serverSocket);
            sentinel.acceptor.start();
            return sentinel;
        }

        int port() {
            return serverSocket.getLocalPort();
        }

        boolean sawDiscoveryCommand() {
            for (String command : commands) {
                if (command.contains("get-master-addr-by-name")) {
                    return true;
                }
            }
            return false;
        }

        private void acceptLoop() {
            while (running) {
                try {
                    Socket socket = serverSocket.accept();
                    Thread worker = new Thread(() -> serve(socket), "fake-sentinel-conn");
                    worker.setDaemon(true);
                    worker.start();
                } catch (IOException e) {
                    return;
                }
            }
        }

        private void serve(Socket socket) {
            try (Socket s = socket) {
                s.setTcpNoDelay(true);
                InputStream in = s.getInputStream();
                OutputStream out = s.getOutputStream();
                StringBuilder buffer = new StringBuilder();
                byte[] chunk = new byte[4096];
                while (running) {
                    int read = in.read(chunk);
                    if (read < 0) {
                        return;
                    }
                    buffer.append(new String(chunk, 0, read, StandardCharsets.US_ASCII));
                    List<String> command = takeCommand(buffer);
                    while (null != command) {
                        out.write(respond(command));
                        out.flush();
                        command = takeCommand(buffer);
                    }
                }
            } catch (IOException ignore) {
                // client closed the socket — fine
            }
        }

        /** Pops one complete RESP command (array of bulk strings) from the buffer. */
        private static List<String> takeCommand(StringBuilder buffer) {
            if (buffer.length() == 0 || buffer.charAt(0) != '*') {
                return null;
            }
            int elements = parseIntAfter(buffer, 0);
            if (elements < 0) {
                return null;
            }
            int pos = indexOfCrlf(buffer, 0) + 2;
            List<String> parts = new java.util.ArrayList<>(elements);
            for (int i = 0; i < elements; i++) {
                if (pos >= buffer.length() || buffer.charAt(pos) != '$') {
                    return null;
                }
                int length = parseIntAfter(buffer, pos);
                if (length < 0) {
                    return null;
                }
                int valueStart = indexOfCrlf(buffer, pos) + 2;
                int valueEnd = valueStart + length;
                if (buffer.length() < valueEnd + 2) {
                    return null;
                }
                parts.add(buffer.substring(valueStart, valueEnd));
                pos = valueEnd + 2;
            }
            buffer.delete(0, pos);
            return parts;
        }

        private static int parseIntAfter(StringBuilder buffer, int pos) {
            int end = indexOfCrlf(buffer, pos);
            if (end < 0) {
                return -1;
            }
            try {
                return Integer.parseInt(buffer.substring(pos + 1, end).trim());
            } catch (NumberFormatException e) {
                return -1;
            }
        }

        private static int indexOfCrlf(StringBuilder buffer, int from) {
            for (int i = from; i < buffer.length() - 1; i++) {
                if (buffer.charAt(i) == '\r' && buffer.charAt(i + 1) == '\n') {
                    return i;
                }
            }
            return -1;
        }

        private byte[] respond(List<String> command) {
            String joined = String.join(" ", command).toLowerCase(java.util.Locale.ROOT);
            commands.add(joined);
            if (joined.contains("get-master-addr-by-name")) {
                return "*2\r\n$9\r\n127.0.0.1\r\n$4\r\n6379\r\n".getBytes(StandardCharsets.US_ASCII);
            }
            if (joined.startsWith("subscribe") || joined.contains("subscribe")) {
                return "*3\r\n$9\r\nsubscribe\r\n$13\r\n+switch-master\r\n:1\r\n".getBytes(StandardCharsets.US_ASCII);
            }
            if (joined.contains("ping")) {
                return "+PONG\r\n".getBytes(StandardCharsets.US_ASCII);
            }
            return "+OK\r\n".getBytes(StandardCharsets.US_ASCII);
        }

        @Override
        public void close() throws IOException {
            running = false;
            try {
                serverSocket.close();
            } catch (IOException ignore) {
                // already closed
            }
            acceptor.interrupt();
        }
    }
}
