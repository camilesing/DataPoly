// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.executor;

import lombok.extern.slf4j.Slf4j;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.scheduling.annotation.EnableScheduling;

@Slf4j
@EnableDiscoveryClient
@EnableScheduling
@MapperScan("com.cs.persistence.mapper")
@SpringBootApplication(
        scanBasePackages = {
                "com.cs.persistence",
                "com.cs.core.driver",
                "com.cs.core.servlet",
                "com.cs.core.exec",
                "com.cs.core.executor",
                "com.cs.core.datatask",
                "com.cs.cache",
                "com.cs.executor",
        }
)
public class ExecutorApplication {

    public static void main(String[] args) {
        SpringApplication springApplication = new SpringApplication(ExecutorApplication.class);
        springApplication.setBannerMode(Banner.Mode.OFF);
        springApplication.run(args);
    }
}
