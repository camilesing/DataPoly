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
import java.util.Properties;
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
                            return true;
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
}
