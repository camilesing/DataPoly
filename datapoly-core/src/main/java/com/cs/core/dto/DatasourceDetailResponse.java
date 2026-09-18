// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.core.dto;

import com.cs.common.enums.ProductTypeEnum;
import com.cs.persistence.entity.PoolConfig;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.sql.Timestamp;

@NoArgsConstructor
@Data
@Schema(description = "数据源详情")
public class DatasourceDetailResponse {

    @Schema(description = "ID编号")
    private Long id;

    @Schema(description = "标题")
    private String name;

    @Schema(description = "数据库类型")
    private ProductTypeEnum type;

    @Schema(description = "驱动版本")
    private String version;

    @Schema(description = "驱动类")
    private String driver;

    @Schema(description = "URL连接串")
    private String url;

    @Schema(description = "账号名")
    private String username;

    @Schema(description = "密码")
    private String password;

    @Schema(description = "连接池配置")
    private PoolConfig poolConfig;

    @Schema(description = "创建时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Timestamp createTime;

    @Schema(description = "更新时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Timestamp updateTime;
}
