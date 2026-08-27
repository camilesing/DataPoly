// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.core.dto;

import io.swagger.annotations.*;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ApiModel("MCP服务端地址前缀")
public class McpServerAddrResponse {

    @ApiModelProperty("SSE地址的路径")
    private String sseAddrPrefix;

    @ApiModelProperty("StreamHttp地址的路径")
    private String streamAddrPrefix;
}
