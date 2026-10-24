// Use of this source code is governed by a BSD-style license
package com.cs.manager.service;

import com.cs.common.consts.Constants;
import com.cs.common.exception.CommonException;
import com.cs.common.exception.ResponseErrorCode;
import com.cs.manager.config.DataPolyUrlConfiguration;
import com.cs.manager.model.ServiceUrlItemResponse;
import com.cs.persistence.dao.SystemParamDao;
import com.cs.persistence.entity.SystemParamEntity;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.List;

/**
 * Single source of the externally reachable gateway / manager addresses.
 * Priority: system param (gatewayUrl / managerUrl, admin-editable at runtime)
 * > environment-configured datapoly.url.* (effective only while the service is
 * registered, as before) > Nacos discovery instance address. A param value that
 * is not a plain absolute http(s):// address is treated as unset, so a garbage
 * value written through the generic param endpoint can only fall back to the
 * next source, never leak into the UI.
 */
@Slf4j
@Service
public class ServiceUrlResolver {

    public static final String SOURCE_PARAM = "PARAM";
    public static final String SOURCE_ENV = "ENV";
    public static final String SOURCE_DISCOVERY = "DISCOVERY";
    public static final String SOURCE_NONE = "NONE";

    private static final String HTTP_PREFIX = "http://";
    private static final String HTTPS_PREFIX = "https://";

    @Resource
    private SystemParamDao systemParamDao;
    @Resource
    private DataPolyUrlConfiguration datapolyUrlConfiguration;
    @Resource
    private DiscoveryClient discoveryClient;

    public String resolveGateway() {
        Resolved resolved = tryResolve(Constants.SYS_PARAM_KEY_GATEWAY_URL, datapolyUrlConfiguration.getGateway(),
                Constants.GATEWAY_APPLICATION_NAME);
        if (null == resolved) {
            throw new CommonException(ResponseErrorCode.ERROR_RESOURCE_NOT_EXISTS, "gateway.not.found");
        }
        return resolved.getUrl();
    }

    public String resolveManager() {
        Resolved resolved = tryResolve(Constants.SYS_PARAM_KEY_MANAGER_URL, datapolyUrlConfiguration.getManager(),
                Constants.MANAGER_APPLICATION_NAME);
        if (null == resolved) {
            throw new CommonException(ResponseErrorCode.ERROR_RESOURCE_NOT_EXISTS, "manager.not.found");
        }
        return resolved.getUrl();
    }

    public String getGatewayApiPrefix() {
        return resolveGateway() + "/" + Constants.API_PATH_PREFIX + "/";
    }

    /** Config view for the settings page: never throws, so the form stays usable while a service is unregistered. */
    public ServiceUrlItemResponse gatewayConfig() {
        return configOf(Constants.SYS_PARAM_KEY_GATEWAY_URL, datapolyUrlConfiguration.getGateway(),
                Constants.GATEWAY_APPLICATION_NAME);
    }

    public ServiceUrlItemResponse managerConfig() {
        return configOf(Constants.SYS_PARAM_KEY_MANAGER_URL, datapolyUrlConfiguration.getManager(),
                Constants.MANAGER_APPLICATION_NAME);
    }

    /**
     * Validates and normalizes a candidate value from the update endpoint: blank
     * clears the setting (falls back to env/discovery), anything else must be an
     * absolute http(s):// address without whitespace. Trailing slashes are stripped.
     */
    public static String normalizeValidatedUrl(String url) {
        if (StringUtils.isBlank(url)) {
            return "";
        }
        String trimmed = url.trim();
        if (!isAcceptable(trimmed)) {
            throw new CommonException(ResponseErrorCode.ERROR_INVALID_ARGUMENT, "node.url.invalid", trimmed);
        }
        return stripTrailingSlash(trimmed);
    }

    private ServiceUrlItemResponse configOf(String paramKey, String envUrl, String serviceName) {
        String value = readStoredValue(paramKey);
        Resolved resolved = tryResolve(paramKey, envUrl, serviceName);
        if (null == resolved) {
            return new ServiceUrlItemResponse(value, null, SOURCE_NONE);
        }
        return new ServiceUrlItemResponse(value, resolved.getUrl(), resolved.getSource());
    }

    private Resolved tryResolve(String paramKey, String envUrl, String serviceName) {
        String paramUrl = readUsableParamUrl(paramKey);
        if (null != paramUrl) {
            log.info("Configured address found in system param [{}]: {}, skip auto self discover", paramKey, paramUrl);
            return new Resolved(paramUrl, SOURCE_PARAM);
        }
        List<ServiceInstance> instances = discoveryClient.getInstances(serviceName);
        ServiceInstance instance = instances.stream().findAny().orElse(null);
        // Prefer the externally configured address only when the service actually exists
        if (StringUtils.isNotBlank(envUrl) && instance != null) {
            log.info("Configured address found :{},Skip auto self discover", envUrl);
            return new Resolved(stripTrailingSlash(envUrl.trim()), SOURCE_ENV);
        }
        if (null != instance) {
            return new Resolved(String.format("http://%s:%d", instance.getHost(), instance.getPort()), SOURCE_DISCOVERY);
        }
        return null;
    }

    private String readUsableParamUrl(String paramKey) {
        String stored = readStoredValue(paramKey);
        if (StringUtils.isBlank(stored)) {
            return null;
        }
        String trimmed = stored.trim();
        if (!isAcceptable(trimmed)) {
            log.warn("System param [{}] holds an invalid url [{}], fall back to env/discovered address", paramKey, stored);
            return null;
        }
        return stripTrailingSlash(trimmed);
    }

    private String readStoredValue(String paramKey) {
        SystemParamEntity entity = systemParamDao.getByParamKey(paramKey);
        return null == entity ? "" : StringUtils.trimToEmpty(entity.getParamValue());
    }

    private static boolean isAcceptable(String url) {
        if (StringUtils.containsWhitespace(url)) {
            return false;
        }
        return (url.startsWith(HTTP_PREFIX) && url.length() > HTTP_PREFIX.length())
                || (url.startsWith(HTTPS_PREFIX) && url.length() > HTTPS_PREFIX.length());
    }

    private static String stripTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private static final class Resolved {
        private final String url;
        private final String source;

        private Resolved(String url, String source) {
            this.url = url;
            this.source = source;
        }

        private String getUrl() {
            return url;
        }

        private String getSource() {
            return source;
        }
    }
}