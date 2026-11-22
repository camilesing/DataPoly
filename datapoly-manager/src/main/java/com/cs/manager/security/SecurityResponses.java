// Use of this source code is governed by a BSD-style license
package com.cs.manager.security;

import com.cs.common.dto.ResultEntity;
import com.cs.common.exception.ResponseErrorCode;
import com.cs.common.util.I18nUtils;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Writes a security rejection as the same {@code ResultEntity} body the managers's
 * {@code ExceptionController} produces (same code, same message composition, same optional
 * HTTP-status mapping), so the frontend's {@code body.code === 401/403} handling sees one shape
 * whether the gate said no in the filter chain or in an advice method.
 */
final class SecurityResponses {

    private static final ObjectMapper JSON = new ObjectMapper();

    private SecurityResponses() {
    }

    /**
     * @param messageKey i18n key of the detail line, or {@code null} for the code's own message
     */
    static void write(HttpServletResponse response, ResponseErrorCode code, String messageKey,
                      boolean httpStatusMappingEnabled) throws IOException {
        if (response.isCommitted()) {
            return;
        }
        ResultEntity body = (messageKey == null)
                ? ResultEntity.failed(code)
                : ResultEntity.failed(code, I18nUtils.getMessage(messageKey));
        response.setStatus(httpStatusMappingEnabled ? code.getHttpStatus() : 200);
        response.setContentType("application/json;charset=UTF-8");
        JSON.writeValue(response.getWriter(), body);
    }
}
