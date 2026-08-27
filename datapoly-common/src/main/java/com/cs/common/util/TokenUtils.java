// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.common.util;

import cn.hutool.core.util.HexUtil;
import cn.hutool.extra.spring.SpringUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.core.env.Environment;

import javax.servlet.http.HttpServletRequest;
import java.security.MessageDigest;
import java.util.UUID;

/**
 * Token utility
 */
@Slf4j
public final class TokenUtils {

    /**
     * Compatibility switch for passing token via URL query parameter (H7 hardening, off by
     * default): a token in the URL leaks via Referer/proxy access logs, so only the
     * Authorization: Bearer header is allowed by default; may be temporarily enabled via
     * environment variable for legacy callers.
     */
    private static final String KEY_QUERY_PARAM_ENABLED = "datapoly.security.token-query-param-enabled";
    private static final boolean DEFAULT_QUERY_PARAM_ENABLED = false;

    public static String getRequestToken(HttpServletRequest httpRequest) {
        // Read the token from the header first
        String authorization = httpRequest.getHeader("Authorization");
        if (!StringUtils.isEmpty(authorization)) {
            String[] splitString = authorization.split(" ");
            if (splitString.length == 2 && "Bearer".equalsIgnoreCase(splitString[0])) {
                return splitString[1];
            }
        }

        // If no token in header, fall back to query parameter only when the compatibility switch is enabled (off by default)
        if (StringUtils.isEmpty(authorization) && isQueryParamEnabled()) {
            return httpRequest.getParameter("token");
        }

        return null;
    }

    private static boolean isQueryParamEnabled() {
        try {
            Environment environment = SpringUtil.getBean(Environment.class);
            String value = environment.getProperty(KEY_QUERY_PARAM_ENABLED);
            return (null == value || value.isEmpty()) ? DEFAULT_QUERY_PARAM_ENABLED : Boolean.parseBoolean(value.trim());
        } catch (Exception e) {
            return DEFAULT_QUERY_PARAM_ENABLED;
        }
    }

    public static String generateValue() {
        return generateValue(UUID.randomUUID().toString());
    }

    public static int getTokenStringLength() {
        return "9097ac1ab13198dfa4ddb2ecc1079693".length();
    }

    private static String generateValue(String param) {
        try {
            MessageDigest algorithm = MessageDigest.getInstance("MD5");
            algorithm.reset();
            algorithm.update(param.getBytes());
            byte[] messageDigest = algorithm.digest();
            return HexUtil.encodeHexStr(messageDigest);
        } catch (Exception e) {
            throw new RuntimeException("Generate Token String failed: " + e.getMessage());
        }
    }

}
