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

    @Bean
    public WebMvcSseServerTransportProvider webMvcSseServerTransportProvider(WebMvcSseServerAuthChecker checker) {
        String sseEndpoint = Constants.DEFAULT_SSE_ENDPOINT;
        return new WebMvcSseServerTransportProvider(new ObjectMapper(), checker, Constants.MESSAGE_ENDPOINT, sseEndpoint);
    }

    @Bean
    public McpSyncServer mcpSyncServer(WebMvcSseServerTransportProvider transportProvider) {
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
    public WebMvcStreamHttpServerProvider webMvcStreamHttpServerProvider(WebMvcSseServerAuthChecker checker,
                                                                         McpSyncServer mcpSyncServer) {
        String mcpEndpoint = Constants.DEFAULT_STREAM_ENDPOINT;
        return new WebMvcStreamHttpServerProvider(new ObjectMapper(), checker, mcpEndpoint, mcpSyncServer);
    }

    @Bean
    public RouterFunction<ServerResponse> routerFunction(WebMvcSseServerTransportProvider transportProvider,
                                                         WebMvcStreamHttpServerProvider streamHttpServerProvider) {
        return transportProvider.getRouterFunction().and(streamHttpServerProvider.getRouterFunction());
    }
}
