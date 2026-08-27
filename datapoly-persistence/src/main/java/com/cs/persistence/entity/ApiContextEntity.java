// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.persistence.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName(value = "DATAPOLY_API_CONTEXT", autoResultMap = true)
public class ApiContextEntity {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("api_id")
    private Long apiId;

    @TableField("sql_text")
    private String sqlText;

    public ApiContextEntity(String sqlText) {
        this.sqlText = sqlText;
    }
}
