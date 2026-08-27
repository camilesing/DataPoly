// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.manager.controller;

import com.cs.common.consts.Constants;
import com.cs.common.dto.*;
import com.cs.core.dto.*;
import com.cs.core.service.AppClientService;
import io.swagger.annotations.*;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.validation.Valid;
import java.util.List;

@Api(tags = {"客户端应用接口"})
@RestController
@RequestMapping(value = Constants.MANAGER_API_V1 + "/client")
public class AppClientController {

    @Resource
    private AppClientService appClientService;

    @ApiOperation(value = "添加客户端应用")
    @PostMapping(value = "/create", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResultEntity create(@Valid @RequestBody AppClientSaveRequest request) {
        appClientService.create(request);
        return ResultEntity.success();
    }

    @ApiOperation(value = "删除客户端应用")
    @DeleteMapping(value = "/delete/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResultEntity delete(@PathVariable("id") Long id) {
        appClientService.delete(id);
        return ResultEntity.success();
    }

    @ApiOperation(value = "客户端应用列表")
    @PostMapping(value = "/list", produces = MediaType.APPLICATION_JSON_VALUE)
    public PageResult<AppClientDetailResponse> searchList(@RequestBody AppClientSearchRequest request) {
        return appClientService.searchList(request);
    }

    @ApiOperation(value = "查询应用密钥")
    @GetMapping(value = "/secret/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResultEntity<String> secret(@PathVariable("id") Long id) {
        return ResultEntity.success(appClientService.getSecret(id));
    }

    @ApiOperation(value = "创建分组关联")
    @PostMapping(value = "/auth/create", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResultEntity createGroupAuth(@Valid @RequestBody AppClientGroupRequest request) {
        appClientService.createGroupAuth(request);
        return ResultEntity.success();
    }

    @ApiOperation(value = "查询分组关联")
    @GetMapping(value = "/auth/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<EntityIdNameResponse> getGroupAuth(@PathVariable("id") Long id) {
        return appClientService.getGroupAuth(id);
    }
}
