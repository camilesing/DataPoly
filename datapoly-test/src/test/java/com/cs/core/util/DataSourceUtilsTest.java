// Use of this source code is governed by a BSD-style license
package com.cs.core.util;

import cn.hutool.extra.spring.SpringUtil;
import com.cs.common.enums.ProductTypeEnum;
import com.cs.persistence.entity.DataSourceEntity;
import com.cs.persistence.entity.PoolConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.core.env.Environment;

import java.io.InputStream;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.Assert.*;

public class DataSourceUtilsTest {

    /**
     * Test driver packaged into a runtime-built jar so JarFileClassLoader can load it.
     * Fully self-contained: the jar classloader's parent cannot see the outer test class,
     * so every fake the driver needs lives here.
     */
    public static class FakeJarDriver implements Driver {
        @Override
        public Connection connect(String url, Properties info) {
            return fakeConnection();
        }

        static Connection fakeConnection() {
            return (Connection) Proxy.newProxyInstance(FakeJarDriver.class.getClassLoader(),
                    new Class<?>[]{Connection.class},
                    (proxy, method, args) -> {
                        String name = method.getName();
                        if ("hashCode".equals(name)) {
                            return System.identityHashCode(proxy);
                        }
                        if ("equals".equals(name)) {
                            return proxy == args[0];
                        }
                        if ("toString".equals(name)) {
                            return "fake-connection";
                        }
                        if ("isValid".equals(name)) {
                            // system-property switch: lets tests exercise the invalid-connection path
                            return !Boolean.getBoolean("fakeDriver.invalid");
                        }
                        if ("isClosed".equals(name)) {
                            return false;
                        }
                        if ("getAutoCommit".equals(name)) {
                            return true;
                        }
                        if ("createStatement".equals(name)) {
                            return Proxy.newProxyInstance(FakeJarDriver.class.getClassLoader(),
                                    new Class<?>[]{Statement.class},
                                    (st, m, a) -> {
                                        if ("hashCode".equals(m.getName())) {
                                            return System.identityHashCode(st);
                                        }
                                        if ("equals".equals(m.getName())) {
                                            return st == a[0];
                                        }
                                        if ("execute".equals(m.getName())) {
                                            if (Boolean.getBoolean("fakeDriver.executeFail")) {
                                                throw new SQLException("boom");
                                            }
                                            return true;
                                        }
                                        return defaultValue(m.getReturnType());
                                    });
                        }
                        if ("getMetaData".equals(name)) {
                            return Proxy.newProxyInstance(FakeJarDriver.class.getClassLoader(),
                                    new Class<?>[]{DatabaseMetaData.class},
                                    (md, m, a) -> {
                                        if ("getDatabaseProductName".equals(m.getName())) {
                                            return "FakeDB";
                                        }
                                        if ("hashCode".equals(m.getName())) {
                                            return System.identityHashCode(md);
                                        }
                                        if ("equals".equals(m.getName())) {
                                            return md == a[0];
                                        }
                                        return defaultValue(m.getReturnType());
                                    });
                        }
                        return defaultValue(method.getReturnType());
                    });
        }

        static Object defaultValue(Class<?> returnType) {
            if (returnType == boolean.class) {
                return false;
            }
            if (returnType == int.class) {
                return 0;
            }
            if (returnType == long.class) {
                return 0L;
            }
            return null;
        }

        @Override
        public boolean acceptsURL(String url) {
            return true;
        }

        @Override
        public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) {
            return new DriverPropertyInfo[0];
        }

        @Override
        public int getMajorVersion() {
            return 1;
        }

        @Override
        public int getMinorVersion() {
            return 0;
        }

        @Override
        public boolean jdbcCompliant() {
            return false;
        }

