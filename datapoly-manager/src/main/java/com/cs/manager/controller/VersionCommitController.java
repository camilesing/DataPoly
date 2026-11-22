// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.manager.controller;

import com.cs.common.consts.Constants;
import com.cs.common.consts.PermissionAction;
import com.cs.common.consts.PermissionModule;
import com.cs.manager.security.RequirePermission;
import com.cs.common.dto.ResultEntity;
import com.cs.core.dto.VersionCommitResponse;
import com.cs.core.service.ApiAssignmentService;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import java.util.List;

@Tag(name = "版本控制接口")
@RestController
@RequestMapping(value = Constants.MANAGER_API_V1 + "/version")
public class VersionCommitController {

    @Resource
    private ApiAssignmentService apiAssignmentService;

    @Operation(summary = "查询版本列表")
    @RequirePermission(module = PermissionModule.VERSION, action = PermissionAction.R)
    @GetMapping(value = "/list/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResultEntity<List<VersionCommitResponse>> listVersions(@PathVariable("id") Long bizId) {
        return ResultEntity.success(apiAssignmentService.listVersions(bizId));
    }

    @Operation(summary = "查询版本详情")
    @RequirePermission(module = PermissionModule.VERSION, action = PermissionAction.R)
    @GetMapping(value = "/show/{commitId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResultEntity showVersion(@PathVariable("commitId") Long commitId) {
        return ResultEntity.success(apiAssignmentService.showVersion(commitId));
    }

    @Operation(summary = "回滚指定版本")
    @RequirePermission(module = PermissionModule.VERSION, action = PermissionAction.U)
    @PostMapping(value = "/revert/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResultEntity revertVersion(@PathVariable("id") Long bizId, @RequestParam("commitId") Long commitId) {
        apiAssignmentService.revertVersion(bizId, commitId);
        return ResultEntity.success();
    }
}
