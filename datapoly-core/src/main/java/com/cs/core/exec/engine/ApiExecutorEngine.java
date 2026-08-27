// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.core.exec.engine;

import com.cs.common.enums.NamingStrategyEnum;
import com.cs.persistence.entity.ApiContextEntity;

import java.util.*;

public interface ApiExecutorEngine {

    void setPrintSqlLog(boolean printSqlLog);

    /**
     * @param dollarSubstitutionAllowed whether the SQL template allows ${} literal substitution (S3 decision, computed by the caller
     *        from the API open state and the global switches); the script engine passes it through to the db module
     */
    List<Object> execute(List<ApiContextEntity> scripts, Map<String, Object> params, NamingStrategyEnum strategy,
                         boolean dollarSubstitutionAllowed);
}
