// Use of this source code is governed by a BSD-style license
package com.cs.common.model;

import org.junit.Before;
import org.junit.Test;

import java.sql.Connection;
import java.sql.Driver;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Properties;
import java.lang.reflect.Proxy;

import static org.junit.Assert.*;

public class SimpleDataSourceTest {

    /**
     * Hand-written fake driver loaded by name through the data source's classloader.
     * doGetConnection instantiates a fresh driver on every getConnection call, so the
     * observed state must live in static fields.
     */
    public static class FakeDriver implements Driver {
        static Connection next;
        static String lastUrl;
        static Properties lastInfo;

        @Override
        public Connection connect(String url, Properties info) {
            lastUrl = url;
            lastInfo = info;
            return next;
        }

        @Override
        public boolean acceptsURL(String url) {
            return true;
        }

        @Override
        public java.sql.DriverPropertyInfo[] getPropertyInfo(String url, Properties info) {
            return new java.sql.DriverPropertyInfo[0];
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

    private static Connection fakeConnection() {
        return (Connection) Proxy.newProxyInstance(SimpleDataSourceTest.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                (proxy, method, args) -> "toString".equals(method.getName()) ? "fake-connection" : null);
    }

    @Before
    public void resetFakeDriver() {
        FakeDriver.next = null;
        FakeDriver.lastUrl = null;
        FakeDriver.lastInfo = null;
    }

    private SimpleDataSource newDataSource() {
        Properties props = new Properties();
        return new SimpleDataSource(getClass().getClassLoader(), "jdbc:fake://host:1234/db",
                FakeDriver.class.getName(), "default-user", "default-pass", props);
    }

    @Test
    public void testConstructorAppliesCredentialDefaults() {
        Properties props = new Properties();
        SimpleDataSource ds = new SimpleDataSource(getClass().getClassLoader(), "jdbc:fake://h/db",
                FakeDriver.class.getName(), "u1", "p1", props);
        assertEquals("u1", ds.getProperties().getProperty("user"));
        assertEquals("p1", ds.getProperties().getProperty("password"));
        assertEquals("jdbc:fake://h/db", ds.getJdbcUrl());
        assertEquals(FakeDriver.class.getName(), ds.getDriverClassName());
    }

    @Test
    public void testConstructorRejectsNulls() {
        Properties props = new Properties();
        String url = "jdbc:fake://h/db";
        String driver = FakeDriver.class.getName();
        ClassLoader cl = getClass().getClassLoader();
        assertNpe(() -> new SimpleDataSource(null, url, driver, null, null, props));
        assertNpe(() -> new SimpleDataSource(cl, null, driver, null, null, props));
        assertNpe(() -> new SimpleDataSource(cl, url, null, null, null, props));
        assertNpe(() -> new SimpleDataSource(cl, url, driver, null, null, null));
    }

    private void assertNpe(Runnable construction) {
        try {
            construction.run();
            fail("expected NullPointerException");
        } catch (NullPointerException expected) {
        }
    }

    @Test
    public void testGetConnectionDelegatesToDriverWithDefaultCredentials() throws SQLException {
        SimpleDataSource ds = newDataSource();
        Connection connection = fakeConnection();
        FakeDriver.next = connection;

        assertSame(connection, ds.getConnection());
        assertEquals("jdbc:fake://host:1234/db", FakeDriver.lastUrl);
        assertEquals("default-user", FakeDriver.lastInfo.getProperty("user"));
        assertEquals("default-pass", FakeDriver.lastInfo.getProperty("password"));
    }

    @Test
    public void testGetConnectionWithCredentialsOverridesPropertiesClone() throws SQLException {
        SimpleDataSource ds = newDataSource();
        FakeDriver.next = fakeConnection();

        ds.getConnection("override-user", "override-pass");
        assertEquals("override-user", FakeDriver.lastInfo.getProperty("user"));
        assertEquals("override-pass", FakeDriver.lastInfo.getProperty("password"));
        // original properties untouched (clone semantics)
        assertEquals("default-user", ds.getProperties().getProperty("user"));
    }

    @Test
    public void testNullConnectionFromDriverRaisesSqlException() {
        SimpleDataSource ds = newDataSource();
        FakeDriver.next = null;
        try {
            ds.getConnection();
            fail("driver returning null must surface as SQLException");
        } catch (SQLException expected) {
            assertTrue(expected.getMessage().contains("Maybe invalid driver class name"));
        }
    }

    @Test
    public void testUnknownDriverClassRaisesSqlException() {
        Properties props = new Properties();
        SimpleDataSource ds = new SimpleDataSource(getClass().getClassLoader(), "jdbc:fake://h/db",
                "com.example.no.SuchDriver", null, null, props);
        try {
            ds.getConnection();
            fail("unknown driver must surface as SQLException");
        } catch (SQLException expected) {
            assertTrue(expected.getMessage().contains("Invalid driver class name"));
        }
    }

    @Test
    public void testUnsupportedDataSourceOperations() throws SQLException {
        SimpleDataSource ds = newDataSource();
        assertFalse(ds.isWrapperFor(Connection.class));
        try {
            ds.unwrap(Connection.class);
            fail("unwrap must not be supported");
        } catch (SQLFeatureNotSupportedException expected) {
        }
        try {
            ds.getLogWriter();
            fail("getLogWriter must not be supported");
        } catch (SQLFeatureNotSupportedException expected) {
        }
        try {
            ds.setLogWriter(null);
            fail("setLogWriter must not be supported");
        } catch (SQLFeatureNotSupportedException expected) {
        }
        try {
            ds.getParentLogger();
            fail("getParentLogger must not be supported");
        } catch (SQLFeatureNotSupportedException expected) {
        }
    }

    @Test
    public void testLoginTimeoutDelegatesToDriverManager() throws SQLException {
        SimpleDataSource ds = newDataSource();
        try {
            ds.setLoginTimeout(7);
            assertEquals(7, ds.getLoginTimeout());
        } finally {
            ds.setLoginTimeout(0);
        }
    }
}
