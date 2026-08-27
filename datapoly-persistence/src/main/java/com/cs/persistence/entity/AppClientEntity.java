// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.persistence.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.cs.common.enums.*;
import lombok.*;
import org.apache.ibatis.type.EnumTypeHandler;

import java.sql.Timestamp;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName(value = "DATAPOLY_APP_CLIENT", autoResultMap = true)
public class AppClientEntity {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("name")
    private String name;

    @TableField("description")
    private String description;

    @TableField("app_key")
    private String appKey;

    @TableField("app_secret")
    private String appSecret;

    @TableField(value = "expire_duration", typeHandler = EnumTypeHandler.class)
    private DurationTimeEnum expireDuration;

    @TableField("expire_at")
    private Long expireAt;

    @TableField(value = "access_token", insertStrategy = FieldStrategy.NEVER)
    private String accessToken;

    @TableField(value = "token_alive", typeHandler = EnumTypeHandler.class)
    private AliveTimeEnum tokenAlive;

    @TableField(value = "create_time", insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private Timestamp createTime;

    @TableField(value = "update_time", insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private Timestamp updateTime;
}
