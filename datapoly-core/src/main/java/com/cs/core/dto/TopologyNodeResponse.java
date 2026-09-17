// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.core.dto;

import com.cs.common.enums.NodeStatusEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "拓扑节点信息")
public class TopologyNodeResponse {

    @Schema(description = "服务ID")
    private String serviceId;

    @Schema(description = "实例ID")
    private String instanceId;

    @Schema(description = "主机地址")
    private String host;

    @Schema(description = "端口号")
    private Integer port;


    @Schema(description = "内存使用")
    private Integer memory;

    @Schema(description = "CPU使用")
    private Integer cpu;

    @Schema(description = "存储使用")
    private Integer disk;

    @Schema(description = "节点状态")
    private NodeStatusEnum status;
}
