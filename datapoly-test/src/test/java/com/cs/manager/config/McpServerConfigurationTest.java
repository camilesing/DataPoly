// Use of this source code is governed by a BSD-style license
package com.cs.manager.config;

import cn.hutool.extra.spring.SpringUtil;
import com.cs.persistence.dao.McpClientDao;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.WebMvcSseServerAuthChecker;
import io.modelcontextprotocol.server.transport.WebMvcSseServerTransportProvider;
import io.modelcontextprotocol.server.transport.WebMvcStreamHttpServerProvider;
import org.junit.After;
import org.junit.Test;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

import java.lang.reflect.Proxy;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * Exercises the data-tool MCP server wiring factory methods directly: the token checker
 * (backed by a faked {@link McpClientDao} through SpringUtil), both transports and the
 * composite router function they build.
 */
public class McpServerConfigurationTest {

    @After
    public void tearDown() throws Exception {
        java.lang.reflect.Field field = SpringUtil.class.getDeclaredField("beanFactory");
        field.setAccessible(true);
        field.set(null, null);
    }

    private void installSpringUtil(Object... beans) throws Exception {
        Object beanFactory = Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{ConfigurableListableBeanFactory.class},
                (proxy, method, args) -> {
                    if ("getBean".equals(method.getName()) && args != null && args.length == 1
                            && args[0] instanceof Class) {
                        for (Object bean : beans) {
                            if (((Class<?>) args[0]).isInstance(bean)) {
                                return bean;
                            }
                        }
                        return null;
                    }
                    if ("hashCode".equals(method.getName())) {
                        return System.identityHashCode(proxy);
                    }
                    if ("equals".equals(method.getName())) {
                        return proxy == args[0];
                    }
                    if ("toString".equals(method.getName())) {
                        return "fake-bean-factory";
                    }
                    return null;
                });
        java.lang.reflect.Field field = SpringUtil.class.getDeclaredField("beanFactory");
        field.setAccessible(true);
        field.set(null, beanFactory);
    }

    @Test
    public void authCheckerValidatesAnyDataTokenAgainstClientDao() throws Exception {
        McpClientDao dao = new McpClientDao() {
            @Override
            public boolean existsAccessToken(String accessToken) {
                return "data-token".equals(accessToken);
            }
        };
        installSpringUtil(dao);

        WebMvcSseServerAuthChecker checker = new McpServerConfiguration().serverAuthChecker();

        assertEquals("token", checker.getTokenParamName());
        assertTrue(checker.checkTokenValid("data-token"));
        assertFalse(checker.checkTokenValid("other-token"));
    }

    /**
     * Seeds the version cache so mcpSyncServer's serverInfo lookup stays deterministic:
     * the live probe in PomVersionUtils is classpath-layout dependent (under surefire the
     * test-classes directory shadows the jar package lookup and the file branch throws).
     */
    @SuppressWarnings("unchecked")
    private static void seedProjectVersion(String version) throws Exception {
        java.lang.reflect.Field cacheField = com.cs.common.util.PomVersionUtils.class
                .getDeclaredField("persistCache");
        cacheField.setAccessible(true);
        Map<String, String> cache = (Map<String, String>) cacheField.get(null);
        cache.put("version=", version);
    }

    @Test
    public void serverBeansWireTransportsAndRouter() throws Exception {
        seedProjectVersion("1.9.0-test");
        McpServerConfiguration configuration = new McpServerConfiguration();
        WebMvcSseServerAuthChecker checker = configuration.serverAuthChecker();

        WebMvcSseServerTransportProvider sseProvider = configuration.webMvcSseServerTransportProvider(checker);
        assertNotNull(sseProvider);
        assertNotNull(sseProvider.getRouterFunction());

        McpSyncServer syncServer = configuration.mcpSyncServer(sseProvider);
        assertNotNull(syncServer);
        assertTrue("fresh server must expose no tools", syncServer.listTool().isEmpty());

        WebMvcStreamHttpServerProvider streamProvider =
                configuration.webMvcStreamHttpServerProvider(checker, syncServer);
        assertNotNull(streamProvider);

        RouterFunction<ServerResponse> router = configuration.routerFunction(sseProvider, streamProvider);
        assertNotNull(router);
    }
}
