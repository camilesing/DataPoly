// Use of this source code is governed by a BSD-style license
package com.cs.executor.model;

import com.cs.common.dto.ResultEntity;
import com.cs.common.enums.DataTypeFormatEnum;
import com.cs.core.exec.ApiAssignmentCache;
import com.cs.core.exec.ApiExecuteService;
import com.cs.persistence.entity.ApiAssignmentEntity;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * HttpApiServlet dispatch for every supported HTTP method, response format selection
 * (full ResultEntity vs data-only) and the printSqlLog flag pass-through.
 */
public class HttpApiServletTest {

    private FakeApiExecuteService apiExecuteService;
    private ApiAssignmentEntity apiConfig;
    private StringWriter responseBody;

    @Before
    public void setUp() {
        apiExecuteService = new FakeApiExecuteService();
        apiConfig = new ApiAssignmentEntity();
        Map<DataTypeFormatEnum, String> responseFormat = new HashMap<>();
        responseFormat.put(DataTypeFormatEnum.USE_SYSTEM_RESPONSE_FORMAT, "true");
        apiConfig.setResponseFormat(responseFormat);
        ApiAssignmentCache.set(apiConfig);
        responseBody = new StringWriter();
    }

    @After
    public void tearDown() {
        ApiAssignmentCache.remove();
    }

    @Test
    public void getReturnsFullResultEntityWhenSystemFormatEnabled() throws Exception {
        apiExecuteService.result = ResultEntity.success("payload-data");
        HttpApiServlet servlet = new HttpApiServlet(apiExecuteService, false);

        servlet.service(request("GET"), response());

        assertEquals(1, apiExecuteService.calls.size());
        assertSame(apiConfig, apiExecuteService.calls.get(0)[0]);
        String body = responseBody.toString();
        assertTrue("system format must wrap the data in a ResultEntity, was: " + body,
                body.contains("\"code\"") && body.contains("\"success\"") && body.contains("payload-data"));
    }

    @Test
    public void allSupportedMethodsAreDispatchedToProcess() throws Exception {
        apiExecuteService.result = ResultEntity.success("m");
        HttpApiServlet servlet = new HttpApiServlet(apiExecuteService, false);
        for (String method : new String[]{"GET", "HEAD", "POST", "PUT", "DELETE"}) {
            responseBody.getBuffer().setLength(0);
            servlet.service(request(method), response());
            assertEquals("method " + method + " must be handled", 1, apiExecuteService.calls.size());
            assertEquals(method, apiExecuteService.lastMethod);
            apiExecuteService.calls.clear();
        }
    }

    @Test
    public void systemFormatDisabledReturnsOnlyDataPart() throws Exception {
        apiConfig.getResponseFormat().put(DataTypeFormatEnum.USE_SYSTEM_RESPONSE_FORMAT, "false");
        apiExecuteService.result = ResultEntity.success("only-data");
        HttpApiServlet servlet = new HttpApiServlet(apiExecuteService, false);

        servlet.service(request("POST"), response());

        String body = responseBody.toString();
        assertEquals("\"only-data\"", body);
    }

    @Test
    public void systemFormatDefaultWhenEntryMissing() throws Exception {
        apiConfig.getResponseFormat().remove(DataTypeFormatEnum.USE_SYSTEM_RESPONSE_FORMAT);
        apiExecuteService.result = ResultEntity.success("d");
        HttpApiServlet servlet = new HttpApiServlet(apiExecuteService, false);

        servlet.service(request("GET"), response());
        // missing config defaults to the system format (only "false" disables it)
        assertTrue(responseBody.toString().contains("\"code\""));
    }

    @Test
    public void printSqlLogFlagIsForwardedToExecuteService() throws Exception {
        apiExecuteService.result = ResultEntity.success("d");

        HttpApiServlet verbose = new HttpApiServlet(apiExecuteService, true);
        verbose.service(request("GET"), response());
        assertEquals(Boolean.TRUE, apiExecuteService.calls.get(0)[2]);

        apiExecuteService.calls.clear();
        HttpApiServlet quiet = new HttpApiServlet(apiExecuteService, false);
        quiet.service(request("GET"), response());
        assertEquals(Boolean.FALSE, apiExecuteService.calls.get(0)[2]);
    }

    @Test
    public void writesToResponseWriterInUtf8() throws Exception {
        apiExecuteService.result = ResultEntity.success("数据");
        HttpApiServlet servlet = new HttpApiServlet(apiExecuteService, false);
        servlet.service(request("GET"), response());
        assertTrue(responseBody.toString().contains("数据"));
    }

    private HttpServletRequest request(String method) {
        return (HttpServletRequest) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{HttpServletRequest.class},
                (p, m, a) -> {
                    switch (m.getName()) {
                        case "getMethod":
                            return method;
                        case "getRequestURI":
                            return "/api/demo";
                        case "getRemoteAddr":
                            return "10.0.0.1";
                        default:
                            return null;
                    }
                });
    }

    private HttpServletResponse response() {
        return (HttpServletResponse) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{HttpServletResponse.class},
                (p, m, a) -> {
                    switch (m.getName()) {
                        case "getWriter":
                            return new PrintWriter(responseBody, true);
                        case "containsHeader":
                            // HttpServlet#maybeSetLastModified probes this on the HEAD path
                            return Boolean.TRUE;
                        default:
                            return null;
                    }
                });
    }

    private static class FakeApiExecuteService extends ApiExecuteService {
        ResultEntity<Object> result = ResultEntity.success("default");
        final List<Object[]> calls = new ArrayList<>();
        volatile String lastMethod;

        @Override
        public ResultEntity<Object> execute(ApiAssignmentEntity config, jakarta.servlet.http.HttpServletRequest request,
                                            boolean printSqlLog) {
            calls.add(new Object[]{config, request, printSqlLog});
            lastMethod = request.getMethod();
            return result;
        }
    }
}
