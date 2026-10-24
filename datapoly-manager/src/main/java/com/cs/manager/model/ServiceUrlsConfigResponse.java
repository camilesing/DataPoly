// Use of this source code is governed by a BSD-style license
package com.cs.manager.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "对外地址配置(网关与 Manager 相互独立)")
public class ServiceUrlsConfigResponse {

    @Schema(description = "网关地址:接口文档入口与在线接口调用地址前缀")
    private ServiceUrlItemResponse gateway;

    @Schema(description = "Manager 地址:MCP 服务地址前缀")
    private ServiceUrlItemResponse manager;
}