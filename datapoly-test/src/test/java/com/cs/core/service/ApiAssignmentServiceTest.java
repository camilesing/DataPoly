// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.core.service;

import com.cs.common.dto.ApiIdVersion;
import com.cs.common.dto.PageResult;
import com.cs.common.enums.*;
import com.cs.common.exception.*;
import com.cs.core.datatask.DataTaskTestSupport;
import com.cs.core.dto.ApiAssignmentBaseResponse;
import com.cs.core.dto.ApiAssignmentSaveRequest;
import com.cs.core.dto.AssignmentSearchRequest;
import com.cs.core.dto.DataTypeFormatMapValue;
import com.cs.core.extension.ApiAssignmentPostProcessor;
import com.cs.core.extension.ApiAssignmentPostProcessors;
import com.cs.core.extension.ApiAssignmentUpdateEvent;
import com.cs.core.extension.ApiUpdatePostContext;
import com.cs.persistence.dao.ApiAssignmentDao;
import com.cs.persistence.dao.ApiGroupDao;
import com.cs.persistence.dao.ApiModuleDao;
import com.cs.persistence.dao.ApiOnlineDao;
import com.cs.persistence.dao.DataSourceDao;
import com.cs.persistence.entity.ApiAssignmentEntity;
import com.cs.persistence.entity.ApiGroupEntity;
import com.cs.persistence.entity.ApiModuleEntity;
import com.cs.persistence.entity.DataSourceEntity;
import com.cs.persistence.entity.VersionCommitEntity;
import com.github.pagehelper.PageHelper;
import org.junit.*;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;

import java.util.*;

