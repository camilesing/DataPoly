// Use of this source code is governed by a BSD-style license
package com.cs.manager.controller;

import com.cs.common.dto.PageResult;
import com.cs.common.dto.ResultEntity;
import com.cs.core.datatask.DataTaskTestSupport;
import com.cs.core.dto.EntitySearchRequest;
import com.cs.core.dto.McpServerAddrResponse;
import com.cs.manager.service.McpManageService;
import com.cs.manager.service.PermissionService;
import com.cs.persistence.entity.McpClientEntity;
import org.junit.Before;
import org.junit.Test;
import org.springframework.security.authorization.AuthorizationDeniedException;

import java.util.Collections;

import static org.junit.Assert.*;

/**
 * MCP token management endpoints are thin delegations over {@link McpManageService}; in
 * particular {@code getToken} is the only plaintext-token exit and just wraps the service value.
 */
public class McpClientControllerTest {

    private StubMcpManageService service;
    private StubPermissionService permissionService;
    private McpClientController controller;

    @Before
    public void setUp() {
        service = new StubMcpManageService();
        permissionService = new StubPermissionService();
        controller = new McpClientController();
        DataTaskTestSupport.setField(controller, "mcpManageService", service);
        DataTaskTestSupport.setField(controller, "permissionService", permissionService);
    }

    @Test
    public void endpointReturnsServiceValue() {
        McpServerAddrResponse addr = McpServerAddrResponse.builder().build();
        service.endpointResult = addr;
        ResultEntity<McpServerAddrResponse> result = controller.getMcpServerEndpoint();
        assertSame(addr, result.getData());
    }

    @Test
    public void createDelegatesNameAndManageFlag() {
        controller.create("reader", true);
        assertEquals("reader", service.createName);
        assertEquals(Boolean.TRUE, service.createManage);

        controller.create("reader", false);
        assertEquals(Boolean.FALSE, service.createManage);
    }

    @Test
    public void updateDelegatesAllThreeArguments() {
        controller.update(5L, "renamed", Boolean.TRUE);
        assertEquals(Long.valueOf(5L), service.updateId);
        assertEquals("renamed", service.updateName);
        assertEquals(Boolean.TRUE, service.updateManage);

        // manage stays optional: a plain rename passes null through
        controller.update(6L, "renamed2", null);
        assertNull(service.updateManage);
    }

    /**
     * Regression: a manage token is admin-equivalent (it reaches /mcp/admin, plaintext secrets
     * included), but granting it only needed the MCP_CLIENT matrix cell — a USER holding U could
     * turn any data token into an admin-equivalent one.
     */
    @Test
    public void grantingManageIsAdminOnly() {
        permissionService.admin = false;

        try {
            controller.create("reader", true);
            fail("a USER must not create a manage token");
        } catch (AuthorizationDeniedException expected) {
            // denied before anything reaches the service
        }
        assertNull(service.createName);

        try {
            controller.update(5L, "same-name", Boolean.TRUE);
            fail("a USER must not promote a token to manage");
        } catch (AuthorizationDeniedException expected) {
            // denied
        }
        assertNull(service.updateId);
    }

    @Test
    public void ordinaryTokenChangesStillNeedOnlyTheMatrixCell() {
        permissionService.admin = false;

        controller.create("reader", false);
        assertEquals(Boolean.FALSE, service.createManage);

        // revoking manage is not an escalation either
        controller.update(6L, "renamed", Boolean.FALSE);
        assertEquals(Boolean.FALSE, service.updateManage);
        assertEquals(0, permissionService.adminChecks);
    }

    @Test
    public void adminCanGrantManage() {
        controller.create("admin-tool", true);
        assertEquals(Boolean.TRUE, service.createManage);

        controller.update(5L, "renamed", Boolean.TRUE);
        assertEquals(Boolean.TRUE, service.updateManage);
        assertEquals(2, permissionService.adminChecks);
    }

    @Test
    public void deleteDelegatesId() {
        ResultEntity result = controller.delete(7L);
        assertEquals(Long.valueOf(7L), service.deletedClientId);
        assertEquals(Integer.valueOf(0), result.getCode());
    }

    @Test
    public void listAllDelegatesSearchRequest() {
        PageResult<McpClientEntity> page = new PageResult<>();
        service.listClientResult = page;
        EntitySearchRequest request = new EntitySearchRequest();
        assertSame(page, controller.listAll(request));
        assertSame(request, service.listClientRequest);
    }

    @Test
    public void tokenIsTheOnlyPlaintextExit() {
        service.tokenResult = "dp_plain_token";
        ResultEntity<String> result = controller.getToken(3L);
        assertEquals(Long.valueOf(3L), service.tokenId);
        assertEquals("dp_plain_token", result.getData());
    }

    private static class StubPermissionService extends PermissionService {
        boolean admin = true;
        int adminChecks;

        @Override
        public void checkAdminCurrent() {
            adminChecks++;
            if (!admin) {
                throw new AuthorizationDeniedException("admin only");
            }
        }
    }

    private static class StubMcpManageService extends McpManageService {
        McpServerAddrResponse endpointResult;
        PageResult<McpClientEntity> listClientResult = new PageResult<>();
        String tokenResult;

        String createName;
        Boolean createManage;
        Long updateId;
        String updateName;
        Boolean updateManage;
        Long deletedClientId;
        EntitySearchRequest listClientRequest;
        Long tokenId;

        @Override
        public McpServerAddrResponse getMcpServerEndpoint() {
            return endpointResult;
        }

        @Override
        public void createClient(String name, boolean manage) {
            createName = name;
            createManage = manage;
        }

        @Override
        public void updateClient(Long id, String newName, Boolean manage) {
            updateId = id;
            updateName = newName;
            updateManage = manage;
        }

        @Override
        public void deleteClient(Long id) {
            deletedClientId = id;
        }

        @Override
        public PageResult<McpClientEntity> listClientAll(EntitySearchRequest request) {
            listClientRequest = request;
            return listClientResult;
        }

        @Override
        public String getClientToken(Long id) {
            tokenId = id;
            return tokenResult;
        }
    }
}
