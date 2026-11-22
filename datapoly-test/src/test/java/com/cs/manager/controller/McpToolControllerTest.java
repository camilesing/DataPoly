// Use of this source code is governed by a BSD-style license
package com.cs.manager.controller;

import com.cs.common.dto.PageResult;
import com.cs.common.dto.ResultEntity;
import com.cs.core.datatask.DataTaskTestSupport;
import com.cs.core.dto.EntitySearchRequest;
import com.cs.core.dto.McpToolResponse;
import com.cs.core.dto.McpToolSaveRequest;
import com.cs.manager.service.McpManageService;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * MCP tool management endpoints are thin delegations over {@link McpManageService}.
 */
public class McpToolControllerTest {

    private StubMcpManageService service;
    private McpToolController controller;

    @Before
    public void setUp() {
        service = new StubMcpManageService();
        controller = new McpToolController();
        DataTaskTestSupport.setField(controller, "mcpManageService", service);
    }

    @Test
    public void createDelegatesRequest() {
        McpToolSaveRequest request = new McpToolSaveRequest();
        ResultEntity result = controller.create(request);
        assertSame(request, service.createToolRequest);
        assertEquals(Integer.valueOf(0), result.getCode());
    }

    @Test
    public void updateDelegatesRequest() {
        McpToolSaveRequest request = new McpToolSaveRequest();
        ResultEntity result = controller.update(request);
        assertSame(request, service.updateToolRequest);
        assertEquals(Integer.valueOf(0), result.getCode());
    }

    @Test
    public void deleteDelegatesId() {
        ResultEntity result = controller.delete(2L);
        assertEquals(Long.valueOf(2L), service.deletedToolId);
        assertEquals(Integer.valueOf(0), result.getCode());
    }

    @Test
    public void listAllDelegatesSearchRequest() {
        PageResult<McpToolResponse> page = new PageResult<>();
        service.listToolResult = page;
        EntitySearchRequest request = new EntitySearchRequest();
        assertSame(page, controller.listAll(request));
        assertSame(request, service.listToolRequest);
    }

    private static class StubMcpManageService extends McpManageService {
        PageResult<McpToolResponse> listToolResult = new PageResult<>();

        McpToolSaveRequest createToolRequest;
        McpToolSaveRequest updateToolRequest;
        Long deletedToolId;
        EntitySearchRequest listToolRequest;

        @Override
        public void createTool(McpToolSaveRequest request) {
            createToolRequest = request;
        }

        @Override
        public void updateTool(McpToolSaveRequest request) {
            updateToolRequest = request;
        }

        @Override
        public void deleteTool(Long id) {
            deletedToolId = id;
        }

        @Override
        public PageResult<McpToolResponse> listToolAll(EntitySearchRequest request) {
            listToolRequest = request;
            return listToolResult;
        }
    }
}
