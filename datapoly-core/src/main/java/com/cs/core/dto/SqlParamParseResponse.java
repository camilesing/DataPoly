// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.core.dto;

import io.swagger.annotations.*;
import lombok.*;

import java.util.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@ApiModel("SQL参数解析")
public class SqlParamParseResponse {

    @ApiModelProperty("参数名")
    private String name;

    @ApiModelProperty("是否为数组")
    private Boolean isArray;

    @ApiModelProperty("Object类型的子元素")
    private List<SqlParamParseResponse> children;

    public SqlParamParseResponse(String name, Boolean isArray) {
        this.name = name;
        this.isArray = isArray;
        this.children = new LinkedList<>();
    }
}
