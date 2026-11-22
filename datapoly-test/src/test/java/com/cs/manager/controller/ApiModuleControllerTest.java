// Use of this source code is governed by a BSD-style license
package com.cs.manager.controller;

import com.cs.common.dto.PageResult;
import com.cs.common.dto.ResultEntity;
import com.cs.core.datatask.DataTaskTestSupport;
import com.cs.core.dto.ApiModuleAssignments;
import com.cs.core.dto.EntitySearchRequest;
import com.cs.core.service.ApiModuleService;
import com.cs.persistence.entity.ApiModuleEntity;
import org.junit.Before;
import org.junit.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Module management endpoints are thin delegations over {@link ApiModuleService}.
 */
public class ApiModuleControllerTest {

    private StubApiModuleService service;
    private ApiModuleController controller;

    @Before
    public void setUp() {
        service = new StubApiModuleService();
        controller = new ApiModuleController();
        DataTaskTestSupport.setField(controller, "apiModuleService", service);
    }

    @Test
    public void createDelegatesName() {
        ResultEntity result = controller.create("orders");
        assertEquals("orders", service.createName);
        assertEquals(Integer.valueOf(0), result.getCode());
    }

    @Test
    public void updateDelegatesIdAndName() {
        ResultEntity result = controller.update(3L, "billing");
        assertEquals(Long.valueOf(3L), service.updateId);
        assertEquals("billing", service.updateName);
        assertEquals(Integer.valueOf(0), result.getCode());
    }

    @Test
    public void deleteDelegatesId() {
        ResultEntity result = controller.delete(4L);
        assertEquals(Long.valueOf(4L), service.deletedId);
        assertEquals(Integer.valueOf(0), result.getCode());
    }

    @Test
    public void listAllDelegatesSearchRequest() {
        PageResult<ApiModuleEntity> page = new PageResult<>();
        service.listResult = page;
        EntitySearchRequest request = new EntitySearchRequest();
        assertSame(page, controller.listAll(request));
        assertSame(request, service.listRequest);
    }

    @Test
    public void moduleTreeDelegatesGroupId() {
        ApiModuleAssignments node = new ApiModuleAssignments();
        service.moduleTreeResult = Collections.singletonList(node);
        ResultEntity<List<ApiModuleAssignments>> result = controller.moduleTree(8L);
        assertEquals(Long.valueOf(8L), service.moduleTreeGroupId);
        assertSame(node, result.getData().get(0));
    }

    private static class StubApiModuleService extends ApiModuleService {
        PageResult<ApiModuleEntity> listResult = new PageResult<>();
        List<ApiModuleAssignments> moduleTreeResult = Collections.emptyList();

        String createName;
        Long updateId;
        String updateName;
        Long deletedId;
        EntitySearchRequest listRequest;
        Long moduleTreeGroupId;

        @Override
        public void createModule(String name) {
            createName = name;
        }

        @Override
        public void updateModule(Long id, String newName) {
            updateId = id;
            updateName = newName;
        }

        @Override
        public void deleteModule(Long id) {
            deletedId = id;
        }

        @Override
        public PageResult<ApiModuleEntity> listAll(EntitySearchRequest request) {
            listRequest = request;
            return listResult;
        }

        @Override
        public List<ApiModuleAssignments> moduleTree(Long groupId) {
            moduleTreeGroupId = groupId;
            return moduleTreeResult;
        }
    }
}
