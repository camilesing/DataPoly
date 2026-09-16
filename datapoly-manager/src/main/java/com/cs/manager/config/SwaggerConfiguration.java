// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.manager.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.*;
import org.springframework.util.ReflectionUtils;
import org.springframework.web.servlet.handler.AbstractHandlerMethodMapping;
import springfox.documentation.builders.*;
import springfox.documentation.service.*;
import springfox.documentation.spi.DocumentationType;
import springfox.documentation.spring.web.plugins.Docket;
import springfox.documentation.spring.web.plugins.WebMvcRequestHandlerProvider;
import springfox.documentation.swagger2.annotations.EnableSwagger2;

import javax.servlet.http.*;
import java.lang.reflect.Field;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
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

    /**
     * Springfox 3.0 与 Boot 2.6+ 的 actuator 端点不兼容（WebMvcRequestHandlerProvider 遍历到
     * 使用 PathPatternParser 的 handler mapping 时 NPE）。在 provider 初始化后剔除基于
     * PathPatternParser 的 mapping（actuator 端点本就不进 swagger 文档），保证启动不失败。
     */
    @Bean
    public static BeanPostProcessor springfoxHandlerProviderBeanPostProcessor() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
                if (bean instanceof WebMvcRequestHandlerProvider) {
                    customizeSpringfoxHandlerMappings(getHandlerMappings(bean));
                }
                return bean;
            }

            private <T extends AbstractHandlerMethodMapping<?>> void customizeSpringfoxHandlerMappings(List<T> mappings) {
                List<T> copy = mappings.stream()
                        .filter(mapping -> mapping.getPatternParser() == null)
                        .collect(Collectors.toList());
                if (copy.size() != mappings.size()) {
                    log.info("Springfox: excluded {} PathPatternParser-based handler mapping(s) (actuator)",
                            mappings.size() - copy.size());
                    mappings.clear();
                    mappings.addAll(copy);
                }
            }

            @SuppressWarnings("unchecked")
            private List<AbstractHandlerMethodMapping<?>> getHandlerMappings(Object bean) {
                try {
                    Field field = ReflectionUtils.findField(bean.getClass(), "handlerMappings");
                    if (field == null) {
                        return Collections.emptyList();
                    }
                    field.setAccessible(true);
                    return (List<AbstractHandlerMethodMapping<?>>) field.get(bean);
                } catch (IllegalAccessException e) {
                    log.warn("Springfox handler mapping adjustment skipped: {}", e.getMessage());
                    return Collections.emptyList();
                }
            }
        };
    }

}
