// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.executor.interceptor;

import com.cs.common.consts.Constants;
import com.cs.common.exception.ResponseErrorCode;
import com.cs.core.util.ResponseWriteUtils;
import com.cs.persistence.dao.SystemParamDao;
import com.cs.persistence.entity.SystemParamEntity;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.*;
import java.io.IOException;

public class ExecutorInterceptor implements HandlerInterceptor {

    private final SystemParamDao systemParamDao;

    public ExecutorInterceptor(SystemParamDao systemParamDao) {
        this.systemParamDao = systemParamDao;
    }

    /**
     * apidoc switch check (fail-closed after the exposure lockdown): reject when the system
     * parameter is missing, has an invalid type, or fails to load; allow only when the
     * value is explicitly true.
     */
    private boolean isApiDocOpen() {
        try {
            SystemParamEntity entity = systemParamDao.getByParamKey(Constants.SYS_PARAM_KEY_API_DOC_OPEN);
            if (null == entity || null == entity.getParamType() || null == entity.getParamValue()) {
                return false;
            }
            Class<Boolean> clazz = entity.getParamType().getClazz();
            return clazz.cast(entity.getParamType().getConverter().apply(entity.getParamValue()));
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        String path = request.getRequestURI();
        if (path.startsWith("/apidoc") && !isApiDocOpen()) {
            ResponseWriteUtils.writeError(response, HttpServletResponse.SC_FORBIDDEN,
                    ResponseErrorCode.ERROR_ACCESS_FORBIDDEN, "apidoc disabled");
            return false;
        }
        return true;
    }
}
