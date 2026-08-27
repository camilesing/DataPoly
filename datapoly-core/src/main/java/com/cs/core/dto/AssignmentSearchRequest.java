// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.core.dto;

import io.swagger.annotations.*;
import lombok.*;

@Data
@NoArgsConstructor
@ApiModel("列表搜索")
public class AssignmentSearchRequest extends EntitySearchRequest {

    @ApiModelProperty("是否上线")
    private Boolean online;

    @ApiModelProperty("分组ID")
    private Long groupId;

    @ApiModelProperty("模块ID")
    private Long moduleId;

    @ApiModelProperty("是否公开")
    private Boolean open;
}
