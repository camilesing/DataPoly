// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.executor.config;

import com.cs.common.consts.Constants;
import com.cs.core.exec.ApiExecuteService;
import com.cs.core.servlet.AuthenticationFilter;
import com.cs.executor.filter.GatewaySourceFilter;
import com.cs.executor.model.HttpApiServlet;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.*;
import org.springframework.context.annotation.*;
import org.springframework.scheduling.annotation.EnableScheduling;

import javax.annotation.Resource;
import javax.servlet.http.HttpServlet;

@Slf4j
@EnableScheduling
@Configuration
public class ExecutorServletConfig {

    private static final String URL_PATH_PATTERN = String.format("/%s/*", Constants.API_PATH_PREFIX);

    @Value("${datapoly.executor.print-sql-log}")
    private boolean printSqlLog;

    @Resource
    private AuthenticationFilter authenticationFilter;

    @Resource
    private GatewaySourceFilter gatewaySourceFilter;

    /**
     * Gateway source check: rejects direct access that bypasses the gateway (S2)
     *
     * @return FilterRegistrationBean
     */
    @Bean
    public FilterRegistrationBean gatewaySourceFilterRegistrationBean() {
        FilterRegistrationBean registrationBean = new FilterRegistrationBean();
        registrationBean.setFilter(gatewaySourceFilter);
        registrationBean.addUrlPatterns("/*");
        registrationBean.setOrder(1);
        log.info("Register gatewaySourceFilter for /* UrlPatterns, and order is {}", 1);
        return registrationBean;
    }

    /**
     * API authentication
     *
     * @return FilterRegistrationBean
     */
    @Bean
    public FilterRegistrationBean authFilterRegistrationBean() {
        FilterRegistrationBean registrationBean = new FilterRegistrationBean();
        registrationBean.setFilter(authenticationFilter);
        registrationBean.addUrlPatterns(URL_PATH_PATTERN);
        registrationBean.setOrder(2);
        log.info("Register authFilter for {} UrlPatterns, and order is {}", URL_PATH_PATTERN, 2);
        return registrationBean;
    }

    /**
     * API request handling
     *
     * @return ServletRegistrationBean
     */
    @Bean
    public ServletRegistrationBean apiServletRegistrationBean(ApiExecuteService apiExecuteService) {
        HttpServlet httpServlet = new HttpApiServlet(apiExecuteService, printSqlLog);
        return new ServletRegistrationBean(httpServlet, URL_PATH_PATTERN);
    }
}
