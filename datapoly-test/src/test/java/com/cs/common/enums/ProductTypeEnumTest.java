// Use of this source code is governed by a BSD-style license
package com.cs.common.enums;

import com.cs.common.util.JdbcUrlUtils;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;

import static org.junit.Assert.*;

public class ProductTypeEnumTest {

    @Test
    public void testEnumUiNameInvariant() {
        for (ProductTypeEnum type : ProductTypeEnum.values()) {
            assertEquals("name must uppercase to its enum constant", type.name(), type.getName().toUpperCase());
        }
    }

    @Test
    public void testEnumIdsAreUnique() {
        Set<Integer> ids = new HashSet<>();
        for (ProductTypeEnum type : ProductTypeEnum.values()) {
            assertTrue("duplicated id " + type.getId() + " for " + type.name(), ids.add(type.getId()));
        }
    }

    @Test
    public void testOdpsLookup() {
        assertTrue(ProductTypeEnum.exists("odps"));
        assertTrue(ProductTypeEnum.exists("ODPS"));
        assertEquals(ProductTypeEnum.ODPS, ProductTypeEnum.of("odps"));
        assertEquals("com.aliyun.odps.jdbc.OdpsDriver", ProductTypeEnum.ODPS.getDriver());
    }

    @Test
    public void testLookupByNameAndRejections() {
        assertTrue(ProductTypeEnum.exists("mysql"));
        assertTrue(ProductTypeEnum.exists("MySQL"));
        assertFalse(ProductTypeEnum.exists("not-a-db"));
        assertEquals(ProductTypeEnum.MYSQL, ProductTypeEnum.of("mysql"));
        // context name may differ in case from the enum constant name
        assertEquals(ProductTypeEnum.TDENGINE, ProductTypeEnum.of("TDengine"));
        assertEquals(ProductTypeEnum.MONGODB, ProductTypeEnum.of("MongoDB"));
        try {
            ProductTypeEnum.of("not-a-db");
            fail("unknown name must be rejected");
        } catch (IllegalArgumentException expected) {
        }
        try {
            ProductTypeEnum.of(null);
            fail("null name must be rejected");
        } catch (IllegalArgumentException expected) {
        }
    }

    @Test
    public void testCapabilitySwitches() {
        // database-name capability: catalog-less types answer false
        assertFalse(ProductTypeEnum.MYSQL.hasDatabaseName());
        assertFalse(ProductTypeEnum.MONGODB.hasDatabaseName());
        assertTrue(ProductTypeEnum.ORACLE.hasDatabaseName());
        assertTrue(ProductTypeEnum.POSTGRESQL.hasDatabaseName());

        // file/address capability only diverges for sqlite
        assertTrue(ProductTypeEnum.SQLITE3.hasFilePath());
        assertFalse(ProductTypeEnum.SQLITE3.hasAddress());
        assertFalse(ProductTypeEnum.MYSQL.hasFilePath());
        assertTrue(ProductTypeEnum.MYSQL.hasAddress());

        // transactional off-switch for engines without real transactions
        assertTrue(ProductTypeEnum.HIVE.offTransactional());
        assertTrue(ProductTypeEnum.SYBASE.offTransactional());
        assertTrue(ProductTypeEnum.IMPALA.offTransactional());
        assertFalse(ProductTypeEnum.MYSQL.offTransactional());

        // multi-dialect only for OceanBase
        assertTrue(ProductTypeEnum.OCEANBASE.isMultiDialect());
        assertFalse(ProductTypeEnum.MARIADB.isMultiDialect());

        assertEquals(ProductTypeEnum.MYSQL.getSample(), ProductTypeEnum.MYSQL.getSample());
        assertEquals("/* ping */ SELECT 1", ProductTypeEnum.MYSQL.getSql());
        assertEquals(ProductTypeEnum.MYSQL.getTestSql(), ProductTypeEnum.MYSQL.getSql());
    }

