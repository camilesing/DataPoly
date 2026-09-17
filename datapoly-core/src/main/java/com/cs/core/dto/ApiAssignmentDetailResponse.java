// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.core.dto;

import com.baomidou.mybatisplus.annotation.TableField;
import com.cs.common.dto.*;
import com.cs.common.enums.*;
import com.cs.persistence.entity.ApiContextEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.util.List;

@Data
@NoArgsConstructor
@Schema(description = "API配置详细详情")
public class ApiAssignmentDetailResponse extends ApiAssignmentBaseResponse {

    @Schema(description = "分组ID")
    private Long groupId;

    @Schema(description = "模块ID")
    private Long moduleId;

    @Schema(description = "数据源ID")
    private Long datasourceId;

    @Schema(description = "描述")
    private String description;

    @Schema(description = "接口入参")
    private List<ItemParam> params;

    @Schema(description = "接口出参")
    private List<OutParam> outputs;

    @Schema(description = "HTTP请求的contentType")
    private String contentType;

    @Schema(description = "SQL列表")
    private List<ApiContextEntity> sqlList;

    @Schema(description = "接口出参数据类型转换格式")
    private List<DataTypeFormatMapValue> formatMap;

    @Schema(description = "接口出参属性命名策略")
    private NamingStrategyEnum namingStrategy;

    @Schema(description = "是否开启流量控制")
    private Boolean flowStatus;

    @Schema(description = "阈值类型")
    private Integer flowGrade;

    @TableField("阈值大小")
    private Integer flowCount;

    @Schema(description = "缓存键类型")
    private CacheKeyTypeEnum cacheKeyType;

    @Schema(description = "缓存表达式")
    private String cacheKeyExpr;

    @Schema(description = "缓存时常")
    private Long cacheExpireSeconds;
}
