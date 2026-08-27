// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.core.dto;

import io.swagger.annotations.*;
import lombok.*;

@NoArgsConstructor
@Data
@ApiModel("流控规则详情")
public class FlowRuleSaveRequest {

    @ApiModelProperty("ID编号")
    private Long id;

    @ApiModelProperty("标题")
    private String name;

    @ApiModelProperty("阈值类型")
    private Integer flowGrade;

    @ApiModelProperty("单机阈值")
    private Integer flowCount;
}
