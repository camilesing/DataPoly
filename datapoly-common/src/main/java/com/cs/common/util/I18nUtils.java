// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.common.util;

import cn.hutool.extra.spring.SpringUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;

import java.util.Locale;

@Slf4j
public class I18nUtils {

    private static MessageSource messageSource;

    private I18nUtils() {
    }

    private static MessageSource getMessageSource() {
        if (messageSource == null) {
            try {
                messageSource = SpringUtil.getBean(MessageSource.class);
            } catch (Exception e) {
                // No Spring container (e.g. plain unit test): returns null, getMessage falls back to the key itself
                log.warn("No Spring context available for i18n, use message key as default.");
                return null;
            }
        }
        return messageSource;
    }

    /**
     * Get an internationalized message
     *
     * @param key message key
     * @param args message arguments
     * @return localized message string
     */
    public static String getMessage(String key, Object... args) {
        MessageSource messageSource = getMessageSource();
        Locale locale = LocaleContextHolder.getLocale();
        try {
            return messageSource.getMessage(key, args, locale);
        } catch (Exception e) {
            log.warn("No key found for {}, use key as default.", key);
            return key;
        }
    }

    /**
     * Get an internationalized message with a default value
     *
     * @param key message key
     * @param defaultMessage default message
     * @param args message arguments
     * @return localized message string
     */
    public static String getMessage(String key, String defaultMessage, Object... args) {
        MessageSource messageSource = getMessageSource();
        Locale locale = LocaleContextHolder.getLocale();
        try {
            return messageSource.getMessage(key, args, defaultMessage, locale);
        } catch (Exception e) {
            log.warn("No key found for {}, use key as default.", key);
            return defaultMessage;
        }
    }

    /**
     * Get an internationalized message with an explicit Locale (for WebFlux etc. where RequestContext is unavailable)
     *
     * @param key message key
     * @param locale locale
     * @param args message arguments
     * @return localized message string
     */
    public static String getMessage(String key, Locale locale, Object... args) {
        MessageSource messageSource = getMessageSource();
        try {
            return messageSource.getMessage(key, args, locale);
        } catch (Exception e) {
            log.warn("No key found for {}, use key as default.", key);
            return key;
        }
    }
}
