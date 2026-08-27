// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.manager.config;

import com.cs.common.consts.Constants;
import com.cs.common.exception.*;
import com.cs.common.util.*;
import com.cs.persistence.dao.SystemUserDao;
import com.cs.persistence.entity.SystemUserEntity;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.*;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.*;
import java.util.Arrays;

@Configuration
public class AuthenticationConfiguration implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authenticationInterceptor())
                .excludePathPatterns(Arrays.asList(
                        "/user/login",
                        "/mcp/**",
                        "/js/**",
                        "/css/**",
                        "/fonts/**",
                        "/index.html",
                        "/favicon.svg",
                        "/swagger-resources/**",
                        "/swagger-resources",
                        "/swagger-ui.html",
                        "/v2/**",
                        "/v3/**",
                        "/swagger-ui/**",
                        "/actuator/**",
                        "/eureka/**",
                        "/error**",
                        Constants.MANAGER_API_V1 + "/health/**"
                ))
                .addPathPatterns("/**");
    }

    @Bean
    public HandlerInterceptor authenticationInterceptor() {
        return new HandlerInterceptor() {

            @Resource
            private SystemUserDao systemUserDAO;

            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
                if (!(handler instanceof HandlerMethod)) {
                    return true;
                }

                String accessToken = TokenUtils.getRequestToken(request);
                if (StringUtils.isEmpty(accessToken)) {
                    throw new CommonException(ResponseErrorCode.ERROR_TOKEN_EXPIRED, "auth.no.token");
                }

                Object cache = CacheUtils.get(accessToken);
                if (null == cache) {
                    throw new CommonException(ResponseErrorCode.ERROR_TOKEN_EXPIRED, "auth.token.invalid");
                }

                SystemUserEntity systemUserEntity = (SystemUserEntity) cache;
                SystemUserEntity user = systemUserDAO.findByUsername(systemUserEntity.getUsername());
                if (null == user) {
                    throw new CommonException(ResponseErrorCode.ERROR_ACCESS_FORBIDDEN, "auth.token.user.not.exists");
                }
                if (Boolean.TRUE.equals(user.getLocked())) {
                    throw new CommonException(ResponseErrorCode.ERROR_ACCESS_FORBIDDEN, "auth.token.user.locked");
                }

                request.setAttribute("username", user.getUsername());
                return true;
            }
        };
    }

}