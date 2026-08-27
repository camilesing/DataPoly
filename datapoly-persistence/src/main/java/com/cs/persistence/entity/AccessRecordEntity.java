// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.persistence.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.cs.persistence.handler.ParamMapHandler;
import lombok.*;

import java.sql.Timestamp;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName(value = "DATAPOLY_ACCESS_RECORD", autoResultMap = true)
public class AccessRecordEntity {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("path")
    private String path;

    @TableField("status")
    private Integer status;

    @TableField("duration")
    private Long duration;

    @TableField("ip_addr")
    private String ipAddr;

    @TableField("user_agent")
    private String userAgent;

    @TableField("client_key")
    private String clientKey;

    @TableField("api_id")
    private Long apiId;

    @TableField(value = "parameters", typeHandler = ParamMapHandler.class)
    private Map<String, Object> parameters;

    @TableField("exception")
    private String exception;

    @TableField(value = "create_time", insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private Timestamp createTime;

    @TableField("executor_addr")
    private String executorAddr;

    @TableField("gateway_addr")
    private String gatewayAddr;
}
