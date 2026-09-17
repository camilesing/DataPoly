// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.gateway.config;

import com.netflix.discovery.shared.transport.jersey.TransportClientFactories;
import com.netflix.discovery.shared.transport.jersey3.Jersey3TransportClientFactories;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Cloud 2025 的 eureka-client 自动装配在 jersey 在场时不再自带 TransportClientFactories
 * （仅 eureka-server 侧的 EurekaServerJerseyClientAutoConfiguration 提供），纯客户端服务
 * 须自备该 bean，否则 RefreshableEurekaClientConfiguration 因缺 bean 启动失败。
 * manager 无需此类（eureka-server 依赖自带装配）。
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(Jersey3TransportClientFactories.class)
public class EurekaTransportConfiguration {

    @Bean
    @ConditionalOnMissingBean(TransportClientFactories.class)
    public TransportClientFactories<?> jersey3TransportClientFactories() {
        return Jersey3TransportClientFactories.getInstance();
    }
}
