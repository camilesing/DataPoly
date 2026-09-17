// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.executor.interceptor;

import com.cs.common.enums.ParamTypeEnum;
import com.cs.persistence.dao.SystemParamDao;
import com.cs.persistence.entity.SystemParamEntity;
import org.junit.*;

import javax.servlet.http.*;
import java.lang.reflect.Proxy;

public class ExecutorInterceptorTest {

    private static SystemParamDao daoReturning(final SystemParamEntity entity) {
        return new SystemParamDao() {
            @Override
            public SystemParamEntity getByParamKey(String paramKey) {
                if (null != entity) {
                    throwEntityIfBroken();
                }
                return entity;
            }

            private void throwEntityIfBroken() {
                if ("BROKEN".equals(entity.getParamValue())) {
                    throw new IllegalStateException("simulated dao failure");
                }
            }
        };
    }

    private static SystemParamEntity param(String value) {
        return SystemParamEntity.builder()
                .paramKey("apiDocOpen")
                .paramType(ParamTypeEnum.BOOLEAN)
                .paramValue(value)
                .build();
    }

    private static HttpServletRequest apidocRequest(String uri) {
        return (HttpServletRequest) Proxy.newProxyInstance(ExecutorInterceptorTest.class.getClassLoader(),
                new Class[]{HttpServletRequest.class}, (proxy, method, args) -> {
                    String name = method.getName();
                    if ("getRequestURI".equals(name)) {
                        return uri;
                    }
                    if ("hashCode".equals(name)) {
                        return System.identityHashCode(proxy);
                    }
                    if ("equals".equals(name)) {
                        return proxy == args[0];
                    }
                    if ("toString".equals(name)) {
                        return "stubRequest";
                    }
                    return null;
                });
    }

    private static final HttpServletResponse RESPONSE = (HttpServletResponse) Proxy.newProxyInstance(
            ExecutorInterceptorTest.class.getClassLoader(), new Class[]{HttpServletResponse.class},
            (proxy, method, args) -> {
                if ("getWriter".equals(method.getName())) {
                    return new java.io.PrintWriter(new java.io.StringWriter());
                }
                if ("hashCode".equals(method.getName())) {
                    return System.identityHashCode(proxy);
                }
                if ("toString".equals(method.getName())) {
                    return "stubResponse";
                }
                return null;
            });

    @Test
    public void missingParamFailsClosed() throws Exception {
        ExecutorInterceptor interceptor = new ExecutorInterceptor(daoReturning(null));
        Assert.assertFalse(interceptor.preHandle(apidocRequest("/apidoc/swagger.json"), RESPONSE, new Object()));
    }

    @Test
    public void daoFailureFailsClosed() throws Exception {
        ExecutorInterceptor interceptor = new ExecutorInterceptor(daoReturning(param("BROKEN")));
        Assert.assertFalse(interceptor.preHandle(apidocRequest("/apidoc/swagger.json"), RESPONSE, new Object()));
    }

    @Test
    public void explicitTrueAllows() throws Exception {
        ExecutorInterceptor interceptor = new ExecutorInterceptor(daoReturning(param("true")));
        Assert.assertTrue(interceptor.preHandle(apidocRequest("/apidoc/swagger.json"), RESPONSE, new Object()));
    }

    @Test
    public void explicitFalseRejects() throws Exception {
        ExecutorInterceptor interceptor = new ExecutorInterceptor(daoReturning(param("false")));
        Assert.assertFalse(interceptor.preHandle(apidocRequest("/apidoc/swagger.json"), RESPONSE, new Object()));
    }

    @Test
    public void nonApidocPathAlwaysPasses() throws Exception {
        ExecutorInterceptor interceptor = new ExecutorInterceptor(daoReturning(null));
        Assert.assertTrue(interceptor.preHandle(apidocRequest("/api/anything"), RESPONSE, new Object()));
    }
}
