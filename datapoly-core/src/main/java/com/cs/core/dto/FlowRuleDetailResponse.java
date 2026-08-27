// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.core.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.annotations.*;
import lombok.*;

import java.sql.Timestamp;

@NoArgsConstructor
@Data
@ApiModel("流控规则详情")
public class FlowRuleDetailResponse {

    @ApiModelProperty("ID编号")
    private Long id;

    @ApiModelProperty("标题")
    private String name;

    @ApiModelProperty("阈值类型")
    private Integer flowGrade;

    @ApiModelProperty("单机阈值")
    private Integer flowCount;

    @ApiModelProperty("创建时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Timestamp createTime;

    @ApiModelProperty("更新时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Timestamp updateTime;
}
