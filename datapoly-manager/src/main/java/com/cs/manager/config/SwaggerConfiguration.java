// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.manager.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import springfox.documentation.builders.*;
import springfox.documentation.service.*;
import springfox.documentation.spi.DocumentationType;
import springfox.documentation.spring.web.plugins.Docket;
import springfox.documentation.swagger2.annotations.EnableSwagger2;

import javax.servlet.http.*;
import java.util.*;

@Configuration
@EnableSwagger2
public class SwaggerConfiguration {

    private static final String API_CONTROLLER_PACKAGE = "com.cs.manager.controller";

    @Value("${datapoly.manager.swagger.enable:true}")
    private boolean enable;

    @Bean
    public Docket managerApi() {
        RequestParameterBuilder ticketPar = new RequestParameterBuilder();
        List<RequestParameter> pars = new ArrayList<>();
        ticketPar.name("Authorization")
                .description("认证头，格式：Bearer {token}")
                .in(ParameterType.HEADER)
                .required(false)
                .build();
        pars.add(ticketPar.build());

        return new Docket(DocumentationType.SWAGGER_2)
                .enable(enable)
                .groupName("Manager的接口")
                .apiInfo(new ApiInfoBuilder()
                        .title("DataPoly管理服务API文档")
                        .description("在线API文档")
                        .version("1.0")
                        .build())
                .select()
                .apis(RequestHandlerSelectors.basePackage(API_CONTROLLER_PACKAGE))
                .paths(PathSelectors.any())
                .build()
                .globalRequestParameters(pars)
                .ignoredParameterTypes(HttpServletResponse.class, HttpServletRequest.class);
    }

}
