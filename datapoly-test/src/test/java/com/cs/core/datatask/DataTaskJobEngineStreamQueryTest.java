// Use of this source code is governed by a BSD-style license
package com.cs.core.datatask;

import com.cs.common.datatask.ColumnMetadata;
import com.cs.common.enums.NamingStrategyEnum;
import com.cs.common.enums.ProductTypeEnum;
import com.cs.template.SqlMeta;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.Assert;
import org.junit.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Locks the JDBC-level contract of {@link DataTaskJobEngine#streamQuery} using
 * proxy doubles: the single statement runs inside an explicit transaction so
 * PostgreSQL-family drivers honor the fetch size instead of buffering the whole
 * result set, DML is committed before the connection goes back to the pool, and
 * auto-commit is restored only when the engine flipped it.
 */
public class DataTaskJobEngineStreamQueryTest {

    /** Shared state between the proxy doubles plus a sequential call log. */
    private static class Trace {
        final List<String> calls = new ArrayList<>();
        final List<Integer> fetchSizes = new ArrayList<>();
        boolean autoCommit = true;
        boolean autoCommitAtExecute;
        boolean committed;
        boolean closed;
        boolean rowset;
        boolean executed;
        int updateCount;
        int remainingRows;
        Object cellValue;
        String productName = "PostgreSQL";
    }

    private static class CaptureChannel implements DataTaskJobEngine.ResultChannel {
        List<String> columns;
        final List<Object[]> rows = new ArrayList<>();

        @Override
        public void start(List<String> columns, List<ColumnMetadata> metadata) {
            this.columns = new ArrayList<>(columns);
        }

        @Override
        public boolean batch(List<Object[]> batch) {
            rows.addAll(batch);
            return true;
        }
    }

    private static final class FakeJdbc {
        final Trace trace;
        final Connection connection;
        final HikariDataSource dataSource;

        FakeJdbc(Trace trace) {
            this.trace = trace;
            this.connection = connectionProxy(trace);
            this.dataSource = new HikariDataSource() {
                @Override
                public Connection getConnection() {
                    return connection;
                }
            };
        }
    }

    private static Connection connectionProxy(final Trace trace) {
        final Connection[] self = new Connection[1];
        InvocationHandler handler = new InvocationHandler() {
            @Override
            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                String name = method.getName();
                trace.calls.add("conn." + name);
                if ("getAutoCommit".equals(name)) {
                    return trace.autoCommit;
                }
                if ("setAutoCommit".equals(name)) {
                    trace.autoCommit = (Boolean) args[0];
                    return null;
                }
                if ("commit".equals(name)) {
                    trace.committed = true;
                    return null;
                }
                if ("close".equals(name)) {
                    trace.closed = true;
                    return null;
                }
                if ("prepareStatement".equals(name)) {
                    return statementProxy((String) args[0], trace);
                }
                if ("getMetaData".equals(name)) {
                    return databaseMetaDataProxy(trace);
                }
                return scalarDefault(method.getReturnType());
            }
        };
        self[0] = (Connection) Proxy.newProxyInstance(
                DataTaskJobEngineStreamQueryTest.class.getClassLoader(),
                new Class<?>[]{Connection.class}, handler);
        return self[0];
    }

    private static PreparedStatement statementProxy(final String sql, final Trace trace) {
        final ResultSet[] resultSet = new ResultSet[1];
        InvocationHandler handler = new InvocationHandler() {
            @Override
            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                String name = method.getName();
                trace.calls.add("stmt." + name);
                if ("setQueryTimeout".equals(name)) {
                    return null;
                }
                if ("setFetchSize".equals(name)) {
                    trace.fetchSizes.add((Integer) args[0]);
                    return null;
                }
                if ("setObject".equals(name)) {
                    return null;
                }
                if ("execute".equals(name)) {
                    trace.executed = true;
                    trace.autoCommitAtExecute = trace.autoCommit;
                    return trace.rowset;
                }
                if ("getUpdateCount".equals(name)) {
                    return trace.updateCount;
                }
                if ("getResultSet".equals(name)) {
                    resultSet[0] = resultSetProxy(trace);
                    return resultSet[0];
                }
                if ("close".equals(name)) {
                    return null;
                }
                if ("toString".equals(name)) {
                    return "statement(" + sql + ")";
                }
                return scalarDefault(method.getReturnType());
            }
        };
        return (PreparedStatement) Proxy.newProxyInstance(
                DataTaskJobEngineStreamQueryTest.class.getClassLoader(),
                new Class<?>[]{PreparedStatement.class}, handler);
    }

    private static ResultSet resultSetProxy(final Trace trace) {
        InvocationHandler handler = new InvocationHandler() {
            @Override
            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                String name = method.getName();
                trace.calls.add("rs." + name);
                if ("next".equals(name)) {
                    return trace.remainingRows-- > 0;
                }
                if ("getObject".equals(name)) {
                    return trace.cellValue;
                }
                if ("getMetaData".equals(name)) {
                    return resultSetMetaDataProxy(trace);
                }
                if ("close".equals(name)) {
                    return null;
                }
                return scalarDefault(method.getReturnType());
            }
        };
        return (ResultSet) Proxy.newProxyInstance(
                DataTaskJobEngineStreamQueryTest.class.getClassLoader(),
                new Class<?>[]{ResultSet.class}, handler);
    }

    private static ResultSetMetaData resultSetMetaDataProxy(final Trace trace) {
        InvocationHandler handler = new InvocationHandler() {
            @Override
            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                String name = method.getName();
                trace.calls.add("meta." + name);
                if ("getColumnCount".equals(name)) {
                    return 1;
                }
                if ("getColumnLabel".equals(name)) {
                    return "c1";
                }
                if ("getColumnType".equals(name)) {
                    return Types.VARCHAR;
                }
                if ("getColumnClassName".equals(name)) {
                    return "java.lang.String";
                }
                return scalarDefault(method.getReturnType());
            }
        };
        return (ResultSetMetaData) Proxy.newProxyInstance(
                DataTaskJobEngineStreamQueryTest.class.getClassLoader(),
                new Class<?>[]{ResultSetMetaData.class}, handler);
    }

    private static DatabaseMetaData databaseMetaDataProxy(final Trace trace) {
        InvocationHandler handler = new InvocationHandler() {
            @Override
            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                String name = method.getName();
                trace.calls.add("dbmd." + name);
                if ("getDatabaseProductName".equals(name)) {
                    return trace.productName;
                }
                return scalarDefault(method.getReturnType());
            }
        };
        return (DatabaseMetaData) Proxy.newProxyInstance(
                DataTaskJobEngineStreamQueryTest.class.getClassLoader(),
                new Class<?>[]{DatabaseMetaData.class}, handler);
    }

    private static Object scalarDefault(Class<?> type) {
        if (type == boolean.class) {
            return Boolean.FALSE;
        }
        if (type == int.class) {
            return Integer.valueOf(0);
        }
        if (type == long.class) {
            return Long.valueOf(0);
        }
        return null;
    }

    private DataTaskJobEngine.StreamSpec spec(HikariDataSource dataSource, int fetchSize) {
        return DataTaskJobEngine.StreamSpec.builder()
                .dataSource(dataSource)
                .product(ProductTypeEnum.POSTGRESQL)
                .sqlMeta(new SqlMeta("SELECT c1 FROM t", Collections.<Object>emptyList()))
                .naming(NamingStrategyEnum.NONE)
                .cancelSupplier(new java.util.function.BooleanSupplier() {
                    @Override
                    public boolean getAsBoolean() {
                        return false;
                    }
                })
                .rowLimit(10L)
                .flushIntervalMs(5000L)
                .fetchSize(fetchSize)
                .timeoutSeconds(1800)
                .progress(null)
                .build();
    }

    @Test
    public void rowsetRunsInLocalTransactionWithBoundedFetchAndRestoresAutoCommit() throws Exception {
        Trace trace = new Trace();
        trace.rowset = true;
        trace.remainingRows = 1;
        trace.cellValue = "v1";
        FakeJdbc jdbc = new FakeJdbc(trace);
        CaptureChannel channel = new CaptureChannel();
        DataTaskJobEngine engine = new DataTaskJobEngine();

        DataTaskJobEngine.StreamResult result = engine.streamQuery(spec(jdbc.dataSource, 1000), channel);

        Assert.assertTrue(result.isRowset());
        Assert.assertEquals(1L, result.getRows());
        Assert.assertEquals(Collections.singletonList("c1"), channel.columns);
        Assert.assertEquals(1, channel.rows.size());
        Assert.assertArrayEquals(new Object[]{"v1"}, channel.rows.get(0));

        // auto-commit flipped off before the statement ran, so the PG-family driver
        // must treat fetch size as a statement cursor bound instead of buffering all
        Assert.assertFalse(trace.autoCommitAtExecute);
        Assert.assertTrue(trace.executed);
        Assert.assertEquals(Collections.singletonList(Integer.valueOf(1000)), trace.fetchSizes);
        // reads never commit, the engine restores auto-commit before closing, and
        // restore + close happen after the statement finished
        Assert.assertFalse(trace.committed);
        Assert.assertTrue(indexOf(trace.calls, "conn.setAutoCommit")
                < indexOf(trace.calls, "conn.prepareStatement"));
        Assert.assertTrue(lastIndexOf(trace.calls, "conn.setAutoCommit") > indexOf(trace.calls, "stmt.close"));
        Assert.assertTrue(lastIndexOf(trace.calls, "conn.setAutoCommit") < indexOf(trace.calls, "conn.close"));
        Assert.assertTrue(trace.closed);
    }

    @Test
    public void updateStatementCommitsBeforeRestoringAutoCommit() throws Exception {
        Trace trace = new Trace();
        trace.rowset = false;
        trace.updateCount = 7;
        FakeJdbc jdbc = new FakeJdbc(trace);
        CaptureChannel channel = new CaptureChannel();
        DataTaskJobEngine engine = new DataTaskJobEngine();

        DataTaskJobEngine.StreamResult result = engine.streamQuery(spec(jdbc.dataSource, 1000), channel);

        Assert.assertFalse(result.isRowset());
        Assert.assertEquals(7, result.getUpdateCount());
        Assert.assertTrue(channel.rows.isEmpty());
        // DML now runs outside autocommit: rows must be committed, and the commit
        // lands after execute but before restore + close
        Assert.assertTrue(trace.committed);
        Assert.assertTrue(indexOf(trace.calls, "conn.commit") > indexOf(trace.calls, "stmt.execute"));
        Assert.assertTrue(indexOf(trace.calls, "conn.commit") < lastIndexOf(trace.calls, "conn.setAutoCommit"));
        Assert.assertTrue(lastIndexOf(trace.calls, "conn.setAutoCommit") < indexOf(trace.calls, "conn.close"));
    }

    @Test
    public void autocommitAlreadyDisabledIsLeftAlone() throws Exception {
        Trace trace = new Trace();
        trace.autoCommit = false;
        trace.rowset = false;
        trace.updateCount = 3;
        FakeJdbc jdbc = new FakeJdbc(trace);
        DataTaskJobEngine engine = new DataTaskJobEngine();

        DataTaskJobEngine.StreamResult result = engine.streamQuery(spec(jdbc.dataSource, 1000), new CaptureChannel());

        Assert.assertEquals(3, result.getUpdateCount());
        // the pool owns the autocommit state here: the engine must neither flip nor
        // restore it, only commit its own single-statement transaction
        Assert.assertFalse(contains(trace.calls, "conn.setAutoCommit"));
        Assert.assertTrue(trace.committed);
        Assert.assertTrue(trace.closed);
    }

    private static int indexOf(List<String> calls, String name) {
        return calls.indexOf(name);
    }

    private static int lastIndexOf(List<String> calls, String name) {
        return calls.lastIndexOf(name);
    }

    private static boolean contains(List<String> calls, String name) {
        return calls.contains(name);
    }
}