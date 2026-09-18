// Use of this source code is governed by a BSD-style license
package com.cs.core.util;

import com.cs.common.enums.ProductTypeEnum;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class PageSqlUtilsTest {

    /**
     * Builds a connection whose prepareStatement(sqlDialect) yields a result set with
     * the given mode value on column 2 (or no row / prepare failure).
     */
    private Connection buildObConnection(String modeValue, boolean hasNext, boolean failPrepare,
                                         List<String> preparedSql) {
        ResultSet resultSet = (ResultSet) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{ResultSet.class},
                (p, method, args) -> {
                    switch (method.getName()) {
                        case "next":
                            return hasNext;
                        case "getString":
                            return modeValue;
                        case "hashCode":
                            return System.identityHashCode(p);
                        case "equals":
                            return p == args[0];
                        default:
                            return null;
                    }
                });
        PreparedStatement statement = (PreparedStatement) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{PreparedStatement.class},
                (p, method, args) -> {
                    switch (method.getName()) {
                        case "executeQuery":
                            return resultSet;
                        case "hashCode":
                            return System.identityHashCode(p);
                        case "equals":
                            return p == args[0];
                        default:
                            return null;
                    }
                });
        return (Connection) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{Connection.class},
                (p, method, args) -> {
                    switch (method.getName()) {
                        case "prepareStatement":
                            if (failPrepare) {
                                throw new SQLException("prepare failed");
                            }
                            preparedSql.add((String) args[0]);
                            return statement;
                        case "hashCode":
                            return System.identityHashCode(p);
                        case "equals":
                            return p == args[0];
                        default:
                            return null;
                    }
                });
    }

    @Test
    public void testNonOceanBaseDelegatesToProductType() {
        assertEquals("SELECT 1 LIMIT ? OFFSET ? ",
                PageSqlUtils.getPageSql(ProductTypeEnum.MYSQL, null, "SELECT 1", 2, 10));
    }

    @Test
    public void testOceanBaseMysqlModeUsesMysqlPaging() {
        List<String> preparedSql = new ArrayList<>();
        Connection conn = buildObConnection("MySQL", true, false, preparedSql);
        assertEquals("SELECT 1 LIMIT ? OFFSET ? ",
                PageSqlUtils.getPageSql(ProductTypeEnum.OCEANBASE, conn, "SELECT 1", 2, 10));
        assertEquals(PageSqlUtils.sqlDialect, preparedSql.get(0));
    }

    @Test
    public void testOceanBaseOracleModeUsesOraclePaging() {
        Connection conn = buildObConnection("Oracle", true, false, new ArrayList<String>());
        String pageSql = PageSqlUtils.getPageSql(ProductTypeEnum.OCEANBASE, conn, "SELECT 1", 2, 10);
        assertTrue(pageSql.contains("ROWNUM"));
    }

    @Test
    public void testOceanBaseNullModeValueRejected() {
        Connection conn = buildObConnection(null, true, false, new ArrayList<String>());
        try {
            PageSqlUtils.isInMysqlMode(conn);
            fail("null mode value must be rejected");
        } catch (RuntimeException expected) {
            assertTrue(expected.getMessage().contains("return null value"));
        }
    }

    @Test
    public void testOceanBaseNoResultRejected() {
        Connection conn = buildObConnection("MySQL", false, false, new ArrayList<String>());
        try {
            PageSqlUtils.isInMysqlMode(conn);
            fail("empty mode result must be rejected");
        } catch (RuntimeException expected) {
            assertTrue(expected.getMessage().contains("no result"));
        }
    }

    @Test
    public void testSqlFailureFallsBackToMysqlModeGuess() {
        Connection conn = buildObConnection("MySQL", true, true, new ArrayList<String>());
        assertTrue(PageSqlUtils.isInMysqlMode(conn));
    }
}
