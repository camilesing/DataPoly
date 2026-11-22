// Use of this source code is governed by a BSD-style license
package com.cs.manager.security;

import com.cs.common.consts.UserRole;
import com.cs.common.exception.ResponseErrorCode;
import com.cs.common.util.TokenUtils;
import com.cs.core.service.LoginSessionService;
import com.cs.persistence.dao.SystemUserDao;
import com.cs.persistence.entity.SystemUserEntity;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

/**
 * Bearer-token gate of the manager, the filter-chain successor of the former session
 * interceptor: resolves the token into a login session (in-memory entry first, the persisted
 * row on miss), reloads the authoritative user row (lock + role) on every request and publishes
 * the caller as a Spring Security authentication carrying the role read from that row — never
 * from anything the client sent. Requests to paths that authenticate somewhere else
 * ({@code /mcp/**}, login, static assets, eureka, health, swagger, actuator) skip the gate, the
 * same single list the chain's permitAll rules come from.
 */
public class TokenAuthenticationFilter extends OncePerRequestFilter {

    private final LoginSessionService loginSessionService;
    private final SystemUserDao systemUserDao;
    private final boolean httpStatusMappingEnabled;
    private final List<AntPathRequestMatcher> skipMatchers;

    public TokenAuthenticationFilter(LoginSessionService loginSessionService, SystemUserDao systemUserDao,
                                     List<String> skipPatterns, boolean httpStatusMappingEnabled) {
        this.loginSessionService = loginSessionService;
        this.systemUserDao = systemUserDao;
        this.httpStatusMappingEnabled = httpStatusMappingEnabled;
        this.skipMatchers = skipPatterns.stream().map(AntPathRequestMatcher::new).toList();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // Preflight never carries credentials; the CORS filter answers it before authorization
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;
        }
        for (AntPathRequestMatcher matcher : skipMatchers) {
            if (matcher.matches(request)) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String accessToken = TokenUtils.getRequestToken(request);
        if (StringUtils.isEmpty(accessToken)) {
            // Anonymous: protected paths are rejected by the chain (entry point) with the same body
            chain.doFilter(request, response);
            return;
        }

        LoginSessionService.LoginSession session = loginSessionService.resolve(accessToken);
        if (null == session) {
            SecurityResponses.write(response, ResponseErrorCode.ERROR_TOKEN_EXPIRED, "auth.token.invalid",
                    httpStatusMappingEnabled);
            return;
        }

        SystemUserEntity user = systemUserDao.findByUsername(session.getUsername());
        if (null == user) {
            SecurityResponses.write(response, ResponseErrorCode.ERROR_ACCESS_FORBIDDEN, "auth.token.user.not.exists",
                    httpStatusMappingEnabled);
            return;
        }
        if (Boolean.TRUE.equals(user.getLocked())) {
            SecurityResponses.write(response, ResponseErrorCode.ERROR_ACCESS_FORBIDDEN, "auth.token.user.locked",
                    httpStatusMappingEnabled);
            return;
        }

        UserRole role = UserRole.of(user.getRole());
        UsernamePasswordAuthenticationToken authentication = UsernamePasswordAuthenticationToken.authenticated(
                user.getUsername(), accessToken,
                Collections.singletonList(new SimpleGrantedAuthority(role.authority())));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        // Legacy request attribute: self-service handlers (change own password, ...) read this
        request.setAttribute("username", user.getUsername());

        chain.doFilter(request, response);
    }
}
