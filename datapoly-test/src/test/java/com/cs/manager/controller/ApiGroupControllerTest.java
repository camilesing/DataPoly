// Use of this source code is governed by a BSD-style license
package com.cs.manager.controller;

import com.cs.common.dto.PageResult;
import com.cs.common.dto.ResultEntity;
import com.cs.core.datatask.DataTaskTestSupport;
import com.cs.core.dto.EntitySearchRequest;
import com.cs.core.service.ApiGroupService;
import com.cs.persistence.entity.ApiGroupEntity;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Group management endpoints are thin delegations over {@link ApiGroupService}.
 */
public class ApiGroupControllerTest {

    private StubApiGroupService service;
    private ApiGroupController controller;

    @Before
    public void setUp() {
        service = new StubApiGroupService();
        controller = new ApiGroupController();
        DataTaskTestSupport.setField(controller, "apiGroupService", service);
    }

    @Test
    public void createDelegatesName() {
        ResultEntity result = controller.create("public");
        assertEquals("public", service.createName);
        assertEquals(Integer.valueOf(0), result.getCode());
    }

    @Test
    public void updateDelegatesIdAndName() {
        ResultEntity result = controller.update(2L, "internal");
        assertEquals(Long.valueOf(2L), service.updateId);
        assertEquals("internal", service.updateName);
        assertEquals(Integer.valueOf(0), result.getCode());
    }

    @Test
    public void deleteDelegatesId() {
        ResultEntity result = controller.delete(3L);
        assertEquals(Long.valueOf(3L), service.deletedId);
        assertEquals(Integer.valueOf(0), result.getCode());
    }

    @Test
    public void listAllDelegatesSearchRequest() {
        PageResult<ApiGroupEntity> page = new PageResult<>();
        service.listResult = page;
        EntitySearchRequest request = new EntitySearchRequest();
        assertSame(page, controller.listAll(request));
        assertSame(request, service.listRequest);
    }

    private static class StubApiGroupService extends ApiGroupService {
        PageResult<ApiGroupEntity> listResult = new PageResult<>();

        String createName;
        Long updateId;
        String updateName;
        Long deletedId;
        EntitySearchRequest listRequest;

        @Override
        public void createGroup(String name) {
            createName = name;
        }

        @Override
        public void updateGroup(Long id, String newName) {
            updateId = id;
            updateName = newName;
        }

        @Override
        public void deleteGroup(Long id) {
            deletedId = id;
        }

        @Override
        public PageResult<ApiGroupEntity> listAll(EntitySearchRequest request) {
            listRequest = request;
            return listResult;
        }
    }
}
