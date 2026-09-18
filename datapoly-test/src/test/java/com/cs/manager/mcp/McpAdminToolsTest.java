// Use of this source code is governed by a BSD-style license
package com.cs.manager.mcp;

import cn.hutool.extra.spring.SpringUtil;
import com.cs.common.dto.PageResult;
import com.cs.common.exception.CommonException;
import com.cs.common.exception.ResponseErrorCode;
import com.cs.core.dto.*;
import com.cs.core.service.*;
import com.cs.manager.config.McpAdminServerConfiguration;
import com.cs.manager.service.McpManageService;
import com.cs.persistence.dao.McpClientDao;
import com.cs.persistence.entity.ApiModuleEntity;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.server.transport.WebMvcSseServerAuthChecker;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import org.junit.After;
import org.junit.Test;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;

import java.lang.reflect.Proxy;
import java.util.*;

import static org.junit.Assert.*;

public class McpAdminToolsTest {

    private static String textOf(CallToolResult result) {
        return ((io.modelcontextprotocol.spec.McpSchema.TextContent) result.getContent().get(0)).getText();
    }

    // ------------------------------------------------------------------
    // fake services: 仅覆写被工具调用的方法,记录入参
    // ------------------------------------------------------------------

    private static class FakeApiModuleService extends ApiModuleService {
        String createdName;
        EntitySearchRequest listedRequest;

        @Override
        public void createModule(String name) {
            this.createdName = name;
        }

        @Override
        public PageResult<ApiModuleEntity> listAll(EntitySearchRequest request) {
            this.listedRequest = request;
            PageResult<ApiModuleEntity> page = new PageResult<>();
            ApiModuleEntity entity = new ApiModuleEntity();
            entity.setId(11L);
            entity.setName("module-1");
            page.setData(Collections.singletonList(entity));
            return page;
        }
    }

    private static class FakeApiAssignmentService extends ApiAssignmentService {
        ApiAssignmentSaveRequest created;
        Long createId = 88L;
        Long updateGroupGroupId;
        List<Long> updateGroupIds;

        @Override
        public Long createAssignment(ApiAssignmentSaveRequest request) {
            this.created = request;
            return createId;
        }

        @Override
        public void updateGroup(Long groupId, List<Long> ids) {
            this.updateGroupGroupId = groupId;
            this.updateGroupIds = ids;
        }
    }

    private static class FakeDataSourceService extends DataSourceService {
        DataSourceSaveRequest created;

        @Override
        public void createDataSource(DataSourceSaveRequest request) {
            this.created = request;
        }
    }

    private static class FakeAppClientService extends AppClientService {
        AppClientSaveRequest created;

        @Override
        public void create(AppClientSaveRequest request) {
            this.created = request;
        }
    }

    private static class FakeMcpManageService extends McpManageService {
        String clientName;
        boolean clientManage;
        boolean failCreateTool;

        @Override
        public void createClient(String name, boolean manage) {
            this.clientName = name;
            this.clientManage = manage;
        }

        @Override
        public void createTool(McpToolSaveRequest request) {
            if (failCreateTool) {
                throw new CommonException(ResponseErrorCode.ERROR_RESOURCE_NOT_ONLINE, "apiId={0}", request.getApiId());
            }
        }
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private McpAdminTools newTools(ApiModuleService moduleService, ApiAssignmentService assignmentService,
                                   DataSourceService dataSourceService, AppClientService appClientService,
                                   McpManageService mcpManageService) {
        McpAdminTools tools = new McpAdminTools();
        tools.apiModuleService = moduleService;
        tools.apiAssignmentService = assignmentService;
        tools.dataSourceService = dataSourceService;
        tools.appClientService = appClientService;
        tools.mcpManageService = mcpManageService;
        return tools;
    }

