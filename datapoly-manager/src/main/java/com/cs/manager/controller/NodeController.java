// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.manager.controller;

import com.cs.common.consts.Constants;
import com.cs.common.dto.ResultEntity;
import com.cs.core.dto.TopologyNodeResponse;
import com.cs.core.service.SystemParamService;
import com.cs.manager.model.ServiceUrlsConfigResponse;
import com.cs.manager.service.NodeService;
import com.cs.manager.service.ServiceUrlResolver;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;

@Tag(name = "节点信息接口")
@RestController
@RequestMapping(value = Constants.MANAGER_API_V1 + "/node")
public class NodeController {

    @Resource
    private NodeService nodeService;
    @Resource
    private ServiceUrlResolver serviceUrlResolver;
    @Resource
    private SystemParamService systemParamService;

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

    @GetMapping(value = "/url-config", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResultEntity<ServiceUrlsConfigResponse> getUrlConfig() {
        return ResultEntity.success(ServiceUrlsConfigResponse.builder()
                .gateway(serviceUrlResolver.gatewayConfig())
                .manager(serviceUrlResolver.managerConfig())
                .build());
    }

    @PostMapping(value = "/url-config", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResultEntity updateUrlConfig(@RequestParam("gatewayUrl") String gatewayUrl,
                                        @RequestParam("managerUrl") String managerUrl) {
        // Validate both values before touching the database so a rejected request changes nothing
        String gateway = ServiceUrlResolver.normalizeValidatedUrl(gatewayUrl);
        String manager = ServiceUrlResolver.normalizeValidatedUrl(managerUrl);
        systemParamService.updateByParamKey(Constants.SYS_PARAM_KEY_GATEWAY_URL, gateway);
        systemParamService.updateByParamKey(Constants.SYS_PARAM_KEY_MANAGER_URL, manager);
        return ResultEntity.success();
    }
}
