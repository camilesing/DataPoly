// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.core.dto;

import com.cs.common.enums.ProductTypeEnum;
import com.cs.persistence.entity.PoolConfig;
import io.swagger.annotations.ApiModelProperty;
import lombok.*;

import javax.validation.constraints.*;

@Data
@NoArgsConstructor
public class DataSourceBaseRequest {

    @NotBlank(message = "name不能为空")
    @ApiModelProperty("名称")
    private String name;

    @NotNull(message = "type不能为null")
    @ApiModelProperty("类型")
    private ProductTypeEnum type;

    @NotBlank(message = "version不能为空")
    @ApiModelProperty("驱动版本")
    private String version;

    @NotBlank(message = "driver不能为空")
    @ApiModelProperty("驱动类型")
    private String driver;

    @NotBlank(message = "url不能为空")
    @ApiModelProperty("连接JDBC-URL")
    private String url;

    @NotBlank(message = "username不能为空")
    @ApiModelProperty("账号")
    private String username;

    @ApiModelProperty("密码")
    private String password;

    @ApiModelProperty("连接池配置")
    private PoolConfig poolConfig;
}
