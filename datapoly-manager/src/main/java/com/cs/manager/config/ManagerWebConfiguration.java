// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.manager.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.*;

@Configuration
public class ManagerWebConfiguration implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Serve the built-in UI from classpath (resources/index.html)
        registry.addResourceHandler("/index.html").addResourceLocations("classpath:/index.html");
        registry.addResourceHandler("/favicon.svg").addResourceLocations("classpath:/svg.ico");
        // Serve static assets from classpath (resources/static/)
        registry.addResourceHandler("/static/**").addResourceLocations("classpath:/static/");
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/").setViewName("forward:index.html");
    }
}
