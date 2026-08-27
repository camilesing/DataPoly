// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.core.exec.engine.impl;

import com.cs.common.enums.*;
import com.cs.common.exception.*;
import com.cs.common.util.LambdaUtils;
import com.cs.core.exec.engine.AbstractExecutorEngine;
import com.cs.core.util.*;
import com.cs.persistence.entity.ApiContextEntity;
import com.cs.template.*;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;

import java.sql.Connection;
import java.util.*;

@Slf4j
public class SqlExecutorService extends AbstractExecutorEngine {

    public SqlExecutorService(HikariDataSource dataSource, ProductTypeEnum productType) {
        super(dataSource, productType);
    }

    @Override
    public List<Object> execute(List<ApiContextEntity> scripts, Map<String, Object> params, NamingStrategyEnum strategy,
                                boolean dollarSubstitutionAllowed) {
        List<Object> dataList = new ArrayList<>();
        try (Connection connection = this.dataSource.getConnection()) {
            boolean supportsTx = (!productType.offTransactional()) && connection.getMetaData().supportsTransactions();
            try {
                LambdaUtils.ifDo(supportsTx, () -> connection.setAutoCommit(false));
                for (ApiContextEntity sql : scripts) {
                    XmlSqlTemplate template = new XmlSqlTemplate(sql.getSqlText());
                    SqlMeta sqlMeta = processTemplate(template, params, dollarSubstitutionAllowed);
                    int page = PageSizeUtils.getPageFromParams(params);
                    int size = PageSizeUtils.getSizeFromParams(params);
                    boolean isPaging = PageSizeUtils.shouldAppendPagination(params);
                    Object result = SqlJdbcUtils.execute(productType, connection, sqlMeta, strategy, page, size, isPaging,
                            printSqlLog);
                    if (result instanceof Collection) {
                        dataList.add(result);
                    }
                }
                LambdaUtils.ifDo(supportsTx, () -> connection.commit());
                return dataList;
            } catch (Exception e) {
                LambdaUtils.ifDoIgnoreThrow(supportsTx, () -> connection.rollback());
                throw e;
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static SqlMeta processTemplate(XmlSqlTemplate template, Map<String, Object> params,
                                           boolean dollarSubstitutionAllowed) {
        try {
            return template.process(params, dollarSubstitutionAllowed);
        } catch (DollarSubstitutionException e) {
            log.warn("Blocked SQL template with dollar substitution: {}", e.getMessage());
            throw new CommonException(ResponseErrorCode.ERROR_INTERNAL_ERROR, "api.sql.dollar.forbidden");
        }
    }
}
