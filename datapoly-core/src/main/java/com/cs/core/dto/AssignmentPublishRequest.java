// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.core.dto;

import io.swagger.annotations.*;
import lombok.*;

import javax.validation.constraints.NotNull;

@Data
@NoArgsConstructor
@ApiModel("版本发布请求")
public class AssignmentPublishRequest {

    @NotNull(message = "id不能为null")
    @ApiModelProperty("接口ID")
    private Long id;

    @ApiModelProperty("版本描述")
    private String description;
}
