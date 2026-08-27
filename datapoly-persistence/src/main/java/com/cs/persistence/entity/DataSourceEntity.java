// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.persistence.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.cs.common.enums.ProductTypeEnum;
import com.cs.persistence.handler.PoolConfigHandler;
import lombok.*;
import org.apache.commons.lang3.StringUtils;
import org.apache.ibatis.type.EnumTypeHandler;

import java.sql.Timestamp;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName(value = "DATAPOLY_DATASOURCE", autoResultMap = true)
public class DataSourceEntity {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("name")
    private String name;

    @TableField(value = "type", typeHandler = EnumTypeHandler.class)
    private ProductTypeEnum type;

    @TableField("version")
    private String version;

    @TableField("driver")
    private String driver;

    @TableField("url")
    private String url;

    @TableField("username")
    private String username;

    @TableField("password")
    private String password;

    @TableField(value = "create_time", insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private Timestamp createTime;

    @TableField(value = "update_time", insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private Timestamp updateTime;

    @TableField(value = "pool_config", typeHandler = PoolConfigHandler.class)
    private PoolConfig poolConfig;

    public static boolean isSame(DataSourceEntity lhs, DataSourceEntity rhs) {
        if (null == lhs || null == rhs) {
            return false;
        }
        return StringUtils.equals(lhs.version, rhs.version)
                && StringUtils.equals(lhs.driver, rhs.driver)
                && StringUtils.equals(lhs.url, rhs.url)
                && StringUtils.equals(lhs.username, rhs.username)
                && StringUtils.equals(lhs.password, rhs.password)
                && java.util.Objects.equals(lhs.poolConfig, rhs.poolConfig);
    }
}
