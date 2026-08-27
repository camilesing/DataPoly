// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.manager.config;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.*;

import java.util.Arrays;

@Configuration
public class ManagerCorsConfiguration implements WebMvcConfigurer {

    private static final String ALL = "*";

    /**
     * CORS allowed origins (S5/H7): comma-separated whitelist, defaults to * for compatibility.
     * allowCredentials is only sent for an explicit whitelist, avoiding the wildcard-origins-with-credentials combination.
     */
    @Value("${datapoly.cors.allowed-origins:*}")
    private String allowedOrigins;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        String[] origins = splitOrigins();
        boolean wildcard = (1 == origins.length && ALL.equals(origins[0]));
        registry.addMapping("/**")
                .allowedOriginPatterns(origins)
                .allowedMethods("GET", "HEAD", "POST", "PUT", "DELETE", "OPTIONS")
                .allowCredentials(!wildcard)
                .maxAge(3600)
                .allowedHeaders("*");
    }

    private String[] splitOrigins() {
        return Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(StringUtils::isNotBlank)
                .toArray(String[]::new);
    }

}
