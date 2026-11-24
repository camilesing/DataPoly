// Use of this source code is governed by a BSD-style license
package com.cs.manager.model;

import cn.hutool.extra.spring.SpringUtil;
import com.cs.common.consts.Constants;
import com.cs.common.dto.ItemParam;
import com.cs.common.dto.BaseParam;
import com.cs.common.dto.ResultEntity;
import com.cs.common.enums.ParamLocationEnum;
import com.cs.common.enums.ParamTypeEnum;
import com.cs.core.exec.ApiExecuteService;
import com.cs.core.service.SystemParamService;
import com.cs.persistence.dao.ApiOnlineDao;
import com.cs.persistence.entity.ApiAssignmentEntity;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import org.junit.After;
import org.junit.Test;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;

import java.lang.reflect.Proxy;
import java.util.*;

import static org.junit.Assert.*;

/**
 * Drives the per-API MCP tool wrapper with fake ApiExecuteService/SystemParamService beans
 * installed into SpringUtil: JSON schema generation from params and the execute path with
 * default-value and page-size argument preparation.
 */
public class McpToolCallHandlerTest {

    private static String textOf(CallToolResult result) {
        return ((McpSchema.TextContent) result.getContent().get(0)).getText();
    }

    private static class FakeApiExecuteService extends ApiExecuteService {
        ApiAssignmentEntity capturedConfig;
        Map<String, Object> capturedArgs;
        Boolean capturedPrintSqlLog;
        Object answer = Collections.singletonMap("rows", 12);
        RuntimeException failure;

        @Override
        public ResultEntity<Object> execute(ApiAssignmentEntity config, Map<String, Object> paramValues,
                                            boolean printSqlLog) {
            this.capturedConfig = config;
            this.capturedArgs = new HashMap<>(paramValues);
            this.capturedPrintSqlLog = printSqlLog;
            if (null != failure) {
                throw failure;
            }
            return ResultEntity.success(answer);
        }
    }

    private static class FakeSystemParamService extends SystemParamService {
        int pageSize = 777;

        @Override
        public Integer getIntByParamKey(String key, int defaultValue) {
            return pageSize;
        }
    }

    @After
    public void tearDown() throws Exception {
        java.lang.reflect.Field field = SpringUtil.class.getDeclaredField("beanFactory");
        field.setAccessible(true);
        field.set(null, null);
    }

