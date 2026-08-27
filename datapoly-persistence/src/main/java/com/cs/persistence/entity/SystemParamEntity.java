// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.persistence.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.cs.common.enums.ParamTypeEnum;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName(value = "DATAPOLY_SYSTEM_PARAM", autoResultMap = true)
public class SystemParamEntity {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("param_key")
    private String paramKey;

    @TableField("param_type")
    private ParamTypeEnum paramType;

    @TableField("param_value")
    private String paramValue;
}
