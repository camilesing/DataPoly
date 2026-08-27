// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.core.dto;

import com.cs.common.enums.*;
import io.swagger.annotations.*;
import lombok.*;

import javax.validation.constraints.*;

@Data
@NoArgsConstructor
@ApiModel("客户端应用信息")
public class AppClientSaveRequest {

    @NotBlank(message = "name不能为空")
    @ApiModelProperty("客户端应用名称")
    private String name;

    @ApiModelProperty("客户端应用描述")
    private String description;

    @NotBlank(message = "appKey不能为空")
    @ApiModelProperty("应用AppKey账号")
    private String appKey;

    @NotNull(message = "expireTime不能为null")
    @ApiModelProperty("到期时间")
    private ExpireTimeEnum expireTime;

    @NotNull(message = "tokenAlive不能为null")
    @ApiModelProperty("Token存活期")
    private AliveTimeEnum tokenAlive;
}
