// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.core.util;

import cn.hutool.extra.spring.SpringUtil;
import org.apache.commons.lang3.StringUtils;
import org.springframework.core.env.Environment;

import java.util.*;

/**
 * access_record parameter masking (phase 4): replaces sensitive parameter values with a mask based on parameter-name keywords,
 * affecting only the audit display; the execution path keeps using the original values.
 *
 * <p>Keywords are configurable via {@code datapoly.executor.access-record.mask-keys} (comma-separated);
 * the default list covers common password/secret/token naming; matching is lowercase contains (catches user_password,
 * api_token and other compound names), with hyphens normalized to underscores before comparison.
 */
public final class ParamMaskUtils {

    public static final String MASK = "******";
    public static final String MASK_KEYS_CONFIG = "datapoly.executor.access-record.mask-keys";
    public static final String DEFAULT_MASK_KEYS =
            "password,passwd,pwd,secret,appsecret,app_secret,access_token,authorization,credential,"
                    + "api_key,apikey,access_key,accesskey,token";

    private static volatile String[] maskKeys;

    private ParamMaskUtils() {
    }

    public static Map<String, Object> mask(Map<String, Object> params) {
        if (null == params || params.isEmpty()) {
            return params;
        }
        boolean changed = false;
        Map<String, Object> result = new LinkedHashMap<>(params.size());
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            if (isSensitiveKey(entry.getKey())) {
                result.put(entry.getKey(), MASK);
                changed = true;
            } else {
                result.put(entry.getKey(), entry.getValue());
            }
        }
        return changed ? result : params;
    }

    static boolean isSensitiveKey(String key) {
        if (StringUtils.isBlank(key)) {
            return false;
        }
        String normalized = key.toLowerCase(Locale.ROOT).replace('-', '_');
        for (String maskKey : getMaskKeys()) {
            if (normalized.contains(maskKey)) {
                return true;
            }
        }
        return false;
    }

    private static String[] getMaskKeys() {
        if (null != maskKeys) {
            return maskKeys;
        }
        synchronized (ParamMaskUtils.class) {
            if (null == maskKeys) {
                String csv = DEFAULT_MASK_KEYS;
                try {
                    Environment environment = SpringUtil.getBean(Environment.class);
                    String configured = environment.getProperty(MASK_KEYS_CONFIG);
                    if (StringUtils.isNotBlank(configured)) {
                        csv = configured;
                    }
                } catch (Exception e) {
                    // Use the default keyword list without a Spring container (plain unit tests)
                }
                maskKeys = Arrays.stream(csv.split(","))
                        .map(String::trim)
                        .filter(StringUtils::isNotEmpty)
                        .map(k -> k.toLowerCase(Locale.ROOT).replace('-', '_'))
                        .toArray(String[]::new);
            }
            return maskKeys;
        }
    }

}
