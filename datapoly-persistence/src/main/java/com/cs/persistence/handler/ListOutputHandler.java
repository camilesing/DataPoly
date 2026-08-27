// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.persistence.handler;

import com.cs.common.dto.OutParam;
import com.cs.persistence.util.JsonUtils;
import org.apache.ibatis.type.*;

import java.sql.*;
import java.util.*;

public class ListOutputHandler extends BaseTypeHandler<List<OutParam>> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, List<OutParam> list, JdbcType jdbcType)
            throws SQLException {
        ps.setString(i, list2string(list));
    }

    @Override
    public List<OutParam> getNullableResult(ResultSet rs, String columnName)
            throws SQLException {
        String r = rs.getString(columnName);
        if (rs.wasNull()) {
            return null;
        }
        return string2list(r);
    }

    @Override
    public List<OutParam> getNullableResult(ResultSet rs, int columnIndex)
            throws SQLException {
        String r = rs.getString(columnIndex);
        if (rs.wasNull()) {
            return null;
        }
        return string2list(r);
    }

    @Override
    public List<OutParam> getNullableResult(CallableStatement cs, int columnIndex)
            throws SQLException {
        String r = cs.getString(columnIndex);
        if (cs.wasNull()) {
            return null;
        }
        return string2list(r);
    }

    private String list2string(List<OutParam> list) {
        if (list == null || list.isEmpty()) {
            return null;
        }
        return JsonUtils.toJsonString(list);
    }

    private List<OutParam> string2list(String str) {
        if (str == null || str.isEmpty()) {
            return new ArrayList<>();
        }
        return JsonUtils.toBeanList(str, OutParam.class);
    }
}
