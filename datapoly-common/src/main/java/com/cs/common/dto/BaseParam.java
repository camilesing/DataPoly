// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.common.dto;

import com.cs.common.enums.*;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.io.Serializable;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class BaseParam implements Serializable {

    @Schema(description = "ID(前端生成并使用)")
    private String id;

    @Schema(description = "参数名")
    private String name;

    @Schema(description = "参数类型")
    private ParamTypeEnum type;

    @Schema(description = "参数位置")
    private ParamLocationEnum location;

    @Schema(description = "是否为数组")
    private Boolean isArray;

    @Schema(description = "是否必填")
    private Boolean required;

    @Schema(description = "默认值")
    private String defaultValue;

    @Schema(description = "参数描述")
    private String remark;
}
