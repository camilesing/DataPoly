// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.persistence.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.*;

import java.sql.Timestamp;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName(value = "DATAPOLY_VERSION_COMMIT", autoResultMap = true)
public class VersionCommitEntity {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("biz_id")
    private Long bizId;

    @TableField("version")
    private Integer version;

    @TableField("description")
    private String description;

    @TableField("content")
    private String content;

    @TableField(value = "create_time", insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private Timestamp createTime;
}
