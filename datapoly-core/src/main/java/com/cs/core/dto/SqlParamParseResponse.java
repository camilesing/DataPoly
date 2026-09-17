// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.core.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.util.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "SQL参数解析")
public class SqlParamParseResponse {

    @Schema(description = "参数名")
    private String name;

    @Schema(description = "是否为数组")
    private Boolean isArray;

    @Schema(description = "Object类型的子元素")
    private List<SqlParamParseResponse> children;

    public SqlParamParseResponse(String name, Boolean isArray) {
        this.name = name;
        this.isArray = isArray;
        this.children = new LinkedList<>();
    }
}
