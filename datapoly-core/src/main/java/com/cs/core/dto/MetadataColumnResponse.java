// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.core.dto;

import io.swagger.annotations.*;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ApiModel("列信息")
public class MetadataColumnResponse {

    @ApiModelProperty("列名")
    private String name;

    @ApiModelProperty("列类型")
    private String type;

    @ApiModelProperty("注释")
    private String remarks;
}
