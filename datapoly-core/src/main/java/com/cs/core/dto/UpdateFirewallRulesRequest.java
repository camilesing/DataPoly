// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.core.dto;

import com.cs.common.enums.*;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import jakarta.validation.constraints.NotNull;

@Data
@NoArgsConstructor
@Schema(description = "防火墙规则")
public class UpdateFirewallRulesRequest {

    @NotNull(message = "status不能为null")
    @Schema(description = "开启状态")
    private OnOffEnum status;

    @Schema(description = "黑白名单选项")
    private WhiteBlackEnum mode;

    @Schema(description = "地址列表")
    private String addresses;
}
