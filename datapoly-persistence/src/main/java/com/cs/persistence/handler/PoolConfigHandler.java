// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.persistence.handler;

import com.cs.persistence.entity.PoolConfig;
import com.cs.persistence.util.JsonUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.type.*;

import java.sql.*;

/**
 * MyBatis type handler for PoolConfig as JSON
 */
@Slf4j
public class PoolConfigHandler extends BaseTypeHandler<PoolConfig> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, PoolConfig parameter, JdbcType jdbcType)
            throws SQLException {
        ps.setString(i, JsonUtils.toJsonString(parameter));
    }

    @Override
    public PoolConfig getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return parseJson(rs.getString(columnName));
    }

    @Override
    public PoolConfig getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return parseJson(rs.getString(columnIndex));
    }

    @Override
    public PoolConfig getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return parseJson(cs.getString(columnIndex));
    }

    private PoolConfig parseJson(String json) {
        if (json == null || json.isEmpty()) {
            return PoolConfig.DEFAULT;
        }
        return JsonUtils.toBeanObject(json, PoolConfig.class);
    }
}
