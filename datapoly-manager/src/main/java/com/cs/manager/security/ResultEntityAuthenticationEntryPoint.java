// Use of this source code is governed by a BSD-style license
package com.cs.manager.security;

import com.cs.common.exception.ResponseErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

import java.io.IOException;

/**
 * Answers an unauthenticated call on a protected path with the ResultEntity shape of the former
 * session interceptor ({@code ERROR_TOKEN_EXPIRED} / HTTP 401) instead of Spring Security's
 * default empty 401, keeping the frontend contract ({@code body.code === 401} → login page).
 */
public class ResultEntityAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final boolean httpStatusMappingEnabled;

    public ResultEntityAuthenticationEntryPoint(boolean httpStatusMappingEnabled) {
        this.httpStatusMappingEnabled = httpStatusMappingEnabled;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        SecurityResponses.write(response, ResponseErrorCode.ERROR_TOKEN_EXPIRED, "auth.no.token",
                httpStatusMappingEnabled);
    }
}
