// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.core.dto;

import io.swagger.annotations.*;
import lombok.*;

import javax.validation.constraints.NotNull;
import java.util.List;

@Data
@NoArgsConstructor
@ApiModel("客户端分组关联")
public class AppClientGroupRequest {

    @NotNull(message = "id不能为null")
    @ApiModelProperty("客户端应用ID")
    private Long id;

    @ApiModelProperty("分组ID列表")
    private List<Long> groupIds;
}
