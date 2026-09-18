// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.manager.config;

import cn.hutool.extra.spring.SpringUtil;
import com.cs.common.consts.Constants;
import com.cs.common.util.PomVersionUtils;
import com.cs.persistence.dao.McpClientDao;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.WebMvcSseServerAuthChecker;
import io.modelcontextprotocol.server.transport.WebMvcSseServerTransportProvider;
import io.modelcontextprotocol.server.transport.WebMvcStreamHttpServerProvider;
import io.modelcontextprotocol.spec.McpSchema.ServerCapabilities;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

/**
 * 管理 MCP 服务：与数据工具服务({@link McpServerConfiguration})并行的第二个 MCP Server 实例，
 * 暴露 DataPoly 管理实体(数据源/模块/分组/API/应用客户端/MCP配置)的增删改查工具，供 AI Agent 运维使用。
 * 仅接受 manage_flag=1 的 MCP 令牌，与数据工具令牌隔离，避免普通令牌获得管理能力。
 */
@Configuration
public class McpAdminServerConfiguration {

    @Bean
    public WebMvcSseServerAuthChecker mcpAdminAuthChecker() {
        return new WebMvcSseServerAuthChecker() {

            @Override
            public String getTokenParamName() {
                return Constants.DEFAULT_MCP_TOKEN_PRAM_NAME;
            }

            @Override
            public boolean checkTokenValid(String token) {
                McpClientDao clientDao = SpringUtil.getBean(McpClientDao.class);
                return clientDao.existsManageAccessToken(token);
            }
        };
    }

    @Bean
    public WebMvcSseServerTransportProvider mcpAdminSseTransportProvider(
            @Qualifier("mcpAdminAuthChecker") WebMvcSseServerAuthChecker checker) {
        return new WebMvcSseServerTransportProvider(new ObjectMapper(), checker,
                Constants.ADMIN_MESSAGE_ENDPOINT, Constants.ADMIN_SSE_ENDPOINT);
    }

    @Bean
    public McpSyncServer mcpAdminSyncServer(
            @Qualifier("mcpAdminSseTransportProvider") WebMvcSseServerTransportProvider transportProvider) {
        return McpServer.sync(transportProvider)
                .serverInfo(Constants.MCP_ADMIN_SERVER_NAME, PomVersionUtils.getCachedProjectVersion())
                .capabilities(ServerCapabilities.builder().tools(true).build())
                .build();
    }

    @Bean
    public WebMvcStreamHttpServerProvider mcpAdminStreamHttpServerProvider(
            @Qualifier("mcpAdminAuthChecker") WebMvcSseServerAuthChecker checker,
            @Qualifier("mcpAdminSyncServer") McpSyncServer mcpAdminSyncServer) {
        return new WebMvcStreamHttpServerProvider(new ObjectMapper(), checker,
                Constants.ADMIN_STREAM_ENDPOINT, mcpAdminSyncServer);
    }

    @Bean
    public RouterFunction<ServerResponse> mcpAdminRouterFunction(
            @Qualifier("mcpAdminSseTransportProvider") WebMvcSseServerTransportProvider transportProvider,
            @Qualifier("mcpAdminStreamHttpServerProvider") WebMvcStreamHttpServerProvider streamHttpServerProvider) {
        return transportProvider.getRouterFunction().and(streamHttpServerProvider.getRouterFunction());
    }
}
