// Use of this source code is governed by a BSD-style license
package com.cs.persistence.handler;

import com.cs.persistence.util.JsonUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import org.apache.ibatis.type.*;

import java.sql.*;
import java.util.*;

public class StringMapHandler extends BaseTypeHandler<Map<String, String>> {

    private static final TypeReference<Map<String, String>> TYPE = new TypeReference<Map<String, String>>() {
    };

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, Map<String, String> map, JdbcType jdbcType)
            throws SQLException {
        ps.setString(i, JsonUtils.toJsonString(map));
    }

    @Override
    public Map<String, String> getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return string2map(rs.getString(columnName));
    }

    @Override
    public Map<String, String> getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return string2map(rs.getString(columnIndex));
    }

    @Override
    public Map<String, String> getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return string2map(cs.getString(columnIndex));
    }

    private Map<String, String> string2map(String str) {
        if (str == null || str.isEmpty()) {
            return new LinkedHashMap<>();
        }
        return JsonUtils.toBeanType(str, TYPE);
    }
}
