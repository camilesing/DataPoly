// Use of this source code is governed by a BSD-style license
package com.cs.core.servlet;

import cn.hutool.extra.spring.SpringUtil;
import com.cs.common.enums.HttpMethodEnum;
import com.cs.common.exception.CommonException;
import com.cs.common.exception.ResponseErrorCode;
import com.cs.common.service.FlowControlLease;
import com.cs.common.service.FlowControlManger;
import com.cs.common.util.I18nUtils;
import com.cs.core.datatask.DataTaskTestSupport;
import com.cs.core.exec.ExecutorMetadataCache;
import com.cs.core.executor.UnifyAlarmOpsService;
import com.cs.persistence.dao.ApiOnlineDao;
import com.cs.persistence.entity.AccessRecordEntity;
import com.cs.persistence.entity.ApiAssignmentEntity;
import com.cs.persistence.mapper.AccessRecordMapper;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

public class AuthenticationFilterTest {

    private static class MapperRecorder {
        final List<Object[]> inserts = new CopyOnWriteArrayList<>();
        final Map<String, Queue<Object>> stubs = new HashMap<>();

        @SuppressWarnings("unchecked")
        <M> M create(Class<M> type) {
            return (M) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                    (p, m, a) -> {
                        if ("insert".equals(m.getName())) {
                            inserts.add(a);
                            return 1;
                        }
                        Queue<Object> queue = stubs.get(m.getName());
                        if (queue != null && !queue.isEmpty()) {
                            return queue.poll();
                        }
                        if (m.getReturnType() == int.class) {
                            return 0;
                        }
                        return null;
                    });
        }

        void stub(String method, Object... results) {
            Queue<Object> queue = stubs.computeIfAbsent(method, k -> new LinkedList<>());
            for (Object result : results) {
                queue.add(result);
            }
        }

