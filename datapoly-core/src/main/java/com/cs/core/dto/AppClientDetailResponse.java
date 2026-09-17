// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.core.dto;

import com.cs.common.enums.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.sql.Timestamp;

@Data
@NoArgsConstructor
@Schema(description = "客户端应用详情")
public class AppClientDetailResponse {

    @Schema(description = "ID编号")
    private Long id;

    @Schema(description = "客户端应用名称")
    private String name;

    @Schema(description = "客户端应用描述")
    private String description;

    @Schema(description = "应用AppKey账号")
    private String appKey;

    @Schema(description = "到期类型")
    private DurationTimeEnum expireDuration;

    @Schema(description = "到期时间")
    private Long expireAt;

    @Schema(description = "到期时间(字符串)")
    private String expireAtStr;

    @Schema(description = "过期类型")
    private String expireType;

    @Schema(description = "是否过期")
    private Boolean isExpired;

    @Schema(description = "Token生命期")
    private AliveTimeEnum tokenAlive;

    @Schema(description = "创建时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Timestamp createTime;

    @Schema(description = "更新时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Timestamp updateTime;
}
