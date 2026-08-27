// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.manager;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableEurekaServer
@EnableDiscoveryClient
@EnableScheduling
@MapperScan("com.cs.persistence.mapper")
@SpringBootApplication(
        scanBasePackages = {
                "com.cs.persistence",
                "com.cs.core.driver",
                "com.cs.core.gateway",
                "com.cs.core.executor",
                "com.cs.core.datatask",
                "com.cs.core.service",
                "com.cs.core.exec",
                "com.cs.core.extension",
                "com.cs.cache",
                "com.cs.manager"
        }
)
public class ManagerApplication {

    public static void main(String[] args) {
        SpringApplication springApplication = new SpringApplication(ManagerApplication.class);
        springApplication.setBannerMode(Banner.Mode.OFF);
        springApplication.run(args);
    }
}
