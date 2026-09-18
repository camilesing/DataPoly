// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.core.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "脚本编辑器提示列表")
public class ScriptEditorCompletion {

    @Schema(description = "返回值类型")
    private String meta;

    @Schema(description = "下拉提示")
    private String caption;

    @Schema(description = "选择填充")
    private String value;

    @Builder.Default
    @Schema(description = "分数值")
    private Integer score = 1;
}
