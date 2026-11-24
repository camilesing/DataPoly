// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.manager.controller;

import com.cs.common.consts.Constants;
import com.cs.common.dto.PageResult;
import com.cs.common.dto.ResultEntity;
import com.cs.core.dto.EntitySearchRequest;
import com.cs.core.dto.SystemUserCreateRequest;
import com.cs.core.dto.SystemUserDetailResponse;
import com.cs.core.dto.SystemUserManageResponse;
import com.cs.core.dto.SystemUserResetPasswordRequest;
import com.cs.core.dto.SystemUserUpdateRequest;
import com.cs.core.dto.UserPermissionResponse;
import com.cs.core.dto.UserPermissionSaveRequest;
import com.cs.core.service.SystemUserService;
import com.cs.manager.security.RequireAdmin;
import com.cs.manager.service.PermissionService;
import com.cs.manager.service.SystemUserManageService;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;

@Tag(name = "用户管理接口")
@RestController
@RequestMapping(value = Constants.MANAGER_API_V1 + "/user")
public class SystemUserController {

    @Resource
    private SystemUserService systemUserService;

    @Resource
    private SystemUserManageService systemUserManageService;

    @Resource
    private PermissionService permissionService;

    @Operation(summary = "用户详情", description = "根据用户ID获取用户详情")
    @GetMapping(value = "/detail/id", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResultEntity<SystemUserDetailResponse> getUserById(@RequestParam("id") Long id, HttpServletRequest request) {
        SystemUserDetailResponse detail = systemUserService.getUserDetailById(id);
        requireSelfOrAdmin(detail.getUsername(), request);
        return ResultEntity.success(detail);
    }

    @Operation(summary = "用户详情", description = "根据用户名获取用户详情")
    @GetMapping(value = "/detail/name", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResultEntity<SystemUserDetailResponse> getUserByName(@RequestParam("username") String username,
                                                                 HttpServletRequest request) {
        requireSelfOrAdmin(username, request);
        return ResultEntity.success(systemUserService.getUserDetailByUsername(username));
    }

    /**
     * The detail endpoints sit outside the matrix on purpose: they are self-service. An account's
     * profile goes to its owner (the username the token filter put on the request) or to an ADMIN.
     */
    private void requireSelfOrAdmin(String username, HttpServletRequest request) {
        Object caller = request.getAttribute("username");
        if (null == caller || !caller.equals(username)) {
            permissionService.checkAdminCurrent();
        }
    }

    @Operation(summary = "修改密码", description = "用户修改自己的密码")
    @RequestMapping(value = "/changePassword", method = RequestMethod.POST, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResultEntity changeOwnPassword(HttpServletRequest request,
                                          @RequestParam("oldPassword") String oldPassword,
                                          @RequestParam("newPassword") String newPassword) {
        systemUserService.changeOwnPassword(request, oldPassword, newPassword);
        return ResultEntity.success();
    }

    @Operation(summary = "用户列表", description = "分页列出账号，可按用户名/实际名过滤")
    @RequireAdmin
    @PostMapping(value = "/list", produces = MediaType.APPLICATION_JSON_VALUE)
    public PageResult<SystemUserManageResponse> listUsers(@RequestBody EntitySearchRequest request) {
        return systemUserManageService.list(request);
    }

    @Operation(summary = "创建用户", description = "管理员创建账号并指定角色（USER 的细粒度权限另经权限矩阵配置）")
    @RequireAdmin
    @PostMapping(value = "/create", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResultEntity createUser(@Valid @RequestBody SystemUserCreateRequest request) {
        systemUserManageService.create(request);
        return ResultEntity.success();
    }

    @Operation(summary = "更新用户", description = "改名/邮箱/地址/角色/锁定；不能改自己的角色与锁定，不能动最后一个可用管理员")
    @RequireAdmin
    @PostMapping(value = "/update", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResultEntity updateUser(@Valid @RequestBody SystemUserUpdateRequest request, HttpServletRequest httpRequest) {
        systemUserManageService.update(request, httpRequest.getAttribute("username").toString());
        return ResultEntity.success();
    }

    @Operation(summary = "重置口令", description = "管理员重置他人口令，该账号全部会话随之吊销")
    @RequireAdmin
    @PostMapping(value = "/resetPassword", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResultEntity resetPassword(@Valid @RequestBody SystemUserResetPasswordRequest request) {
        systemUserManageService.resetPassword(request);
        return ResultEntity.success();
    }

    @Operation(summary = "查询权限配置行", description = "某账号已配置的权限行（未出现的模块走默认只读）")
    @RequireAdmin
    @GetMapping(value = "/permission/list", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResultEntity<List<UserPermissionResponse>> listPermissions(@RequestParam("username") String username) {
        return ResultEntity.success(systemUserManageService.configuredMatrix(username));
    }

    @Operation(summary = "保存权限配置", description = "整表替换该账号的权限行；actions 为空的模块回到默认只读")
    @RequireAdmin
    @PostMapping(value = "/permission/update", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResultEntity updatePermissions(@Valid @RequestBody UserPermissionSaveRequest request) {
        systemUserManageService.replacePermissions(request);
        return ResultEntity.success();
    }

    @Operation(summary = "自己的有效权限", description = "当前登录者的有效权限矩阵（前端按它裁剪菜单与按钮）")
    @GetMapping(value = "/permission/self", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResultEntity<List<UserPermissionResponse>> selfPermissions(HttpServletRequest request) {
        return ResultEntity.success(
                systemUserManageService.effectiveMatrix(request.getAttribute("username").toString()));
    }
}
