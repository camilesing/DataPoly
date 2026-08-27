// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.core.dto;

import io.swagger.annotations.ApiModelProperty;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SelectedEntityIdName {

    @ApiModelProperty("ID编号")
    private Long id;

    @ApiModelProperty("名称")
    private String name;

    @ApiModelProperty("是否选中")
    private Boolean selected;
}
