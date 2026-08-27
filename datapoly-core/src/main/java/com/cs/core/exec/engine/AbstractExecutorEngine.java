// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.core.exec.engine;

import com.cs.common.enums.ProductTypeEnum;
import com.zaxxer.hikari.HikariDataSource;

public abstract class AbstractExecutorEngine implements ApiExecutorEngine {

    protected HikariDataSource dataSource;
    protected ProductTypeEnum productType;
    protected boolean printSqlLog;

    public AbstractExecutorEngine(HikariDataSource dataSource, ProductTypeEnum productType) {
        this.dataSource = dataSource;
        this.productType = productType;
        this.printSqlLog = true;
    }

    @Override
    public void setPrintSqlLog(boolean printSqlLog) {
        this.printSqlLog = printSqlLog;
    }
}
