// Use of this source code is governed by a BSD-style license
package com.cs.core.util;

import com.cs.common.enums.NamingStrategyEnum;
import com.cs.common.enums.ProductTypeEnum;
import com.cs.template.SqlMeta;
import org.junit.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.*;
import java.util.*;

import static org.junit.Assert.*;

public class SqlJdbcUtilsTest {

    /** Per-test JDBC fake state captured by the proxies below. */
    private static class FakeJdbc {
        final String productName;
        final List<String> preparedSqls = new ArrayList<>();
        final List<String> plainStatements = new ArrayList<>();
        final List<Object> boundParams = new ArrayList<>();
        int queryTimeout = -1;
        int fetchSize = Integer.MAX_VALUE;
        boolean hasResult = true;
        int updateCount = -1;
        List<String> columns = Collections.emptyList();
        List<Map<String, Object>> rows = Collections.emptyList();

        FakeJdbc(String productName) {
            this.productName = productName;
        }

        /** Columns whose ResultSet.getObject() blows up: the row must keep a null for them. */
        final Set<String> getObjectFailures = new HashSet<>();

        /** Simulates a driver whose metadata call fails: isMySqlConnection must fall back to false. */
        boolean metaDataThrows;

        Connection connection() {
            return (Connection) Proxy.newProxyInstance(FakeJdbc.class.getClassLoader(),
                    new Class<?>[]{Connection.class}, this::dispatch);
        }

        private Object dispatch(Object proxy, Method method, Object[] args) {
            String name = method.getName();
            if ("hashCode".equals(name)) {
                return System.identityHashCode(proxy);
            }
            if ("equals".equals(name)) {
                return proxy == args[0];
            }
            if ("toString".equals(name)) {
                return "fake-" + method.getDeclaringClass().getSimpleName();
            }
            if ("prepareStatement".equals(name)) {
                preparedSqls.add((String) args[0]);
                return statement();
            }
            if ("createStatement".equals(name)) {
                return statement();
            }
            if ("getMetaData".equals(name)) {
                return databaseMetaData();
            }
            return defaultValue(method.getReturnType());
        }

        private Object statement() {
            return Proxy.newProxyInstance(FakeJdbc.class.getClassLoader(),
                    new Class<?>[]{PreparedStatement.class, Statement.class},
                    (proxy, method, args) -> {
                        String name = method.getName();
                        if ("hashCode".equals(name)) {
                            return System.identityHashCode(proxy);
                        }
                        if ("equals".equals(name)) {
                            return proxy == args[0];
                        }
                        switch (name) {
                            case "setQueryTimeout":
                                queryTimeout = (Integer) args[0];
                                return null;
                            case "setFetchSize":
                                fetchSize = (Integer) args[0];
                                return null;
                            case "setObject":
                                boundParams.add(args[1]);
                                return null;
                            case "execute":
                                if (args != null && args.length == 1) {
                                    plainStatements.add(String.valueOf(args[0]));
                                }
                                return hasResult;
                            case "getResultSet":
                                return resultSet();
                            case "getUpdateCount":
                                return updateCount;
                            default:
                                return defaultValue(method.getReturnType());
                        }
                    });
        }

        private Object resultSet() {
            Iterator<Map<String, Object>> rowIterator = rows.iterator();
            Map<String, Object>[] current = new Map[]{null};
            return Proxy.newProxyInstance(FakeJdbc.class.getClassLoader(),
                    new Class<?>[]{ResultSet.class},
                    (proxy, method, args) -> {
                        String name = method.getName();
                        if ("hashCode".equals(name)) {
                            return System.identityHashCode(proxy);
                        }
                        if ("equals".equals(name)) {
                            return proxy == args[0];
                        }
                        switch (name) {
                            case "getMetaData":
                                return resultSetMetaData();
                            case "next":
                                if (rowIterator.hasNext()) {
                                    current[0] = rowIterator.next();
                                    return true;
                                }
                                return false;
                            case "getObject":
                                if (getObjectFailures.contains(String.valueOf(args[0]))) {
                                    throw new SQLException("getObject failed for " + args[0]);
                                }
                                return current[0].get(String.valueOf(args[0]));
                            default:
                                return defaultValue(method.getReturnType());
                        }
                    });
        }

