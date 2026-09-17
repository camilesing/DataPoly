// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.core.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import jakarta.validation.constraints.*;

@Data
@NoArgsConstructor
@Schema(description = "MCP工具配置")
public class McpToolSaveRequest {

    @Schema(description = "ID编号(保存接口使用)")
    private Long id;

    @NotNull(message = "apiId不能为null")
    @Schema(description = "API的ID")
    private Long apiId;

    @NotBlank(message = "name不能为空")
    @Schema(description = "工具名称")
    private String name;

    @NotBlank(message = "description不能为空")
    @Schema(description = "工具描述")
    private String description;
}
