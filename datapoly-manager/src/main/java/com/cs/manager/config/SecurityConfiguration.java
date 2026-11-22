// Use of this source code is governed by a BSD-style license
package com.cs.manager.config;

import com.cs.common.consts.Constants;
import com.cs.core.service.LoginSessionService;
import com.cs.manager.security.RequireAdmin;
import com.cs.manager.security.RequirePermission;
import com.cs.manager.security.ResultEntityAccessDeniedHandler;
import com.cs.manager.security.ResultEntityAuthenticationEntryPoint;
import com.cs.manager.security.TokenAuthenticationFilter;
import com.cs.manager.service.PermissionService;
import com.cs.persistence.dao.SystemUserDao;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.apache.commons.lang3.StringUtils;
import org.springframework.aop.Advisor;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.aop.support.StaticMethodMatcherPointcut;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.context.annotation.Role;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

/**
 * The manager's single request gate (Spring Security full adoption): the token filter publishes
 * the caller, the chain rules decide which paths may pass unauthenticated, and the method advice
 * ({@code @RequirePermission} / {@code @RequireAdmin}) decides per module×action. Everything
 * downstream — role, lock, permission rows — is re-read from the database per request.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfiguration {

    private static final String CORS_ALL = "*";

    /**
     * Paths that authenticate somewhere else or nowhere — the successor of the former
     * interceptor's exclude list, and the one source both the chain's permitAll rules and the
     * token filter's skip list are built from. {@code /mcp/**} carries its own token check,
     * {@code /eureka/**} must stay open for executor/gateway registration, the rest are the
     * built-in UI, swagger, actuator and health probes.
     */
    static final List<String> PERMIT_ALL_PATTERNS = List.of(
            "/user/login",
            "/mcp/**",
            "/js/**",
            "/css/**",
            "/fonts/**",
            "/index.html",
            "/favicon.svg",
            "/static/**",
            "/",
            "/swagger-resources/**",
            "/swagger-resources",
            "/swagger-ui.html",
            "/v2/**",
            "/v3/**",
            "/swagger-ui/**",
            "/actuator/**",
            "/eureka/**",
            "/error**",
            Constants.MANAGER_API_V1 + "/health/**");

    /**
     * Whether to map error codes to real HTTP statuses — shared with {@code ExceptionController}
     * so the filter chain and the advice answer in exactly the same shape.
     */
    @Value("${datapoly.manager.http-status-mapping.enabled:true}")
    private boolean httpStatusMappingEnabled;

    /** CORS allowed origins (S5/H7), the same property the former MVC-only CORS config used. */
    @Value("${datapoly.cors.allowed-origins:*}")
    private String allowedOrigins;

    @Bean
    public SecurityFilterChain managerFilterChain(HttpSecurity http, LoginSessionService loginSessionService,
                                                  SystemUserDao systemUserDao) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                // The built-in UI predates security headers; keep responses byte-identical to before
                .headers(headers -> headers.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(PERMIT_ALL_PATTERNS.toArray(new String[0])).permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(new ResultEntityAuthenticationEntryPoint(httpStatusMappingEnabled))
                        .accessDeniedHandler(new ResultEntityAccessDeniedHandler(httpStatusMappingEnabled)))
                .addFilterBefore(
                        new TokenAuthenticationFilter(loginSessionService, systemUserDao, PERMIT_ALL_PATTERNS,
                                httpStatusMappingEnabled),
                        UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /**
     * Method-level enforcement of the two security marks. Registered as an infrastructure advisor
     * so Spring's auto-proxying wraps every controller method carrying either annotation; the
     * decision itself lives in {@link PermissionService}, the single judge.
     */
    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    public static Advisor requirePermissionAdvisor(PermissionService permissionService) {
        return new DefaultPointcutAdvisor(
                new StaticMethodMatcherPointcut() {
                    @Override
                    public boolean matches(Method method, Class<?> targetClass) {
                        return AnnotatedElementUtils.findMergedAnnotation(method, RequirePermission.class) != null
                                || AnnotatedElementUtils.findMergedAnnotation(method, RequireAdmin.class) != null;
                    }
                },
                (MethodInterceptor) (MethodInvocation invocation) -> {
                    RequirePermission require = AnnotatedElementUtils.findMergedAnnotation(invocation.getMethod(),
                            RequirePermission.class);
                    if (require != null) {
                        permissionService.checkCurrent(require.module(), require.action());
                    } else if (AnnotatedElementUtils.findMergedAnnotation(invocation.getMethod(),
                            RequireAdmin.class) != null) {
                        permissionService.checkAdminCurrent();
                    }
                    return invocation.proceed();
                });
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        String[] origins = splitOrigins();
        boolean wildcard = (1 == origins.length && CORS_ALL.equals(origins[0]));
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of(origins));
        config.setAllowedMethods(Arrays.asList("GET", "HEAD", "POST", "PUT", "DELETE", "OPTIONS"));
        // Only an explicit origin whitelist may carry credentials — same rule as the former MVC mapping
        config.setAllowCredentials(!wildcard);
        config.setMaxAge(3600L);
        config.setAllowedHeaders(List.of(CORS_ALL));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    private String[] splitOrigins() {
        return Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(StringUtils::isNotBlank)
                .toArray(String[]::new);
    }
}
