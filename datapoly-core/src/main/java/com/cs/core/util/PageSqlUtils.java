// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.core.util;

import com.cs.common.enums.ProductTypeEnum;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import java.sql.*;
import java.util.Objects;

@Slf4j
@UtilityClass
public class PageSqlUtils {

    public static final String sqlDialect = "show global variables where variable_name = 'ob_compatibility_mode'";

    public static String getPageSql(ProductTypeEnum productType, Connection conn, String sql, Integer page,
                                    Integer size) {
        return pagingDialect(productType, conn).getPageSql(sql, page, size);
    }

    /**
     * The dialect that decides both the paging SQL and the order of its bound values. OceanBase speaks
     * Oracle when its tenant runs in Oracle mode; taking the SQL from one dialect and the values from
     * the other swaps the bounds (ROWNUM needs the upper bound first, LIMIT the page size).
     */
    public static ProductTypeEnum pagingDialect(ProductTypeEnum productType, Connection connection) {
        if (ProductTypeEnum.OCEANBASE == productType && !PageSqlUtils.isInMysqlMode(connection)) {
            return ProductTypeEnum.ORACLE;
        }
        return productType;
    }

    public static boolean isInMysqlMode(Connection connection) {
        try (PreparedStatement statement = connection.prepareStatement(sqlDialect)) {
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    String value = resultSet.getString(2);
                    if (Objects.nonNull(value)) {
                        if (value.toUpperCase().contains("MYSQL")) {
                            return true;
                        } else {
                            return false;
                        }
                    } else {
                        throw new RuntimeException("Execute SQL[" + sqlDialect + "] return null value");
                    }
                } else {
                    throw new RuntimeException("Execute SQL[" + sqlDialect + "] no result");
                }
            }
        } catch (SQLException sqlException) {
            if (log.isDebugEnabled()) {
                log.debug("Failed to execute sql :{}, and guesses OceanBase is MySQL Mode!", sqlDialect);
            }
        }
        return true;
    }
}