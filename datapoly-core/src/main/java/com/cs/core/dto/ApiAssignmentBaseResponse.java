// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.core.dto;

import com.cs.common.enums.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.annotations.*;
import lombok.*;

import java.sql.Timestamp;

@Data
@NoArgsConstructor
@ApiModel("API配置简单详情")
public class ApiAssignmentBaseResponse {

    @ApiModelProperty("ID编号")
    private Long id;

    @ApiModelProperty("API配置名称")
    private String name;

    @ApiModelProperty("描述信息")
    private String description;

    @ApiModelProperty("模块ID")
    private Long moduleId;

    @ApiModelProperty("模块名称")
    private String moduleName;

    @ApiModelProperty("授权分组ID")
    private Long groupId;

    @ApiModelProperty("授权分组名称")
    private String groupName;

    @ApiModelProperty("API请求方法:GET, HEAD, PUT, POST, DELETE")
    private HttpMethodEnum method;

    @ApiModelProperty("请求路径(不带api前缀)")
    private String path;

    @ApiModelProperty("是否上线")
    private Boolean status;

    @ApiModelProperty("commitId")
    private Long commitId;

    @ApiModelProperty("上线版本")
    private Integer version;

    @ApiModelProperty("是否公开")
    private Boolean open;

    @ApiModelProperty("是否告警")
    private Boolean alarm;

    @ApiModelProperty("执行引擎")
    private ExecuteEngineEnum engine;

    @ApiModelProperty("创建时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Timestamp createTime;

    @ApiModelProperty("更新时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Timestamp updateTime;
}
