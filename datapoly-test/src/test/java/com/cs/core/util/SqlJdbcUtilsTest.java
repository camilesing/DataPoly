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
}
