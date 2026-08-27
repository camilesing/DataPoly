// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.manager.controller;

import com.cs.common.consts.Constants;
import com.cs.common.dto.*;
import com.cs.core.dto.*;
import com.cs.manager.service.McpManageService;
import com.cs.persistence.entity.McpClientEntity;
import io.swagger.annotations.*;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.validation.Valid;
import javax.validation.constraints.NotBlank;

@Api(tags = {"MCP令牌管理接口"})
@RestController
@RequestMapping(value = Constants.MANAGER_API_V1 + "/mcp/client")
public class McpClientController {

    @Resource
    private McpManageService mcpManageService;

    @ApiOperation(value = "获取MCP服务地址")
    @GetMapping(value = "/endpoint", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResultEntity<McpServerAddrResponse> getMcpServerEndpoint() {
        return ResultEntity.success(mcpManageService.getMcpServerEndpoint());
    }

    @ApiOperation(value = "添加令牌")
    @PostMapping(value = "/create", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResultEntity create(@Valid @NotBlank(message = "name不能为空") @RequestParam("name") String name) {
        mcpManageService.createClient(name);
        return ResultEntity.success();
    }

    @ApiOperation(value = "更新令牌")
    @PostMapping(value = "/update/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResultEntity update(@PathVariable("id") Long id,
                               @Valid @NotBlank(message = "name不能为空") @RequestParam("name") String name) {
        mcpManageService.updateClient(id, name);
        return ResultEntity.success();
    }

    @ApiOperation(value = "删除令牌")
    @DeleteMapping(value = "/delete/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResultEntity delete(@PathVariable("id") Long id) {
        mcpManageService.deleteClient(id);
        return ResultEntity.success();
    }

    @ApiOperation(value = "令牌列表")
    @PostMapping(value = "/listAll", produces = MediaType.APPLICATION_JSON_VALUE)
    public PageResult<McpClientEntity> listAll(@RequestBody EntitySearchRequest request) {
        return mcpManageService.listClientAll(request);
    }
}