        private Object resultSetMetaData() {
            return Proxy.newProxyInstance(FakeJdbc.class.getClassLoader(),
                    new Class<?>[]{ResultSetMetaData.class},
                    (proxy, method, args) -> {
                        String name = method.getName();
                        if ("getColumnCount".equals(name)) {
                            return columns.size();
                        }
                        if ("getColumnLabel".equals(name)) {
                            return columns.get((Integer) args[0] - 1);
                        }
                        if ("hashCode".equals(name)) {
                            return System.identityHashCode(proxy);
                        }
                        if ("equals".equals(name)) {
                            return proxy == args[0];
                        }
                        return defaultValue(method.getReturnType());
                    });
        }

        private Object databaseMetaData() {
            return Proxy.newProxyInstance(FakeJdbc.class.getClassLoader(),
                    new Class<?>[]{DatabaseMetaData.class},
                    (proxy, method, args) -> {
                        String name = method.getName();
                        if ("getDatabaseProductName".equals(name)) {
                            if (metaDataThrows) {
                                throw new SQLException("metadata unavailable");
                            }
                            return productName;
                        }
                        if ("hashCode".equals(name)) {
                            return System.identityHashCode(proxy);
                        }
                        if ("equals".equals(name)) {
                            return proxy == args[0];
                        }
                        return defaultValue(method.getReturnType());
                    });
        }

        private static Object defaultValue(Class<?> returnType) {
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
    }

