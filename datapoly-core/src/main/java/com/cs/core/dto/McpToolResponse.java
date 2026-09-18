// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.core.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.sql.Timestamp;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "MCP工具详情")
public class McpToolResponse {

    @Schema(description = "ID编号")
    private Long id;

    @Schema(description = "工具名称")
    private String name;

    @Schema(description = "工具描述")
    private String description;

    @Schema(description = "接口模块ID")
    private Long moduleId;

    @Schema(description = "接口模块名称")
    private String moduleName;

    @Schema(description = "接口ID")
    private Long apiId;

    @Schema(description = "接口名称")
    private String apiName;

    @Schema(description = "接口Method")
    private String apiMethod;

    @Schema(description = "接口Path")
    private String apiPath;

    @Schema(description = "创建时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Timestamp createTime;

    @Schema(description = "更新时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Timestamp updateTime;
}
