// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.common.dto;

import com.cs.common.enums.*;
import io.swagger.annotations.ApiModelProperty;
import lombok.*;

import java.io.Serializable;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class BaseParam implements Serializable {

    @ApiModelProperty("ID(前端生成并使用)")
    private String id;

    @ApiModelProperty("参数名")
    private String name;

    @ApiModelProperty("参数类型")
    private ParamTypeEnum type;

    @ApiModelProperty("参数位置")
    private ParamLocationEnum location;

    @ApiModelProperty("是否为数组")
    private Boolean isArray;

    @ApiModelProperty("是否必填")
    private Boolean required;

    @ApiModelProperty("默认值")
    private String defaultValue;

    @ApiModelProperty("参数描述")
    private String remark;
}
