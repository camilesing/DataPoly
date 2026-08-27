// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.core.dto;

import io.swagger.annotations.*;
import lombok.*;

@Data
@NoArgsConstructor
@ApiModel("客户端应用列表搜索")
public class AppClientSearchRequest extends EntitySearchRequest {

    @ApiModelProperty("分组ID")
    private Long groupId;
}
