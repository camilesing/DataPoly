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
@ApiModel("ID名称")
public class IdWithName implements Serializable {

    @ApiModelProperty("ID编号")
    private Long id;

    @ApiModelProperty("名称")
    private String name;
}
