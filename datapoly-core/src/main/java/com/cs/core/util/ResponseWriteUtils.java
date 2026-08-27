// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.core.util;

import cn.hutool.json.JSONUtil;
import com.cs.common.dto.ResultEntity;
import com.cs.common.exception.ResponseErrorCode;
import com.google.common.base.Charsets;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.MediaType;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Unified error response writing: HTTP status + ResultEntity envelope + application/json;charset=UTF-8
 */
public final class ResponseWriteUtils {

    private ResponseWriteUtils() {
    }

    public static void writeError(HttpServletResponse response, int status, ResponseErrorCode code,
                                  String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(Charsets.UTF_8.name());
        ResultEntity result = StringUtils.isNotBlank(message)
                ? ResultEntity.failed(code, message)
                : ResultEntity.failed(code);
        response.getWriter().append(JSONUtil.toJsonStr(result));
    }
}
