// Use of this source code is governed by a BSD-style license
package com.cs.manager.service;

import cn.hutool.extra.spring.SpringUtil;
import com.cs.common.dto.PageResult;
import com.cs.common.enums.HttpMethodEnum;
import com.cs.common.exception.CommonException;
import com.cs.common.exception.ResponseErrorCode;
import com.cs.core.dto.EntitySearchRequest;
import com.cs.core.dto.McpServerAddrResponse;
import com.cs.core.dto.McpToolResponse;
import com.cs.core.dto.McpToolSaveRequest;
import com.cs.core.service.SystemParamService;
import com.cs.persistence.dao.ApiAssignmentDao;
import com.cs.persistence.dao.ApiModuleDao;
import com.cs.persistence.dao.ApiOnlineDao;
import com.cs.persistence.dao.McpClientDao;
import com.cs.persistence.dao.McpToolDao;
import com.cs.persistence.entity.ApiAssignmentEntity;
import com.cs.persistence.entity.ApiModuleEntity;
import com.cs.persistence.entity.McpClientEntity;
import com.cs.persistence.entity.McpToolEntity;
import io.modelcontextprotocol.server.McpAsyncServer;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.server.McpSyncServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.*;

import static org.junit.Assert.*;

/**
 * MCP client/tool management: CRUD validation, the plaintext token reveal,
 * the address prefixes from {@link ServiceUrlResolver} and the in-memory
 * tool registry synchronization (immediate without a transaction, after
 * commit inside one).
 */
public class McpManageServiceTest {

    private StubMcpClientDao clientDao;
    private StubMcpToolDao toolDao;
    private StubModuleDao moduleDao;
    private StubAssignmentDao assignmentDao;
    private StubOnlineDao onlineDao;
    private RecordingSyncServer syncServer;
    private McpManageService service;

