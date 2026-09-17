// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.core.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.sql.Timestamp;

@NoArgsConstructor
@Data
@Schema(description = "流控规则详情")
public class FlowRuleDetailResponse {

    @Schema(description = "ID编号")
    private Long id;

    @Schema(description = "标题")
    private String name;

    @Schema(description = "阈值类型")
    private Integer flowGrade;

    @Schema(description = "单机阈值")
    private Integer flowCount;

    @Schema(description = "创建时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Timestamp createTime;

    @Schema(description = "更新时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Timestamp updateTime;
}
