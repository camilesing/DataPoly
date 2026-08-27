// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.core.dto;

import com.cs.common.service.DisplayRecord;
import lombok.*;
import org.apache.ibatis.reflection.ArrayUtil;

import java.sql.*;
import java.util.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExecuteSqlRecord implements DisplayRecord {

    private String sql;

    private List<Object> parameter;

    private Long costs;


    protected String getParameterValueString() {
        List<Object> typeList = new ArrayList<>(parameter.size());
        for (Object value : parameter) {
            if (value == null) {
                typeList.add("null");
            } else {
                typeList.add(objectValueString(value) + "(" + value.getClass().getSimpleName() + ")");
            }
        }
        final String parameters = typeList.toString();
        return parameters.substring(1, parameters.length() - 1);
    }

    protected String objectValueString(Object value) {
        if (value instanceof Array) {
            try {
                return ArrayUtil.toString(((Array) value).getArray());
            } catch (SQLException e) {
                return value.toString();
            }
        }
        return value.toString();
    }

    @Override
    public String getDisplayText() {
        return "==>   Preparing: " + sql.trim() + "\n"
                + "==>  Parameters: " + getParameterValueString() + "\n"
                + "==>       costs: " + costs + " ms";
    }
}
