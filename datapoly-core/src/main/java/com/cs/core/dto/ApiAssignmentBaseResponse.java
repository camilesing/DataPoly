// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.core.dto;

import com.cs.common.enums.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.sql.Timestamp;

@Data
@NoArgsConstructor
@Schema(description = "API配置简单详情")
public class ApiAssignmentBaseResponse {

    @Schema(description = "ID编号")
    private Long id;

    @Schema(description = "API配置名称")
    private String name;

    @Schema(description = "描述信息")
    private String description;

    @Schema(description = "模块ID")
    private Long moduleId;

    @Schema(description = "模块名称")
    private String moduleName;

    @Schema(description = "授权分组ID")
    private Long groupId;

    @Schema(description = "授权分组名称")
    private String groupName;

    @Schema(description = "API请求方法:GET, HEAD, PUT, POST, DELETE")
    private HttpMethodEnum method;

    @Schema(description = "请求路径(不带api前缀)")
    private String path;

    @Schema(description = "是否上线")
    private Boolean status;

    @Schema(description = "commitId")
    private Long commitId;

    @Schema(description = "上线版本")
    private Integer version;

    @Schema(description = "是否公开")
    private Boolean open;

    @Schema(description = "是否告警")
    private Boolean alarm;

    @Schema(description = "执行引擎")
    private ExecuteEngineEnum engine;

    @Schema(description = "创建时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Timestamp createTime;

    @Schema(description = "更新时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Timestamp updateTime;
}
