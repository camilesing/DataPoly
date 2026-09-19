// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.manager.config;

import cn.hutool.extra.spring.SpringUtil;
import com.cs.common.consts.Constants;
import com.cs.common.util.PomVersionUtils;
import com.cs.persistence.dao.McpClientDao;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.server.*;
import io.modelcontextprotocol.server.transport.*;
import io.modelcontextprotocol.spec.McpSchema.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.*;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.function.*;

@Configuration
@EnableWebMvc
public class McpServerConfiguration {

    @Bean
    public WebMvcSseServerAuthChecker serverAuthChecker() {
        return new WebMvcSseServerAuthChecker() {

            @Override
            public String getTokenParamName() {
                return Constants.DEFAULT_MCP_TOKEN_PRAM_NAME;
            }

            @Override
            public boolean checkTokenValid(String token) {
                McpClientDao clientDao = SpringUtil.getBean(McpClientDao.class);
                return clientDao.existsAccessToken(token);
            }
        };
    }

    // 管理 MCP（McpAdminServerConfiguration）并行注册了同类型的 transport/server/stream/router bean，
    // 数据 MCP 侧所有按类型注入必须 @Qualifier 钉死到本类 bean，否则容器启动即歧义失败
    @Bean
    public WebMvcSseServerTransportProvider webMvcSseServerTransportProvider(
            @Qualifier("serverAuthChecker") WebMvcSseServerAuthChecker checker) {
        String sseEndpoint = Constants.DEFAULT_SSE_ENDPOINT;
        return new WebMvcSseServerTransportProvider(new ObjectMapper(), checker, Constants.MESSAGE_ENDPOINT, sseEndpoint);
    }

    @Bean
    public McpSyncServer mcpSyncServer(
            @Qualifier("webMvcSseServerTransportProvider") WebMvcSseServerTransportProvider transportProvider) {
        McpSyncServer syncServer = McpServer.sync(transportProvider)
                .serverInfo(Constants.MCP_SERVER_NAME, PomVersionUtils.getCachedProjectVersion())
                .capabilities(
                        ServerCapabilities.builder()
                                .resources(true, true)
                                .tools(true)
                                .prompts(true)
                                .logging()
                                .build())
                .build();
        syncServer.loggingNotification(
                LoggingMessageNotification.builder()
                        .level(LoggingLevel.INFO)
                        .logger("custom-logger")
                        .data("Server initialized")
                        .build());
        return syncServer;
    }

    @Bean
    public WebMvcStreamHttpServerProvider webMvcStreamHttpServerProvider(
            @Qualifier("serverAuthChecker") WebMvcSseServerAuthChecker checker,
            @Qualifier("mcpSyncServer") McpSyncServer mcpSyncServer) {
        String mcpEndpoint = Constants.DEFAULT_STREAM_ENDPOINT;
        return new WebMvcStreamHttpServerProvider(new ObjectMapper(), checker, mcpEndpoint, mcpSyncServer);
    }

    @Bean
    public RouterFunction<ServerResponse> routerFunction(
            @Qualifier("webMvcSseServerTransportProvider") WebMvcSseServerTransportProvider transportProvider,
            @Qualifier("webMvcStreamHttpServerProvider") WebMvcStreamHttpServerProvider streamHttpServerProvider) {
        return transportProvider.getRouterFunction().and(streamHttpServerProvider.getRouterFunction());
    }
}
