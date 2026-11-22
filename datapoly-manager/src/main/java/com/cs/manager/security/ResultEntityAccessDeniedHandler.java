// Use of this source code is governed by a BSD-style license
package com.cs.manager.security;

import com.cs.common.exception.ResponseErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;

import java.io.IOException;

/**
 * Chain-level denial fallback ({@code ERROR_ACCESS_FORBIDDEN} / HTTP 403) in the shared
 * ResultEntity shape. The matrix denials normally surface from the method advice via
 * {@code ExceptionController}; this handler keeps any rule-level denial on the same contract.
 */
public class ResultEntityAccessDeniedHandler implements AccessDeniedHandler {

    private final boolean httpStatusMappingEnabled;

    public ResultEntityAccessDeniedHandler(boolean httpStatusMappingEnabled) {
        this.httpStatusMappingEnabled = httpStatusMappingEnabled;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        SecurityResponses.write(response, ResponseErrorCode.ERROR_ACCESS_FORBIDDEN, null, httpStatusMappingEnabled);
    }
}
