// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.gateway;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableDiscoveryClient
@EnableScheduling
@MapperScan("com.cs.persistence.mapper")
@SpringBootApplication(
        scanBasePackages = {
                "com.cs.persistence",
                "com.cs.core.gateway",
                "com.cs.gateway",
        }
)
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication springApplication = new SpringApplication(GatewayApplication.class);
        springApplication.setBannerMode(Banner.Mode.OFF);
        springApplication.run(args);
    }
}
