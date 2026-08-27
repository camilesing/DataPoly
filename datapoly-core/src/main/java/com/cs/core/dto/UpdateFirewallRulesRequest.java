// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.core.dto;

import com.cs.common.enums.*;
import io.swagger.annotations.*;
import lombok.*;

import javax.validation.constraints.NotNull;

@Data
@NoArgsConstructor
@ApiModel("防火墙规则")
public class UpdateFirewallRulesRequest {

    @NotNull(message = "status不能为null")
    @ApiModelProperty("开启状态")
    private OnOffEnum status;

    @ApiModelProperty("黑白名单选项")
    private WhiteBlackEnum mode;

    @ApiModelProperty("地址列表")
    private String addresses;
}
