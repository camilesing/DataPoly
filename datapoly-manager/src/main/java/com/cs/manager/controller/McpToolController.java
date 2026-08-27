// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.manager.controller;

import com.cs.common.consts.Constants;
import com.cs.common.dto.*;
import com.cs.core.dto.*;
import com.cs.manager.service.McpManageService;
import io.swagger.annotations.*;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.validation.Valid;

@Api(tags = {"MCP工具管理接口"})
@RestController
@RequestMapping(value = Constants.MANAGER_API_V1 + "/mcp/tool")
public class McpToolController {

    @Resource
    private McpManageService mcpManageService;

    @ApiOperation(value = "添加MCP工具")
    @PostMapping(value = "/create", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResultEntity create(@Valid @RequestBody McpToolSaveRequest request) {
        mcpManageService.createTool(request);
        return ResultEntity.success();
    }

    @ApiOperation(value = "更新MCP工具")
    @PostMapping(value = "/update", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResultEntity update(@Valid @RequestBody McpToolSaveRequest request) {
        mcpManageService.updateTool(request);
        return ResultEntity.success();
    }

    @ApiOperation(value = "删除MCP工具")
    @DeleteMapping(value = "/delete/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResultEntity delete(@PathVariable("id") Long id) {
        mcpManageService.deleteTool(id);
        return ResultEntity.success();
    }

    @ApiOperation(value = "MCP工具列表")
    @PostMapping(value = "/listAll", produces = MediaType.APPLICATION_JSON_VALUE)
    public PageResult<McpToolResponse> listAll(@RequestBody EntitySearchRequest request) {
        return mcpManageService.listToolAll(request);
    }
}
