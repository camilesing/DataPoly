// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.manager.controller;

import com.cs.common.consts.Constants;
import com.cs.common.consts.PermissionAction;
import com.cs.common.consts.PermissionModule;
import com.cs.manager.security.RequireAdmin;
import com.cs.manager.security.RequirePermission;
import com.cs.common.dto.*;
import com.cs.core.dto.*;
import com.cs.core.service.AppClientService;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import java.util.List;

@Tag(name = "客户端应用接口")
@RestController
@RequestMapping(value = Constants.MANAGER_API_V1 + "/client")
public class AppClientController {

    @Resource
    private AppClientService appClientService;

    @Operation(summary = "添加客户端应用")
    @RequirePermission(module = PermissionModule.APP_CLIENT, action = PermissionAction.C)
    @PostMapping(value = "/create", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResultEntity create(@Valid @RequestBody AppClientSaveRequest request) {
        appClientService.create(request);
        return ResultEntity.success();
    }

    @Operation(summary = "删除客户端应用")
    @RequirePermission(module = PermissionModule.APP_CLIENT, action = PermissionAction.D)
    @DeleteMapping(value = "/delete/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResultEntity delete(@PathVariable("id") Long id) {
        appClientService.delete(id);
        return ResultEntity.success();
    }

    @Operation(summary = "客户端应用列表")
    @RequirePermission(module = PermissionModule.APP_CLIENT, action = PermissionAction.R)
    @PostMapping(value = "/list", produces = MediaType.APPLICATION_JSON_VALUE)
    public PageResult<AppClientDetailResponse> searchList(@RequestBody AppClientSearchRequest request) {
        return appClientService.searchList(request);
    }

    @Operation(summary = "查询应用密钥")
    @RequireAdmin
    @GetMapping(value = "/secret/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResultEntity<String> secret(@PathVariable("id") Long id) {
        return ResultEntity.success(appClientService.getSecret(id));
    }

    @Operation(summary = "创建分组关联")
    @RequirePermission(module = PermissionModule.APP_CLIENT, action = PermissionAction.C)
    @PostMapping(value = "/auth/create", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResultEntity createGroupAuth(@Valid @RequestBody AppClientGroupRequest request) {
        appClientService.createGroupAuth(request);
        return ResultEntity.success();
    }

    @Operation(summary = "查询分组关联")
    @RequirePermission(module = PermissionModule.APP_CLIENT, action = PermissionAction.R)
    @GetMapping(value = "/auth/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<EntityIdNameResponse> getGroupAuth(@PathVariable("id") Long id) {
        return appClientService.getGroupAuth(id);
    }
}
