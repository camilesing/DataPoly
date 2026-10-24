// Use of this source code is governed by a BSD-style license
package com.cs.manager.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "对外地址配置项")
public class ServiceUrlItemResponse {

    @Schema(description = "当前保存值,空串表示未设置(回退环境变量或自动发现)")
    private String value;

    @Schema(description = "当前生效地址,服务未注册时为空")
    private String effective;

    @Schema(description = "来源:PARAM(页面设置)/ENV(环境变量)/DISCOVERY(自动发现)/NONE")
    private String source;
}