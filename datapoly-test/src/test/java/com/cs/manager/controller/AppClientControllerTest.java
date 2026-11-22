// Use of this source code is governed by a BSD-style license
package com.cs.manager.controller;

import com.cs.common.dto.PageResult;
import com.cs.common.dto.ResultEntity;
import com.cs.core.datatask.DataTaskTestSupport;
import com.cs.core.dto.AppClientDetailResponse;
import com.cs.core.dto.AppClientGroupRequest;
import com.cs.core.dto.AppClientSaveRequest;
import com.cs.core.dto.AppClientSearchRequest;
import com.cs.core.dto.EntityIdNameResponse;
import com.cs.core.service.AppClientService;
import org.junit.Before;
import org.junit.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Every client-app endpoint is a thin delegation over {@link AppClientService}: the request
 * object or id is passed through and the service result (or plain success) is returned.
 */
public class AppClientControllerTest {

    private StubAppClientService service;
    private AppClientController controller;

    @Before
    public void setUp() {
        service = new StubAppClientService();
        controller = new AppClientController();
        DataTaskTestSupport.setField(controller, "appClientService", service);
    }

    @Test
    public void createDelegatesRequest() {
        AppClientSaveRequest request = new AppClientSaveRequest();
        ResultEntity result = controller.create(request);
        assertSame(request, service.createRequest);
        assertEquals(Integer.valueOf(0), result.getCode());
        assertNull(result.getData());
    }

    @Test
    public void deleteDelegatesId() {
        ResultEntity result = controller.delete(6L);
        assertEquals(Long.valueOf(6L), service.deletedId);
        assertEquals(Integer.valueOf(0), result.getCode());
    }

    @Test
    public void searchListDelegatesRequest() {
        PageResult<AppClientDetailResponse> page = new PageResult<>();
        service.searchListResult = page;
        AppClientSearchRequest request = new AppClientSearchRequest();
        assertSame(page, controller.searchList(request));
        assertSame(request, service.searchListRequest);
    }

    @Test
    public void secretReturnsServiceValue() {
        service.secretResult = "s3cret";
        ResultEntity<String> result = controller.secret(9L);
        assertEquals(Long.valueOf(9L), service.secretId);
        assertEquals("s3cret", result.getData());
    }

    @Test
    public void createGroupAuthDelegatesRequest() {
        AppClientGroupRequest request = new AppClientGroupRequest();
        ResultEntity result = controller.createGroupAuth(request);
        assertSame(request, service.groupAuthRequest);
        assertEquals(Integer.valueOf(0), result.getCode());
    }

    @Test
    public void getGroupAuthDelegatesId() {
        EntityIdNameResponse row = new EntityIdNameResponse();
        service.groupAuthResult = Collections.singletonList(row);
        List<EntityIdNameResponse> result = controller.getGroupAuth(4L);
        assertEquals(Long.valueOf(4L), service.groupAuthId);
        assertSame(row, result.get(0));
    }

    private static class StubAppClientService extends AppClientService {
        PageResult<AppClientDetailResponse> searchListResult = new PageResult<>();
        String secretResult;
        List<EntityIdNameResponse> groupAuthResult = Collections.emptyList();

        AppClientSaveRequest createRequest;
        Long deletedId;
        AppClientSearchRequest searchListRequest;
        Long secretId;
        AppClientGroupRequest groupAuthRequest;
        Long groupAuthId;

        @Override
        public void create(AppClientSaveRequest request) {
            createRequest = request;
        }

        @Override
        public void delete(Long id) {
            deletedId = id;
        }

        @Override
        public PageResult<AppClientDetailResponse> searchList(AppClientSearchRequest request) {
            searchListRequest = request;
            return searchListResult;
        }

        @Override
        public String getSecret(Long id) {
            secretId = id;
            return secretResult;
        }

        @Override
        public void createGroupAuth(AppClientGroupRequest request) {
            groupAuthRequest = request;
        }

        @Override
        public List<EntityIdNameResponse> getGroupAuth(Long id) {
            groupAuthId = id;
            return groupAuthResult;
        }
    }
}
