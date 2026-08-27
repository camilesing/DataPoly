// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.core.dto;

import io.swagger.annotations.*;
import lombok.*;

import javax.validation.constraints.*;

@Data
@NoArgsConstructor
@ApiModel("MCP工具配置")
public class McpToolSaveRequest {

    @ApiModelProperty("ID编号(保存接口使用)")
    private Long id;

    @NotNull(message = "apiId不能为null")
    @ApiModelProperty("API的ID")
    private Long apiId;

    @NotBlank(message = "name不能为空")
    @ApiModelProperty("工具名称")
    private String name;

    @NotBlank(message = "description不能为空")
    @ApiModelProperty("工具描述")
    private String description;
}
