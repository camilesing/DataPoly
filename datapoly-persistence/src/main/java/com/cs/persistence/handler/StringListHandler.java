// Use of this source code is governed by a BSD-style license
package com.cs.persistence.handler;

import com.cs.persistence.util.JsonUtils;
import org.apache.ibatis.type.*;

import java.sql.*;
import java.util.*;

public class StringListHandler extends BaseTypeHandler<List<String>> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, List<String> list, JdbcType jdbcType)
            throws SQLException {
        ps.setString(i, JsonUtils.toJsonString(list));
    }

    @Override
    public List<String> getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return string2list(rs.getString(columnName));
    }

    @Override
    public List<String> getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return string2list(rs.getString(columnIndex));
    }

    @Override
    public List<String> getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return string2list(cs.getString(columnIndex));
    }

    private List<String> string2list(String str) {
        if (str == null || str.isEmpty()) {
            return new ArrayList<>();
        }
        return JsonUtils.toBeanList(str, String.class);
    }
}