    private FakeApiExecuteService installBeans(FakeSystemParamService paramService) throws Exception {
        FakeApiExecuteService executeService = new FakeApiExecuteService();
        Object beanFactory = Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{ConfigurableListableBeanFactory.class},
                (proxy, method, args) -> {
                    if ("getBean".equals(method.getName()) && args != null && args.length == 1
                            && args[0] instanceof Class) {
                        if (args[0] == ApiExecuteService.class) {
                            return executeService;
                        }
                        if (args[0] == SystemParamService.class) {
                            return paramService;
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
        return executeService;
    }

    private static ItemParam param(String name, ParamTypeEnum type, boolean required, String defaultValue) {
        ItemParam param = new ItemParam();
        param.setName(name);
        param.setType(type);
        param.setLocation(ParamLocationEnum.REQUEST_FORM);
        param.setRequired(required);
        param.setDefaultValue(defaultValue);
        param.setRemark("remark of " + name);
        return param;
    }

    private static ApiAssignmentEntity configWithParams(List<ItemParam> params) {
        ApiAssignmentEntity entity = new ApiAssignmentEntity();
        entity.setId(5L);
        entity.setName("user-query");
        entity.setParams(params);
        return entity;
    }

    /** The online-assignment table as the handler reads it back on every call. */
    private static class StubApiOnlineDao extends ApiOnlineDao {
        ApiAssignmentEntity online;

        @Override
        public ApiAssignmentEntity getByApiId(Long apiId) {
            return online;
        }
    }

    private static StubApiOnlineDao serving(ApiAssignmentEntity config) {
        StubApiOnlineDao dao = new StubApiOnlineDao();
        dao.online = config;
        return dao;
    }

    /** A handler over a fake online-assignment table that currently serves exactly {@code config}. */
    private static McpToolCallHandler handlerFor(String name, String description, ApiAssignmentEntity config) {
        return new McpToolCallHandler(name, description, config, serving(config));
    }

    @Test
    public void schemaDescribesScalarArrayAndNestedObjectParams() throws Exception {
        installBeans(new FakeSystemParamService());

        ItemParam userId = param("userId", ParamTypeEnum.LONG, true, null);
        ItemParam keyword = param("keyword", ParamTypeEnum.STRING, false, "abc");
        ItemParam flags = param("flags", ParamTypeEnum.BOOLEAN, false, null);
        flags.setIsArray(true);
        ItemParam filter = param("filter", ParamTypeEnum.OBJECT, false, null);
        BaseParam city = new BaseParam();
        city.setName("city");
        city.setType(ParamTypeEnum.STRING);
        city.setRequired(true);
        city.setRemark("city remark");
        BaseParam zone = new BaseParam();
        zone.setName("zone");
        zone.setType(ParamTypeEnum.STRING);
        zone.setRequired(false);
        filter.setChildren(Arrays.asList(city, zone));

        McpToolCallHandler handler = handlerFor("query_users", "查询用户",
                configWithParams(Arrays.asList(userId, keyword, flags, filter)));
        McpSchema.Tool tool = handler.getMcpToolSchema();

        assertEquals("query_users", tool.getName());
        assertEquals("查询用户", tool.getDescription());
        McpSchema.JsonSchema schema = tool.getInputSchema();
        assertEquals("object", schema.getType());

        Map<String, Object> properties = schema.getProperties();
        assertEquals(4, properties.size());

        Map<?, ?> userIdNode = (Map<?, ?>) properties.get("userId");
        assertEquals("number", userIdNode.get("type"));
        assertEquals("remark of userId", userIdNode.get("description"));

        Map<?, ?> keywordNode = (Map<?, ?>) properties.get("keyword");
        assertEquals("string", keywordNode.get("type"));

        Map<?, ?> flagsNode = (Map<?, ?>) properties.get("flags");
        assertEquals("array", flagsNode.get("type"));
        Map<?, ?> items = (Map<?, ?>) flagsNode.get("items");
        assertEquals("boolean", items.get("type"));

        Map<?, ?> filterNode = (Map<?, ?>) properties.get("filter");
        assertEquals("object", filterNode.get("type"));
        Map<?, ?> subProperties = (Map<?, ?>) filterNode.get("properties");
        assertEquals(2, subProperties.size());
        assertEquals("string", ((Map<?, ?>) subProperties.get("city")).get("type"));
        List<?> subRequired = (List<?>) filterNode.get("required");
        assertEquals(Collections.singletonList("city"), subRequired);

        // scalar required params and every array param land in root required, optional ones don't
        assertEquals(Arrays.asList("userId", "flags"), schema.getRequired());
    }

    @Test
    public void schemaWithoutParamsHasEmptyPropertiesAndRequired() throws Exception {
        installBeans(new FakeSystemParamService());

        McpToolCallHandler handler = handlerFor("bare", "bare tool", configWithParams(null));
        McpSchema.JsonSchema schema = handler.getMcpToolSchema().getInputSchema();

        assertEquals("object", schema.getType());
        assertTrue(schema.getProperties().isEmpty());
        assertTrue(schema.getRequired().isEmpty());
    }

    /**
     * Regression: a missing required flag is legal data (the admin MCP tool marks it optional and the
     * executor treats null as "not required"); unboxing it here threw an NPE that failed every later startup.
     */
    @Test
    public void schemaTreatsMissingRequiredFlagAsNotRequired() throws Exception {
        installBeans(new FakeSystemParamService());

        ItemParam keyword = param("keyword", ParamTypeEnum.STRING, true, "abc");
        keyword.setRequired(null);
        ItemParam filter = param("filter", ParamTypeEnum.OBJECT, false, null);
        filter.setRequired(null);
        BaseParam city = new BaseParam();
        city.setName("city");
        city.setType(ParamTypeEnum.STRING);
        city.setRequired(null);
        filter.setChildren(Collections.singletonList(city));
        McpToolCallHandler handler = handlerFor("query_users", "查询用户",
                configWithParams(Arrays.asList(keyword, filter)));

        McpSchema.JsonSchema schema = handler.getMcpToolSchema().getInputSchema();

        assertTrue(schema.getRequired().isEmpty());
        Map<?, ?> filterNode = (Map<?, ?>) schema.getProperties().get("filter");
        assertTrue(((List<?>) filterNode.get("required")).isEmpty());
    }

    @Test
    public void executeToolTreatsMissingRequiredFlagAsOptional() throws Exception {
        FakeApiExecuteService executeService = installBeans(new FakeSystemParamService());
        ItemParam keyword = param("keyword", ParamTypeEnum.STRING, true, "abc");
        keyword.setRequired(null);
        McpToolCallHandler handler = handlerFor("query_users", "查询用户",
                configWithParams(Collections.singletonList(keyword)));

        CallToolResult result = handler.executeTool(null, new HashMap<>());

        assertFalse(result.getIsError());
        // the absent argument behaves like any optional scalar: its default value goes out
        assertEquals("abc", executeService.capturedArgs.get("keyword"));
    }

    @Test
    public void executeToolRunsApiAndInjectsDefaultsAndPageSize() throws Exception {
        FakeApiExecuteService executeService = installBeans(new FakeSystemParamService());
        ItemParam userId = param("userId", ParamTypeEnum.LONG, true, null);
        ItemParam keyword = param("keyword", ParamTypeEnum.STRING, false, "abc");
        ItemParam filter = param("filter", ParamTypeEnum.OBJECT, false, null);
        ApiAssignmentEntity config = configWithParams(Arrays.asList(userId, keyword, filter));
        McpToolCallHandler handler = handlerFor("query_users", "查询用户", config);

        Map<String, Object> arguments = new HashMap<>();
        arguments.put("userId", 3);
        CallToolResult result = handler.executeTool(null, arguments);

        assertFalse(result.getIsError());
        String text = textOf(result);
        assertTrue(text.contains("rows"));
        assertTrue(text.contains("12"));

        assertSame(config, executeService.capturedConfig);
        assertEquals(Boolean.TRUE, executeService.capturedPrintSqlLog);
        Map<String, Object> sent = executeService.capturedArgs;
        assertEquals(3, ((Number) sent.get("userId")).intValue());
        // absent optional scalar gets its converted default value
        assertEquals("abc", sent.get("keyword"));
        // absent OBJECT params are deliberately not defaulted
        assertFalse(sent.containsKey("filter"));
        // page size injected from the system parameter
        assertEquals(777, sent.get(Constants.PARAM_PAGE_SIZE));
    }

    @Test
    public void executeToolKeepsCallerProvidedPageSizeAndValues() throws Exception {
        FakeApiExecuteService executeService = installBeans(new FakeSystemParamService());
        ItemParam keyword = param("keyword", ParamTypeEnum.STRING, false, "abc");
        McpToolCallHandler handler = handlerFor("t", "d", configWithParams(
                Collections.singletonList(keyword)));

        Map<String, Object> arguments = new HashMap<>();
        arguments.put("keyword", "caller-value");
        arguments.put(Constants.PARAM_PAGE_SIZE, 5);
        handler.executeTool(null, arguments);

        assertEquals("caller-value", executeService.capturedArgs.get("keyword"));
        assertEquals(5, executeService.capturedArgs.get(Constants.PARAM_PAGE_SIZE));
    }

    @Test
    public void executeToolReportsFailureWithExceptionMessage() throws Exception {
        FakeApiExecuteService executeService = installBeans(new FakeSystemParamService());
        executeService.failure = new RuntimeException("boom");
        McpToolCallHandler handler = handlerFor("t", "d", configWithParams(null));

        CallToolResult result = handler.executeTool(null, new HashMap<>());

        assertTrue(result.getIsError());
        assertTrue(textOf(result).contains("boom"));
    }

    @Test
    public void executeToolFallsBackToRootCauseMessageWhenMessageMissing() throws Exception {
        FakeApiExecuteService executeService = installBeans(new FakeSystemParamService());
        executeService.failure = new NullPointerException();
        McpToolCallHandler handler = handlerFor("t", "d", configWithParams(null));

        CallToolResult result = handler.executeTool(null, new HashMap<>());

        assertTrue(result.getIsError());
        assertTrue(textOf(result).contains("NullPointerException"));
    }

    @Test
    public void executeToolRunsWithoutArguments() throws Exception {
        // the spec makes tools/call params.arguments optional, and the transport then hands over null
        FakeApiExecuteService executeService = installBeans(new FakeSystemParamService());
        ApiAssignmentEntity config = configWithParams(
                Collections.singletonList(param("keyword", ParamTypeEnum.STRING, false, "abc")));
        McpToolCallHandler handler = new McpToolCallHandler("t", "d", config, serving(config));

        CallToolResult result = handler.executeTool(null, null);

        assertFalse(result.getIsError());
        assertEquals("abc", executeService.capturedArgs.get("keyword"));
        assertEquals(777, executeService.capturedArgs.get(Constants.PARAM_PAGE_SIZE));
    }

    /**
     * Regression: the handler used to execute the assignment captured at registration forever, so
     * an API taken offline (or deleted) stayed callable through the data MCP endpoint.
     */
    @Test
    public void executeToolRefusesOnceTheApiIsOffline() throws Exception {
        FakeApiExecuteService executeService = installBeans(new FakeSystemParamService());
        McpToolCallHandler handler = new McpToolCallHandler("t", "d", configWithParams(
                Collections.singletonList(param("keyword", ParamTypeEnum.STRING, false, "abc"))),
                new StubApiOnlineDao());

        CallToolResult result = handler.executeTool(null, new HashMap<>());

        assertTrue(result.getIsError());
        assertTrue(textOf(result).contains("下线"));
        assertNull("an offline API must not reach the executor", executeService.capturedConfig);
    }

    @Test
    public void executeToolRunsTheCurrentOnlineVersion() throws Exception {
        FakeApiExecuteService executeService = installBeans(new FakeSystemParamService());
        ApiAssignmentEntity atRegistration = configWithParams(
                Collections.singletonList(param("old", ParamTypeEnum.STRING, false, "old-default")));
        ApiAssignmentEntity republished = configWithParams(
                Collections.singletonList(param("fresh", ParamTypeEnum.STRING, false, "fresh-default")));
        McpToolCallHandler handler = new McpToolCallHandler("t", "d", atRegistration, serving(republished));

        handler.executeTool(null, new HashMap<>());

        assertSame(republished, executeService.capturedConfig);
        // argument preparation follows the current version too
        assertEquals("fresh-default", executeService.capturedArgs.get("fresh"));
        assertFalse(executeService.capturedArgs.containsKey("old"));
    }
}