    @Before
    public void setUp() throws Exception {
        clientDao = new StubMcpClientDao();
        toolDao = new StubMcpToolDao();
        moduleDao = new StubModuleDao();
        assignmentDao = new StubAssignmentDao();
        onlineDao = new StubOnlineDao();
        syncServer = new RecordingSyncServer();
        service = new McpManageService();
        inject("mcpClientDao", clientDao);
        inject("mcpToolDao", toolDao);
        inject("apiModuleDao", moduleDao);
        inject("apiAssignmentDao", assignmentDao);
        inject("apiOnlineDao", onlineDao);
        inject("mcpSyncServer", syncServer);
        inject("serviceUrlResolver", new FixedManagerResolver("http://manager.example.com:8090"));

        // McpToolCallHandler pulls its collaborators from the static hutool
        // SpringUtil registry; a dynamic proxy bean factory serves a stub for
        // SystemParamService and null for everything else (never invoked).
        Object beanFactory = Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{org.springframework.beans.factory.config.ConfigurableListableBeanFactory.class},
                (p, m, a) -> {
                    if ("getBean".equals(m.getName()) && a != null && a.length == 1
                            && a[0] == SystemParamService.class) {
                        return new SystemParamService() {
                            @Override
                            public Integer getIntByParamKey(String key, int defaultValue) {
                                return defaultValue;
                            }
                        };
                    }
                    if ("hashCode".equals(m.getName())) {
                        return System.identityHashCode(p);
                    }
                    if ("equals".equals(m.getName())) {
                        return p == a[0];
                    }
                    return null;
                });
        Field beanFactoryField = SpringUtil.class.getDeclaredField("beanFactory");
        beanFactoryField.setAccessible(true);
        beanFactoryField.set(null, beanFactory);
    }

    @After
    public void tearDown() throws Exception {
        Field beanFactoryField = SpringUtil.class.getDeclaredField("beanFactory");
        beanFactoryField.setAccessible(true);
        beanFactoryField.set(null, null);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        com.github.pagehelper.PageHelper.clearPage();
    }

    // ------------------------------------------------------------------
    // endpoint address
    // ------------------------------------------------------------------

    @Test
    public void mcpEndpointPrefixesUseResolvedManagerUrl() throws Exception {
        inject("serviceUrlResolver", new FixedManagerResolver("https://mgr.example.com"));

        McpServerAddrResponse endpoint = service.getMcpServerEndpoint();

        assertEquals("https://mgr.example.com/mcp/sse?token=", endpoint.getSseAddrPrefix());
        assertEquals("https://mgr.example.com/mcp?token=", endpoint.getStreamAddrPrefix());
        assertEquals("https://mgr.example.com/mcp/admin/sse?token=", endpoint.getAdminSseAddrPrefix());
        assertEquals("https://mgr.example.com/mcp/admin?token=", endpoint.getAdminStreamAddrPrefix());
    }

    @Test
    public void mcpEndpointPropagatesResolverFailure() throws Exception {
        inject("serviceUrlResolver", new ServiceUrlResolver() {
            @Override
            public String resolveManager() {
                throw new CommonException(ResponseErrorCode.ERROR_RESOURCE_NOT_EXISTS, "manager.not.found");
            }
        });
        try {
            service.getMcpServerEndpoint();
            fail("expected manager.not.found to propagate");
        } catch (CommonException e) {
            assertEquals(ResponseErrorCode.ERROR_RESOURCE_NOT_EXISTS, e.getCode());
        }
    }

    // ------------------------------------------------------------------
    // client CRUD
    // ------------------------------------------------------------------

    @Test
    public void createClientDefaultsToNonManageAndGeneratesHexToken() {
        service.createClient("reader");

        McpClientEntity inserted = clientDao.inserted;
        assertEquals("reader", inserted.getName());
        assertEquals(Boolean.FALSE, inserted.getManage());
        // MD5 hex of a random UUID
        assertEquals(32, inserted.getToken().length());
        assertTrue(inserted.getToken().matches("[0-9a-f]{32}"));
    }

    @Test
    public void createClientCanRequestManageFlag() {
        service.createClient("admin-client", true);

        assertEquals(Boolean.TRUE, clientDao.inserted.getManage());
    }

    @Test
    public void createClientRejectsDuplicateName() {
        clientDao.failInsertWithDuplicate = true;
        try {
            service.createClient("dup");
            fail("expected duplicate client name to be rejected");
        } catch (CommonException e) {
            assertEquals(ResponseErrorCode.ERROR_RESOURCE_ALREADY_EXISTS, e.getCode());
        }
    }

    @Test
    public void updateClientFailsWhenIdUnknown() {
        try {
            service.updateClient(404L, "new-name");
            fail("expected common.id.not.found");
        } catch (CommonException e) {
            assertEquals(ResponseErrorCode.ERROR_RESOURCE_NOT_EXISTS, e.getCode());
        }
    }

    @Test
    public void updateClientKeepsManageWhenFlagOmitted() {
        clientDao.rows.put(7L, client(7L, "old", "tok-7", false));

        service.updateClient(7L, "renamed");

        McpClientEntity updated = clientDao.updated;
        assertEquals("renamed", updated.getName());
        assertEquals(Boolean.FALSE, updated.getManage());
    }

    @Test
    public void updateClientSetsManageFlagWhenGiven() {
        clientDao.rows.put(7L, client(7L, "old", "tok-7", false));

        service.updateClient(7L, "renamed", true);

        assertEquals(Boolean.TRUE, clientDao.updated.getManage());
    }

    @Test
    public void updateClientRejectsDuplicateName() {
        clientDao.rows.put(7L, client(7L, "old", "tok-7", false));
        clientDao.failUpdateWithDuplicate = true;
        try {
            service.updateClient(7L, "dup");
            fail("expected duplicate name on update to be rejected");
        } catch (CommonException e) {
            assertEquals(ResponseErrorCode.ERROR_RESOURCE_ALREADY_EXISTS, e.getCode());
        }
    }

    @Test
    public void getClientTokenRevealsPlaintextOnlyForExistingRow() {
        clientDao.rows.put(7L, client(7L, "name", "secret-token", false));
        assertEquals("secret-token", service.getClientToken(7L));

        try {
            service.getClientToken(404L);
            fail("expected common.id.not.found");
        } catch (CommonException e) {
            assertEquals(ResponseErrorCode.ERROR_RESOURCE_NOT_EXISTS, e.getCode());
        }
    }

    @Test
    public void deleteClientDelegatesToDao() {
        service.deleteClient(9L);
        assertEquals(Long.valueOf(9L), clientDao.deletedId);
    }

    @Test
    public void listClientAllAppliesSearchAndDefaultPaging() {
        clientDao.all = Arrays.asList(
                client(1L, "alpha", "t1", false),
                client(2L, "beta", "t2", true));
        EntitySearchRequest request = new EntitySearchRequest();
        request.setSearchText("alp");

        PageResult<McpClientEntity> page = service.listClientAll(request);

        assertEquals("alp", clientDao.listedSearchText);
        assertEquals(2, page.getData().size());
        assertEquals(1, page.getPagination().getPage());
        assertEquals(com.cs.persistence.util.PageUtils.MAX_PAGE_SIZE, page.getPagination().getSize());
    }

    // ------------------------------------------------------------------
    // startup load
    // ------------------------------------------------------------------

    @Test
    public void loadMcpToolsRegistersOnlyToolsWithOnlineApi() {
        toolDao.all = Arrays.asList(tool(1L, "tool-a", 100L), tool(2L, "tool-b", 200L));
        onlineDao.all = Collections.singletonList(assignment(100L, "get-user", HttpMethodEnum.GET));
        onlineDao.listAllCount = 0;

        service.loadMcpTools();

        // one shared online lookup regardless of the number of tools
        assertEquals(1, onlineDao.listAllCount);
        assertEquals(Collections.singletonList("tool-a"), syncServer.addedToolNames);
        assertTrue(syncServer.removedToolNames.isEmpty());
    }

    @Test
    public void loadMcpToolsWithNoRowsIsNoop() {
        toolDao.all = Collections.emptyList();
        service.loadMcpTools();
        assertTrue(syncServer.addedToolNames.isEmpty());
        assertEquals(0, onlineDao.listAllCount);
    }

    @Test
    public void loadMcpToolsPropagatesFailure() {
        toolDao.failListAll = new RuntimeException("db down");
        try {
            service.loadMcpTools();
            fail("expected the load failure to propagate");
        } catch (RuntimeException e) {
            assertEquals("db down", e.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // tool CRUD
    // ------------------------------------------------------------------

    @Test
    public void createToolRequiresApiId() {
        McpToolSaveRequest request = request(null, "name", "desc");
        try {
            service.createTool(request);
            fail("expected ERROR_INVALID_ARGUMENT for missing apiId");
        } catch (CommonException e) {
            assertEquals(ResponseErrorCode.ERROR_INVALID_ARGUMENT, e.getCode());
        }
    }

    @Test
    public void createToolRequiresAssignmentRow() {
        McpToolSaveRequest request = request(100L, "name", "desc");
        assignmentDao.rows.clear();
        try {
            service.createTool(request);
            fail("expected ERROR_RESOURCE_NOT_EXISTS for missing assignment");
        } catch (CommonException e) {
            assertEquals(ResponseErrorCode.ERROR_RESOURCE_NOT_EXISTS, e.getCode());
        }
    }

    @Test
    public void createToolRequiresOnlineApi() {
        McpToolSaveRequest request = request(100L, "name", "desc");
        assignmentDao.rows.put(100L, assignment(100L, "get-user", HttpMethodEnum.POST));
        onlineDao.byApiId.clear();
        try {
            service.createTool(request);
            fail("expected ERROR_RESOURCE_NOT_ONLINE");
        } catch (CommonException e) {
            assertEquals(ResponseErrorCode.ERROR_RESOURCE_NOT_ONLINE, e.getCode());
        }
    }

    @Test
    public void createToolRegistersImmediatelyWithoutActiveTransaction() {
        McpToolSaveRequest request = request(100L, "query-user", "desc");
        assignmentDao.rows.put(100L, assignment(100L, "get-user", HttpMethodEnum.POST));
        onlineDao.byApiId.put(100L, assignment(100L, "get-user", HttpMethodEnum.POST));

        service.createTool(request);

        assertEquals("query-user", toolDao.inserted.getName());
        assertEquals(Long.valueOf(100L), toolDao.inserted.getApiId());
        // no transaction synchronization is active, so the registry runs inline
        assertEquals(Collections.singletonList("query-user"), syncServer.addedToolNames);
    }

    @Test
    public void createToolRegistersOnlyAfterCommitInsideTransaction() {
        McpToolSaveRequest request = request(100L, "query-user", "desc");
        assignmentDao.rows.put(100L, assignment(100L, "get-user", HttpMethodEnum.POST));
        onlineDao.byApiId.put(100L, assignment(100L, "get-user", HttpMethodEnum.POST));

        TransactionSynchronizationManager.initSynchronization();
        try {
            service.createTool(request);
            // the row is written but the in-memory registry is still untouched
            assertNotNull(toolDao.inserted);
            assertTrue(syncServer.addedToolNames.isEmpty());

            for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
                synchronization.afterCommit();
            }
            assertEquals(Collections.singletonList("query-user"), syncServer.addedToolNames);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    public void createToolRejectsDuplicateToolName() {
        McpToolSaveRequest request = request(100L, "dup", "desc");
        assignmentDao.rows.put(100L, assignment(100L, "get-user", HttpMethodEnum.POST));
        onlineDao.byApiId.put(100L, assignment(100L, "get-user", HttpMethodEnum.POST));
        toolDao.failInsertWithDuplicate = true;

        try {
            service.createTool(request);
            fail("expected duplicate tool name to be rejected");
        } catch (CommonException e) {
            assertEquals(ResponseErrorCode.ERROR_RESOURCE_ALREADY_EXISTS, e.getCode());
            assertTrue(syncServer.addedToolNames.isEmpty());
        }
    }

    @Test
    public void updateToolValidatesIdAndApi() {
        // missing id
        try {
            service.updateTool(requestWithName(null, 100L, "n", "d"));
            fail("expected ERROR_INVALID_ARGUMENT for missing id");
        } catch (CommonException e) {
            assertEquals(ResponseErrorCode.ERROR_INVALID_ARGUMENT, e.getCode());
        }
        // missing apiId
        try {
            service.updateTool(requestWithName(1L, null, "n", "d"));
            fail("expected ERROR_INVALID_ARGUMENT for missing apiId");
        } catch (CommonException e) {
            assertEquals(ResponseErrorCode.ERROR_INVALID_ARGUMENT, e.getCode());
        }
        // unknown tool id
        try {
            service.updateTool(requestWithName(404L, 100L, "n", "d"));
            fail("expected ERROR_RESOURCE_NOT_EXISTS for missing tool");
        } catch (CommonException e) {
            assertEquals(ResponseErrorCode.ERROR_RESOURCE_NOT_EXISTS, e.getCode());
        }
        // tool exists but assignment does not
        toolDao.rows.put(1L, tool(1L, "old", 100L));
        assignmentDao.rows.clear();
        try {
            service.updateTool(requestWithName(1L, 100L, "n", "d"));
            fail("expected ERROR_RESOURCE_NOT_EXISTS for missing assignment");
        } catch (CommonException e) {
            assertEquals(ResponseErrorCode.ERROR_RESOURCE_NOT_EXISTS, e.getCode());
        }
        // assignment exists but is offline
        assignmentDao.rows.put(100L, assignment(100L, "get-user", HttpMethodEnum.POST));
        onlineDao.byApiId.clear();
        try {
            service.updateTool(requestWithName(1L, 100L, "n", "d"));
            fail("expected ERROR_RESOURCE_NOT_ONLINE");
        } catch (CommonException e) {
            assertEquals(ResponseErrorCode.ERROR_RESOURCE_NOT_ONLINE, e.getCode());
        }
    }

    @Test
    public void updateToolSwapsRegistrationUnderTheNewName() {
        toolDao.rows.put(1L, tool(1L, "old-name", 100L));
        assignmentDao.rows.put(100L, assignment(100L, "get-user", HttpMethodEnum.POST));
        onlineDao.byApiId.put(100L, assignment(100L, "get-user", HttpMethodEnum.POST));

        service.updateTool(requestWithName(1L, 100L, "new-name", "new-desc"));

        assertEquals("new-name", toolDao.updated.getName());
        assertEquals(Long.valueOf(100L), toolDao.updated.getApiId());
        assertEquals(Collections.singletonList("old-name"), syncServer.removedToolNames);
        assertEquals(Collections.singletonList("new-name"), syncServer.addedToolNames);
    }

    @Test
    public void updateToolRejectsDuplicateName() {
        toolDao.rows.put(1L, tool(1L, "old-name", 100L));
        assignmentDao.rows.put(100L, assignment(100L, "get-user", HttpMethodEnum.POST));
        onlineDao.byApiId.put(100L, assignment(100L, "get-user", HttpMethodEnum.POST));
        toolDao.failUpdateWithDuplicate = true;

        try {
            service.updateTool(requestWithName(1L, 100L, "dup-name", "d"));
            fail("expected duplicate tool name to be rejected");
        } catch (CommonException e) {
            assertEquals(ResponseErrorCode.ERROR_RESOURCE_ALREADY_EXISTS, e.getCode());
            assertTrue(syncServer.addedToolNames.isEmpty());
            assertTrue(syncServer.removedToolNames.isEmpty());
        }
    }

    @Test
    public void deleteToolFailsWhenIdUnknown() {
        try {
            service.deleteTool(404L);
            fail("expected common.id.not.found");
        } catch (CommonException e) {
            assertEquals(ResponseErrorCode.ERROR_RESOURCE_NOT_EXISTS, e.getCode());
        }
    }

    @Test
    public void deleteToolRemovesRegistration() {
        toolDao.rows.put(1L, tool(1L, "gone", 100L));

        service.deleteTool(1L);

        assertEquals(Long.valueOf(1L), toolDao.deletedId);
        assertEquals(Collections.singletonList("gone"), syncServer.removedToolNames);
    }

    @Test
    public void listToolAllEnrichesWithModuleAndApiInfo() {
        toolDao.all = Arrays.asList(tool(1L, "tool-a", 100L), tool(2L, "tool-b", 999L));
        assignmentDao.batchIds = Collections.singletonList(assignment(100L, "get-user", HttpMethodEnum.POST));
        // assignment 100 belongs to module 5
        assignmentDao.batchIds.get(0).setModuleId(5L);
        moduleDao.modules.add(module(5L, "user-module"));
        EntitySearchRequest request = new EntitySearchRequest();
        request.setPage(1);
        request.setSize(10);
        request.setSearchText("tool");

        PageResult<McpToolResponse> page = service.listToolAll(request);

        assertEquals("tool", toolDao.listedSearchText);
        assertEquals(2, page.getData().size());
        assertEquals(1, page.getPagination().getPage());
        assertEquals(10, page.getPagination().getSize());

        McpToolResponse enriched = page.getData().get(0);
        assertEquals("tool-a", enriched.getName());
        assertEquals(Long.valueOf(5L), enriched.getModuleId());
        assertEquals("user-module", enriched.getModuleName());
        assertEquals("get-user", enriched.getApiName());
        assertEquals("POST", enriched.getApiMethod());
        assertEquals("/api/get-user", enriched.getApiPath());

        // tool-b references a deleted assignment: kept in the list without api info
        McpToolResponse orphan = page.getData().get(1);
        assertEquals("tool-b", orphan.getName());
        assertNull(orphan.getApiName());
        assertNull(orphan.getModuleId());
    }

    // ------------------------------------------------------------------
    // fakes & helpers
    // ------------------------------------------------------------------

    private static McpToolSaveRequest request(Long apiId, String name, String description) {
        return requestWithName(null, apiId, name, description);
    }

    private static McpToolSaveRequest requestWithName(Long id, Long apiId, String name, String description) {
        McpToolSaveRequest request = new McpToolSaveRequest();
        request.setId(id);
        request.setApiId(apiId);
        request.setName(name);
        request.setDescription(description);
        return request;
    }

    private static McpClientEntity client(Long id, String name, String token, boolean manage) {
        return McpClientEntity.builder().id(id).name(name).token(token).manage(manage).build();
    }

    private static McpToolEntity tool(Long id, String name, Long apiId) {
        return McpToolEntity.builder().id(id).name(name).description("d-" + name).apiId(apiId).build();
    }

    private static ApiModuleEntity module(Long id, String name) {
        return ApiModuleEntity.builder().id(id).name(name).build();
    }

    private static ApiAssignmentEntity assignment(Long id, String name, HttpMethodEnum method) {
        return ApiAssignmentEntity.builder()
                .id(id).name(name).method(method).path(name).build();
    }

    private void inject(String name, Object value) throws Exception {
        Field field = McpManageService.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(service, value);
    }

    /** Resolver stub: the real priority matrix lives in ServiceUrlResolverTest. */
    private static class FixedManagerResolver extends ServiceUrlResolver {
        private final String url;

        FixedManagerResolver(String url) {
            this.url = url;
        }

        @Override
        public String resolveManager() {
            return url;
        }
    }

    /** Records tool registrations; the super constructor needs a non-null async delegate. */
    private static final class RecordingSyncServer extends McpSyncServer {
        final List<String> addedToolNames = new ArrayList<>();
        final List<String> removedToolNames = new ArrayList<>();

        RecordingSyncServer() throws Exception {
            super(bareAsyncServer());
        }

        private static McpAsyncServer bareAsyncServer() throws Exception {
            Constructor<McpAsyncServer> constructor = McpAsyncServer.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        }

        @Override
        public void addTool(SyncToolSpecification toolHandler) {
            addedToolNames.add(toolHandler.getTool().getName());
        }

        @Override
        public void removeTool(String toolName) {
            removedToolNames.add(toolName);
        }
    }

    private static class StubMcpClientDao extends McpClientDao {
        final Map<Long, McpClientEntity> rows = new HashMap<>();
        List<McpClientEntity> all = Collections.emptyList();
        McpClientEntity inserted;
        McpClientEntity updated;
        Long deletedId;
        String listedSearchText;
        boolean failInsertWithDuplicate;
        boolean failUpdateWithDuplicate;

        @Override
        public void insert(McpClientEntity entity) {
            if (failInsertWithDuplicate) {
                throw new DuplicateKeyException("uk_name");
            }
            inserted = entity;
        }

        @Override
        public McpClientEntity getById(Long id) {
            return rows.get(id);
        }

        @Override
        public List<McpClientEntity> listAll(String searchText) {
            listedSearchText = searchText;
            return all;
        }

        @Override
        public void updateById(McpClientEntity entity) {
            if (failUpdateWithDuplicate) {
                throw new DuplicateKeyException("uk_name");
            }
            updated = entity;
        }

        @Override
        public void deleteById(Long id) {
            deletedId = id;
        }
    }

    private static class StubMcpToolDao extends McpToolDao {
        final Map<Long, McpToolEntity> rows = new HashMap<>();
        List<McpToolEntity> all = Collections.emptyList();
        McpToolEntity inserted;
        McpToolEntity updated;
        Long deletedId;
        String listedSearchText;
        boolean failInsertWithDuplicate;
        boolean failUpdateWithDuplicate;
        RuntimeException failListAll;

        @Override
        public void insert(McpToolEntity entity) {
            if (failInsertWithDuplicate) {
                throw new DuplicateKeyException("uk_name");
            }
            inserted = entity;
        }

        @Override
        public McpToolEntity getById(Long id) {
            return rows.get(id);
        }

        @Override
        public List<McpToolEntity> listAll(String searchText) {
            if (failListAll != null) {
                throw failListAll;
            }
            listedSearchText = searchText;
            return all;
        }

        @Override
        public void updateById(McpToolEntity entity) {
            if (failUpdateWithDuplicate) {
                throw new DuplicateKeyException("uk_name");
            }
            updated = entity;
        }

        @Override
        public void deleteById(Long id) {
            deletedId = id;
        }
    }

    private static final class StubModuleDao extends ApiModuleDao {
        final List<ApiModuleEntity> modules = new ArrayList<>();

        @Override
        public List<ApiModuleEntity> listAll(String searchText) {
            return modules;
        }
    }

    private static final class StubAssignmentDao extends ApiAssignmentDao {
        final Map<Long, ApiAssignmentEntity> rows = new HashMap<>();
        List<ApiAssignmentEntity> batchIds = Collections.emptyList();

        @Override
        public ApiAssignmentEntity getById(Long id, boolean withSql) {
            return rows.get(id);
        }

        @Override
        public List<ApiAssignmentEntity> getByIds(List<Long> ids) {
            return batchIds;
        }
    }

    private static final class StubOnlineDao extends ApiOnlineDao {
        final Map<Long, ApiAssignmentEntity> byApiId = new HashMap<>();
        List<ApiAssignmentEntity> all = Collections.emptyList();
        int listAllCount;

        @Override
        public ApiAssignmentEntity getByApiId(Long apiId) {
            return byApiId.get(apiId);
        }

        @Override
        public List<ApiAssignmentEntity> listAll() {
            listAllCount++;
            return all;
        }
    }
}
