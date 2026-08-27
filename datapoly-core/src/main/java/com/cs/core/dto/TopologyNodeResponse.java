// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.core.dto;

import com.cs.common.enums.NodeStatusEnum;
import io.swagger.annotations.*;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ApiModel("拓扑节点信息")
public class TopologyNodeResponse {

    @ApiModelProperty("服务ID")
    private String serviceId;

    @ApiModelProperty("实例ID")
    private String instanceId;

    @ApiModelProperty("主机地址")
    private String host;

    @ApiModelProperty("端口号")
    private Integer port;


    @ApiModelProperty("内存使用")
    private Integer memory;

    @ApiModelProperty("CPU使用")
    private Integer cpu;

    @ApiModelProperty("存储使用")
    private Integer disk;

    @ApiModelProperty("节点状态")
    private NodeStatusEnum status;
}
