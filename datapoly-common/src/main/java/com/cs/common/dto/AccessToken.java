// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.common.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.io.Serializable;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "TOKEN信息")
public class AccessToken implements Serializable {

    @Schema(description = "实际名称")
    private String realName;

    @Schema(description = "唯一标识")
    private String appKey;

    @Schema(description = "token字符串")
    private String accessToken;

    @JsonIgnore
    @Schema(description = "创建时的时间戳")
    private Long createTimestamp;

    @Schema(description = "有效期(时间段，单位:秒)")
    private Long expireSeconds;
}
