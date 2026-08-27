// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.common.dto;

import io.swagger.annotations.*;
import lombok.*;

import java.io.Serializable;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@ApiModel("统计数量")
public class NameCount implements Serializable {

    @ApiModelProperty("名称")
    private String name;

    @ApiModelProperty("数量")
    private Long count;
}
