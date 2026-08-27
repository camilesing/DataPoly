// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.manager.controller;

import com.cs.common.consts.Constants;
import com.cs.common.dto.ResultEntity;
import com.cs.core.dto.SystemUserDetailResponse;
import com.cs.core.service.SystemUserService;
import io.swagger.annotations.*;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

@Api(tags = {"用户管理接口"})
@RestController
@RequestMapping(value = Constants.MANAGER_API_V1 + "/user")
public class SystemUserController {

    @Resource
    private SystemUserService systemUserService;

    @ApiOperation(value = "用户详情", notes = "根据用户ID获取用户详情")
    @GetMapping(value = "/detail/id", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResultEntity<SystemUserDetailResponse> getUserById(@RequestParam("id") Long id) {
        return ResultEntity.success(systemUserService.getUserDetailById(id));
    }

    @ApiOperation(value = "用户详情", notes = "根据用户名获取用户详情")
    @GetMapping(value = "/detail/name", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResultEntity<SystemUserDetailResponse> getUserByName(@RequestParam("username") String username) {
        return ResultEntity.success(systemUserService.getUserDetailByUsername(username));
    }

    @ApiOperation(value = "修改密码", notes = "用户修改自己的密码")
    @RequestMapping(value = "/changePassword", method = RequestMethod.POST, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResultEntity changeOwnPassword(HttpServletRequest request,
                                          @RequestParam("oldPassword") String oldPassword,
                                          @RequestParam("newPassword") String newPassword) {
        systemUserService.changeOwnPassword(request, oldPassword, newPassword);
        return ResultEntity.success();
    }

}
