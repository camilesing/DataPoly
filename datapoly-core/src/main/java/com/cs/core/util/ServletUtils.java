// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.core.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.*;

import javax.servlet.http.HttpServletRequest;

/**
 * Utilities for reading HTTP parameters from the servlet container.
 */
@Slf4j
public final class ServletUtils {

    public static HttpServletRequest getHttpServletRequest() {
        return ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
    }

    public static String getDomain() {
        HttpServletRequest request = getHttpServletRequest();
        StringBuffer url = request.getRequestURL();
        return url.delete(url.length() - request.getRequestURI().length(), url.length()).toString();
    }

    public static String getOrigin() {
        HttpServletRequest request = getHttpServletRequest();
        return request.getHeader("Origin");
    }

    public static String getPathUri() {
        HttpServletRequest request = getHttpServletRequest();
        return request.getRequestURI();
    }

    public static String getUserAgent() {
        HttpServletRequest request = getHttpServletRequest();
        return request.getHeader("user-agent");
    }


    /**
     * Get the client IP address.
     * <p>
     * Behind a reverse proxy such as Nginx, request.getRemoteAddr() does not return the real client IP.
     * </p>
     * <p>
     * With multi-level proxies, X-Forwarded-For contains a chain of IPs; the first non-unknown valid IP in it is the real client IP.
     * </p>
     */
    public static String getIpAddr() {
        HttpServletRequest request = getHttpServletRequest();
        String ip = "0.0.0.0";
        try {
            ip = request.getHeader("x-forwarded-for");
            if (StringUtils.isEmpty(ip) || "unknown".equalsIgnoreCase(ip)) {
                ip = request.getHeader("Proxy-Client-IP");
            }
            if (StringUtils.isEmpty(ip) || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
                ip = request.getHeader("WL-Proxy-Client-IP");
            }
            if (StringUtils.isEmpty(ip) || "unknown".equalsIgnoreCase(ip)) {
                ip = request.getHeader("HTTP_CLIENT_IP");
            }
            if (StringUtils.isEmpty(ip) || "unknown".equalsIgnoreCase(ip)) {
                ip = request.getHeader("HTTP_X_FORWARDED_FOR");
            }
            if (StringUtils.isEmpty(ip) || "unknown".equalsIgnoreCase(ip)) {
                ip = request.getRemoteAddr();
            }
        } catch (Exception e) {
            log.error("get client IP address error: ", e);
        }

        return ip;
    }

}