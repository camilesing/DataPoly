// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.manager.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.parameters.Parameter;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * API 文档由 springdoc 提供（springfox 已停更且不兼容 Boot 3）。
 * 文档与 UI 的总开关经 springdoc.api-docs.enabled / springdoc.swagger-ui.enabled 配置
 * （沿用环境变量 DATAPOLY_MANAGER_SWAGGER_ENABLE，见 application.yaml）。
 */
@Configuration
public class SwaggerConfiguration {

    private static final String API_CONTROLLER_PACKAGE = "com.cs.manager.controller";

    @Bean
    public GroupedOpenApi managerApi() {
        return GroupedOpenApi.builder()
                .group("Manager的接口")
                .packagesToScan(API_CONTROLLER_PACKAGE)
                .addOperationCustomizer((operation, handlerMethod) -> {
                    operation.addParametersItem(new Parameter()
                            .name("Authorization")
                            .description("认证头，格式：Bearer {token}")
                            .in("header")
                            .required(false));
                    return operation;
                })
                .build();
    }

    @Bean
    public OpenAPI managerOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("DataPoly管理服务API文档")
                        .description("在线API文档")
                        .version("1.0"));
    }

}
