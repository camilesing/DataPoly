// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.common.dto;

import lombok.*;
import org.apache.commons.lang3.tuple.Pair;

import java.io.Serializable;
import java.sql.*;
import java.util.List;
import java.util.function.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductContext implements Serializable {

    private int id;
    private String quote;
    private String name;
    private String driver;
    private int defaultPort;
    private boolean multiDialect;
    private String testSql;
    private String urlPrefix;
    private String[] tplUrls;
    private String urlSample;
    private String sqlSchemaList;
    private List<String> retSchemaList;
    private boolean hasCatalogAndSchema;
    private boolean noViewTables;
    private Function<String, Pair<String, String>> adapter;
    private String pageSql;
    private ThreeConsumer<Integer, Integer, List<Object>> pageConsumer;
    private Consumer<Connection> executeBeforeQuery;
    private BiFunction<Boolean, Statement, Boolean> resultSetFunc;
}
