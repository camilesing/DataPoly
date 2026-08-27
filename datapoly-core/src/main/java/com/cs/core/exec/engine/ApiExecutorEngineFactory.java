// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.core.exec.engine;

import com.cs.common.enums.*;
import com.cs.core.exec.engine.impl.*;
import com.zaxxer.hikari.HikariDataSource;

import java.util.*;
import java.util.function.BiFunction;

public class ApiExecutorEngineFactory {

    private static Map<ExecuteEngineEnum, BiFunction<HikariDataSource, ProductTypeEnum, ApiExecutorEngine>> engineMap = new HashMap<>();

    static {
        engineMap.put(ExecuteEngineEnum.SQL, SqlExecutorService::new);
        engineMap.put(ExecuteEngineEnum.SCRIPT, ScriptExecutorService::new);
    }

    public static ApiExecutorEngine getExecutor(ExecuteEngineEnum engine, HikariDataSource dataSource,
                                                ProductTypeEnum productType, boolean printSqlLog) {
        BiFunction<HikariDataSource, ProductTypeEnum, ApiExecutorEngine> creator = engineMap.get(engine);
        if (null == creator) {
            throw new RuntimeException("Unsupported engine :" + engine);
        }
        ApiExecutorEngine executorEngine = creator.apply(dataSource, productType);
        executorEngine.setPrintSqlLog(printSqlLog);
        return executorEngine;
    }

}
