// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.executor.interceptor;

import com.cs.common.consts.Constants;
import com.cs.common.exception.ResponseErrorCode;
import com.cs.core.util.ResponseWriteUtils;
import com.cs.persistence.dao.SystemParamDao;
import com.cs.persistence.entity.SystemParamEntity;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.*;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

public class ExecutorInterceptor implements HandlerInterceptor {

    /**
     * The apidoc switch is read from the database; caching it briefly keeps a closed switch
     * from costing one unauthenticated query per blocked probe.
     */
    private static final long API_DOC_OPEN_CACHE_TTL_MS = 5_000L;

    private final SystemParamDao systemParamDao;

    private volatile boolean cachedApiDocOpen;
    private volatile long cachedApiDocOpenAt;

    public ExecutorInterceptor(SystemParamDao systemParamDao) {
        this.systemParamDao = systemParamDao;
    }

    /**
     * apidoc switch check (fail-closed after the exposure lockdown): reject when the system
     * parameter is missing, has an invalid type, or fails to load; allow only when the
     * value is explicitly true.
     */
    private boolean isApiDocOpen() {
        long now = System.currentTimeMillis();
        long cachedAt = this.cachedApiDocOpenAt;
        if (now - cachedAt < API_DOC_OPEN_CACHE_TTL_MS) {
            return cachedApiDocOpen;
        }
        boolean open = loadApiDocOpen();
        cachedApiDocOpen = open;
        cachedApiDocOpenAt = now;
        return open;
    }

    private boolean loadApiDocOpen() {
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
        String path = decodePath(request.getRequestURI());
        if (path.startsWith("/apidoc") && !isApiDocOpen()) {
            ResponseWriteUtils.writeError(response, HttpServletResponse.SC_FORBIDDEN,
                    ResponseErrorCode.ERROR_ACCESS_FORBIDDEN, "apidoc disabled");
            return false;
        }
        return true;
    }

    /**
     * {@code getRequestURI()} is the undecoded form, so percent-encoded spellings of the
     * lockdown prefix (e.g. {@code /%61pidoc}) must be decoded before the check. A malformed
     * sequence cannot decode into the prefix, so the raw form is kept (fail-closed).
     */
    private static String decodePath(String uri) {
        try {
            // '+' is a literal character in a path, not an encoded space
            return URLDecoder.decode(uri.replace("+", "%2B"), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return uri;
        }
    }
}
