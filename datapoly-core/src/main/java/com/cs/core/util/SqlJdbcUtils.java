// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.core.util;

import com.cs.common.enums.*;
import com.cs.common.util.LambdaUtils;
import com.cs.core.exec.logger.DebugExecuteLogger;
import com.cs.persistence.util.JsonUtils;
import com.cs.template.SqlMeta;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import java.sql.*;
import java.util.*;
import java.util.function.*;

@Slf4j
@UtilityClass
public class SqlJdbcUtils {

    private static final int QUERY_TIMEOUT = 300;

    public static Function<String, String> getConverter(NamingStrategyEnum strategy) {
        return Objects.isNull(strategy) ? Function.identity() : strategy.getFunction();
    }

    public static Object execute(ProductTypeEnum productType, Connection connection, SqlMeta sqlMeta,
                                 NamingStrategyEnum strategy, int page, int size, boolean isPaging, boolean printSqlLog) throws SQLException {
        List<Object> paramValues = sqlMeta.getParameter();
        boolean isQuerySql = sqlMeta.isQuerySQL();
        boolean appendPagination = isQuerySql && isPaging;
        if (appendPagination && size <= 0) {
            // Second line of defense: clamp an illegal size passed in bypassing PageSizeUtils back to the default page size, avoiding empty LIMIT 0/TOP 0 results
            size = 10;
        }
        String sql = appendPagination
                ? PageSqlUtils.getPageSql(productType, connection, sqlMeta.getSql(), page, size)
                : sqlMeta.getSql();
        Consumer<Connection> executeBeforeQuery = productType.getContext().getExecuteBeforeQuery();
        LambdaUtils.ifDo(Objects.nonNull(executeBeforeQuery), () -> executeBeforeQuery.accept(connection));

        long start = System.currentTimeMillis();
        try (PreparedStatement statement = buildStatement(productType, connection, sql, page, size,
                appendPagination, paramValues, printSqlLog)) {
            Function<String, String> converter = getConverter(strategy);
            if (execute(productType, statement)) {
                try (ResultSet rs = statement.getResultSet()) {
                    List<String> columns = new ArrayList<>();
                    for (int i = 1; i <= rs.getMetaData().getColumnCount(); i++) {
                        String columnName = rs.getMetaData().getColumnLabel(i);
                        columns.add(columnName);
                    }
                    List<Map<String, Object>> list = new ArrayList<>();
                    while (rs.next()) {
                        Map<String, Object> row = new LinkedHashMap<>();
                        for (String column : columns) {
                            try {
                                Object value = rs.getObject(column);
                                if (value instanceof java.sql.Clob) {
                                    // Serializing CLOB/NCLOB (including oracle.sql.CLOB) directly would trigger a
                                    // CLOB.physicalConnection -> Connection self-reference cycle; convert to String uniformly
                                    value = convertClob((java.sql.Clob) value);
                                } else if (value instanceof java.sql.Blob) {
                                    value = convertBlob((java.sql.Blob) value);
                                } else if (ProductTypeEnum.HTTP == productType) {
                                    if (value instanceof java.sql.Array) {
                                        value = convertArray((java.sql.Array) value);
                                    } else if (value instanceof java.sql.Struct) {
                                        value = convertStruct((java.sql.Struct) value);
                                    }
                                }
                                row.put(column, value);
                            } catch (SQLException se) {
                                log.warn("Failed to call jdbc ResultSet::getObject(): {}", se.getMessage(), se);
                                row.put(column, null);
                            }
                        }
                        list.add(ConvertUtils.to(row, converter));
                    }
                    return list;
                }
            } else {
                int updateCount = statement.getUpdateCount();
                return "(" + updateCount + ") rows affected";
            }
        } finally {
            DebugExecuteLogger.add(sql, paramValues, System.currentTimeMillis() - start);
        }
    }

    private static PreparedStatement buildStatement(ProductTypeEnum productType, Connection connection,
                                                    String sql, int page, int size, boolean appendPagination, List<Object> paramValues,
                                                    boolean printSqlLog) throws SQLException {
        PreparedStatement statement = connection.prepareStatement(sql);
        statement.setQueryTimeout(QUERY_TIMEOUT);
        statement.setFetchSize(isMySqlConnection(connection) ? Integer.MIN_VALUE : (appendPagination ? size : 0));
        LambdaUtils.ifDo(appendPagination, () -> productType.getPageConsumer().accept(page, size, paramValues));
        for (int i = 1; i <= paramValues.size(); i++) {
            statement.setObject(i, paramValues.get(i - 1));
        }

        if (printSqlLog) {
            log.info("ExecuteSQL:{}\n{}", sql, paramValues);
        }
        return statement;
    }

    private boolean execute(ProductTypeEnum product, PreparedStatement statement) throws SQLException {
        boolean hasResult = statement.execute();
        if (null == product.getResultSetFunc()) {
            return hasResult;
        }
        return product.getResultSetFunc().apply(hasResult, statement);
    }

    private Object convertArray(java.sql.Array array) {
        try {
            return JsonUtils.toBeanList(array.toString(), Object.class);
        } catch (Exception e) {
            return array;
        }
    }

    private Object convertStruct(java.sql.Struct struct) {
        try {
            return JsonUtils.toBeanObject(struct.toString(), Map.class);
        } catch (Exception e) {
            return struct;
        }
    }

    private Object convertClob(java.sql.Clob clob) {
        try {
            long length = clob.length();
            if (length < 0) {
                return null;
            }
            return clob.getSubString(1, (int) Math.min(length, Integer.MAX_VALUE));
        } catch (Exception e) {
            log.warn("Failed to convert Clob to String: {}", e.getMessage(), e);
            return null;
        }
    }

    private Object convertBlob(java.sql.Blob blob) {
        try {
            long length = blob.length();
            if (length < 0) {
                return null;
            }
            return blob.getBytes(1, (int) Math.min(length, Integer.MAX_VALUE));
        } catch (Exception e) {
            log.warn("Failed to convert Blob to byte[]: {}", e.getMessage(), e);
            return null;
        }
    }

    private boolean isMySqlConnection(Connection connection) {
        try {
            String productName = connection.getMetaData().getDatabaseProductName();
            return productName.contains("MySQL") || productName.contains("MariaDB");
        } catch (Exception e) {
            return false;
        }
    }
}
