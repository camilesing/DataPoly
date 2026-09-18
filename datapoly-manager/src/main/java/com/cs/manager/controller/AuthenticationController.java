// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.manager.controller;

import com.cs.common.dto.*;
import com.cs.common.exception.CommonException;
import com.cs.core.dto.UserLoginRequest;
import com.cs.core.service.SystemUserService;
import com.cs.manager.config.LoginGuard;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@Tag(name = "登陆认证接口")
@Slf4j
@RestController
@RequestMapping(value = "/user")
public class AuthenticationController {

    @Resource
    private SystemUserService systemUserService;

    @Resource
    private LoginGuard loginGuard;

    @Operation(summary = "账号登录", description = "使用一个账号密码登录")
    @PostMapping(value = "/login", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResultEntity login(@Valid @RequestBody UserLoginRequest request, HttpServletRequest httpRequest) {
        String username = request.getUsername();
        String remoteAddr = httpRequest.getRemoteAddr();
        loginGuard.checkAllowed(username, remoteAddr);
        try {
            AccessToken accessToken = systemUserService.login(username, request.getPassword());
            loginGuard.recordSuccess(username, remoteAddr);
            return ResultEntity.success(accessToken);
        } catch (CommonException e) {
            // Record failure for lockout counting (rate limiting already enforced in checkAllowed); logged above for audit
            loginGuard.recordFailure(username, remoteAddr);
            log.warn("Login failed for username [{}] from [{}]: {}", username, remoteAddr, e.getMessage());
            throw e;
        }
    }

    @GetMapping(value = "/logout", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "认证登出", description = "登出系统")
    @Parameters({
            @Parameter(in = ParameterIn.HEADER, name = "token", description = "token标记", required = true)
    })
    public ResultEntity logout(HttpServletRequest request) {
        systemUserService.logout(request);
        return ResultEntity.success();
    }
}
