// Use of this source code is governed by a BSD-style license
package com.cs.manager.controller;

import com.cs.common.dto.PageResult;
import com.cs.common.dto.ResultEntity;
import com.cs.core.datatask.DataTaskTestSupport;
import com.cs.core.dto.EntitySearchRequest;
import com.cs.core.dto.McpServerAddrResponse;
import com.cs.manager.service.McpManageService;
import com.cs.persistence.entity.McpClientEntity;
import org.junit.Before;
import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.*;

/**
 * MCP token management endpoints are thin delegations over {@link McpManageService}; in
 * particular {@code getToken} is the only plaintext-token exit and just wraps the service value.
 */
public class McpClientControllerTest {

    private StubMcpManageService service;
    private McpClientController controller;

    @Before
    public void setUp() {
        service = new StubMcpManageService();
        controller = new McpClientController();
        DataTaskTestSupport.setField(controller, "mcpManageService", service);
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
