// Use of this source code is governed by a BSD-style license
package com.cs.common.util;

import org.junit.Test;

import java.net.ServerSocket;
import java.util.regex.Matcher;

import static org.junit.Assert.*;

public class JdbcUrlUtilsTest {

    @Test
    public void testMysqlUrlWithAllSegments() {
        Matcher matcher = JdbcUrlUtils.getPattern("jdbc:mysql://{host}[:{port}]/[{database}][\\?{params}]")
                .matcher("jdbc:mysql://localhost:3306/test_demo?useUnicode=true&useSSL=false");
        assertTrue(matcher.matches());
        assertEquals("localhost", matcher.group("host"));
        assertEquals("3306", matcher.group("port"));
        assertEquals("test_demo", matcher.group("database"));
        assertEquals("useUnicode=true&useSSL=false", matcher.group("params"));
    }

    @Test
    public void testMysqlUrlWithoutOptionalSegments() {
        Matcher matcher = JdbcUrlUtils.getPattern("jdbc:mysql://{host}[:{port}]/[{database}][\\?{params}]")
                .matcher("jdbc:mysql://dbhost/mysql");
        assertTrue(matcher.matches());
        assertEquals("dbhost", matcher.group("host"));
        assertNull(matcher.group("port"));
        assertEquals("mysql", matcher.group("database"));
        assertNull(matcher.group("params"));
    }

    @Test
    public void testMysqlUrlRejectsForeignPrefix() {
        Matcher matcher = JdbcUrlUtils.getPattern("jdbc:mysql://{host}[:{port}]/[{database}][\\?{params}]")
                .matcher("jdbc:mariadb://localhost:3306/mysql");
        assertFalse(matcher.matches());
    }

    @Test
    public void testPostgresUrlWithoutDatabase() {
        // the "/" before [database] is mandatory; only the database itself is optional
        Matcher matcher = JdbcUrlUtils.getPattern("jdbc:postgresql://{host}[:{port}]/[{database}][\\?{params}]")
                .matcher("jdbc:postgresql://localhost:5432/");
        assertTrue(matcher.matches());
        assertEquals("localhost", matcher.group("host"));
        assertEquals("5432", matcher.group("port"));
        assertNull(matcher.group("database"));
    }

    @Test
    public void testOracleSidUrl() {
        Matcher matcher = JdbcUrlUtils.getPattern("jdbc:oracle:thin:@{host}[:{port}]:{sid}")
                .matcher("jdbc:oracle:thin:@localhost:1521:orcl");
        assertTrue(matcher.matches());
        assertEquals("localhost", matcher.group("host"));
        assertEquals("1521", matcher.group("port"));
        assertEquals("orcl", matcher.group("sid"));
    }

    @Test
    public void testOracleServiceNameUrl() {
        Matcher matcher = JdbcUrlUtils.getPattern("jdbc:oracle:thin:@//{host}[:{port}]/{name}")
                .matcher("jdbc:oracle:thin:@//localhost:1521/orcl.city.com");
        assertTrue(matcher.matches());
        assertEquals("localhost", matcher.group("host"));
        assertEquals("orcl.city.com", matcher.group("name"));
    }

    @Test
    public void testSqlServerUrlWithParams() {
        Matcher matcher = JdbcUrlUtils.getPattern("jdbc:sqlserver://{host}[:{port}][;DatabaseName={database}][;{params}]")
                .matcher("jdbc:sqlserver://localhost:1433;DatabaseName=中文;user=MyUserName");
        assertTrue(matcher.matches());
        assertEquals("localhost", matcher.group("host"));
        assertEquals("1433", matcher.group("port"));
        assertEquals("中文", matcher.group("database"));
        assertEquals("user=MyUserName", matcher.group("params"));
    }

    @Test
    public void testSqliteFileUrl() {
        Matcher matcher = JdbcUrlUtils.getPattern("jdbc:sqlite:{file}")
                .matcher("jdbc:sqlite:/tmp/phone.db");
        assertTrue(matcher.matches());
        assertEquals("/tmp/phone.db", matcher.group("file"));
    }

    @Test
    public void testTeradataUrlWithParams() {
        Matcher matcher = JdbcUrlUtils.getPattern("jdbc:teradata://{host}/DATABASE={database},DBS_PORT={port}[,{params}]")
                .matcher("jdbc:teradata://localhost/DATABASE=test,DBS_PORT=1234,CLIENT_CHARSET=EUC_CN");
        assertTrue(matcher.matches());
        assertEquals("localhost", matcher.group("host"));
        assertEquals("test", matcher.group("database"));
        assertEquals("1234", matcher.group("port"));
        assertEquals("CLIENT_CHARSET=EUC_CN", matcher.group("params"));
    }

    @Test
    public void testDb2UrlWithColonSeparatedParams() {
        Matcher matcher = JdbcUrlUtils.getPattern("jdbc:db2://{host}:{port}/{database}[:{params}]")
                .matcher("jdbc:db2://localhost:50000/testdb:driverType=4;fullyMaterializeLobData=true");
        assertTrue(matcher.matches());
        assertEquals("localhost", matcher.group("host"));
        assertEquals("50000", matcher.group("port"));
        assertEquals("testdb", matcher.group("database"));
        assertEquals("driverType=4;fullyMaterializeLobData=true", matcher.group("params"));
    }

    @Test
    public void testReachableAgainstOpenAndClosedLocalPorts() throws Exception {
        try (ServerSocket serverSocket = new ServerSocket(0, 1, java.net.InetAddress.getByName("127.0.0.1"))) {
            int openPort = serverSocket.getLocalPort();
            assertTrue(JdbcUrlUtils.reachable("127.0.0.1", String.valueOf(openPort)));
        }
        // port 1 on loopback is almost certainly closed in build environments
        assertFalse(JdbcUrlUtils.reachable("127.0.0.1", "1"));
    }
}
