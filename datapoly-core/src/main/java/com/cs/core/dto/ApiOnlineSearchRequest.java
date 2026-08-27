// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.core.dto;

import io.swagger.annotations.*;
import lombok.*;

import java.util.List;

@Data
@NoArgsConstructor
@ApiModel("接口搜索")
public class ApiOnlineSearchRequest extends EntitySearchRequest {

    @ApiModelProperty("模块ID")
    private List<Long> moduleIds;
}
