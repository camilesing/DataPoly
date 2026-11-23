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
import com.cs.persistence.entity.ApiGroupEntity;
import com.cs.persistence.entity.ApiModuleEntity;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.WebMvcSseServerAuthChecker;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpServerTransportProvider;
import org.junit.After;
import org.junit.Test;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import reactor.core.publisher.Mono;

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
        Long updatedId;
        String updatedName;
        Long deletedId;

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

        @Override
        public void updateModule(Long id, String name) {
            this.updatedId = id;
            this.updatedName = name;
        }

        @Override
        public void deleteModule(Long id) {
            this.deletedId = id;
        }
    }

    private static class FakeApiGroupService extends ApiGroupService {
        String createdName;
        Long updatedId;
        String updatedName;
        Long deletedId;
        EntitySearchRequest listedRequest;

        @Override
        public void createGroup(String name) {
            this.createdName = name;
        }

        @Override
        public void updateGroup(Long id, String newName) {
            this.updatedId = id;
            this.updatedName = newName;
        }

        @Override
        public void deleteGroup(Long id) {
            this.deletedId = id;
        }

        @Override
        public PageResult<ApiGroupEntity> listAll(EntitySearchRequest request) {
            this.listedRequest = request;
            PageResult<ApiGroupEntity> page = new PageResult<>();
            ApiGroupEntity entity = new ApiGroupEntity();
            entity.setId(21L);
            entity.setName("group-1");
            page.setData(Collections.singletonList(entity));
            return page;
        }
    }

    private static class FakeApiAssignmentService extends ApiAssignmentService {
        ApiAssignmentSaveRequest created;
        Long createId = 88L;
        Long updateGroupGroupId;
        List<Long> updateGroupIds;
        AssignmentSearchRequest listedRequest;
        Long detailedId;
        ApiAssignmentSaveRequest updated;
        Long deletedId;
        String parsedSql;
        AssignmentPublishRequest published;
        Long deployedId;
        Long deployedCommitId;
        Long retiredId;
        Long versionsBizId;
        Long versionDetailCommitId;
        Long revertedId;
        Long revertedCommitId;

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

        @Override
        public PageResult<ApiAssignmentBaseResponse> listAll(AssignmentSearchRequest request) {
            this.listedRequest = request;
            PageResult<ApiAssignmentBaseResponse> page = new PageResult<>();
            ApiAssignmentBaseResponse row = new ApiAssignmentBaseResponse();
            row.setName("api-1");
            page.setData(Collections.singletonList(row));
            return page;
        }

        @Override
        public ApiAssignmentDetailResponse detailAssignment(Long id) {
            this.detailedId = id;
            ApiAssignmentDetailResponse detail = new ApiAssignmentDetailResponse();
            detail.setName("detail-1");
            return detail;
        }

        @Override
        public void updateAssignment(ApiAssignmentSaveRequest request) {
            this.updated = request;
        }

        @Override
        public void deleteAssignment(Long id) {
            this.deletedId = id;
        }

        @Override
        public List<SqlParamParseResponse> parseSqlParams(String sqlOrXml) {
            this.parsedSql = sqlOrXml;
            return Collections.singletonList(new SqlParamParseResponse("userId", false, null));
        }

        @Override
        public void publish(AssignmentPublishRequest request) {
            this.published = request;
        }

        @Override
        public void deployAssignment(Long id, Long commitId) {
            this.deployedId = id;
            this.deployedCommitId = commitId;
        }

        @Override
        public void retireAssignment(Long id) {
            this.retiredId = id;
        }

        @Override
        public List<VersionCommitResponse> listVersions(Long bizId) {
            this.versionsBizId = bizId;
            VersionCommitResponse commit = new VersionCommitResponse();
            commit.setCommitId(77L);
            commit.setVersion(3);
            return Collections.singletonList(commit);
        }

        @Override
        public VersionDetailResponse showVersion(Long commitId) {
            this.versionDetailCommitId = commitId;
            return new VersionDetailResponse();
        }

        @Override
        public void revertVersion(Long bizId, Long commitId) {
            this.revertedId = bizId;
            this.revertedCommitId = commitId;
        }
    }

    private static class FakeDataSourceService extends DataSourceService {
        DataSourceSaveRequest created;
        List<DatabaseTypeDetailResponse> types = new ArrayList<>();
        EntitySearchRequest searchedRequest;
        Long gotDetailId;
        DataSourceSaveRequest updated;
        Long deletedId;
        Long testedId;
        Long schemasId;
        Long tablesId;
        String tablesSchema;
        Long columnsId;
        String columnsSchema;
        String columnsTable;

        @Override
        public void createDataSource(DataSourceSaveRequest request) {
            this.created = request;
        }

        @Override
        public List<DatabaseTypeDetailResponse> getTypes() {
            if (types.isEmpty()) {
                DatabaseTypeDetailResponse mysql = new DatabaseTypeDetailResponse();
                mysql.setId(1);
                mysql.setType("MYSQL");
                mysql.setDriver("com.mysql.cj.jdbc.Driver");
                types.add(mysql);
            }
            return types;
        }

        @Override
        public PageResult<DatasourceDetailResponse> searchList(EntitySearchRequest request) {
            this.searchedRequest = request;
            return new PageResult<>();
        }

        @Override
        public DatasourceDetailResponse getDetailById(Long id) {
            this.gotDetailId = id;
            DatasourceDetailResponse detail = new DatasourceDetailResponse();
            detail.setName("ds-1");
            return detail;
        }

        @Override
        public void updateDataSource(DataSourceSaveRequest request) {
            this.updated = request;
        }

        @Override
        public void deleteDataSource(Long id) {
            this.deletedId = id;
        }

        @Override
        public void testDataSource(Long id) {
            this.testedId = id;
        }

        @Override
        public List<String> getDatasourceSchemas(Long id) {
            this.schemasId = id;
            return Collections.singletonList("information_schema");
        }

        @Override
        public List<String> getSchemaTables(Long id, String schema) {
            this.tablesId = id;
            this.tablesSchema = schema;
            return Collections.singletonList("t_user");
        }

        @Override
        public List<MetadataColumnResponse> getTableColumns(Long id, String schema, String table) {
            this.columnsId = id;
            this.columnsSchema = schema;
            this.columnsTable = table;
            return Collections.singletonList(MetadataColumnResponse.builder()
                    .name("id").type("bigint").remarks("pk").build());
        }
    }

    private static class FakeAppClientService extends AppClientService {
        AppClientSaveRequest created;
        AppClientSearchRequest searchedRequest;
        Long deletedId;
        Long secretId;
        Long groupAuthId;
        AppClientGroupRequest groupAuthRequest;

        @Override
        public void create(AppClientSaveRequest request) {
            this.created = request;
        }

        @Override
        public void delete(Long id) {
            this.deletedId = id;
        }

        @Override
        public PageResult<AppClientDetailResponse> searchList(AppClientSearchRequest request) {
            this.searchedRequest = request;
            return new PageResult<>();
        }

        @Override
        public String getSecret(Long id) {
            this.secretId = id;
            return "s3cret-5";
        }

        @Override
        public List<EntityIdNameResponse> getGroupAuth(Long id) {
            this.groupAuthId = id;
            return Collections.singletonList(new EntityIdNameResponse(6L, "group-6"));
        }

        @Override
        public void createGroupAuth(AppClientGroupRequest request) {
            this.groupAuthRequest = request;
        }
    }

    private static class FakeMcpManageService extends McpManageService {
        String clientName;
        boolean clientManage;
        boolean failCreateTool;
        McpToolSaveRequest createdTool;
        EntitySearchRequest listedToolsRequest;
        McpToolSaveRequest updatedTool;
        Long deletedToolId;
        EntitySearchRequest listedClientsRequest;
        Long updatedClientId;
        String updatedClientName;
        Boolean updatedClientManage;
        Long deletedClientId;
        Long tokenClientId;

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
            this.createdTool = request;
        }

        @Override
        public PageResult<McpToolResponse> listToolAll(EntitySearchRequest request) {
            this.listedToolsRequest = request;
            return new PageResult<>();
        }

        @Override
        public void updateTool(McpToolSaveRequest request) {
            this.updatedTool = request;
        }

        @Override
        public void deleteTool(Long id) {
            this.deletedToolId = id;
        }

        @Override
        public PageResult<com.cs.persistence.entity.McpClientEntity> listClientAll(EntitySearchRequest request) {
            this.listedClientsRequest = request;
            return new PageResult<>();
        }

        @Override
        public void updateClient(Long id, String newName, Boolean manage) {
            this.updatedClientId = id;
            this.updatedClientName = newName;
            this.updatedClientManage = manage;
        }

        @Override
        public void deleteClient(Long id) {
            this.deletedClientId = id;
        }

        @Override
        public String getClientToken(Long id) {
            this.tokenClientId = id;
            return "token-9";
        }

        @Override
        public McpServerAddrResponse getMcpServerEndpoint() {
            return McpServerAddrResponse.builder()
                    .streamAddrPrefix("https://gw.example.com/mcp")
                    .adminStreamAddrPrefix("https://mgr.example.com/mcp/admin")
                    .build();
        }
    }

    /** No-op transport so a real McpSyncServer can be built outside a servlet container. */
    private static final class NoopTransportProvider implements McpServerTransportProvider {

        @Override
        public void setSessionFactory(io.modelcontextprotocol.spec.McpServerSession.Factory sessionFactory) {
        }

        @Override
        public Mono<Void> notifyClients(String method, Map<String, Object> params) {
            return Mono.empty();
        }

        @Override
        public Mono<Void> closeGracefully() {
            return Mono.empty();
        }
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private McpAdminTools newTools(ApiModuleService moduleService, ApiAssignmentService assignmentService,
                                   DataSourceService dataSourceService, AppClientService appClientService,
                                   McpManageService mcpManageService) {
        return newTools(moduleService, new FakeApiGroupService(), assignmentService, dataSourceService,
                appClientService, mcpManageService);
    }

    private McpAdminTools newTools(ApiModuleService moduleService, ApiGroupService groupService,
                                   ApiAssignmentService assignmentService, DataSourceService dataSourceService,
                                   AppClientService appClientService, McpManageService mcpManageService) {
        McpAdminTools tools = new McpAdminTools();
        tools.apiModuleService = moduleService;
        tools.apiGroupService = groupService;
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

    private CallToolResult callTool(McpAdminTools tools, String name, Map<String, Object> args) {
        return call(findTool(tools.buildToolSpecifications(), name), args);
    }

    // ------------------------------------------------------------------
    // tests
    // ------------------------------------------------------------------

    @Test
    public void createToolsDoNotDemandAnIdAndUpdateToolsDo() {
        // Regression: the update schema was derived by adding `id` to the very same mutable object,
        // so the create tools asked for an id that does not exist yet
        McpAdminTools tools = newTools(new FakeApiModuleService(), new FakeApiAssignmentService(),
                new FakeDataSourceService(), new FakeAppClientService(), new FakeMcpManageService());
        List<SyncToolSpecification> specs = tools.buildToolSpecifications();

        for (String create : new String[]{"dp_datasource_create", "dp_api_create"}) {
            McpSchema.JsonSchema schema = findTool(specs, create).getTool().getInputSchema();
            assertFalse(create + " must not take an id", schema.getProperties().containsKey("id"));
            assertFalse(create + " must not require an id", schema.getRequired().contains("id"));
        }
        for (String update : new String[]{"dp_datasource_update", "dp_api_update"}) {
            McpSchema.JsonSchema schema = findTool(specs, update).getTool().getInputSchema();
            assertTrue(update + " must take its id", schema.getProperties().containsKey("id"));
            assertTrue(update + " must require the id", schema.getRequired().contains("id"));
        }
    }

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
    public void registerAdminToolsLoadsEveryToolOntoTheServer() {
        McpSyncServer server = McpServer.sync(new NoopTransportProvider())
                .serverInfo("test-admin-server", "1.0.0")
                .capabilities(McpSchema.ServerCapabilities.builder().tools(true).build())
                .build();
        McpAdminTools tools = newTools(new FakeApiModuleService(), new FakeApiAssignmentService(),
                new FakeDataSourceService(), new FakeAppClientService(), new FakeMcpManageService());
        tools.mcpAdminSyncServer = server;

        tools.registerAdminTools();

        List<SyncToolSpecification> expected = tools.buildToolSpecifications();
        assertEquals(expected.size(), server.listTool().size());
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
    public void moduleUpdateAndDeleteDelegateByIdAndName() {
        FakeApiModuleService moduleService = new FakeApiModuleService();
        McpAdminTools tools = newTools(moduleService, new FakeApiAssignmentService(),
                new FakeDataSourceService(), new FakeAppClientService(), new FakeMcpManageService());

        Map<String, Object> updateArgs = new HashMap<>();
        updateArgs.put("id", 12);
        updateArgs.put("name", "billing");
        CallToolResult updated = callTool(tools, "dp_module_update", updateArgs);
        assertFalse(updated.getIsError());
        assertEquals(Long.valueOf(12L), moduleService.updatedId);
        assertEquals("billing", moduleService.updatedName);

        CallToolResult deleted = callTool(tools, "dp_module_delete", Collections.singletonMap("id", 12));
        assertFalse(deleted.getIsError());
        assertEquals(Long.valueOf(12L), moduleService.deletedId);
    }

    @Test
    public void groupCrudToolsDelegateToGroupService() {
        FakeApiGroupService groupService = new FakeApiGroupService();
        McpAdminTools tools = newTools(new FakeApiModuleService(), groupService, new FakeApiAssignmentService(),
                new FakeDataSourceService(), new FakeAppClientService(), new FakeMcpManageService());

        CallToolResult created = callTool(tools, "dp_group_create", Collections.singletonMap("name", "bi"));
        assertFalse(created.getIsError());
        assertEquals("bi", groupService.createdName);

        Map<String, Object> updateArgs = new HashMap<>();
        updateArgs.put("id", 21);
        updateArgs.put("name", "bi-2");
        CallToolResult updated = callTool(tools, "dp_group_update", updateArgs);
        assertFalse(updated.getIsError());
        assertEquals(Long.valueOf(21L), groupService.updatedId);
        assertEquals("bi-2", groupService.updatedName);

        CallToolResult deleted = callTool(tools, "dp_group_delete", Collections.singletonMap("id", 21));
        assertFalse(deleted.getIsError());
        assertEquals(Long.valueOf(21L), groupService.deletedId);

        CallToolResult listed = callTool(tools, "dp_group_list",
                Collections.singletonMap("searchText", "b"));
        assertFalse(listed.getIsError());
        assertEquals("b", groupService.listedRequest.getSearchText());
        assertTrue(textOf(listed).contains("group-1"));
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
    public void apiLifecycleToolsDelegateWithParsedArguments() {
        FakeApiAssignmentService assignmentService = new FakeApiAssignmentService();
        McpAdminTools tools = newTools(new FakeApiModuleService(), assignmentService,
                new FakeDataSourceService(), new FakeAppClientService(), new FakeMcpManageService());

        Map<String, Object> listArgs = new HashMap<>();
        listArgs.put("online", true);
        listArgs.put("groupId", 2);
        listArgs.put("moduleId", 3);
        listArgs.put("open", false);
        listArgs.put("searchText", "user");
        CallToolResult listed = callTool(tools, "dp_api_list", listArgs);
        assertFalse(listed.getIsError());
        AssignmentSearchRequest search = assignmentService.listedRequest;
        assertEquals(Boolean.TRUE, search.getOnline());
        assertEquals(Long.valueOf(2), search.getGroupId());
        assertEquals(Long.valueOf(3), search.getModuleId());
        assertEquals(Boolean.FALSE, search.getOpen());
        assertEquals("user", search.getSearchText());

        CallToolResult got = callTool(tools, "dp_api_get", Collections.singletonMap("id", 9));
        assertFalse(got.getIsError());
        assertEquals(Long.valueOf(9L), assignmentService.detailedId);
        assertTrue(textOf(got).contains("detail-1"));

        Map<String, Object> updateArgs = new HashMap<>();
        updateArgs.put("id", 9);
        updateArgs.put("groupId", 1);
        updateArgs.put("moduleId", 2);
        updateArgs.put("datasourceId", 3);
        updateArgs.put("name", "查询用户");
        updateArgs.put("method", "POST");
        updateArgs.put("path", "/user/query");
        updateArgs.put("contentType", "application/json");
        updateArgs.put("engine", "SQL");
        updateArgs.put("contextList", Collections.singletonList("select 1"));
        updateArgs.put("open", false);
        updateArgs.put("alarm", false);
        updateArgs.put("flowStatus", false);
        CallToolResult updated = callTool(tools, "dp_api_update", updateArgs);
        assertFalse(updated.getIsError());
        assertEquals(Long.valueOf(9), assignmentService.updated.getId());

        CallToolResult deleted = callTool(tools, "dp_api_delete", Collections.singletonMap("id", 9));
        assertFalse(deleted.getIsError());
        assertEquals(Long.valueOf(9L), assignmentService.deletedId);

        CallToolResult parsed = callTool(tools, "dp_api_parse_sql",
                Collections.singletonMap("sql", "select * from t where id = #{userId}"));
        assertFalse(parsed.getIsError());
        assertEquals("select * from t where id = #{userId}", assignmentService.parsedSql);
        assertTrue(textOf(parsed).contains("userId"));

        Map<String, Object> publishArgs = new HashMap<>();
        publishArgs.put("id", 4);
        publishArgs.put("description", "v1");
        CallToolResult published = callTool(tools, "dp_api_publish", publishArgs);
        assertFalse(published.getIsError());
        assertEquals(Long.valueOf(4), assignmentService.published.getId());
        assertEquals("v1", assignmentService.published.getDescription());

        Map<String, Object> deployArgs = new HashMap<>();
        deployArgs.put("id", 4);
        deployArgs.put("commitId", 77);
        CallToolResult deployed = callTool(tools, "dp_api_deploy", deployArgs);
        assertFalse(deployed.getIsError());
        assertEquals(Long.valueOf(4L), assignmentService.deployedId);
        assertEquals(Long.valueOf(77L), assignmentService.deployedCommitId);

        CallToolResult retired = callTool(tools, "dp_api_retire", Collections.singletonMap("id", 4));
        assertFalse(retired.getIsError());
        assertEquals(Long.valueOf(4L), assignmentService.retiredId);

        CallToolResult versions = callTool(tools, "dp_api_versions", Collections.singletonMap("id", 4));
        assertFalse(versions.getIsError());
        assertEquals(Long.valueOf(4L), assignmentService.versionsBizId);
        assertTrue(textOf(versions).contains("77"));

        CallToolResult versionDetail = callTool(tools, "dp_api_version_detail",
                Collections.singletonMap("commitId", 77));
        assertFalse(versionDetail.getIsError());
        assertEquals(Long.valueOf(77L), assignmentService.versionDetailCommitId);

        Map<String, Object> revertArgs = new HashMap<>();
        revertArgs.put("id", 4);
        revertArgs.put("commitId", 77);
        CallToolResult reverted = callTool(tools, "dp_api_revert", revertArgs);
        assertFalse(reverted.getIsError());
        assertEquals(Long.valueOf(4L), assignmentService.revertedId);
        assertEquals(Long.valueOf(77L), assignmentService.revertedCommitId);
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
    public void datasourceUpdateDeleteAndTestCarryTheId() {
        FakeDataSourceService dataSourceService = new FakeDataSourceService();
        McpAdminTools tools = newTools(new FakeApiModuleService(), new FakeApiAssignmentService(),
                dataSourceService, new FakeAppClientService(), new FakeMcpManageService());

        Map<String, Object> updateArgs = new HashMap<>();
        updateArgs.put("id", 5);
        updateArgs.put("name", "pg");
        updateArgs.put("type", "POSTGRESQL");
        updateArgs.put("version", "16");
        updateArgs.put("driver", "org.postgresql.Driver");
        updateArgs.put("url", "jdbc:postgresql://h/db");
        updateArgs.put("username", "u");
        CallToolResult updated = callTool(tools, "dp_datasource_update", updateArgs);
        assertFalse(updated.getIsError());
        assertEquals(Long.valueOf(5), dataSourceService.updated.getId());
        // password omitted on update means "keep the old one"
        assertNull(dataSourceService.updated.getPassword());

        CallToolResult deleted = callTool(tools, "dp_datasource_delete", Collections.singletonMap("id", 5));
        assertFalse(deleted.getIsError());
        assertEquals(Long.valueOf(5L), dataSourceService.deletedId);

        CallToolResult tested = callTool(tools, "dp_datasource_test", Collections.singletonMap("id", 5));
        assertFalse(tested.getIsError());
        assertEquals(Long.valueOf(5L), dataSourceService.testedId);
    }

    @Test
    public void datasourceMetadataToolsReturnTypesSchemasTablesAndColumns() {
        FakeDataSourceService dataSourceService = new FakeDataSourceService();
        McpAdminTools tools = newTools(new FakeApiModuleService(), new FakeApiAssignmentService(),
                dataSourceService, new FakeAppClientService(), new FakeMcpManageService());

        CallToolResult types = callTool(tools, "dp_datasource_types", Collections.emptyMap());
        assertFalse(types.getIsError());
        assertTrue(textOf(types).contains("com.mysql.cj.jdbc.Driver"));

        Map<String, Object> listArgs = new HashMap<>();
        listArgs.put("page", 2);
        listArgs.put("size", 15);
        listArgs.put("searchText", "pg");
        CallToolResult listed = callTool(tools, "dp_datasource_list", listArgs);
        assertFalse(listed.getIsError());
        assertEquals("pg", dataSourceService.searchedRequest.getSearchText());
        assertEquals(Integer.valueOf(2), dataSourceService.searchedRequest.getPage());

        CallToolResult got = callTool(tools, "dp_datasource_get", Collections.singletonMap("id", 3));
        assertFalse(got.getIsError());
        assertEquals(Long.valueOf(3L), dataSourceService.gotDetailId);
        assertTrue(textOf(got).contains("ds-1"));

        CallToolResult schemas = callTool(tools, "dp_datasource_schemas", Collections.singletonMap("id", 3));
        assertFalse(schemas.getIsError());
        assertEquals(Long.valueOf(3L), dataSourceService.schemasId);
        assertTrue(textOf(schemas).contains("information_schema"));

        Map<String, Object> tableArgs = new HashMap<>();
        tableArgs.put("id", 3);
        tableArgs.put("schema", "public");
        CallToolResult tables = callTool(tools, "dp_datasource_tables", tableArgs);
        assertFalse(tables.getIsError());
        assertEquals(Long.valueOf(3L), dataSourceService.tablesId);
        assertEquals("public", dataSourceService.tablesSchema);
        assertTrue(textOf(tables).contains("t_user"));

        Map<String, Object> columnArgs = new HashMap<>();
        columnArgs.put("id", 3);
        columnArgs.put("schema", "public");
        columnArgs.put("table", "t_user");
        CallToolResult columns = callTool(tools, "dp_datasource_columns", columnArgs);
        assertFalse(columns.getIsError());
        assertEquals(Long.valueOf(3L), dataSourceService.columnsId);
        assertEquals("public", dataSourceService.columnsSchema);
        assertEquals("t_user", dataSourceService.columnsTable);
        assertTrue(textOf(columns).contains("bigint"));
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
    public void appClientLifecycleToolsDelegate() {
        FakeAppClientService appClientService = new FakeAppClientService();
        McpAdminTools tools = newTools(new FakeApiModuleService(), new FakeApiAssignmentService(),
                new FakeDataSourceService(), appClientService, new FakeMcpManageService());

        CallToolResult listed = callTool(tools, "dp_client_list", Collections.singletonMap("groupId", 2));
        assertFalse(listed.getIsError());
        assertEquals(Long.valueOf(2), appClientService.searchedRequest.getGroupId());

        CallToolResult deleted = callTool(tools, "dp_client_delete", Collections.singletonMap("id", 5));
        assertFalse(deleted.getIsError());
        assertEquals(Long.valueOf(5L), appClientService.deletedId);

        CallToolResult secret = callTool(tools, "dp_client_secret", Collections.singletonMap("id", 5));
        assertFalse(secret.getIsError());
        assertEquals(Long.valueOf(5L), appClientService.secretId);
        assertTrue(textOf(secret).contains("s3cret-5"));

        CallToolResult groups = callTool(tools, "dp_client_get_groups", Collections.singletonMap("id", 5));
        assertFalse(groups.getIsError());
        assertEquals(Long.valueOf(5L), appClientService.groupAuthId);
        assertTrue(textOf(groups).contains("group-6"));

        Map<String, Object> setGroupArgs = new HashMap<>();
        setGroupArgs.put("id", 5);
        setGroupArgs.put("groupIds", Arrays.asList(1, 2));
        CallToolResult setGroups = callTool(tools, "dp_client_set_groups", setGroupArgs);
        assertFalse(setGroups.getIsError());
        assertEquals(Long.valueOf(5), appClientService.groupAuthRequest.getId());
        assertEquals(Arrays.asList(1L, 2L), appClientService.groupAuthRequest.getGroupIds());
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
    public void mcpConfigToolsDelegateAndExposeTokenAndEndpoint() {
        FakeMcpManageService mcpManageService = new FakeMcpManageService();
        McpAdminTools tools = newTools(new FakeApiModuleService(), new FakeApiAssignmentService(),
                new FakeDataSourceService(), new FakeAppClientService(), mcpManageService);

        CallToolResult toolsListed = callTool(tools, "dp_mcp_tool_list",
                Collections.singletonMap("searchText", "user"));
        assertFalse(toolsListed.getIsError());
        assertEquals("user", mcpManageService.listedToolsRequest.getSearchText());

        Map<String, Object> toolCreateArgs = new HashMap<>();
        toolCreateArgs.put("name", "query_orders");
        toolCreateArgs.put("description", "查订单");
        toolCreateArgs.put("apiId", 3);
        CallToolResult toolCreated = callTool(tools, "dp_mcp_tool_create", toolCreateArgs);
        assertFalse(toolCreated.getIsError());
        assertEquals("query_orders", mcpManageService.createdTool.getName());
        assertEquals("查订单", mcpManageService.createdTool.getDescription());
        assertEquals(Long.valueOf(3), mcpManageService.createdTool.getApiId());

        Map<String, Object> toolUpdateArgs = new HashMap<>();
        toolUpdateArgs.put("id", 8);
        toolUpdateArgs.put("name", "query_users");
        toolUpdateArgs.put("description", "d");
        toolUpdateArgs.put("apiId", 3);
        CallToolResult toolUpdated = callTool(tools, "dp_mcp_tool_update", toolUpdateArgs);
        assertFalse(toolUpdated.getIsError());
        assertEquals(Long.valueOf(8), mcpManageService.updatedTool.getId());
        assertEquals("query_users", mcpManageService.updatedTool.getName());
        assertEquals(Long.valueOf(3), mcpManageService.updatedTool.getApiId());

        CallToolResult toolDeleted = callTool(tools, "dp_mcp_tool_delete", Collections.singletonMap("id", 8));
        assertFalse(toolDeleted.getIsError());
        assertEquals(Long.valueOf(8L), mcpManageService.deletedToolId);

        CallToolResult clientsListed = callTool(tools, "dp_mcp_client_list",
                Collections.singletonMap("searchText", "agent"));
        assertFalse(clientsListed.getIsError());
        assertEquals("agent", mcpManageService.listedClientsRequest.getSearchText());

        Map<String, Object> clientUpdateArgs = new HashMap<>();
        clientUpdateArgs.put("id", 9);
        clientUpdateArgs.put("name", "renamed");
        clientUpdateArgs.put("manage", true);
        CallToolResult clientUpdated = callTool(tools, "dp_mcp_client_update", clientUpdateArgs);
        assertFalse(clientUpdated.getIsError());
        assertEquals(Long.valueOf(9L), mcpManageService.updatedClientId);
        assertEquals("renamed", mcpManageService.updatedClientName);
        assertEquals(Boolean.TRUE, mcpManageService.updatedClientManage);

        CallToolResult clientDeleted = callTool(tools, "dp_mcp_client_delete", Collections.singletonMap("id", 9));
        assertFalse(clientDeleted.getIsError());
        assertEquals(Long.valueOf(9L), mcpManageService.deletedClientId);

        CallToolResult token = callTool(tools, "dp_mcp_client_token", Collections.singletonMap("id", 9));
        assertFalse(token.getIsError());
        assertEquals(Long.valueOf(9L), mcpManageService.tokenClientId);
        assertTrue(textOf(token).contains("token-9"));

        CallToolResult endpoint = callTool(tools, "dp_mcp_endpoint", Collections.emptyMap());
        assertFalse(endpoint.getIsError());
        assertTrue(textOf(endpoint).contains("https://gw.example.com/mcp"));
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
