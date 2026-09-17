// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.core.dto;

import com.baomidou.mybatisplus.annotation.TableField;
import com.cs.common.dto.*;
import com.cs.common.enums.*;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import jakarta.validation.constraints.*;
import java.util.List;

@Data
@NoArgsConstructor
@Schema(description = "API配置")
public class ApiAssignmentSaveRequest {

    @Schema(description = "ID编号(保存接口使用)")
    private Long id;

    @NotNull(message = "groupId不能为null")
    @Schema(description = "分组ID")
    private Long groupId;

    @NotNull(message = "moduleId不能为null")
    @Schema(description = "模块ID")
    private Long moduleId;

    @NotNull(message = "datasourceId不能为null")
    @Schema(description = "数据源ID")
    private Long datasourceId;

    @NotBlank(message = "name不能为空")
    @Schema(description = "API配置名称")
    private String name;

    @Schema(description = "描述")
    private String description;

    @NotNull(message = "method不能为null")
    @Schema(description = "API请求方法:GET, HEAD, PUT, POST, DELETE")
    private HttpMethodEnum method;

    @NotBlank(message = "contentType不能为空")
    @Schema(description = "HTTP请求的contentType")
    private String contentType;

    @NotBlank(message = "path不能为空")
    @Schema(description = "请求路径(不带api前缀)")
    private String path;

    @NotNull(message = "open不能为null")
    @Schema(description = "是否公开")
    private Boolean open;

    @NotNull(message = "alarm不能为null")
    @Schema(description = "是否告警")
    private Boolean alarm;

    @NotNull(message = "engine不能为null")
    @Schema(description = "执行引擎:SQL, SCRIPT")
    private ExecuteEngineEnum engine;

    @NotEmpty(message = "contextList不能为空列表")
    @Schema(description = "SQL列表")
    private List<String> contextList;

    @Schema(description = "接口入参列表")
    private List<ItemParam> params;

    @Schema(description = "接口出参列表")
    private List<OutParam> outputs;

    @Schema(description = "接口出参数据类型转换格式")
    private List<DataTypeFormatMapValue> formatMap;

    @NotNull(message = "namingStrategy不能为null")
    @Schema(description = "接口出参属性命名策略")
    private NamingStrategyEnum namingStrategy;

    @NotNull(message = "flowStatus不能为null")
    @Schema(description = "是否开启流量控制")
    private Boolean flowStatus;

    @Schema(description = "阈值类型")
    private Integer flowGrade;

    @Schema(description = "单机阈值")
    private Integer flowCount;

    @Schema(description = "缓存键类型")
    private CacheKeyTypeEnum cacheKeyType;

    @TableField("缓存表达式")
    private String cacheKeyExpr;

    @TableField("缓存时常")
    private Long cacheExpireSeconds;
}
