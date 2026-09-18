// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.core.service;

import com.cs.common.enums.*;
import com.cs.common.exception.*;
import com.cs.core.datatask.DataTaskTestSupport;
import com.cs.core.dto.ApiAssignmentSaveRequest;
import com.cs.core.dto.DataTypeFormatMapValue;
import com.cs.core.extension.ApiAssignmentPostProcessor;
import com.cs.core.extension.ApiAssignmentPostProcessors;
import com.cs.core.extension.ApiAssignmentUpdateEvent;
import com.cs.core.extension.ApiUpdatePostContext;
import com.cs.persistence.dao.ApiAssignmentDao;
import com.cs.persistence.dao.DataSourceDao;
import com.cs.persistence.entity.ApiAssignmentEntity;
import com.cs.persistence.entity.DataSourceEntity;
import com.cs.persistence.entity.VersionCommitEntity;
import org.junit.*;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;

import java.util.*;

/**
 * Deploy/rollback version ownership validation test (A3): a commit that does not exist or belongs to another API must be rejected,
 * so another API's version content can no longer be deployed/rolled back onto this API, and the NPE 500 is gone.
 * Also covers the postUpdate extension hook of {@code updateAssignment}.
 */
public class ApiAssignmentServiceTest {

    @Test
    public void rejectNullCommit() {
        try {
            ApiAssignmentService.requireOwnedCommit(1L, 100L, null);
            Assert.fail("Expected CommonException for missing commit.");
        } catch (CommonException e) {
            Assert.assertEquals(ResponseErrorCode.ERROR_RESOURCE_NOT_EXISTS, e.getCode());
        }
    }

    @Test
    public void rejectMismatchedCommit() {
        VersionCommitEntity commit = VersionCommitEntity.builder().id(100L).bizId(2L).version(1).build();
        try {
            ApiAssignmentService.requireOwnedCommit(1L, 100L, commit);
            Assert.fail("Expected CommonException for mismatched commit.");
        } catch (CommonException e) {
            Assert.assertEquals(ResponseErrorCode.ERROR_INVALID_ARGUMENT, e.getCode());
        }
    }

    @Test
    public void acceptOwnedCommit() {
        VersionCommitEntity commit = VersionCommitEntity.builder().id(100L).bizId(1L).version(3).build();
        Assert.assertSame(commit, ApiAssignmentService.requireOwnedCommit(1L, 100L, commit));
    }

    @Test
    public void updateAssignmentFiresPostProcessing() {
        ApiAssignmentEntity exists = ApiAssignmentEntity.builder()
                .id(1L).method(HttpMethodEnum.GET).path("/demo").build();
        UpdateHarness harness = wireUpdateService(exists);

        harness.service.updateAssignment(validUpdateRequest());

        Assert.assertEquals(1, harness.updated.size());
        Assert.assertEquals(1, harness.fired.size());
        ApiUpdatePostContext context = harness.fired.get(0);
        Assert.assertSame(harness.updated.get(0), context.getEntity());
        Assert.assertEquals(Long.valueOf(1L), context.getEntity().getId());
        Assert.assertEquals(NamingStrategyEnum.CAMEL_CASE, context.getEntity().getNamingStrategy());
        Assert.assertEquals(1, harness.events.size());
        ApiAssignmentUpdateEvent event = (ApiAssignmentUpdateEvent) harness.events.get(0);
        Assert.assertSame(context, event.getContext());
    }

    @Test
    public void updateAssignmentSkipsPostProcessingWhenValidationFails() {
        ApiAssignmentEntity exists = ApiAssignmentEntity.builder()
                .id(1L).method(HttpMethodEnum.POST).path("/demo").build();
        UpdateHarness harness = wireUpdateService(exists);

        try {
            harness.service.updateAssignment(validUpdateRequest());
            Assert.fail("Expected CommonException for method change.");
        } catch (CommonException e) {
            Assert.assertEquals(ResponseErrorCode.ERROR_INVALID_ARGUMENT, e.getCode());
        }
        Assert.assertTrue(harness.updated.isEmpty());
        Assert.assertTrue(harness.fired.isEmpty());
        Assert.assertTrue(harness.events.isEmpty());
    }

    private static ApiAssignmentSaveRequest validUpdateRequest() {
        ApiAssignmentSaveRequest request = new ApiAssignmentSaveRequest();
        request.setId(1L);
        request.setGroupId(10L);
        request.setModuleId(20L);
        request.setDatasourceId(2L);
        request.setName("demo");
        request.setMethod(HttpMethodEnum.GET);
        request.setContentType("application/json");
        request.setPath("/demo");
        request.setOpen(false);
        request.setAlarm(false);
        request.setEngine(ExecuteEngineEnum.SQL);
        request.setContextList(Collections.singletonList("select 1"));
        request.setFormatMap(new ArrayList<DataTypeFormatMapValue>());
        return request;
    }

    private static UpdateHarness wireUpdateService(ApiAssignmentEntity exists) {
        UpdateHarness harness = new UpdateHarness();
        ApiAssignmentService service = new ApiAssignmentService();
        DataTaskTestSupport.setField(service, "apiAssignmentDao", new ApiAssignmentDao() {
            @Override
            public ApiAssignmentEntity getById(Long id, boolean withSql) {
                return exists;
            }

            @Override
            public void update(ApiAssignmentEntity entity) {
                harness.updated.add(entity);
            }
        });
        DataTaskTestSupport.setField(service, "dataSourceDao", new DataSourceDao() {
            @Override
            public DataSourceEntity getById(Long id) {
                return new DataSourceEntity();
            }
        });
        DataTaskTestSupport.setField(service, "postProcessors", new ApiAssignmentPostProcessors(
                Collections.singletonList(new ApiAssignmentPostProcessor() {
                    @Override
                    public void postUpdate(ApiUpdatePostContext context) {
                        harness.fired.add(context);
                    }
                }), new ApplicationEventPublisher() {
            @Override
            public void publishEvent(ApplicationEvent event) {
                harness.events.add(event);
            }

            @Override
            public void publishEvent(Object event) {
                harness.events.add(event);
            }
        }));
        harness.service = service;
        return harness;
    }

    private static final class UpdateHarness {
        final List<ApiAssignmentEntity> updated = new ArrayList<>();
        final List<ApiUpdatePostContext> fired = new ArrayList<>();
        final List<Object> events = new ArrayList<>();
        ApiAssignmentService service;
    }
}
