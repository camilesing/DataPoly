// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.persistence.handler;

import com.cs.common.enums.DataTypeFormatEnum;
import com.cs.persistence.util.JsonUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import org.apache.ibatis.type.*;

import java.sql.*;
import java.util.*;

public class FormatMapHandler extends BaseTypeHandler<Map<DataTypeFormatEnum, String>> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, Map<DataTypeFormatEnum, String> value,
                                    JdbcType jdbcType) throws SQLException {
        ps.setString(i, map2string(value));
    }

    @Override
    public Map<DataTypeFormatEnum, String> getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return string2map(rs.getString(columnName));
    }

    @Override
    public Map<DataTypeFormatEnum, String> getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return string2map(rs.getString(columnIndex));
    }

    @Override
    public Map<DataTypeFormatEnum, String> getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return string2map(cs.getString(columnIndex));
    }

    private String map2string(Map<DataTypeFormatEnum, String> map) {
        if (map == null || map.isEmpty()) {
            return null;
        }
        return JsonUtils.toJsonString(map);
    }

    private Map<DataTypeFormatEnum, String> string2map(String str) {
        if (str == null || str.isEmpty()) {
            return new HashMap<>(2);
        }
        return JsonUtils.toBeanType(str, new TypeReference<Map<DataTypeFormatEnum, String>>() {
        });
    }
}