        void awaitInserts(int expected, long timeoutMs) throws InterruptedException {
            long deadline = System.currentTimeMillis() + timeoutMs;
            while (inserts.size() < expected) {
                if (System.currentTimeMillis() > deadline) {
                    fail("expected " + expected + " access records, saw " + inserts.size());
                }
                TimeUnit.MILLISECONDS.sleep(20);
            }
        }
    }

    /** Records token verification results without touching the real token pipeline. */
    private static class StubTokenService extends ClientTokenService {
        String appKeyForToken = "client-1";
        boolean groupAllowed = true;

        @Override
        public String verifyTokenAndGetAppKey(String tokenStr) {
            return appKeyForToken;
        }

        @Override
        public boolean verifyAuthGroup(String clientId, Long groupId) {
            return groupAllowed;
        }
    }

    private static class StubAlarmService extends UnifyAlarmOpsService {
        final List<Map<String, String>> alarms = new CopyOnWriteArrayList<>();

        @Override
        public void triggerAlarm(Map<String, String> dataModel) {
            alarms.add(dataModel);
        }
    }

    private AuthenticationFilter filter;
    private MapperRecorder onlineRecorder;
    private MapperRecorder accessRecorder;
    private StubTokenService tokenService;
    private StubAlarmService alarmService;
    private final AtomicInteger flowChecks = new AtomicInteger();
    private volatile boolean flowAllowed = true;
    private final AtomicInteger chainCalls = new AtomicInteger();
    private volatile RuntimeException chainFailure;

    private StringWriter responseBody;
    private HttpServletResponse response;

    @Before
    public void setUp() {
        onlineRecorder = new MapperRecorder();
        accessRecorder = new MapperRecorder();
        tokenService = new StubTokenService();
        alarmService = new StubAlarmService();

        ApiOnlineDao apiOnlineDao = new ApiOnlineDao();
        DataTaskTestSupport.setField(apiOnlineDao, "apiOnlineMapper",
                onlineRecorder.create(com.cs.persistence.mapper.ApiOnlineMapper.class));

        ExecutorMetadataCache metadataCache = new ExecutorMetadataCache();
        metadataCache.init();

        FlowControlManger flowControl = (FlowControlManger) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{FlowControlManger.class},
                (p, m, a) -> {
                    if ("checkFlowControl".equals(m.getName())) {
                        flowChecks.incrementAndGet();
                        return flowAllowed ? (FlowControlLease) () -> { } : null;
                    }
                    if ("hashCode".equals(m.getName())) {
                        return System.identityHashCode(p);
                    }
                    if ("equals".equals(m.getName())) {
                        return p == a[0];
                    }
                    return null;
                });

        filter = new AuthenticationFilter();
        DataTaskTestSupport.setField(filter, "apiOnlineDao", apiOnlineDao);
        DataTaskTestSupport.setField(filter, "executorMetadataCache", metadataCache);
        DataTaskTestSupport.setField(filter, "flowControlManger", flowControl);
        DataTaskTestSupport.setField(filter, "clientTokenService", tokenService);
        DataTaskTestSupport.setField(filter, "accessRecordMapper", accessRecorder.create(AccessRecordMapper.class));
        DataTaskTestSupport.setField(filter, "unifyAlarmOpsService", alarmService);

        responseBody = new StringWriter();
        response = (HttpServletResponse) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{HttpServletResponse.class},
                (p, m, a) -> {
                    switch (m.getName()) {
                        case "getWriter":
                            return new PrintWriter(responseBody, true);
                        case "getStatus":
                            return statusRef[0];
                        case "setStatus":
                            statusRef[0] = (Integer) a[0];
                            return null;
                        default:
                            return null;
                    }
                });
    }

    private final Integer[] statusRef = new Integer[]{200};

    @After
    public void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    private HttpServletRequest request(String uri, String method, Map<String, String> headers) {
        HttpServletRequest request = (HttpServletRequest) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{HttpServletRequest.class},
                (proxy, m, a) -> {
                    switch (m.getName()) {
                        case "getRequestURI":
                            return uri;
                        case "getMethod":
                            return method;
                        case "getHeader":
                            return headers.get(String.valueOf(a[0]));
                        case "getRemoteAddr":
                            return "10.0.0.1";
                        default:
                            return null;
                    }
                });
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        return request;
    }

    private FilterChain chain() {
        return (request, response) -> {
            chainCalls.incrementAndGet();
            if (chainFailure != null) {
                throw chainFailure;
            }
        };
    }

    /** ApiOnlineDao.getByUk maps an ApiOnlineEntity row; semantics ride in the content JSON + columns. */
    private com.cs.persistence.entity.ApiOnlineEntity entity(boolean open, boolean flowStatus, boolean alarm) {
        com.cs.persistence.entity.ApiOnlineEntity entity = new com.cs.persistence.entity.ApiOnlineEntity();
        entity.setId(7L);
        entity.setContent("{\"id\":7,\"method\":\"GET\",\"path\":\"ping\",\"name\":\"ping-api\"}");
        entity.setGroupId(5L);
        entity.setOpen(open);
        entity.setFlowStatus(flowStatus);
        entity.setAlarm(alarm);
        return entity;
    }

    @Test
    public void testUnknownPathYields404WithoutChainCall() throws Exception {
        onlineRecorder.stub("selectOne", (Object) null);
        filter.doFilter(request("/api/missing", "GET", new HashMap<String, String>()),
                response, chain());
        assertEquals(404, statusRef[0].intValue());
        assertEquals(0, chainCalls.get());
        assertTrue(responseBody.toString().contains("ERROR_PATH_NOT_EXISTS"));
    }

    @Test
    public void testOpenApiPassesThroughAndRecordsAccess() throws Exception {
        onlineRecorder.stub("selectOne", entity(true, false, false));
        filter.doFilter(request("/api/ping", "GET", new HashMap<String, String>()), response, chain());
        assertEquals(1, chainCalls.get());

        accessRecorder.awaitInserts(1, 5000);
        AccessRecordEntity record = (AccessRecordEntity) accessRecorder.inserts.get(0)[0];
        assertEquals("/api/ping", record.getPath());
        assertEquals(Integer.valueOf(200), record.getStatus());
        assertEquals(Long.valueOf(7L), record.getApiId());
        assertNull(record.getClientKey());
        assertTrue("no alarm for successful open api", alarmService.alarms.isEmpty());
    }

    @Test
    public void testClosedApiRequiresBearerToken() throws Exception {
        onlineRecorder.stub("selectOne", entity(false, false, false));
        filter.doFilter(request("/api/ping", "GET", new HashMap<String, String>()), response, chain());
        assertEquals(401, statusRef[0].intValue());
        assertEquals(0, chainCalls.get());

        accessRecorder.awaitInserts(1, 5000);
        AccessRecordEntity record = (AccessRecordEntity) accessRecorder.inserts.get(0)[0];
        assertEquals(Integer.valueOf(401), record.getStatus());
        assertNotNull(record.getException());
    }

    @Test
    public void testClosedApiWithTokenAndGroupPasses() throws Exception {
        onlineRecorder.stub("selectOne", entity(false, false, false));
        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Bearer token-1");
        filter.doFilter(request("/api/ping", "GET", headers), response, chain());
        assertEquals(1, chainCalls.get());

        accessRecorder.awaitInserts(1, 5000);
        AccessRecordEntity record = (AccessRecordEntity) accessRecorder.inserts.get(0)[0];
        assertEquals("client-1", record.getClientKey());
        assertEquals(Integer.valueOf(200), record.getStatus());
    }

    @Test
    public void testClosedApiWithWrongGroupYields403() throws Exception {
        onlineRecorder.stub("selectOne", entity(false, false, false));
        tokenService.groupAllowed = false;
        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Bearer token-1");
        filter.doFilter(request("/api/ping", "GET", headers), response, chain());
        assertEquals(403, statusRef[0].intValue());
        assertEquals(0, chainCalls.get());
    }

    @Test
    public void testClosedApiWithInvalidTokenYields401() throws Exception {
        onlineRecorder.stub("selectOne", entity(false, false, false));
        tokenService.appKeyForToken = null;
        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Bearer expired");
        filter.doFilter(request("/api/ping", "GET", headers), response, chain());
        assertEquals(401, statusRef[0].intValue());
        assertEquals(0, chainCalls.get());
    }

    @Test
    public void testFlowControlGateConsultedOnlyWhenEnabled() throws Exception {
        onlineRecorder.stub("selectOne",
                entity(true, true, false), entity(true, true, false), entity(true, false, false));
        flowAllowed = false;
        filter.doFilter(request("/api/flow", "GET", new HashMap<String, String>()), response, chain());
        assertEquals(1, flowChecks.get());
        assertEquals(0, chainCalls.get());

        flowAllowed = true;
        filter.doFilter(request("/api/flow2", "GET", new HashMap<String, String>()), response, chain());
        assertEquals(2, flowChecks.get());
        assertEquals(1, chainCalls.get());

        filter.doFilter(request("/api/noflow", "GET", new HashMap<String, String>()), response, chain());
        assertEquals("flow disabled apis skip the flow check", 2, flowChecks.get());
        assertEquals(2, chainCalls.get());
    }

    @Test
    public void testBusinessExceptionFromChainMapsToHttpStatus() throws Exception {
        onlineRecorder.stub("selectOne", entity(true, false, false));
        chainFailure = new CommonException(ResponseErrorCode.ERROR_INVALID_ARGUMENT, "bad input");
        filter.doFilter(request("/api/ping", "GET", new HashMap<String, String>()), response, chain());
        assertEquals(ResponseErrorCode.ERROR_INVALID_ARGUMENT.getHttpStatus(), statusRef[0].intValue());
        assertEquals("chain ran once before the business exception surfaced", 1, chainCalls.get());
    }

    @Test
    public void testFailingClosedApiWithAlarmTriggersAlarm() throws Exception {
        onlineRecorder.stub("selectOne", entity(false, false, true));
        filter.doFilter(request("/api/ping", "GET", new HashMap<String, String>()), response, chain());
        assertEquals(401, statusRef[0].intValue());

        long deadline = System.currentTimeMillis() + 5000;
        while (alarmService.alarms.isEmpty() && System.currentTimeMillis() < deadline) {
            TimeUnit.MILLISECONDS.sleep(20);
        }
        assertEquals(1, alarmService.alarms.size());
        assertEquals("GET", alarmService.alarms.get(0).get("method"));
        assertEquals("/api/ping", alarmService.alarms.get(0).get("path"));
    }

    @Test
    public void testForbiddenMessageNamesTheApi() throws Exception {
        // Regression: the path was handed over as the defaultMessage (a bare String binds to that
        // overload), so callers saw "No Permission to access: {0}" with the placeholder untouched
        java.lang.reflect.Field factoryField = SpringUtil.class.getDeclaredField("beanFactory");
        factoryField.setAccessible(true);
        Object previousFactory = factoryField.get(null);
        java.lang.reflect.Field cachedField = I18nUtils.class.getDeclaredField("messageSource");
        cachedField.setAccessible(true);
        Object previousSource = cachedField.get(null);
        StaticMessageSource source = new StaticMessageSource();
        source.addMessage("auth.no.permission", Locale.ENGLISH, "No Permission to access: {0}");
        Object beanFactory = Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{ConfigurableListableBeanFactory.class}, (p, m, a) -> {
                    if ("getBean".equals(m.getName()) && null != a && 1 == a.length && a[0] instanceof Class) {
                        return source;
                    }
                    if ("hashCode".equals(m.getName())) {
                        return System.identityHashCode(p);
                    }
                    if ("equals".equals(m.getName())) {
                        return p == a[0];
                    }
                    return null;
                });
        try {
            factoryField.set(null, beanFactory);
            cachedField.set(null, null);
            LocaleContextHolder.setLocale(Locale.ENGLISH);
            onlineRecorder.stub("selectOne", entity(false, false, false));
            tokenService.groupAllowed = false;
            Map<String, String> headers = new HashMap<>();
            headers.put("Authorization", "Bearer token-1");

            filter.doFilter(request("/api/ping", "GET", headers), response, chain());

            assertEquals(403, statusRef[0].intValue());
            assertTrue(responseBody.toString(), responseBody.toString().contains("/api/ping[GET]"));
            assertFalse(responseBody.toString(), responseBody.toString().contains("{0}"));
        } finally {
            factoryField.set(null, previousFactory);
            cachedField.set(null, previousSource);
            LocaleContextHolder.resetLocaleContext();
        }
    }

    @Test
    public void testOverlongCallerControlledHeadersAreFittedToTheRecordColumns() throws Exception {
        // user-agent (255) and the forwarded-for chain (64) go into narrow columns as the caller sent
        // them: a longer value failed the INSERT, and the access record — plus the alarm behind it — vanished
        StringBuilder userAgent = new StringBuilder();
        for (int i = 0; i < 60; i++) {
            userAgent.append("Mozilla/5.0 ");
        }
        StringBuilder forwardedFor = new StringBuilder("10.0.0.1");
        for (int i = 0; i < 30; i++) {
            forwardedFor.append(", 10.0.0.").append(i);
        }
        Map<String, String> headers = new HashMap<>();
        headers.put("user-agent", userAgent.toString());
        headers.put("x-forwarded-for", forwardedFor.toString());
        onlineRecorder.stub("selectOne", entity(true, false, false));

        filter.doFilter(request("/api/ping", "GET", headers), response, chain());

        accessRecorder.awaitInserts(1, 5000);
        AccessRecordEntity record = (AccessRecordEntity) accessRecorder.inserts.get(0)[0];
        assertEquals(255, record.getUserAgent().length());
        assertTrue(record.getUserAgent().startsWith("Mozilla/5.0 Mozilla"));
        assertEquals(64, record.getIpAddr().length());
        assertTrue(record.getIpAddr().startsWith("10.0.0.1, 10.0.0.0"));
    }

    @Test
    public void testOverlongExceptionMessageIsFittedToTheRecordColumn() throws Exception {
        // the PostgreSQL migration sizes the exception column at 1024 characters
        StringBuilder message = new StringBuilder("boom ");
        for (int i = 0; i < 600; i++) {
            message.append("detail ");
        }
        chainFailure = new RuntimeException(message.toString());
        onlineRecorder.stub("selectOne", entity(true, false, false));

        filter.doFilter(request("/api/ping", "GET", new HashMap<String, String>()), response, chain());

        assertEquals(500, statusRef[0].intValue());
        accessRecorder.awaitInserts(1, 5000);
        AccessRecordEntity record = (AccessRecordEntity) accessRecorder.inserts.get(0)[0];
        assertEquals(Integer.valueOf(500), record.getStatus());
        assertEquals(1024, record.getException().length());
        assertTrue(record.getException().startsWith("boom detail"));
    }

    @Test
    public void testUnknownHttpMethodFallsBackToGet() throws Exception {
        onlineRecorder.stub("selectOne", (Object) null, entity(true, false, false));
        filter.doFilter(request("/api/missing", "PROPFIND", new HashMap<String, String>()), response, chain());
        assertEquals(404, statusRef[0].intValue());
        filter.doFilter(request("/api/ping2", "PROPFIND", new HashMap<String, String>()), response, chain());
        assertEquals(1, chainCalls.get());
    }
}