/**
 * Deploy/rollback version ownership validation test (A3): a commit that does not exist or belongs to another API must be rejected,
 * so another API's version content can no longer be deployed/rolled back onto this API, and the NPE 500 is gone.
 * Also covers the postUpdate extension hook of {@code updateAssignment}, and that the list endpoint serves
 * drafts and online interfaces out of the assignment table alone - the {@code online} flag is a tri-state
 * filter over that one source, never a switch to the online snapshot table.
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

    @Test
    public void listAllServesDraftsAndOnlineFromTheAssignmentTableAlone() {
        for (Boolean online : Arrays.asList(null, Boolean.TRUE, Boolean.FALSE)) {
            ListHarness harness = wireListService();
            AssignmentSearchRequest request = new AssignmentSearchRequest();
            request.setGroupId(10L);
            request.setModuleId(20L);
            request.setOpen(Boolean.FALSE);
            request.setSearchText("demo");
            request.setOnline(online);
            request.setPage(1);
            request.setSize(10);

            PageResult<ApiAssignmentBaseResponse> page;
            try {
                page = harness.service.listAll(request);
            } finally {
                // no mapper ran, so nothing cleared the page PageHelper parked on this thread
                PageHelper.clearPage();
            }

            String state = "online=" + online;
            Assert.assertEquals(state, 1, harness.searched.size());
            Assert.assertEquals(state, online, harness.searched.get(0).online);
            Assert.assertEquals(state, Long.valueOf(10L), harness.searched.get(0).groupId);
            Assert.assertEquals(state, Long.valueOf(20L), harness.searched.get(0).moduleId);
            Assert.assertEquals(state, Boolean.FALSE, harness.searched.get(0).open);
            Assert.assertEquals(state, "demo", harness.searched.get(0).searchText);
            // the flag filters one source; it never turns into a read of the online snapshot table
            Assert.assertEquals(state, 0, harness.onlineTableQueries);
            Assert.assertEquals(state, 1, page.getData().size());
            Assert.assertTrue(state, page.getData().get(0).getStatus());
            Assert.assertEquals(state, Integer.valueOf(3), page.getData().get(0).getVersion());
        }
    }

    @Test
    public void assignmentSearchRendersTheOnlineFlagAsATriStateFilter() throws Exception {
        String script = com.cs.persistence.mapper.ApiAssignmentMapper.class
                .getMethod("searchAll", Long.class, Long.class, Boolean.class, String.class, Boolean.class)
                .getAnnotation(org.apache.ibatis.annotations.Select.class).value()[0];
        org.apache.ibatis.scripting.xmltags.XMLLanguageDriver driver =
                new org.apache.ibatis.scripting.xmltags.XMLLanguageDriver();
        org.apache.ibatis.session.Configuration configuration = new org.apache.ibatis.session.Configuration();

        String both = renderAssignmentSearch(driver, configuration, script, null);
        Assert.assertFalse(both, both.contains("api_online"));
        String onlineOnly = renderAssignmentSearch(driver, configuration, script, Boolean.TRUE);
        Assert.assertTrue(onlineOnly, onlineOnly.contains("and exists (select 1 from datapoly_api_online"));
        Assert.assertFalse(onlineOnly, onlineOnly.contains("not exists"));
        String draftsOnly = renderAssignmentSearch(driver, configuration, script, Boolean.FALSE);
        Assert.assertTrue(draftsOnly, draftsOnly.contains("and not exists (select 1 from datapoly_api_online"));
    }

    private static String renderAssignmentSearch(org.apache.ibatis.scripting.xmltags.XMLLanguageDriver driver,
                                                 org.apache.ibatis.session.Configuration configuration,
                                                 String script, Boolean online) {
        Map<String, Object> params = new HashMap<>();
        params.put("groupId", null);
        params.put("moduleId", null);
        params.put("open", null);
        params.put("searchText", null);
        params.put("online", online);
        org.apache.ibatis.mapping.SqlSource sqlSource = driver.createSqlSource(configuration, script, Map.class);
        return sqlSource.getBoundSql(params).getSql().toLowerCase().replaceAll("\\s+", " ");
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

    private static ListHarness wireListService() {
        ListHarness harness = new ListHarness();
        ApiAssignmentService service = new ApiAssignmentService();
        DataTaskTestSupport.setField(service, "apiModuleDao", new ApiModuleDao() {
            @Override
            public List<ApiModuleEntity> listAll() {
                return new ArrayList<>();
            }
        });
        DataTaskTestSupport.setField(service, "apiGroupDao", new ApiGroupDao() {
            @Override
            public List<ApiGroupEntity> listAll() {
                return new ArrayList<>();
            }
        });
        DataTaskTestSupport.setField(service, "apiAssignmentDao", new ApiAssignmentDao() {
            @Override
            public List<ApiAssignmentEntity> searchAll(Long groupId, Long moduleId, Boolean open,
                                                       String searchText, Boolean online) {
                harness.searched.add(new SearchCall(groupId, moduleId, open, searchText, online));
                return Collections.singletonList(ApiAssignmentEntity.builder()
                        .id(1L).name("demo").method(HttpMethodEnum.GET).path("/demo").build());
            }
        });
        DataTaskTestSupport.setField(service, "apiOnlineDao", new ApiOnlineDao() {
            @Override
            public List<ApiIdVersion> filterOnline(List<Long> apiIds) {
                if (apiIds.contains(1L)) {
                    return Collections.singletonList(
                            ApiIdVersion.builder().apiId(1L).commitId(100L).version(3).build());
                }
                return Collections.emptyList();
            }

            @Override
            public List<ApiAssignmentEntity> searchAll(List<Long> groupIds, List<Long> moduleIds, Boolean open,
                                                       String searchText) {
                harness.onlineTableQueries++;
                return Collections.emptyList();
            }
        });
        harness.service = service;
        return harness;
    }

    private static final class SearchCall {
        final Long groupId;
        final Long moduleId;
        final Boolean open;
        final String searchText;
        final Boolean online;

        SearchCall(Long groupId, Long moduleId, Boolean open, String searchText, Boolean online) {
            this.groupId = groupId;
            this.moduleId = moduleId;
            this.open = open;
            this.searchText = searchText;
            this.online = online;
        }
    }

    private static final class ListHarness {
        final List<SearchCall> searched = new ArrayList<>();
        int onlineTableQueries;
        ApiAssignmentService service;
    }

    private static final class UpdateHarness {
        final List<ApiAssignmentEntity> updated = new ArrayList<>();
        final List<ApiUpdatePostContext> fired = new ArrayList<>();
        final List<Object> events = new ArrayList<>();
        ApiAssignmentService service;
    }
}