    @Test
    public void testGetPageSqlPerDialect() {
        assertEquals("SELECT 1 LIMIT ? OFFSET ? ", ProductTypeEnum.MYSQL.getPageSql("SELECT 1", 2, 10));

        String sqlserver = ProductTypeEnum.SQLSERVER.getPageSql("SELECT 1", 3, 10);
        assertTrue(sqlserver.startsWith("SELECT TOP 10 "));
        assertTrue(sqlserver.contains("ROW_NUMBER()"));
        assertTrue(sqlserver.trim().endsWith("ALIAS_ROW_NUM > ?"));

        // ODPS pagination is a no-op: pageSql blank returns the original sql unchanged
        assertEquals("SELECT 1", ProductTypeEnum.ODPS.getPageSql("SELECT 1", 2, 10));
        // MONGODB defines no pageSql template either
        assertEquals("SELECT 1", ProductTypeEnum.MONGODB.getPageSql("SELECT 1", 2, 10));
    }

    @Test
    public void testSybaseResultSetFuncWalksMultiResultStatements() throws Exception {
        java.sql.Statement statement = (java.sql.Statement) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{java.sql.Statement.class},
                (proxy, method, args) -> {
                    if ("getUpdateCount".equals(method.getName())) {
                        return -1;
                    }
                    if ("getMoreResults".equals(method.getName())) {
                        return false;
                    }
                    return null;
                });
        assertTrue(ProductTypeEnum.SYBASE.getResultSetFunc().apply(true, statement));
        assertFalse("exhausted statement must report no result",
                ProductTypeEnum.SYBASE.getResultSetFunc().apply(false, statement));
        assertNull(ProductTypeEnum.MYSQL.getResultSetFunc());
    }

    @Test
    public void testOdpsContextSwitches() {
        assertFalse(ProductTypeEnum.ODPS.hasDatabaseName());
        assertFalse(ProductTypeEnum.ODPS.hasFilePath());
        assertFalse(ProductTypeEnum.ODPS.isNoViewTables());
        assertTrue(ProductTypeEnum.ODPS.offTransactional());
        assertNull(ProductTypeEnum.ODPS.getSql());
        assertNotNull(ProductTypeEnum.ODPS.getPageConsumer());
    }

    @Test
    public void testOdpsUrlPatternMatchesOfficialSample() {
        String httpUrl = "jdbc:odps:http://service.cn-hangzhou.maxcompute.aliyun.com/api?project=test_project&useProjectTimeZone=true";
        String httpsUrl = "jdbc:odps:https://service.cn-hangzhou.maxcompute.aliyun.com/api?project=test_project";
        assertTrue(matchesAny(ProductTypeEnum.ODPS.getUrl(), httpUrl));
        assertTrue(matchesAny(ProductTypeEnum.ODPS.getUrl(), httpsUrl));
        assertFalse(matchesAny(ProductTypeEnum.ODPS.getUrl(), "jdbc:odps:http://bad"));
        assertFalse(matchesAny(ProductTypeEnum.ODPS.getUrl(),
                "jdbc:odps:http://service.cn-hangzhou.maxcompute.aliyun.com/other?project=test_project"));
        assertTrue(httpUrl.startsWith(ProductTypeEnum.ODPS.getUrlPrefix()));

        Matcher matcher = JdbcUrlUtils.getPattern(ProductTypeEnum.ODPS.getUrl()[0]).matcher(httpUrl);
        assertTrue(matcher.matches());
        assertEquals("service.cn-hangzhou.maxcompute.aliyun.com", matcher.group("host"));
        assertEquals("project=test_project&useProjectTimeZone=true", matcher.group("params"));
    }

    private boolean matchesAny(String[] templates, String url) {
        return Arrays.stream(templates)
                .map(JdbcUrlUtils::getPattern)
                .map(pattern -> pattern.matcher(url))
                .anyMatch(Matcher::matches);
    }
}