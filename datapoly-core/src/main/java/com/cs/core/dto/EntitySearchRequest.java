// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.core.dto;

import io.swagger.annotations.*;
import lombok.*;

@Data
@NoArgsConstructor
@ApiModel("列表搜索")
public class EntitySearchRequest {

    @ApiModelProperty("页号")
    private Integer page;

    @ApiModelProperty("页大小")
    private Integer size;

    @ApiModelProperty("关键词")
    private String searchText;

}
