// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.core.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.annotations.*;
import lombok.*;

import java.sql.Timestamp;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ApiModel("MCP工具详情")
public class McpToolResponse {

    @ApiModelProperty("ID编号")
    private Long id;

    @ApiModelProperty("工具名称")
    private String name;

    @ApiModelProperty("工具描述")
    private String description;

    @ApiModelProperty("接口模块ID")
    private Long moduleId;

    @ApiModelProperty("接口模块名称")
    private String moduleName;

    @ApiModelProperty("接口ID")
    private Long apiId;

    @ApiModelProperty("接口名称")
    private String apiName;

    @ApiModelProperty("接口Method")
    private String apiMethod;

    @ApiModelProperty("接口Path")
    private String apiPath;

    @ApiModelProperty("创建时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Timestamp createTime;

    @ApiModelProperty("更新时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Timestamp updateTime;
}
