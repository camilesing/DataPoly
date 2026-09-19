// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.manager.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.*;

@Configuration
public class ManagerWebConfiguration implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 内置 UI 由 build-ui.sh 同步进 resources/static/（classpath:/static/）。
        // location 必须为目录风格（以 / 结尾），文件式 location 会被 Spring 校验拒绝
        registry.addResourceHandler("/index.html").addResourceLocations("classpath:/static/");
        registry.addResourceHandler("/favicon.svg").addResourceLocations("classpath:/static/");
        // Serve static assets from classpath (resources/static/)
        registry.addResourceHandler("/static/**").addResourceLocations("classpath:/static/");
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/").setViewName("forward:index.html");
    }
}
