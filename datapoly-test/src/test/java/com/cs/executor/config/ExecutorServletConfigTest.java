// Use of this source code is governed by a BSD-style license
package com.cs.executor.config;

import com.cs.core.exec.ApiExecuteService;
import com.cs.core.servlet.AuthenticationFilter;
import com.cs.executor.filter.GatewaySourceFilter;
import com.cs.executor.model.HttpApiServlet;
import org.junit.Before;
import org.junit.Test;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.web.servlet.ServletRegistrationBean;

import java.lang.reflect.Field;

import static org.junit.Assert.*;

/**
 * Executor servlet/filter registration wiring: URL patterns, filter order
 * (gateway source check first, auth second) and the API servlet mapping with
 * the printSqlLog flag handed through.
 */
public class ExecutorServletConfigTest {

    private ExecutorServletConfig config;

    @Before
    public void setUp() throws Exception {
        config = new ExecutorServletConfig();
        set(config, "printSqlLog", true);
        set(config, "authenticationFilter", new AuthenticationFilter());
        set(config, "gatewaySourceFilter", new GatewaySourceFilter());
    }

    @Test
    public void gatewaySourceFilterRegistrationCoversAllUrlsFirst() {
        FilterRegistrationBean<GatewaySourceFilter> bean = config.gatewaySourceFilterRegistrationBean();
        assertNotNull(bean.getFilter());
        assertTrue(bean.getFilter() instanceof GatewaySourceFilter);
        assertTrue(bean.getUrlPatterns().contains("/*"));
        assertEquals(1, bean.getOrder());
    }

    @Test
    public void authFilterRegistrationCoversApiPathSecond() {
        FilterRegistrationBean<AuthenticationFilter> bean = config.authFilterRegistrationBean();
        assertNotNull(bean.getFilter());
        assertTrue(bean.getFilter() instanceof AuthenticationFilter);
        assertTrue("pattern must be scoped to the API prefix, was: " + bean.getUrlPatterns(),
                bean.getUrlPatterns().contains("/api/*"));
        assertEquals(2, bean.getOrder());
    }

    @Test
    public void apiServletRegistrationMapsApiPrefixAndCarriesPrintSqlLog() throws Exception {
        ServletRegistrationBean<HttpApiServlet> bean = config.apiServletRegistrationBean(new ApiExecuteService());
        assertNotNull(bean.getServlet());
        assertTrue(bean.getServlet() instanceof HttpApiServlet);
        assertTrue(bean.getUrlMappings().contains("/api/*"));

        // the private flag on the servlet must match the injected config value
        Field flag = HttpApiServlet.class.getDeclaredField("printSqlLog");
        flag.setAccessible(true);
        assertEquals(Boolean.TRUE, flag.get(bean.getServlet()));
    }

    @Test
    public void printSqlLogDisabledIsAlsoForwarded() throws Exception {
        ExecutorServletConfig quiet = new ExecutorServletConfig();
        set(quiet, "printSqlLog", false);
        set(quiet, "authenticationFilter", new AuthenticationFilter());
        set(quiet, "gatewaySourceFilter", new GatewaySourceFilter());

        ServletRegistrationBean<HttpApiServlet> bean = quiet.apiServletRegistrationBean(new ApiExecuteService());
        Field flag = HttpApiServlet.class.getDeclaredField("printSqlLog");
        flag.setAccessible(true);
        assertEquals(Boolean.FALSE, flag.get(bean.getServlet()));
    }

    private static void set(Object target, String name, Object value) throws Exception {
        Field field = ExecutorServletConfig.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
