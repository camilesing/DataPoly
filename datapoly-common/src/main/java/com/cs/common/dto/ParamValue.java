// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.common.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "参数信息")
public class ParamValue extends BaseParam {

    @Schema(description = "Object类型的子元素及值")
    private List<BaseParamValue> children;

    @Schema(description = "非数组参数值")
    private String value;

    @Schema(description = "数组参数值")
    private List<String> arrayValues;

    @Data
    public static class BaseParamValue extends BaseParam {

        private String value;

        private List<String> arrayValues;
    }
}
