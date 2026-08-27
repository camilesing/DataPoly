// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.core.dto;

import io.swagger.annotations.*;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@ApiModel("带ID的名称")
public class EntityIdNameResponse {

    @ApiModelProperty("ID编号")
    private Long id;

    @ApiModelProperty("名称")
    private String name;
}
