// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.executor.config;

import com.cs.common.consts.Constants;
import com.cs.executor.interceptor.ExecutorInterceptor;
import com.cs.persistence.dao.SystemParamDao;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.*;

import javax.annotation.Resource;

@Configuration
public class ExecutorWebConfig implements WebMvcConfigurer {

    private final String swaggerPathPattern = Constants.API_DOC_PATH_PREFIX + "/swagger/**";
    private final String knif4jPathPattern = Constants.API_DOC_PATH_PREFIX + "/knife4j/**";
    // apidoc interception covers the whole /apidoc/**: single-segment paths like swagger.json
    // do not match the swagger/** Ant pattern and were previously bypassed
    private final String apiDocPathPattern = Constants.API_DOC_PATH_PREFIX + "/**";


    @Resource
    private SystemParamDao systemParamDao;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler(swaggerPathPattern).addResourceLocations("classpath:/swagger/");
        registry.addResourceHandler(knif4jPathPattern).addResourceLocations("classpath:/knife4j/");
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        ExecutorInterceptor interceptor = new ExecutorInterceptor(systemParamDao);
        registry.addInterceptor(interceptor).addPathPatterns(apiDocPathPattern);
    }

}