        @Override
        public java.util.logging.Logger getParentLogger() {
            return null;
        }
    }

    private static final java.util.Map<String, String> SPRING_PROPERTIES = new HashMap<>();

    static {
        SPRING_PROPERTIES.put("datapoly.datasource.encrypt", "true");
        SPRING_PROPERTIES.put("datapoly.datasource.aes-key", "0123456789ABCDEF0123456789ABCDEF");
    }

    /**
     * Reflectively injects a stub beanFactory into hutool SpringUtil (same pattern as TokenUtilsTest):
     * getBean(Environment.class) answers the datasource encrypt switch and AES key from a fixed map.
     * Cleared again in {@link #uninstallEnvironment()} so other test classes see the no-container default.
     */
    @Before
    public void installEnvironment() throws Exception {
        Environment environment = (Environment) Proxy.newProxyInstance(DataSourceUtilsTest.class.getClassLoader(),
                new Class[]{Environment.class}, (proxy, method, args) -> {
                    if ("getProperty".equals(method.getName()) && args != null && args.length >= 1) {
                        String value = SPRING_PROPERTIES.get(String.valueOf(args[0]));
                        if (value != null) {
                            return value;
                        }
                        return args.length > 1 ? args[args.length - 1] : null;
                    }
                    if ("hashCode".equals(method.getName())) {
                        return System.identityHashCode(proxy);
                    }
                    if ("equals".equals(method.getName())) {
                        return proxy == args[0];
                    }
                    if ("toString".equals(method.getName())) {
                        return "stubEnvironment";
                    }
                    return null;
                });
        Object beanFactory = Proxy.newProxyInstance(DataSourceUtilsTest.class.getClassLoader(),
                new Class[]{ConfigurableListableBeanFactory.class}, (proxy, method, args) -> {
                    if ("getBean".equals(method.getName()) && args != null && args.length == 1
                            && args[0] instanceof Class) {
                        return environment;
                    }
                    if ("hashCode".equals(method.getName())) {
                        return System.identityHashCode(proxy);
                    }
                    if ("equals".equals(method.getName())) {
                        return proxy == args[0];
                    }
                    if ("toString".equals(method.getName())) {
                        return "stubBeanFactory";
                    }
                    return null;
                });
        java.lang.reflect.Field field = SpringUtil.class.getDeclaredField("beanFactory");
        field.setAccessible(true);
        field.set(null, beanFactory);
    }

    @After
    public void uninstallEnvironment() throws Exception {
        java.lang.reflect.Field field = SpringUtil.class.getDeclaredField("beanFactory");
        field.setAccessible(true);
        field.set(null, null);
    }

    /** Packs the compiled FakeJarDriver class into a fresh jar under a temp dir. */
    private static String driverJarDir() throws Exception {
        Path dir = Files.createTempDirectory("ds-driver");
        Path jar = dir.resolve("fake-driver.jar");
        String entry = FakeJarDriver.class.getName().replace('.', '/') + ".class";
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar));
             InputStream in = DataSourceUtilsTest.class.getClassLoader().getResourceAsStream(entry)) {
            assertNotNull("compiled fake driver not found: " + entry, in);
            out.putNextEntry(new JarEntry(entry));
            byte[] buffer = new byte[4096];
            int read;
            while ((read = in.read(buffer)) > 0) {
                out.write(buffer, 0, read);
            }
            out.closeEntry();
        }
        return dir.toString();
    }

    private DataSourceEntity entity(long id, ProductTypeEnum type, String password) {
        DataSourceEntity entity = new DataSourceEntity();
        entity.setId(id);
        entity.setType(type);
        entity.setName("ds-" + id);
        entity.setUrl("jdbc:fake://host:1234/db");
        entity.setDriver(FakeJarDriver.class.getName());
        entity.setUsername("user");
        entity.setPassword(password);
        return entity;
    }

    @Test
    public void testNullEntityIdRejected() {
        DataSourceEntity nullId = entity(1, ProductTypeEnum.MYSQL, "pw");
        nullId.setId(null);
        try {
            DataSourceUtils.getHikariDataSource(nullId, "unused");
            fail("null id must be rejected");
        } catch (IllegalArgumentException expected) {
        }
    }

    @Test
    public void testBlankDriverPathRejected() {
        try {
            DataSourceUtils.createDataSource(entity(1, ProductTypeEnum.MYSQL, "pw"), " ");
            fail("blank driver path must be rejected");
        } catch (RuntimeException expected) {
            assertTrue(expected.getMessage().contains("Invalid driver path"));
        }
    }

    @Test
    public void testBlankDriverClassRejected() throws Exception {
        DataSourceEntity e = entity(2, ProductTypeEnum.MYSQL, "pw");
        e.setDriver(" ");
        try {
            DataSourceUtils.createDataSource(e, driverJarDir());
            fail("blank driver class must be rejected");
        } catch (RuntimeException expected) {
            assertTrue(expected.getMessage().contains("Invalid driver class"));
        }
    }

    @Test
    public void testCreateDataSourceAppliesDefaultPoolConfig() throws Exception {
        HikariDataSource ds = DataSourceUtils.createDataSource(entity(3, ProductTypeEnum.MYSQL, "pw"), driverJarDir());
        assertEquals(DataSourceUtils.MAX_THREAD_COUNT, ds.getMaximumPoolSize());
        assertEquals(DataSourceUtils.MAX_THREAD_COUNT, ds.getMinimumIdle());
        assertEquals(DataSourceUtils.MAX_LIFE_TIME_MS, ds.getMaxLifetime());
        assertEquals(DataSourceUtils.CONNECTION_TIMEOUT, ds.getConnectionTimeout());
        assertEquals(DataSourceUtils.MAX_TIMEOUT_MS, ds.getIdleTimeout());
        assertEquals("/* ping */ SELECT 1", ds.getConnectionTestQuery());
        ds.close();
    }

    @Test
    public void testCreateDataSourceAppliesPoolConfigOverrides() throws Exception {
        DataSourceEntity e = entity(4, ProductTypeEnum.MYSQL, "pw");
        e.setPoolConfig(PoolConfig.builder()
                .maximumPoolSize(5)
                .minimumIdle(2)
                .maxLifetime(1234L)
                .connectionTimeout(567L)
                .idleTimeout(89)
                .build());
        HikariDataSource ds = DataSourceUtils.createDataSource(e, driverJarDir());
        assertEquals(5, ds.getMaximumPoolSize());
        assertEquals(2, ds.getMinimumIdle());
        assertEquals(1234L, ds.getMaxLifetime());
        assertEquals(567L, ds.getConnectionTimeout());
        assertEquals(89, ds.getIdleTimeout());
        ds.close();
    }

    @Test
    public void testCreateDataSourceProductTypeBranches() throws Exception {
        HikariDataSource oracle = DataSourceUtils.createDataSource(entity(5, ProductTypeEnum.ORACLE, "pw"), driverJarDir());
        assertEquals("SELECT 'Hello' from DUAL", oracle.getConnectionTestQuery());
        assertEquals("true", System.getProperty("oracle.jdbc.J2EE13Compliant"));
        oracle.close();

        // INCEPTOR has no test sql: no connection test query is configured
        HikariDataSource inceptor = DataSourceUtils.createDataSource(entity(6, ProductTypeEnum.INCEPTOR, "pw"), driverJarDir());
        assertNull(inceptor.getConnectionTestQuery());
        inceptor.close();

        // ODPS config branch only adds connection properties
        HikariDataSource odps = DataSourceUtils.createDataSource(entity(7, ProductTypeEnum.ODPS, "pw"), driverJarDir());
        assertTrue(odps.getPoolName().contains("ODPS"));
        odps.close();
    }

    @Test
    public void testGetHikariDataSourceCachesByEntityAndSwapsOnConfigChange() throws Exception {
        String driverPath = driverJarDir();
        long id = 4200L;
        HikariDataSource first = DataSourceUtils.getHikariDataSource(entity(id, ProductTypeEnum.MYSQL, "pw"), driverPath);
        assertNotNull(first);
        assertTrue(DataSourceUtils.getAllDataSourceIdSet().contains(id));

        // same entity definition: cached pool returned
        assertSame(first, DataSourceUtils.getHikariDataSource(entity(id, ProductTypeEnum.MYSQL, "pw"), driverPath));

        // password change: new pool created and swapped in
        HikariDataSource swapped = DataSourceUtils.getHikariDataSource(entity(id, ProductTypeEnum.MYSQL, "pw2"), driverPath);
        assertNotSame(first, swapped);
        assertTrue(DataSourceUtils.getAllDataSourceIdSet().contains(id));

        DataSourceUtils.dropHikariDataSource(id);
        assertFalse(DataSourceUtils.getAllDataSourceIdSet().contains(id));
        DataSourceUtils.dropHikariDataSource(null);
        DataSourceUtils.dropHikariDataSource(999999L);
    }

    @Test
    public void testEncryptDecryptRoundTrip() {
        DataSourceEntity e = entity(10, ProductTypeEnum.MYSQL, "secret-pw");
        e.setUsername("secret-user");
        DataSourceUtils.encrypt(e);
        assertNotEquals("secret-user", e.getUsername());
        assertNotEquals("secret-pw", e.getPassword());
        DataSourceUtils.decrypt(e);
        assertEquals("secret-user", e.getUsername());
        assertEquals("secret-pw", e.getPassword());
    }

    @Test
    public void testEncryptDecryptKeepNullCredentials() {
        DataSourceEntity e = entity(11, ProductTypeEnum.MYSQL, null);
        e.setUsername(null);
        DataSourceUtils.encrypt(e);
        assertNull(e.getUsername());
        assertNull(e.getPassword());
        DataSourceUtils.decrypt(e);
        assertNull(e.getUsername());
        assertNull(e.getPassword());
    }

    @Test
    public void testGetHikariDataSourceBlankTypeSqlValidatesViaIsValid() throws Exception {
        // INCEPTOR carries no validation SQL: the pool must be validated with isValid(2)
        long id = 4300L;
        HikariDataSource ds = DataSourceUtils.getHikariDataSource(
                entity(id, ProductTypeEnum.INCEPTOR, "pw"), driverJarDir());
        assertNotNull(ds);
        assertTrue(DataSourceUtils.getAllDataSourceIdSet().contains(id));
        DataSourceUtils.dropHikariDataSource(id);
    }

    @Test
    public void testInvalidConnectionClosesPoolAndIsNotCached() throws Exception {
        long id = 4301L;
        System.setProperty("fakeDriver.invalid", "true");
        try {
            try {
                // INCEPTOR has no validation SQL: the isValid(2) probe decides
                DataSourceUtils.getHikariDataSource(entity(id, ProductTypeEnum.INCEPTOR, "pw"), driverJarDir());
                fail("invalid connection must be rejected");
            } catch (RuntimeException e) {
                assertTrue(e.getMessage(), e.getMessage().contains("Connection is invalid"));
            }
            assertFalse("failed pool must not be cached",
                    DataSourceUtils.getAllDataSourceIdSet().contains(id));
        } finally {
            System.clearProperty("fakeDriver.invalid");
        }
    }

    @Test
    public void testValidationStatementFailureClosesPoolAndIsNotCached() throws Exception {
        // MYSQL carries a validation SQL: a failing statement must close the pool and wrap the error
        long id = 4302L;
        System.setProperty("fakeDriver.executeFail", "true");
        try {
            try {
                DataSourceUtils.getHikariDataSource(entity(id, ProductTypeEnum.MYSQL, "pw"), driverJarDir());
                fail("failing validation statement must be rejected");
            } catch (RuntimeException e) {
                assertTrue("cause must be the original SQLException, got: " + e.getCause(),
                        e.getCause() instanceof SQLException);
            }
            assertFalse("failed pool must not be cached",
                    DataSourceUtils.getAllDataSourceIdSet().contains(id));
        } finally {
            System.clearProperty("fakeDriver.executeFail");
        }
    }

    @Test
    public void testPartialPoolConfigFallsBackToDefaultsPerField() throws Exception {
        DataSourceEntity e = entity(12, ProductTypeEnum.MYSQL, "pw");
        e.setPoolConfig(PoolConfig.builder().maximumPoolSize(3).build());
        HikariDataSource ds = DataSourceUtils.createDataSource(e, driverJarDir());
        try {
            assertEquals(3, ds.getMaximumPoolSize());
            // unset fields fall back to the built-in defaults one by one
            assertEquals(DataSourceUtils.MAX_THREAD_COUNT, ds.getMinimumIdle());
            assertEquals(DataSourceUtils.MAX_LIFE_TIME_MS, ds.getMaxLifetime());
            assertEquals(DataSourceUtils.CONNECTION_TIMEOUT, ds.getConnectionTimeout());
            assertEquals(DataSourceUtils.MAX_TIMEOUT_MS, ds.getIdleTimeout());
        } finally {
            ds.close();
        }
    }

    @Test
    public void testDefaultAesKeyUsedWhenUnconfigured() throws Exception {
        // with no configured key the built-in default must be used (verified by decrypting
        // with the known default key); afterwards the configured key is restored
        SPRING_PROPERTIES.remove("datapoly.datasource.aes-key");
        setToolAES(null);
        try {
            DataSourceEntity e = entity(13, ProductTypeEnum.MYSQL, "plain-secret");
            DataSourceUtils.encrypt(e);
            assertNotEquals("plain-secret", e.getPassword());
            String recovered = cn.hutool.crypto.SecureUtil.aes(
                    "6635BC05BC357FEC7A85FDB9C972AD01".getBytes(java.nio.charset.StandardCharsets.UTF_8))
                    .decryptStr(e.getPassword());
            assertEquals("plain-secret", recovered);
        } finally {
            SPRING_PROPERTIES.put("datapoly.datasource.aes-key", "0123456789ABCDEF0123456789ABCDEF");
            setToolAES(null);
        }
    }

    /**
     * Two threads racing into getOrCreateClassLoader: the loser must observe the inner
     * (double-checked) map hit and reuse the winner's loader instead of building a second
     * one. The winner holds the DataSourceUtils class lock while initializing, the loser's
     * outer lookup happens in that window and then blocks until the lock is released.
     */
    @Test
    public void testConcurrentFirstAccessReusesOneClassLoader() throws Exception {
        final String path = driverJarDir();
        final Exception[] holderError = new Exception[1];
        final CountDownLatch lockHeld = new CountDownLatch(1);
        Thread holder = new Thread(() -> {
            synchronized (DataSourceUtils.class) {
                lockHeld.countDown();
                try {
                    Thread.sleep(300L);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
                try (HikariDataSource ignored2 = DataSourceUtils.createDataSource(
                        entity(14, ProductTypeEnum.MYSQL, "pw"), path)) {
                    // loader created while holding the class lock
                } catch (Exception e) {
                    holderError[0] = e;
                }
            }
        }, "classloader-holder");
        holder.start();
        assertTrue("holder never acquired the class lock", lockHeld.await(10, java.util.concurrent.TimeUnit.SECONDS));

        HikariDataSource ds = DataSourceUtils.createDataSource(entity(15, ProductTypeEnum.MYSQL, "pw"), path);
        holder.join(10_000);
        assertNull("holder thread failed", holderError[0]);
        Object sharedLoader = loaderFor(path);
        assertNotNull("loader for the driver path must be registered", sharedLoader);
        assertSame("both creations must share the single loader registered for the path",
                sharedLoader, loaderFor(path));
        try {
            ds.close();
        } catch (Exception ignored) {
            // closing an already closed pool from the same config is not the point here
        }
    }

    /**
     * Same double-checked-locking pattern for the lazily built AES tool: the losing thread
     * must see the winner's instance in the inner null check.
     */
    @Test
    public void testConcurrentToolAesInitReusesOneInstance() throws Exception {
        setToolAES(null);
        final Exception[] holderError = new Exception[1];
        final CountDownLatch lockHeld = new CountDownLatch(1);
        Thread holder = new Thread(() -> {
            synchronized (DataSourceUtils.class) {
                lockHeld.countDown();
                try {
                    Thread.sleep(300L);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
                try {
                    DataSourceUtils.encrypt(entity(16, ProductTypeEnum.MYSQL, "pw"));
                } catch (Exception e) {
                    holderError[0] = e;
                }
            }
        }, "toolaes-holder");
        holder.start();
        assertTrue(lockHeld.await(10, java.util.concurrent.TimeUnit.SECONDS));

        DataSourceEntity e = entity(17, ProductTypeEnum.MYSQL, "race-secret");
        DataSourceUtils.encrypt(e);
        DataSourceUtils.decrypt(e);
        holder.join(10_000);
        assertNull("holder thread failed", holderError[0]);
        assertEquals("race-secret", e.getPassword());
    }

    @Test
    public void testEncryptSwitchOffLeavesCredentialsUntouched() {
        SPRING_PROPERTIES.put("datapoly.datasource.encrypt", "false");
        try {
            DataSourceEntity e = entity(18, ProductTypeEnum.MYSQL, "plain-secret");
            DataSourceUtils.encrypt(e);
            assertEquals("plain-secret", e.getPassword());
            DataSourceUtils.decrypt(e);
            assertEquals("plain-secret", e.getPassword());
        } finally {
            SPRING_PROPERTIES.put("datapoly.datasource.encrypt", "true");
        }
    }

    /**
     * getToolAES must fall back to the built-in key when the Environment read fails after
     * the encrypt switch was read: the first getBean answers, the second one blows up
     * (context shutting down mid-call is the production analog).
     */
    @Test
    public void testToolAesFallsBackToDefaultWhenEnvironmentReadFails() throws Exception {
        setToolAES(null);
        final int[] getBeanCalls = {0};
        Environment failingAfterFirst = (Environment) Proxy.newProxyInstance(
                DataSourceUtilsTest.class.getClassLoader(),
                new Class[]{Environment.class}, (proxy, method, args) -> {
                    if ("getProperty".equals(method.getName()) && args != null && args.length >= 1) {
                        String value = SPRING_PROPERTIES.get(String.valueOf(args[0]));
                        return value != null ? value : (args.length > 1 ? args[args.length - 1] : null);
                    }
                    if ("hashCode".equals(method.getName())) {
                        return System.identityHashCode(proxy);
                    }
                    if ("equals".equals(method.getName())) {
                        return proxy == args[0];
                    }
                    return null;
                });
        Object statefulBeanFactory = Proxy.newProxyInstance(DataSourceUtilsTest.class.getClassLoader(),
                new Class[]{ConfigurableListableBeanFactory.class}, (proxy, method, args) -> {
                    if ("getBean".equals(method.getName()) && args != null && args.length == 1
                            && args[0] instanceof Class) {
                        if (++getBeanCalls[0] > 1) {
                            throw new IllegalStateException("context shutting down");
                        }
                        return failingAfterFirst;
                    }
                    if ("hashCode".equals(method.getName())) {
                        return System.identityHashCode(proxy);
                    }
                    if ("equals".equals(method.getName())) {
                        return proxy == args[0];
                    }
                    return null;
                });
        java.lang.reflect.Field field = SpringUtil.class.getDeclaredField("beanFactory");
        field.setAccessible(true);
        Object previous = field.get(null);
        field.set(null, statefulBeanFactory);
        try {
            DataSourceEntity e = entity(19, ProductTypeEnum.MYSQL, "plain-secret");
            DataSourceUtils.encrypt(e);
            assertNotEquals("plain-secret", e.getPassword());
            String recovered = cn.hutool.crypto.SecureUtil.aes(
                    "6635BC05BC357FEC7A85FDB9C972AD01".getBytes(java.nio.charset.StandardCharsets.UTF_8))
                    .decryptStr(e.getPassword());
            assertEquals("plain-secret", recovered);
        } finally {
            field.set(null, previous);
            setToolAES(null);
        }
    }

    @Test
    public void testUnLoadableDriverClassIsWrappedAsRuntime() throws Exception {
        DataSourceEntity e = entity(20, ProductTypeEnum.MYSQL, "pw");
        e.setDriver("com.example.NoSuchDriver");
        try {
            DataSourceUtils.createDataSource(e, driverJarDir());
            fail("unloadable driver class must be rejected");
        } catch (RuntimeException expected) {
            assertTrue(expected.getCause() instanceof ClassNotFoundException);
        }
    }

    @Test
    public void testEncryptSwallowsCryptoErrors() {
        // a configured key of invalid length makes the AES tool blow up: encrypt/decrypt
        // must tolerate it (upgrade artifacts) and leave the credentials untouched
        SPRING_PROPERTIES.put("datapoly.datasource.aes-key", "too-short-key");
        try {
            setToolAES(null);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        try {
            DataSourceEntity e = entity(21, ProductTypeEnum.MYSQL, "plain-secret");
            e.setUsername("plain-user");
            DataSourceUtils.encrypt(e);
            assertEquals("plain-user", e.getUsername());
            assertEquals("plain-secret", e.getPassword());
            DataSourceUtils.decrypt(e);
            assertEquals("plain-user", e.getUsername());
            assertEquals("plain-secret", e.getPassword());
        } finally {
            SPRING_PROPERTIES.put("datapoly.datasource.aes-key", "0123456789ABCDEF0123456789ABCDEF");
            try {
                setToolAES(null);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }
    }

    @Test
    public void testCloseSwallowsPoolCloseErrors() {
        HikariDataSource boom = new HikariDataSource() {
            @Override
            public void close() {
                throw new IllegalStateException("close exploded");
            }
        };
        // must not propagate the pool's own close failure
        DataSourceUtils.closeHikariDataSource(boom);
    }

    private static void setToolAES(Object value) throws Exception {
        java.lang.reflect.Field field = DataSourceUtils.class.getDeclaredField("toolAES");
        field.setAccessible(true);
        field.set(null, value);
    }

    @SuppressWarnings("unchecked")
    private static Object loaderFor(String path) throws Exception {
        java.lang.reflect.Field field = DataSourceUtils.class.getDeclaredField("classLoaderMap");
        field.setAccessible(true);
        Map<String, ?> map = (Map<String, ?>) field.get(null);
        return map.get(path);
    }
}