    private Object clob(String content) {
        return Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Clob.class},
                (p, m, a) -> {
                    if ("length".equals(m.getName())) {
                        return (long) content.length();
                    }
                    if ("getSubString".equals(m.getName())) {
                        return content;
                    }
                    if ("hashCode".equals(m.getName())) {
                        return System.identityHashCode(p);
                    }
                    if ("equals".equals(m.getName())) {
                        return p == a[0];
                    }
                    return null;
                });
    }

    /** A Clob reporting a broken length: conversion must collapse to null instead of throwing. */
    private Object brokenClob() {
        return Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Clob.class},
                (p, m, a) -> {
                    if ("length".equals(m.getName())) {
                        throw new SQLException("clob length failed");
                    }
                    if ("hashCode".equals(m.getName())) {
                        return System.identityHashCode(p);
                    }
                    if ("equals".equals(m.getName())) {
                        return p == a[0];
                    }
                    return null;
                });
    }

    /** A Clob with a negative length: treated as "no data" and converts to null. */
    private Object negativeLengthClob() {
        return Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Clob.class},
                (p, m, a) -> {
                    if ("length".equals(m.getName())) {
                        return -1L;
                    }
                    if ("getSubString".equals(m.getName())) {
                        return "";
                    }
                    if ("hashCode".equals(m.getName())) {
                        return System.identityHashCode(p);
                    }
                    if ("equals".equals(m.getName())) {
                        return p == a[0];
                    }
                    return null;
                });
    }

    private Object blob(final byte[] bytes) {
        return Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Blob.class},
                (p, m, a) -> {
                    if ("length".equals(m.getName())) {
                        return (long) bytes.length;
                    }
                    if ("getBytes".equals(m.getName())) {
                        return bytes;
                    }
                    if ("hashCode".equals(m.getName())) {
                        return System.identityHashCode(p);
                    }
                    if ("equals".equals(m.getName())) {
                        return p == a[0];
                    }
                    return null;
                });
    }

    /** A Blob reporting a broken length: conversion must collapse to null instead of throwing. */
    private Object brokenBlob() {
        return Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Blob.class},
                (p, m, a) -> {
                    if ("length".equals(m.getName())) {
                        throw new SQLException("blob length failed");
                    }
                    if ("hashCode".equals(m.getName())) {
                        return System.identityHashCode(p);
                    }
                    if ("equals".equals(m.getName())) {
                        return p == a[0];
                    }
                    return null;
                });
    }

    /** A Blob with a negative length: treated as "no data" and converts to null. */
    private Object negativeLengthBlob() {
        return Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Blob.class},
                (p, m, a) -> {
                    if ("length".equals(m.getName())) {
                        return -1L;
                    }
                    if ("getBytes".equals(m.getName())) {
                        return new byte[0];
                    }
                    if ("hashCode".equals(m.getName())) {
                        return System.identityHashCode(p);
                    }
                    if ("equals".equals(m.getName())) {
                        return p == a[0];
                    }
                    return null;
                });
    }

    private Object array(String json) {
        return Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Array.class},
                (p, m, a) -> {
                    if ("toString".equals(m.getName())) {
                        return json;
                    }
                    if ("hashCode".equals(m.getName())) {
                        return System.identityHashCode(p);
                    }
                    if ("equals".equals(m.getName())) {
                        return p == a[0];
                    }
                    return null;
                });
    }

    private Object struct(String json) {
        return Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Struct.class},
                (p, m, a) -> {
                    if ("toString".equals(m.getName())) {
                        return json;
                    }
                    if ("hashCode".equals(m.getName())) {
                        return System.identityHashCode(p);
                    }
                    if ("equals".equals(m.getName())) {
                        return p == a[0];
                    }
                    return null;
                });
    }

    @Test
    public void testGetConverterByNamingStrategy() {
        assertEquals("AbC", SqlJdbcUtils.getConverter(null).apply("AbC"));
        assertEquals("user_name", SqlJdbcUtils.getConverter(NamingStrategyEnum.SNAKE_CASE).apply("userName"));
        assertEquals("userName", SqlJdbcUtils.getConverter(NamingStrategyEnum.CAMEL_CASE).apply("user_name"));
        assertEquals("x", SqlJdbcUtils.getConverter(NamingStrategyEnum.LOWER_CASE).apply("X"));
        assertEquals("X", SqlJdbcUtils.getConverter(NamingStrategyEnum.UPPER_CASE).apply("x"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testExecuteQueryAppliesPagingAndNamingStrategy() throws SQLException {
        FakeJdbc jdbc = new FakeJdbc("MySQL");
        jdbc.columns = Arrays.asList("USER_NAME", "USER_AGE");
        Map<String, Object> row1 = new LinkedHashMap<>();
        row1.put("USER_NAME", "tom");
        row1.put("USER_AGE", 23);
        Map<String, Object> row2 = new LinkedHashMap<>();
        row2.put("USER_NAME", "amy");
        row2.put("USER_AGE", 31);
        jdbc.rows = Arrays.asList(row1, row2);

        SqlMeta meta = new SqlMeta("SELECT * FROM t", new ArrayList<>(Collections.singletonList("seed")));
        Object result = SqlJdbcUtils.execute(ProductTypeEnum.MYSQL, jdbc.connection(), meta,
                NamingStrategyEnum.LOWER_CASE, 2, 5, true, false);

        List<Map<String, Object>> list = (List<Map<String, Object>>) result;
        assertEquals(2, list.size());
        assertEquals("tom", list.get(0).get("user_name"));
        assertEquals(23, list.get(0).get("user_age"));
        assertEquals("amy", list.get(1).get("user_name"));
        // MySQL connections stream with Integer.MIN_VALUE fetch size
        assertEquals(Integer.MIN_VALUE, jdbc.fetchSize);
        assertEquals(300, jdbc.queryTimeout);
        assertEquals("SELECT * FROM t LIMIT ? OFFSET ? ", jdbc.preparedSqls.get(0));
        // original param first, then pagination params appended by the page consumer (size, offset)
        assertEquals(Arrays.asList("seed", 5, 5), jdbc.boundParams);
    }

    @Test
    public void testExecuteNonQueryReturnsUpdateCountMessage() throws SQLException {
        FakeJdbc jdbc = new FakeJdbc("FakeDB");
        jdbc.hasResult = false;
        jdbc.updateCount = 7;

        SqlMeta meta = new SqlMeta("UPDATE t SET a = 1", new ArrayList<Object>());
        Object result = SqlJdbcUtils.execute(ProductTypeEnum.POSTGRESQL, jdbc.connection(), meta,
                null, 1, 10, false, false);
        assertEquals("(7) rows affected", result);
        assertEquals("UPDATE t SET a = 1", jdbc.preparedSqls.get(0));
        assertEquals(0, jdbc.fetchSize);
    }

    @Test
    public void testIllegalPagingSizeClampedToDefault() throws SQLException {
        FakeJdbc jdbc = new FakeJdbc("FakeDB");
        jdbc.columns = Collections.emptyList();
        jdbc.rows = Collections.emptyList();

        SqlMeta meta = new SqlMeta("SELECT * FROM t", new ArrayList<Object>());
        SqlJdbcUtils.execute(ProductTypeEnum.POSTGRESQL, jdbc.connection(), meta, null, 3, 0, true, false);

        // size clamped to 10: pagination params become (10, 20) for page 3
        assertEquals(Arrays.asList(10, 20), jdbc.boundParams);
        assertEquals(10, jdbc.fetchSize);
    }

    @Test
    public void testHiveRunsBeforeQueryHook() throws SQLException {
        FakeJdbc jdbc = new FakeJdbc("Hive");
        jdbc.columns = Collections.emptyList();
        jdbc.rows = Collections.emptyList();

        SqlMeta meta = new SqlMeta("SELECT * FROM t", new ArrayList<Object>());
        SqlJdbcUtils.execute(ProductTypeEnum.HIVE, jdbc.connection(), meta, null, 1, 5, true, false);

        assertEquals(1, jdbc.plainStatements.size());
        assertTrue(jdbc.plainStatements.get(0).startsWith("set hive.resultset"));
        // non-MySQL connection with pagination uses the page size as fetch size
        assertEquals(5, jdbc.fetchSize);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testClobAndBlobValuesConverted() throws SQLException {
        FakeJdbc jdbc = new FakeJdbc("FakeDB");
        jdbc.columns = Arrays.asList("TXT", "BIN");
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("TXT", clob("hello"));
        row.put("BIN", blob(new byte[]{9, 8}));
        jdbc.rows = Collections.singletonList(row);

        SqlMeta meta = new SqlMeta("SELECT TXT, BIN FROM t", new ArrayList<Object>());
        List<Map<String, Object>> result = (List<Map<String, Object>>) SqlJdbcUtils.execute(
                ProductTypeEnum.POSTGRESQL, jdbc.connection(), meta, null, 1, 10, false, false);

        assertEquals("hello", result.get(0).get("TXT"));
        assertArrayEquals(new byte[]{9, 8}, (byte[]) result.get(0).get("BIN"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testHttpArrayAndStructConvertedViaJson() throws SQLException {
        FakeJdbc jdbc = new FakeJdbc("Http");
        jdbc.columns = Arrays.asList("ARR", "STRUCT");
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("ARR", array("[1,2,3]"));
        row.put("STRUCT", struct("{\"a\":1}"));
        jdbc.rows = Collections.singletonList(row);

        SqlMeta meta = new SqlMeta("SELECT ARR, STRUCT FROM t", new ArrayList<Object>());
        // isPaging must stay off: the HTTP type defines no page consumer (latent NPE, recorded in the run report)
        List<Map<String, Object>> result = (List<Map<String, Object>>) SqlJdbcUtils.execute(
                ProductTypeEnum.HTTP, jdbc.connection(), meta, null, 1, 10, false, false);

        assertEquals(Arrays.asList(1, 2, 3), result.get(0).get("ARR"));
        Map<String, Object> structValue = (Map<String, Object>) result.get(0).get("STRUCT");
        assertEquals(1, structValue.get("a"));
    }

    @Test
    public void testQueryParametersBoundInOrder() throws SQLException {
        FakeJdbc jdbc = new FakeJdbc("FakeDB");
        jdbc.columns = Collections.emptyList();
        jdbc.rows = Collections.emptyList();

        SqlMeta meta = new SqlMeta("SELECT * FROM t WHERE a = ? AND b = ?",
                new ArrayList<>(Arrays.asList("x", 42)));
        SqlJdbcUtils.execute(ProductTypeEnum.SQLSERVER, jdbc.connection(), meta, null, 1, 10, false, false);
        assertEquals(Arrays.asList("x", 42), jdbc.boundParams);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testBrokenClobConvertsToNull() throws SQLException {
        FakeJdbc jdbc = new FakeJdbc("FakeDB");
        jdbc.columns = Collections.singletonList("TXT");
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("TXT", brokenClob());
        jdbc.rows = Collections.singletonList(row);

        SqlMeta meta = new SqlMeta("SELECT TXT FROM t", new ArrayList<Object>());
        List<Map<String, Object>> result = (List<Map<String, Object>>) SqlJdbcUtils.execute(
                ProductTypeEnum.POSTGRESQL, jdbc.connection(), meta, null, 1, 10, false, false);

        assertTrue(result.get(0).containsKey("TXT"));
        assertNull(result.get(0).get("TXT"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testNegativeLengthClobConvertsToNull() throws SQLException {
        FakeJdbc jdbc = new FakeJdbc("FakeDB");
        jdbc.columns = Collections.singletonList("TXT");
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("TXT", negativeLengthClob());
        jdbc.rows = Collections.singletonList(row);

        SqlMeta meta = new SqlMeta("SELECT TXT FROM t", new ArrayList<Object>());
        List<Map<String, Object>> result = (List<Map<String, Object>>) SqlJdbcUtils.execute(
                ProductTypeEnum.POSTGRESQL, jdbc.connection(), meta, null, 1, 10, false, false);

        assertNull(result.get(0).get("TXT"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testBrokenAndNegativeLengthBlobsConvertToNull() throws SQLException {
        FakeJdbc jdbc = new FakeJdbc("FakeDB");
        jdbc.columns = Arrays.asList("BIN1", "BIN2");
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("BIN1", brokenBlob());
        row.put("BIN2", negativeLengthBlob());
        jdbc.rows = Collections.singletonList(row);

        SqlMeta meta = new SqlMeta("SELECT BIN1, BIN2 FROM t", new ArrayList<Object>());
        List<Map<String, Object>> result = (List<Map<String, Object>>) SqlJdbcUtils.execute(
                ProductTypeEnum.POSTGRESQL, jdbc.connection(), meta, null, 1, 10, false, false);

        assertNull(result.get(0).get("BIN1"));
        assertNull(result.get(0).get("BIN2"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testNonJsonArrayAndStructFallBackToTheRawValue() throws SQLException {
        FakeJdbc jdbc = new FakeJdbc("Http");
        jdbc.columns = Arrays.asList("ARR", "STRUCT");
        Map<String, Object> row = new LinkedHashMap<>();
        Object rawArray = array("[not, json");
        Object rawStruct = struct("{not json");
        row.put("ARR", rawArray);
        row.put("STRUCT", rawStruct);
        jdbc.rows = Collections.singletonList(row);

        SqlMeta meta = new SqlMeta("SELECT ARR, STRUCT FROM t", new ArrayList<Object>());
        List<Map<String, Object>> result = (List<Map<String, Object>>) SqlJdbcUtils.execute(
                ProductTypeEnum.HTTP, jdbc.connection(), meta, null, 1, 10, false, false);

        // unparsable payloads are handed back untouched instead of dropping the column
        assertSame(rawArray, result.get(0).get("ARR"));
        assertSame(rawStruct, result.get(0).get("STRUCT"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testGetObjectFailureKeepsColumnWithNullValue() throws SQLException {
        FakeJdbc jdbc = new FakeJdbc("FakeDB");
        jdbc.columns = Arrays.asList("OK", "BAD");
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("OK", "value");
        row.put("BAD", "dropped");
        jdbc.rows = Collections.singletonList(row);
        jdbc.getObjectFailures.add("BAD");

        SqlMeta meta = new SqlMeta("SELECT OK, BAD FROM t", new ArrayList<Object>());
        List<Map<String, Object>> result = (List<Map<String, Object>>) SqlJdbcUtils.execute(
                ProductTypeEnum.POSTGRESQL, jdbc.connection(), meta, null, 1, 10, false, false);

        // the failed column stays present but null; the healthy column is untouched
        assertEquals("value", result.get(0).get("OK"));
        assertTrue(result.get(0).containsKey("BAD"));
        assertNull(result.get(0).get("BAD"));
    }

    @Test
    public void testPrintSqlLogFlagDoesNotBreakExecution() throws SQLException {
        FakeJdbc jdbc = new FakeJdbc("MySQL");
        jdbc.columns = Collections.singletonList("ID");
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("ID", 1);
        jdbc.rows = Collections.singletonList(row);

        SqlMeta meta = new SqlMeta("SELECT ID FROM t", new ArrayList<Object>());
        Object result = SqlJdbcUtils.execute(ProductTypeEnum.MYSQL, jdbc.connection(), meta,
                null, 1, 10, false, true);

        assertEquals(Collections.singletonList(Collections.singletonMap("ID", 1)), result);
    }

    @Test
    public void testMetaDataFailureIsTreatedAsNonMySqlConnection() throws SQLException {
        FakeJdbc jdbc = new FakeJdbc("MySQL");
        jdbc.metaDataThrows = true;
        jdbc.columns = Collections.emptyList();
        jdbc.rows = Collections.emptyList();

        SqlMeta meta = new SqlMeta("SELECT * FROM t", new ArrayList<Object>());
        SqlJdbcUtils.execute(ProductTypeEnum.MYSQL, jdbc.connection(), meta, null, 1, 10, false, false);

        // detection failed -> not the MySQL streaming path -> fetch size 0 for a non-paged query
        assertEquals(0, jdbc.fetchSize);
    }
}
