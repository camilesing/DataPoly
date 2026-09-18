// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.core.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "MCP服务端地址前缀")
public class McpServerAddrResponse {

    @Schema(description = "SSE地址的路径")
    private String sseAddrPrefix;

    @Schema(description = "StreamHttp地址的路径")
    private String streamAddrPrefix;

    @Schema(description = "管理MCP服务SSE地址的路径(需manage权限令牌)")
    private String adminSseAddrPrefix;

    @Schema(description = "管理MCP服务StreamHttp地址的路径(需manage权限令牌)")
    private String adminStreamAddrPrefix;
}
