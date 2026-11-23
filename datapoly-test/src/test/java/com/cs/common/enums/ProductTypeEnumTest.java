// Use of this source code is governed by a BSD-style license
package com.cs.common.enums;

import com.cs.common.util.JdbcUrlUtils;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
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

    @Test
    public void testUrlTemplatesStartWithUrlPrefixForEveryType() {
        for (ProductTypeEnum type : ProductTypeEnum.values()) {
            String[] templates = type.getUrl();
            assertNotNull("tplUrls missing for " + type, templates);
            assertTrue("tplUrls empty for " + type, templates.length > 0);
            boolean anyStartsWithPrefix = false;
            for (String template : templates) {
                assertNotNull(template);
                assertFalse("blank template for " + type, template.trim().isEmpty());
                if (template.startsWith(type.getUrlPrefix())) {
                    anyStartsWithPrefix = true;
                }
            }
            assertTrue("no template of " + type + " starts with its urlPrefix",
                    anyStartsWithPrefix);
        }
    }

    @Test
    public void testDriversLookLikeClassNames() {
        for (ProductTypeEnum type : ProductTypeEnum.values()) {
            String driver = type.getDriver();
            assertNotNull("driver missing for " + type, driver);
            assertTrue("driver must be a class name: " + type + " -> " + driver,
                    driver.matches("[A-Za-z_$][A-Za-z0-9_$]*(\\.[A-Za-z0-9_$]+)+"));
        }
    }

    @Test
    public void testQuotesAndPortsSane() {
        // sqlite has no port; http carries no quoting
        assertEquals(0, ProductTypeEnum.SQLITE3.getContext().getDefaultPort());
        assertNull(ProductTypeEnum.HTTP.getContext().getQuote());
        for (ProductTypeEnum type : ProductTypeEnum.values()) {
            if (type != ProductTypeEnum.SQLITE3) {
                assertTrue("unexpected port " + type.getContext().getDefaultPort() + " for " + type,
                        type.getContext().getDefaultPort() > 0);
            }
            if (type != ProductTypeEnum.HTTP) {
                assertNotNull(type.getContext().getQuote());
            }
        }
    }

    @Test
    public void testCatalogAndSchemaSwitches() {
        // only SQLSERVER and SYBASE work in catalog+schema mode
        Set<ProductTypeEnum> expected = new HashSet<>(Arrays.asList(
                ProductTypeEnum.SQLSERVER, ProductTypeEnum.SYBASE));
        for (ProductTypeEnum type : ProductTypeEnum.values()) {
            assertEquals("hasCatalogAndSchema drifted for " + type,
                    expected.contains(type), type.getContext().isHasCatalogAndSchema());
        }
    }

    @Test
    public void testAdapterSplitsCatalogAndSchema() {
        // the adapter maps the logical database to (catalog, schema) per product family
        assertEquals("db", ProductTypeEnum.MYSQL.getContext().getAdapter().apply("db").getLeft());
        assertNull(ProductTypeEnum.MYSQL.getContext().getAdapter().apply("db").getRight());
        assertNull(ProductTypeEnum.ORACLE.getContext().getAdapter().apply("db").getLeft());
        assertEquals("db", ProductTypeEnum.ORACLE.getContext().getAdapter().apply("db").getRight());
        assertEquals("db", ProductTypeEnum.GBASE8A.getContext().getAdapter().apply("db").getLeft());
        assertNull(ProductTypeEnum.GBASE8A.getContext().getAdapter().apply("db").getRight());
        // OceanBase is multi-dialect: no fixed mapping
        assertNull(ProductTypeEnum.OCEANBASE.getContext().getAdapter());
    }

    @Test
    public void testEveryAdapterMapsDatabaseIntoDeclaredSlot() {
        // MySQL-family products keep the database in the catalog slot, everything else
        // (except adapter-less OceanBase) maps it into the schema slot
        Set<ProductTypeEnum> catalogSlot = new HashSet<>(Arrays.asList(
                ProductTypeEnum.MYSQL, ProductTypeEnum.MARIADB, ProductTypeEnum.GBASE8A,
                ProductTypeEnum.DORIS, ProductTypeEnum.STARROCKS, ProductTypeEnum.TDENGINE,
                ProductTypeEnum.MONGODB));
        for (ProductTypeEnum type : ProductTypeEnum.values()) {
            if (type == ProductTypeEnum.OCEANBASE) {
                assertNull("multi-dialect type must stay unmapped", type.getContext().getAdapter());
                continue;
            }
            org.apache.commons.lang3.tuple.Pair<String, String> mapped =
                    type.getContext().getAdapter().apply("demo_db");
            assertNotNull("adapter missing for " + type, mapped);
            if (catalogSlot.contains(type)) {
                assertEquals(type.name() + " must map database to catalog", "demo_db", mapped.getLeft());
                assertNull(type.name() + " catalog types leave schema empty", mapped.getRight());
            } else {
                assertNull(type.name() + " schema types leave catalog empty", mapped.getLeft());
                assertEquals(type.name() + " must map database to schema", "demo_db", mapped.getRight());
            }
        }
    }

    @Test
    public void testSchemaListAvailability() {
        assertNull(ProductTypeEnum.SYBASE.getContext().getSqlSchemaList());
        assertNull(ProductTypeEnum.SQLITE3.getContext().getSqlSchemaList());
        assertNull(ProductTypeEnum.HTTP.getContext().getSqlSchemaList());
        assertEquals("SHOW DATABASES", ProductTypeEnum.HIVE.getContext().getSqlSchemaList());
        // types without a schema query return a fixed list instead
        assertEquals(Collections.singletonList("sqlite_master"),
                ProductTypeEnum.SQLITE3.getContext().getRetSchemaList());
        assertEquals(Collections.singletonList("elasticsearch"),
                ProductTypeEnum.ELASTICSEARCH.getContext().getRetSchemaList());
        assertEquals(Collections.singletonList("http"),
                ProductTypeEnum.HTTP.getContext().getRetSchemaList());
        assertNull(ProductTypeEnum.MYSQL.getContext().getRetSchemaList());
    }

    @Test
    public void testPageConsumerOrderingPerDialectFamily() {
        assertPageConsumer(ProductTypeEnum.MYSQL, 2, 10, 10, 10);        // LIMIT size OFFSET (page-1)*size
        assertPageConsumer(ProductTypeEnum.MARIADB, 3, 5, 5, 10);
        assertPageConsumer(ProductTypeEnum.ORACLE, 3, 10, 30, 20);       // <= page*size, > (page-1)*size
        assertPageConsumer(ProductTypeEnum.SQLSERVER, 4, 10, 30);          // only the offset
        assertPageConsumer(ProductTypeEnum.DB2, 2, 10, 10, 20);           // BETWEEN (page-1)*size AND page*size
        assertPageConsumer(ProductTypeEnum.SYBASE, 2, 10, 10, 20);
        assertPageConsumer(ProductTypeEnum.TDENGINE, 2, 4, 4, 4);
        // MaxCompute pagination is a deliberate no-op
        List<Object> noOpParameters = new ArrayList<>();
        ProductTypeEnum.ODPS.getPageConsumer().accept(2, 10, noOpParameters);
        assertTrue(noOpParameters.isEmpty());

        // every other dialect shares the plain LIMIT size OFFSET (page-1)*size family
        Set<ProductTypeEnum> special = new HashSet<>(Arrays.asList(
                ProductTypeEnum.ORACLE, ProductTypeEnum.SQLSERVER, ProductTypeEnum.DB2,
                ProductTypeEnum.SYBASE, ProductTypeEnum.ODPS, ProductTypeEnum.MONGODB,
                ProductTypeEnum.ELASTICSEARCH, ProductTypeEnum.HTTP));
        for (ProductTypeEnum type : ProductTypeEnum.values()) {
            if (special.contains(type)) {
                continue;
            }
            assertPageConsumer(type, 3, 7, 7, 14);
        }
        // pagination is a no-op on the types that define no pageSql template
        for (ProductTypeEnum type : Arrays.asList(ProductTypeEnum.MONGODB, ProductTypeEnum.ELASTICSEARCH,
                ProductTypeEnum.HTTP)) {
            List<Object> untouched = new ArrayList<>();
            type.getPageConsumer().accept(2, 10, untouched);
            assertTrue(type.name(), untouched.isEmpty());
        }
        // every type answers with a consumer: the paging call paths dereference it without a null check
        for (ProductTypeEnum type : ProductTypeEnum.values()) {
            assertNotNull("page consumer missing for " + type, type.getPageConsumer());
        }
    }

    private void assertPageConsumer(ProductTypeEnum type, int page, int size, int... expected) {
        List<Object> parameters = new ArrayList<>();
        type.getPageConsumer().accept(page, size, parameters);
        List<Integer> expectedList = new ArrayList<>();
        for (int value : expected) {
            expectedList.add(value);
        }
        assertEquals(type.name(), expectedList, parameters);
    }

    @Test
    public void testHiveFamilyRunsUniqueColumnNamesBeforeQuery() throws Exception {
        List<String> executed = new ArrayList<>();
        boolean[] closed = {false};
        java.sql.Statement statement = (java.sql.Statement) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{java.sql.Statement.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "execute":
                            executed.add(String.valueOf(args[0]));
                            return Boolean.TRUE;
                        case "close":
                            closed[0] = true;
                            return null;
                        default:
                            return defaultValue(method.getReturnType());
                    }
                });
        java.sql.Connection connection = (java.sql.Connection) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{java.sql.Connection.class},
                (proxy, method, args) -> "createStatement".equals(method.getName())
                        ? statement : defaultValue(method.getReturnType()));

        for (ProductTypeEnum type : Arrays.asList(ProductTypeEnum.HIVE, ProductTypeEnum.IMPALA,
                ProductTypeEnum.INCEPTOR)) {
            executed.clear();
            closed[0] = false;
            type.getContext().getExecuteBeforeQuery().accept(connection);
            assertEquals(Collections.singletonList("set hive.resultset.use.unique.column.names=false"),
                    executed);
            assertTrue("statement must be closed by try-with-resources", closed[0]);
        }
        assertNull(ProductTypeEnum.MYSQL.getContext().getExecuteBeforeQuery());
    }

    @Test
    public void testExecuteBeforeQueryWrapsSqlExceptionAsRuntime() {
        java.sql.Connection connection = (java.sql.Connection) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{java.sql.Connection.class},
                (proxy, method, args) -> {
                    if ("createStatement".equals(method.getName())) {
                        java.sql.Statement statement = (java.sql.Statement) Proxy.newProxyInstance(
                                getClass().getClassLoader(), new Class<?>[]{java.sql.Statement.class},
                                (p, m, a) -> {
                                    if ("execute".equals(m.getName())) {
                                        throw new java.sql.SQLException("set failed");
                                    }
                                    return defaultValue(m.getReturnType());
                                });
                        return statement;
                    }
                    return defaultValue(method.getReturnType());
                });
        for (ProductTypeEnum type : Arrays.asList(ProductTypeEnum.HIVE, ProductTypeEnum.IMPALA,
                ProductTypeEnum.INCEPTOR)) {
            try {
                type.getContext().getExecuteBeforeQuery().accept(connection);
                fail("SQLException must surface as RuntimeException for " + type);
            } catch (RuntimeException expected) {
                assertTrue(expected.getCause() instanceof java.sql.SQLException);
            }
        }
    }

    @Test
    public void testSybaseResultSetFuncWrapsSqlExceptionAsRuntime() {
        java.sql.Statement statement = (java.sql.Statement) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{java.sql.Statement.class},
                (proxy, method, args) -> {
                    if ("getMoreResults".equals(method.getName())) {
                        throw new java.sql.SQLException("broken");
                    }
                    return defaultValue(method.getReturnType());
                });
        try {
            ProductTypeEnum.SYBASE.getResultSetFunc().apply(false, statement);
            fail("SQLException must surface as RuntimeException");
        } catch (RuntimeException expected) {
            assertTrue(expected.getCause() instanceof java.sql.SQLException);
        }
    }

    @Test
    public void testSybaseResultSetFuncSkipsUpdateOnlyResults() throws Exception {
        int[] moreResultsCalls = {0};
        java.sql.Statement statement = (java.sql.Statement) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{java.sql.Statement.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getUpdateCount":
                            // first update count consumed, then exhausted
                            return moreResultsCalls[0] == 0 ? 0 : -1;
                        case "getMoreResults":
                            moreResultsCalls[0]++;
                            return Boolean.TRUE;
                        default:
                            return defaultValue(method.getReturnType());
                    }
                });
        assertTrue("result set behind update counts must be found",
                ProductTypeEnum.SYBASE.getResultSetFunc().apply(false, statement));
    }

    @Test
    public void testDialectPageSqlTemplates() {
        assertTrue(ProductTypeEnum.ORACLE.getPageSql("SELECT 1", 2, 10).contains("ROWNUM"));
        assertTrue(ProductTypeEnum.DB2.getPageSql("SELECT 1", 2, 10).contains("ROWNUMBER()"));
        assertTrue(ProductTypeEnum.SYBASE.getPageSql("SELECT 1", 2, 10).contains("#delTmpTb"));
        // types without a paging template return the sql untouched
        assertEquals("SELECT 1", ProductTypeEnum.ELASTICSEARCH.getPageSql("SELECT 1", 2, 10));
        assertEquals("SELECT 1", ProductTypeEnum.HTTP.getPageSql("SELECT 1", 2, 10));
    }

    @Test
    public void testNoViewTablesFlag() {
        assertTrue(ProductTypeEnum.TDENGINE.isNoViewTables());
        assertTrue(ProductTypeEnum.MONGODB.isNoViewTables());
        assertTrue(ProductTypeEnum.ELASTICSEARCH.isNoViewTables());
        assertTrue(ProductTypeEnum.HTTP.isNoViewTables());
        assertFalse(ProductTypeEnum.MYSQL.isNoViewTables());
        assertFalse(ProductTypeEnum.ORACLE.isNoViewTables());
        assertFalse(ProductTypeEnum.HIVE.isNoViewTables());
    }

    @Test
    public void testInceptorKeepsHiveFamilyDefaults() {
        assertNull(ProductTypeEnum.INCEPTOR.getTestSql());
        assertEquals("jdbc:hive2://", ProductTypeEnum.INCEPTOR.getUrlPrefix());
        assertTrue(ProductTypeEnum.INCEPTOR.offTransactional());
        assertTrue(ProductTypeEnum.INCEPTOR.getContext().getSqlSchemaList().contains("DATABASES"));
    }

    @Test
    public void testExistsAndOfRejectEmptyName() {
        assertFalse(ProductTypeEnum.exists(""));
        try {
            ProductTypeEnum.of("");
            fail("empty name must be rejected");
        } catch (IllegalArgumentException expected) {
        }
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive() || type == void.class) {
            return null;
        }
        if (type == boolean.class) {
            return Boolean.FALSE;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == double.class) {
            return 0D;
        }
        if (type == float.class) {
            return 0F;
        }
        if (type == char.class) {
            return '\0';
        }
        if (type == byte.class) {
            return (byte) 0;
        }
        if (type == short.class) {
            return (short) 0;
        }
        return 0;
    }

    private boolean matchesAny(String[] templates, String url) {
        return Arrays.stream(templates)
                .map(JdbcUrlUtils::getPattern)
                .map(pattern -> pattern.matcher(url))
                .anyMatch(Matcher::matches);
    }
}