// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.template;

import java.util.List;

public class SqlMeta {

    private String sql;
    private List<Object> parameter;

    public SqlMeta(String sql, List<Object> parameter) {
        super();
        this.sql = sql.trim();
        this.parameter = parameter;
    }

    public String getSql() {
        return sql;
    }

    public void setSql(String sql) {
        this.sql = sql.trim();
    }

    public List<Object> getParameter() {
        return parameter;
    }

    public void setParameter(List<Object> parameter) {
        this.parameter = parameter;
    }

    /**
     * Whether this is a query statement: strips leading whitespace/parentheses/line/block comments before checking the SELECT/WITH prefix,
     * so `(select ...)` and queries with leading comments correctly enter the pagination-append branch.
     */
    public boolean isQuerySQL() {
        String upperSql = sql.toUpperCase();
        int idx = 0;
        while (idx < upperSql.length()) {
            char c = upperSql.charAt(idx);
            if (Character.isWhitespace(c) || c == '(') {
                idx++;
                continue;
            }
            if (c == '-' && idx + 1 < upperSql.length() && upperSql.charAt(idx + 1) == '-') {
                int lineEnd = upperSql.indexOf('\n', idx);
                if (lineEnd < 0) {
                    return false;
                }
                idx = lineEnd + 1;
                continue;
            }
            if (c == '/' && idx + 1 < upperSql.length() && upperSql.charAt(idx + 1) == '*') {
                int blockEnd = upperSql.indexOf("*/", idx + 2);
                if (blockEnd < 0) {
                    return false;
                }
                idx = blockEnd + 2;
                continue;
            }
            break;
        }
        String stripped = upperSql.substring(idx);
        return stripped.startsWith("SELECT") || stripped.startsWith("WITH");
    }

    @Override
    public String toString() {
        return "SqlMeta [sql=" + sql + ", parameter=" + parameter + "]";
    }


}
