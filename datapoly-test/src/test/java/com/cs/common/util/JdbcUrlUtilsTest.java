// Use of this source code is governed by a BSD-style license
package com.cs.common.util;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
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

    @Test
    public void testReachableUnresolvableHostReturnsFalse() {
        // ".invalid" is reserved by RFC 6761: resolution fails before any network roundtrip
        assertFalse(JdbcUrlUtils.reachable("no-such-host.invalid", "80"));
    }

    @Test(expected = NumberFormatException.class)
    public void testReachableRejectsNonNumericPort() {
        JdbcUrlUtils.reachable("127.0.0.1", "not-a-port");
    }

    @Test
    public void testMariaDbUrl() {
        Matcher matcher = JdbcUrlUtils.getPattern("jdbc:mariadb://{host}[:{port}]/[{database}][\\?{params}]")
                .matcher("jdbc:mariadb://localhost:3306/test_demo");
        assertTrue(matcher.matches());
        assertEquals("localhost", matcher.group("host"));
        assertEquals("3306", matcher.group("port"));
        assertEquals("test_demo", matcher.group("database"));
        assertNull(matcher.group("params"));
    }

    @Test
    public void testKingbaseUrl() {
        Matcher matcher = JdbcUrlUtils.getPattern("jdbc:kingbase8://{host}[:{port}]/[{database}][\\?{params}]")
                .matcher("jdbc:kingbase8://localhost:54321/sample");
        assertTrue(matcher.matches());
        assertEquals("localhost", matcher.group("host"));
        assertEquals("54321", matcher.group("port"));
        assertEquals("sample", matcher.group("database"));
    }

    @Test
    public void testDamengUrlWithDatabaseAndParams() {
        Matcher matcher = JdbcUrlUtils.getPattern("jdbc:dm://{host}:{port}[/{database}][\\?{params}]")
                .matcher("jdbc:dm://localhost:5236/user?param=hello");
        assertTrue(matcher.matches());
        assertEquals("localhost", matcher.group("host"));
        assertEquals("5236", matcher.group("port"));
        assertEquals("user", matcher.group("database"));
        assertEquals("param=hello", matcher.group("params"));
    }

    @Test
    public void testDamengUrlBareHostPort() {
        Matcher matcher = JdbcUrlUtils.getPattern("jdbc:dm://{host}:{port}[/{database}][\\?{params}]")
                .matcher("jdbc:dm://localhost:5236");
        assertTrue(matcher.matches());
        assertEquals("localhost", matcher.group("host"));
        assertEquals("5236", matcher.group("port"));
        assertNull(matcher.group("database"));
        assertNull(matcher.group("params"));
    }

    @Test
    public void testHiveUrlWithSemicolonParams() {
        Matcher matcher = JdbcUrlUtils.getPattern("jdbc:hive2://{host}[:{port}]/[{database}][;{params}]")
                .matcher("jdbc:hive2://127.0.0.1:10000/default;a=b;c=d?useUnicode=true");
        assertTrue(matcher.matches());
        assertEquals("127.0.0.1", matcher.group("host"));
        assertEquals("10000", matcher.group("port"));
        assertEquals("default", matcher.group("database"));
        assertEquals("a=b;c=d?useUnicode=true", matcher.group("params"));
    }

    @Test
    public void testImpalaUrl() {
        Matcher matcher = JdbcUrlUtils.getPattern("jdbc:impala://{host}[:{port}]/[{database}][;{params}]")
                .matcher("jdbc:impala://172.17.2.12:10000/default;escape=false");
        assertTrue(matcher.matches());
        assertEquals("escape=false", matcher.group("params"));
    }

    @Test
    public void testMongoDbUrl() {
        Matcher matcher = JdbcUrlUtils.getPattern("jdbc:mongodb://{host}[:{port}]/[{database}][\\?{params}]")
                .matcher("jdbc:mongodb://127.0.0.1:27017/admin?authSource=admin&authMechanism=SCRAM-SHA-1");
        assertTrue(matcher.matches());
        assertEquals("127.0.0.1", matcher.group("host"));
        assertEquals("27017", matcher.group("port"));
        assertEquals("admin", matcher.group("database"));
        assertEquals("authSource=admin&authMechanism=SCRAM-SHA-1", matcher.group("params"));
    }

    @Test
    public void testClickHouseUrl() {
        Matcher matcher = JdbcUrlUtils.getPattern("jdbc:clickhouse://{host}[:{port}]/[{database}][\\?{params}]")
                .matcher("jdbc:clickhouse://127.0.0.1:8123/default");
        assertTrue(matcher.matches());
        assertEquals("127.0.0.1", matcher.group("host"));
        assertEquals("8123", matcher.group("port"));
        assertEquals("default", matcher.group("database"));
    }

    @Test
    public void testOpenGaussAndOscarUrls() {
        Matcher opengauss = JdbcUrlUtils.getPattern("jdbc:opengauss://{host}[:{port}]/[{database}][\\?{params}]")
                .matcher("jdbc:opengauss://172.17.2.10:5432/test");
        assertTrue(opengauss.matches());
        assertEquals("test", opengauss.group("database"));

        Matcher oscar = JdbcUrlUtils.getPattern("jdbc:oscar://{host}[:{port}]/[{database}][\\?{params}]")
                .matcher("jdbc:oscar://172.17.2.10:2003/OSCRDB");
        assertTrue(oscar.matches());
        assertEquals("OSCRDB", oscar.group("database"));
    }

    @Test
    public void testGbase8aAndSybaseUrls() {
        Matcher gbase = JdbcUrlUtils.getPattern("jdbc:gbase://{host}[:{port}]/[{database}][\\?{params}]")
                .matcher("jdbc:gbase://172.17.2.10:5258/test");
        assertTrue(gbase.matches());
        assertEquals("5258", gbase.group("port"));

        Matcher sybase = JdbcUrlUtils.getPattern("jdbc:sybase:Tds:{host}[:{port}][/{database}][\\?{params}]")
                .matcher("jdbc:sybase:Tds:172.17.2.10:5000/test?charset=cp936");
        assertTrue(sybase.matches());
        assertEquals("172.17.2.10", sybase.group("host"));
        assertEquals("test", sybase.group("database"));
        assertEquals("charset=cp936", sybase.group("params"));
    }

    @Test
    public void testOceanBaseAndTdEngineUrls() {
        Matcher oceanbase = JdbcUrlUtils.getPattern("jdbc:oceanbase://{host}[:{port}]/[{database}][\\?{params}]")
                .matcher("jdbc:oceanbase://127.0.0.1:2881/test");
        assertTrue(oceanbase.matches());
        assertEquals("2881", oceanbase.group("port"));

        Matcher tdengine = JdbcUrlUtils.getPattern("jdbc:TAOS-RS://{host}[:{port}]/[{database}][\\?{params}]")
                .matcher("jdbc:TAOS-RS://172.17.2.10:6041/test");
        assertTrue(tdengine.matches());
        assertEquals("172.17.2.10", tdengine.group("host"));
    }

    @Test
    public void testElasticSearchAndHttpRestfulUrls() {
        Matcher es = JdbcUrlUtils.getPattern("jdbc:esdriver://{host}:{port}[\\?{params}]")
                .matcher("jdbc:esdriver://172.17.2.10:9200");
        assertTrue(es.matches());
        assertEquals("9200", es.group("port"));
        assertNull(es.group("params"));

        Matcher restful = JdbcUrlUtils.getPattern("jdbc:restful://{host}:{port}[\\?{params}]")
                .matcher("jdbc:restful://172.17.2.10:8080");
        assertTrue(restful.matches());
        assertEquals("172.17.2.10", restful.group("host"));
        assertEquals("8080", restful.group("port"));

        // the documented HTTP sample embeds "http://" in the host slot, which the default
        // host regex rejects — the service therefore skips pattern validation for the HTTP type
        Matcher embeddedScheme = JdbcUrlUtils.getPattern("jdbc:restful://{host}:{port}[\\?{params}]")
                .matcher("jdbc:restful://http://172.17.2.10:8080");
        assertFalse(embeddedScheme.matches());
    }

    @Test
    public void testOdpsHttpAndHttpsUrls() {
        Matcher http = JdbcUrlUtils.getPattern("jdbc:odps:http://{host}/api[\\?{params}]")
                .matcher("jdbc:odps:http://service.cn-hangzhou.maxcompute.aliyun.com/api?project=demo");
        assertTrue(http.matches());
        assertEquals("service.cn-hangzhou.maxcompute.aliyun.com", http.group("host"));
        assertEquals("project=demo", http.group("params"));

        Matcher https = JdbcUrlUtils.getPattern("jdbc:odps:https://{host}/api[\\?{params}]")
                .matcher("jdbc:odps:https://service.cn-hangzhou.maxcompute.aliyun.com/api");
        assertTrue(https.matches());
        assertNull(https.group("params"));
    }

    @Test
    public void testSqliteWindowsPathUrl() {
        Matcher matcher = JdbcUrlUtils.getPattern("jdbc:sqlite:{file}")
                .matcher("jdbc:sqlite:D:\\Project\\Test\\phone.db");
        assertTrue(matcher.matches());
        assertEquals("D:\\Project\\Test\\phone.db", matcher.group("file"));
    }

    @Test
    public void testSqliteResourceUrl() {
        Matcher matcher = JdbcUrlUtils.getPattern("jdbc:sqlite::resource:{file}")
                .matcher("jdbc:sqlite::resource:/tmp/test.db");
        assertTrue(matcher.matches());
        assertEquals("/tmp/test.db", matcher.group("file"));
    }

    @Test
    public void testFolderPropertyRegexAllowsSpaces() {
        // {folder} uses the lazy ".+?" regex: characters the default host class rejects stay legal
        Matcher matcher = JdbcUrlUtils.getPattern("scheme://{folder}/{file}")
                .matcher("scheme://data dir/phone.db");
        assertTrue(matcher.matches());
        assertEquals("data dir", matcher.group("folder"));
        assertEquals("phone.db", matcher.group("file"));
    }

    @Test
    public void testHostRegexRejectsIllegalCharacters() {
        // the default property regex "[\w\-_.~]+" has no room for spaces or colons
        Matcher matcher = JdbcUrlUtils.getPattern("jdbc:mysql://{host}[:{port}]/[{database}]")
                .matcher("jdbc:mysql://bad host:3306/db");
        assertFalse(matcher.matches());
    }

    @Test
    public void testMainDemoPrintsAllDocumentedUrlExamples() throws Exception {
        // main() is a self-checking demo: every documented URL prints an "error for ..." line on mismatch
        PrintStream original = System.out;
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        System.setOut(new PrintStream(buffer, true, StandardCharsets.UTF_8));
        try {
            JdbcUrlUtils.main(new String[0]);
        } finally {
            System.setOut(original);
        }
        String out = buffer.toString(StandardCharsets.UTF_8);

        assertFalse("demo URLs must all match, but saw:\n" + out, out.contains("error for"));
        assertTrue(out.contains("teradata host:localhost"));
        assertTrue(out.contains("postgresql host:localhost"));
        assertTrue(out.contains("oracle sid host:localhost"));
        assertTrue(out.contains("oracle ServiceName host:localhost"));
        assertTrue(out.contains("mysql host:localhost"));
        assertTrue(out.contains("mariadb host:localhost"));
        assertTrue(out.contains("sqlserver host:localhost"));
        assertTrue(out.contains("kingbase8 host:localhost"));
        assertTrue(out.contains("dm host:localhost"));
        assertTrue(out.contains("db2 host:localhost"));
        assertTrue(out.contains("hive host:127.0.0.1"));
        assertTrue(out.contains("sqlite file:D:\\Project\\Test\\phone.db"));
        assertTrue(out.contains("mongodb database:admin"));
        assertTrue(out.contains("clickhouse database:default"));
    }
}
