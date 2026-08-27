// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.manager.controller;

import com.cs.common.consts.Constants;
import com.cs.common.dto.ResultEntity;
import com.cs.core.dto.TopologyNodeResponse;
import com.cs.manager.service.NodeService;
import io.swagger.annotations.Api;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

@Api(tags = {"节点信息接口"})
@RestController
@RequestMapping(value = Constants.MANAGER_API_V1 + "/node")
public class NodeController {

    @Resource
    private NodeService nodeService;

    @GetMapping(value = "/gateway", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResultEntity<String> getApiGatewayPrefix() {
        return ResultEntity.success(nodeService.getGatewayAddr());
    }

    @GetMapping(value = "/prefix", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResultEntity<String> getApiPrefix() {
        return ResultEntity.success(nodeService.getApiPrefix());
    }

    // https://blog.csdn.net/weixin_39085822/article/details/114287774
    @GetMapping(value = "/topology", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResultEntity<TopologyNodeResponse> getNodesTopology() {
        return ResultEntity.success(nodeService.getNodesTopology());
    }
}