    private SyncToolSpecification findTool(List<SyncToolSpecification> tools, String name) {
        return tools.stream()
                .filter(t -> name.equals(t.getTool().getName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("tool not found: " + name));
    }

    private CallToolResult call(SyncToolSpecification tool, Map<String, Object> args) {
        return tool.getCall().apply(null, args);
    }

    // ------------------------------------------------------------------
    // tests
    // ------------------------------------------------------------------

    @Test
    public void toolSchemasAreUniquePrefixedAndValidJson() throws Exception {
        McpAdminTools tools = newTools(new FakeApiModuleService(), new FakeApiAssignmentService(),
                new FakeDataSourceService(), new FakeAppClientService(), new FakeMcpManageService());
        List<SyncToolSpecification> specs = tools.buildToolSpecifications();

        assertTrue("expected a meaningful tool set", specs.size() >= 40);
        Set<String> names = new HashSet<>();
        for (SyncToolSpecification spec : specs) {
            String name = spec.getTool().getName();
            assertTrue("tool name must keep dp_ prefix: " + name, name.startsWith("dp_"));
            assertTrue("duplicate tool name: " + name, names.add(name));
            assertFalse("description must not be empty: " + name, spec.getTool().getDescription().isEmpty());

            McpSchema.JsonSchema schema = spec.getTool().getInputSchema();
            assertEquals("object", schema.getType());
            assertNotNull(schema.getProperties());
            for (String requiredField : schema.getRequired()) {
                assertTrue("required field missing in properties: " + requiredField + " of " + name,
                        schema.getProperties().containsKey(requiredField));
            }
        }
    }

    @Test
    public void moduleCreateAndListInvokeService() {
        FakeApiModuleService moduleService = new FakeApiModuleService();
        McpAdminTools tools = newTools(moduleService, new FakeApiAssignmentService(),
                new FakeDataSourceService(), new FakeAppClientService(), new FakeMcpManageService());
        List<SyncToolSpecification> specs = tools.buildToolSpecifications();

        CallToolResult created = call(findTool(specs, "dp_module_create"),
                Collections.singletonMap("name", "orders"));
        assertFalse(created.getIsError());
        assertEquals("orders", moduleService.createdName);

        Map<String, Object> listArgs = new HashMap<>();
        listArgs.put("searchText", "ord");
        listArgs.put("page", 1);
        listArgs.put("size", 10);
        CallToolResult listed = call(findTool(specs, "dp_module_list"), listArgs);
        assertFalse(listed.getIsError());
        assertEquals("ord", moduleService.listedRequest.getSearchText());
        assertTrue(textOf(listed).contains("module-1"));
    }

    @Test
    public void apiCreateConvertsComplexArgumentsToDto() {
        FakeApiAssignmentService assignmentService = new FakeApiAssignmentService();
        McpAdminTools tools = newTools(new FakeApiModuleService(), assignmentService,
                new FakeDataSourceService(), new FakeAppClientService(), new FakeMcpManageService());
        List<SyncToolSpecification> specs = tools.buildToolSpecifications();

        Map<String, Object> param = new HashMap<>();
        param.put("name", "userId");
        param.put("type", "LONG");
        param.put("location", "REQUEST_FORM");
        param.put("required", true);
        Map<String, Object> child = new HashMap<>();
        child.put("name", "city");
        child.put("type", "STRING");
        child.put("location", "REQUEST_BODY");
        Map<String, Object> objectParam = new HashMap<>();
        objectParam.put("name", "filter");
        objectParam.put("type", "OBJECT");
        objectParam.put("location", "REQUEST_BODY");
        objectParam.put("children", Collections.singletonList(child));
        Map<String, Object> format = new HashMap<>();
        format.put("key", "DATE");
        format.put("value", "yyyy-MM-dd");

        Map<String, Object> args = new HashMap<>();
        args.put("groupId", 1);
        args.put("moduleId", 2);
        args.put("datasourceId", 3);
        args.put("name", "查询用户");
        args.put("method", "POST");
        args.put("path", "/user/query");
        args.put("contentType", "application/json");
        args.put("engine", "SQL");
        args.put("contextList", Collections.singletonList("select * from t_user where id = #{userId}"));
        args.put("open", false);
        args.put("alarm", false);
        args.put("flowStatus", false);
        args.put("namingStrategy", "CAMEL_CASE");
        args.put("params", Arrays.asList(param, objectParam));
        args.put("formatMap", Collections.singletonList(format));

        CallToolResult result = call(findTool(specs, "dp_api_create"), args);
        assertFalse(result.getIsError());
        assertTrue(textOf(result).contains("88"));

        ApiAssignmentSaveRequest request = assignmentService.created;
        assertEquals(Long.valueOf(1), request.getGroupId());
        assertEquals(Long.valueOf(3), request.getDatasourceId());
        assertEquals(com.cs.common.enums.HttpMethodEnum.POST, request.getMethod());
        assertEquals(com.cs.common.enums.ExecuteEngineEnum.SQL, request.getEngine());
        assertEquals(1, request.getContextList().size());
        assertEquals(2, request.getParams().size());
        assertEquals(com.cs.common.enums.ParamTypeEnum.OBJECT, request.getParams().get(1).getType());
        assertEquals(1, request.getParams().get(1).getChildren().size());
        assertEquals(com.cs.common.enums.NamingStrategyEnum.CAMEL_CASE, request.getNamingStrategy());
        assertEquals(com.cs.common.enums.DataTypeFormatEnum.DATE, request.getFormatMap().get(0).getKey());
    }

    @Test
    public void apiSetGroupConvertsJsonNumbersToLongList() {
        FakeApiAssignmentService assignmentService = new FakeApiAssignmentService();
        McpAdminTools tools = newTools(new FakeApiModuleService(), assignmentService,
                new FakeDataSourceService(), new FakeAppClientService(), new FakeMcpManageService());
        List<SyncToolSpecification> specs = tools.buildToolSpecifications();

        Map<String, Object> args = new HashMap<>();
        args.put("groupId", 5);
        args.put("ids", Arrays.asList(1, 2, 3));
        CallToolResult result = call(findTool(specs, "dp_api_set_group"), args);

        assertFalse(result.getIsError());
        assertEquals(Long.valueOf(5), assignmentService.updateGroupGroupId);
        assertEquals(Arrays.asList(1L, 2L, 3L), assignmentService.updateGroupIds);
    }

    @Test
    public void datasourceCreateConvertsTypeAndPoolConfig() {
        FakeDataSourceService dataSourceService = new FakeDataSourceService();
        McpAdminTools tools = newTools(new FakeApiModuleService(), new FakeApiAssignmentService(),
                dataSourceService, new FakeAppClientService(), new FakeMcpManageService());
        List<SyncToolSpecification> specs = tools.buildToolSpecifications();

        Map<String, Object> pool = new HashMap<>();
        pool.put("maximumPoolSize", 20);
        Map<String, Object> args = new HashMap<>();
        args.put("name", "mysql-main");
        args.put("type", "MYSQL");
        args.put("version", "8.0");
        args.put("driver", "com.mysql.cj.jdbc.Driver");
        args.put("url", "jdbc:mysql://127.0.0.1:3306/demo");
        args.put("username", "root");
        args.put("password", "secret");
        args.put("poolConfig", pool);

        CallToolResult result = call(findTool(specs, "dp_datasource_create"), args);
        assertFalse(result.getIsError());
        assertEquals(com.cs.common.enums.ProductTypeEnum.MYSQL, dataSourceService.created.getType());
        assertEquals(Integer.valueOf(20), dataSourceService.created.getPoolConfig().getMaximumPoolSize());
        assertEquals("jdbc:mysql://127.0.0.1:3306/demo", dataSourceService.created.getUrl());
    }

    @Test
    public void appClientCreateConvertsExpireEnums() {
        FakeAppClientService appClientService = new FakeAppClientService();
        McpAdminTools tools = newTools(new FakeApiModuleService(), new FakeApiAssignmentService(),
                new FakeDataSourceService(), appClientService, new FakeMcpManageService());
        List<SyncToolSpecification> specs = tools.buildToolSpecifications();

        Map<String, Object> args = new HashMap<>();
        args.put("name", "bi-tool");
        args.put("appKey", "bi");
        args.put("expireTime", "EXPIRE_01_DAY");
        args.put("tokenAlive", "LONGEVITY");

        CallToolResult result = call(findTool(specs, "dp_client_create"), args);
        assertFalse(result.getIsError());
        assertEquals(com.cs.common.enums.ExpireTimeEnum.EXPIRE_01_DAY, appClientService.created.getExpireTime());
        assertEquals(com.cs.common.enums.AliveTimeEnum.LONGEVITY, appClientService.created.getTokenAlive());
    }

    @Test
    public void mcpClientCreatePassesManageFlag() {
        FakeMcpManageService mcpManageService = new FakeMcpManageService();
        McpAdminTools tools = newTools(new FakeApiModuleService(), new FakeApiAssignmentService(),
                new FakeDataSourceService(), new FakeAppClientService(), mcpManageService);
        List<SyncToolSpecification> specs = tools.buildToolSpecifications();

        Map<String, Object> args = new HashMap<>();
        args.put("name", "agent-admin");
        args.put("manage", true);
        CallToolResult result = call(findTool(specs, "dp_mcp_client_create"), args);
        assertFalse(result.getIsError());
        assertEquals("agent-admin", mcpManageService.clientName);
        assertTrue(mcpManageService.clientManage);
    }

    @Test
    public void serviceErrorBecomesIsErrorResultWithCode() {
        FakeMcpManageService mcpManageService = new FakeMcpManageService();
        mcpManageService.failCreateTool = true;
        McpAdminTools tools = newTools(new FakeApiModuleService(), new FakeApiAssignmentService(),
                new FakeDataSourceService(), new FakeAppClientService(), mcpManageService);
        List<SyncToolSpecification> specs = tools.buildToolSpecifications();

        Map<String, Object> args = new HashMap<>();
        args.put("name", "t1");
        args.put("description", "d1");
        args.put("apiId", 9);
        CallToolResult result = call(findTool(specs, "dp_mcp_tool_create"), args);

        assertTrue(result.getIsError());
        String text = textOf(result);
        assertTrue(text.contains("6"));
    }

    @Test
    public void adminAuthCheckerValidatesManageScopedTokenOnly() throws Exception {
        McpClientDao dao = new McpClientDao() {
            @Override
            public boolean existsManageAccessToken(String accessToken) {
                return "manage-token".equals(accessToken);
            }
        };
        Object beanFactory = Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{ConfigurableListableBeanFactory.class},
                (p, m, a) -> {
                    if ("getBean".equals(m.getName()) && a != null && a.length == 1
                            && a[0] == McpClientDao.class) {
                        return dao;
                    }
                    if ("hashCode".equals(m.getName())) {
                        return System.identityHashCode(p);
                    }
                    if ("equals".equals(m.getName())) {
                        return p == a[0];
                    }
                    return null;
                });
        java.lang.reflect.Field field = SpringUtil.class.getDeclaredField("beanFactory");
        field.setAccessible(true);
        field.set(null, beanFactory);
        try {
            WebMvcSseServerAuthChecker checker = new McpAdminServerConfiguration().mcpAdminAuthChecker();
            assertEquals("token", checker.getTokenParamName());
            assertTrue(checker.checkTokenValid("manage-token"));
            assertFalse(checker.checkTokenValid("data-only-token"));
        } finally {
            field.set(null, null);
        }
    }

    @After
    public void tearDown() throws Exception {
        java.lang.reflect.Field field = SpringUtil.class.getDeclaredField("beanFactory");
        field.setAccessible(true);
        field.set(null, null);
    }
}
