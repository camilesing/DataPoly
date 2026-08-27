// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.persistence.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.cs.common.dto.*;
import com.cs.common.enums.*;
import com.cs.persistence.handler.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;
import org.apache.ibatis.type.EnumTypeHandler;

import java.sql.Timestamp;
import java.util.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName(value = "DATAPOLY_API_ASSIGNMENT", autoResultMap = true)
public class ApiAssignmentEntity {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("group_id")
    private Long groupId;

    @TableField("module_id")
    private Long moduleId;

    @TableField("datasource_id")
    private Long datasourceId;

    @TableField("name")
    private String name;

    @TableField("description")
    private String description;

    @TableField(value = "method", typeHandler = EnumTypeHandler.class)
    private HttpMethodEnum method;

    @TableField("path")
    private String path;

    @TableField(value = "params", typeHandler = ListParamHandler.class)
    private List<ItemParam> params;

    @TableField(value = "outputs", typeHandler = ListOutputHandler.class)
    private List<OutParam> outputs;

    @TableField("open")
    private Boolean open;

    @TableField("alarm")
    private Boolean alarm;

    @TableField("content_type")
    private String contentType;

    @TableField(value = "engine", typeHandler = EnumTypeHandler.class)
    private ExecuteEngineEnum engine;

    @TableField(value = "response_format", typeHandler = FormatMapHandler.class)
    private Map<DataTypeFormatEnum, String> responseFormat;

    @TableField(value = "naming_strategy", typeHandler = EnumTypeHandler.class)
    private NamingStrategyEnum namingStrategy;

    @TableField("flow_status")
    private Boolean flowStatus;

    @TableField("flow_grade")
    private Integer flowGrade;

    @TableField("flow_count")
    private Integer flowCount;

    @TableField(value = "cache_key_type", typeHandler = EnumTypeHandler.class)
    private CacheKeyTypeEnum cacheKeyType;

    @TableField("cache_key_expr")
    private String cacheKeyExpr;

    @TableField("cache_expire_seconds")
    private Long cacheExpireSeconds;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(value = "create_time", insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private Timestamp createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(value = "update_time", insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private Timestamp updateTime;

    @TableField(exist = false)
    private List<ApiContextEntity> contextList;

    /**
     * Overlay field from the online deployment (DATAPOLY_API_ONLINE.commit_id, see ApiOnlineDao#buildAssignmentEntity);
     * not persisted to the assignment table, used to include the version in the response cache key (A5).
     */
    @TableField(exist = false)
    private Long commitId;
}
