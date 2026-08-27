// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.common.dto;

import io.swagger.annotations.*;
import lombok.*;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@ApiModel("参数信息")
public class ParamValue extends BaseParam {

    @ApiModelProperty("Object类型的子元素及值")
    private List<BaseParamValue> children;

    @ApiModelProperty("非数组参数值")
    private String value;

    @ApiModelProperty("数组参数值")
    private List<String> arrayValues;

    @Data
    public static class BaseParamValue extends BaseParam {

        private String value;

        private List<String> arrayValues;
    }
}
