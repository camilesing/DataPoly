// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.executor.controller;

import com.cs.common.dto.*;
import com.cs.common.exception.*;
import com.cs.core.servlet.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.*;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/token")
public class ClientTokenController {

    @Resource
    private ClientTokenService clientTokenService;

    @Resource
    private ClientTokenGuard clientTokenGuard;

    @PostMapping(value = "/generate", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResultEntity generateToken(@RequestBody Map<String, String> body,
                                      HttpServletRequest request, HttpServletResponse response) {
        String clientId = body.get("clientId");
        String remoteAddr = request.getRemoteAddr();
        try {
            clientTokenGuard.checkAllowed(clientId, remoteAddr);
        } catch (CommonException e) {
            response.setStatus(e.getCode().getHttpStatus());
            return ResultEntity.failed(e.getCode(), e.getMessage());
        }

        try {
            AccessToken token = clientTokenService.generateToken(clientId, body.get("secret"));
            clientTokenGuard.recordSuccess(clientId, remoteAddr);
            return ResultEntity.success(token);
        } catch (CommonException e) {
            clientTokenGuard.recordFailure(clientId, remoteAddr);
            response.setStatus(e.getCode().getHttpStatus());
            return ResultEntity.failed(e.getCode(), e.getMessage());
        } catch (Exception e) {
            clientTokenGuard.recordFailure(clientId, remoteAddr);
            log.error("Failed to generate token for clientId [{}]", clientId, e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            return ResultEntity.failed(ResponseErrorCode.ERROR_INTERNAL_ERROR, "token.generate.failed");
        }
    }
}
