// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.core.util;

import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.server.reactive.ServerHttpRequest;

import java.util.Locale;

@UtilityClass
public class LocaleUtils {

    public static Locale resolveLocale(ServerHttpRequest request) {
        String acceptLanguage = request.getHeaders().getFirst("Accept-Language");
        if (StringUtils.isNotBlank(acceptLanguage)) {
            return Locale.forLanguageTag(acceptLanguage.replace("_", "-"));
        }
        return Locale.US;
    }
}
