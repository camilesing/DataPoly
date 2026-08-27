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
@TableName(value = "DATAPOLY_FIREWALL_RULES", autoResultMap = true)
public class FirewallRulesEntity {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField(value = "status", typeHandler = EnumTypeHandler.class)
    private OnOffEnum status;

    @TableField(value = "mode", typeHandler = EnumTypeHandler.class)
    private WhiteBlackEnum mode;

    @TableField(value = "addresses")
    private String addresses;

    @TableField(value = "create_time", insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private Timestamp createTime;

    @TableField(value = "update_time", insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private Timestamp updateTime;
}
