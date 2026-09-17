// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.core.dto;

import com.cs.common.enums.*;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import jakarta.validation.constraints.*;

@Data
@NoArgsConstructor
@Schema(description = "客户端应用信息")
public class AppClientSaveRequest {

    @NotBlank(message = "name不能为空")
    @Schema(description = "客户端应用名称")
    private String name;

    @Schema(description = "客户端应用描述")
    private String description;

    @NotBlank(message = "appKey不能为空")
    @Schema(description = "应用AppKey账号")
    private String appKey;

    @NotNull(message = "expireTime不能为null")
    @Schema(description = "到期时间")
    private ExpireTimeEnum expireTime;

    @NotNull(message = "tokenAlive不能为null")
    @Schema(description = "Token存活期")
    private AliveTimeEnum tokenAlive;
}
