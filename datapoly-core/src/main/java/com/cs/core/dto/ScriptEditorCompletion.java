// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.core.dto;

import io.swagger.annotations.*;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ApiModel("脚本编辑器提示列表")
public class ScriptEditorCompletion {

    @ApiModelProperty("返回值类型")
    private String meta;

    @ApiModelProperty("下拉提示")
    private String caption;

    @ApiModelProperty("选择填充")
    private String value;

    @Builder.Default
    @ApiModelProperty("分数值")
    private Integer score = 1;
}
