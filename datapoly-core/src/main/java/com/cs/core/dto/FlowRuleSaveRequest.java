// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.core.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

@NoArgsConstructor
@Data
@Schema(description = "流控规则详情")
public class FlowRuleSaveRequest {

    @Schema(description = "ID编号")
    private Long id;

    @Schema(description = "标题")
    private String name;

    @Schema(description = "阈值类型")
    private Integer flowGrade;

    @Schema(description = "单机阈值")
    private Integer flowCount;
}
