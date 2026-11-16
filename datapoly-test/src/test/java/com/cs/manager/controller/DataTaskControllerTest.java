// Use of this source code is governed by a BSD-style license
package com.cs.manager.controller;

import com.cs.common.datatask.DataTaskSink;
import com.cs.common.datatask.SinkRequest;
import com.cs.common.datatask.SinkSession;
import com.cs.common.dto.PageResult;
import com.cs.common.dto.ResultEntity;
import com.cs.core.datatask.DataTaskService;
import com.cs.core.datatask.DataTaskSinkRegistry;
import com.cs.core.dto.*;
import com.cs.core.service.ApiAssignmentService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.*;

import static org.junit.Assert.*;

/**
 * Every endpoint is a thin delegation over {@link DataTaskService}: the service result
 * (or plain success) is wrapped untouched, the submit user comes from the request
 * attribute the session interceptor set, and list/jobs default missing paging to 0.
 */
public class DataTaskControllerTest {

    private static class StubService extends DataTaskService {
        DataTaskSaveRequest created;
        DataTaskSaveRequest updated;
        Long deleted;
        Long detailed;
        DataTaskRunRequest previewed;
        DataTaskRunRequest submitted;
        String submittedBy;
        Long jobViewed;
        Long cancelled;
        DataTaskSearchRequest listed;
        DataTaskJobSearchRequest jobsQueried;

        Long createResult = 1L;
        Map<String, Object> previewResult = new LinkedHashMap<>();
        Long submitResult = 8L;
        DataTaskDetailResponse detailResult = DataTaskDetailResponse.builder().build();
        List<DataTaskBaseResponse> listResult = new ArrayList<>();
        DataTaskJobView jobViewResult = DataTaskJobView.builder().build();
        List<DataTaskJobView> jobViewsResult = new ArrayList<>();

        @Override
        public Long create(DataTaskSaveRequest request) {
            created = request;
            return createResult;
        }

        @Override
        public void update(DataTaskSaveRequest request) {
            updated = request;
        }

        @Override
        public void delete(Long id) {
            deleted = id;
        }

        @Override
        public DataTaskDetailResponse detail(Long id) {
            detailed = id;
            return detailResult;
        }

        @Override
        public List<DataTaskBaseResponse> list(DataTaskSearchRequest request) {
            listed = request;
            return listResult;
        }

        @Override
        public Map<String, Object> preview(DataTaskRunRequest request) {
            previewed = request;
            return previewResult;
        }

        @Override
        public Long submit(DataTaskRunRequest request, String submittedBy) {
            submitted = request;
            this.submittedBy = submittedBy;
            return submitResult;
        }

        @Override
        public void cancel(Long jobId) {
            cancelled = jobId;
        }

        @Override
        public DataTaskJobView jobView(Long jobId) {
            jobViewed = jobId;
            return jobViewResult;
        }

        @Override
        public List<DataTaskJobView> jobViews(DataTaskJobSearchRequest request) {
            jobsQueried = request;
            return jobViewsResult;
        }
    }

    private static class StubAssignmentService extends ApiAssignmentService {
        String parsedSql;

        @Override
        public List<SqlParamParseResponse> parseSqlParams(String sqlOrXml) {
            parsedSql = sqlOrXml;
            return Collections.singletonList(new SqlParamParseResponse("dt", Boolean.FALSE));
        }
    }

    private static class NamedSink implements DataTaskSink {
        private final String type;

        NamedSink(String type) {
            this.type = type;
        }

        @Override
        public String type() {
            return type;
        }

        @Override
        public SinkSession openSession(SinkRequest request) {
            throw new UnsupportedOperationException("not needed here");
        }
    }

    private StubService service;
    private StubAssignmentService assignmentService;
    private DataTaskController controller;

    @Before
    public void setUp() {
        service = new StubService();
        assignmentService = new StubAssignmentService();
        controller = new DataTaskController();
        com.cs.core.datatask.DataTaskTestSupport.setField(controller, "dataTaskService", service);
        com.cs.core.datatask.DataTaskTestSupport.setField(controller, "apiAssignmentService", assignmentService);
        com.cs.core.datatask.DataTaskTestSupport.setField(controller, "sinkRegistry",
                new DataTaskSinkRegistry(Arrays.<DataTaskSink>asList(
                        new NamedSink("oss"), new NamedSink("file"))));
    }

    private static HttpServletRequest requestWithUser(final Object username) {
        return (HttpServletRequest) Proxy.newProxyInstance(DataTaskControllerTest.class.getClassLoader(),
                new Class<?>[]{HttpServletRequest.class},
                (p, m, a) -> {
                    if ("getAttribute".equals(m.getName())) {
                        return "username".equals(a[0]) ? username : null;
                    }
                    if ("hashCode".equals(m.getName())) {
                        return System.identityHashCode(p);
                    }
                    if ("equals".equals(m.getName())) {
                        return p == a[0];
                    }
                    Class<?> rt = m.getReturnType();
                    if (rt == boolean.class) {
                        return false;
                    }
                    if (rt == int.class) {
                        return 0;
                    }
                    return null;
                });
    }

    @Test
    public void sinkTypesAreSortedRegistrations() {
        ResultEntity result = controller.sinkTypes();
        assertEquals(Integer.valueOf(0), result.getCode());
        assertEquals(Arrays.asList("file", "oss"), result.getData());
    }

    @Test
    public void parseDelegatesSqlAndUnwrapsList() {
        ResultEntity result = controller.parse("SELECT 1 WHERE dt = #{dt}");
        assertEquals("SELECT 1 WHERE dt = #{dt}", assignmentService.parsedSql);
        List<?> data = (List<?>) result.getData();
        assertEquals(1, data.size());
        assertEquals("dt", ((SqlParamParseResponse) data.get(0)).getName());
    }

    @Test
    public void createUpdateDeleteDetailDelegate() {
        DataTaskSaveRequest save = DataTaskSaveRequest.builder().name("t").build();
        assertEquals(Long.valueOf(1L), controller.create(save).getData());
        assertSame(save, service.created);

        ResultEntity updated = controller.update(save);
        assertEquals(Integer.valueOf(0), updated.getCode());
        assertSame(save, service.updated);

        ResultEntity deleted = controller.delete(3L);
        assertEquals(Integer.valueOf(0), deleted.getCode());
        assertEquals(Long.valueOf(3L), service.deleted);

        assertSame(service.detailResult, controller.detail(4L).getData());
        assertEquals(Long.valueOf(4L), service.detailed);
    }

    @Test
    public void listReturnsPagedResultAndDefaultsMissingPaging() {
        service.listResult = new ArrayList<>(Arrays.asList(
                DataTaskBaseResponse.builder().id(1L).build(),
                DataTaskBaseResponse.builder().id(2L).build()));

        DataTaskSearchRequest unpaged = new DataTaskSearchRequest();
        PageResult<DataTaskBaseResponse> whole = controller.list(unpaged);
        assertSame(service.listResult, whole.getData());
        assertEquals(2, whole.getData().size());
        assertEquals(0, whole.getPagination().getPage());
        assertEquals(0, whole.getPagination().getSize());
        assertEquals(2, whole.getPagination().getTotal());

        DataTaskSearchRequest paged = new DataTaskSearchRequest();
        paged.setPage(2);
        paged.setSize(1);
        PageResult<DataTaskBaseResponse> slice = controller.list(paged);
        assertEquals(1, slice.getData().size());
        assertEquals(Long.valueOf(2L), slice.getData().get(0).getId());
        assertEquals(2, slice.getPagination().getPage());
        assertEquals(1, slice.getPagination().getSize());
    }

    @Test
    public void previewPassesTheViewThrough() {
        service.previewResult.put("rows", Collections.emptyList());
        DataTaskRunRequest request = DataTaskRunRequest.builder().defId(1L).build();

        ResultEntity result = controller.preview(request);

        assertSame(service.previewResult, result.getData());
        assertSame(request, service.previewed);
    }

    @Test
    public void submitUsesTheSessionUsernameOrNull() {
        DataTaskRunRequest request = DataTaskRunRequest.builder().defId(1L).build();

        assertEquals(Long.valueOf(8L), controller.submit(request, requestWithUser("alice")).getData());
        assertSame(request, service.submitted);
        assertEquals("alice", service.submittedBy);

        controller.submit(request, requestWithUser(null));
        assertNull("missing attribute must not become the string 'null'", service.submittedBy);
    }

    @Test
    public void jobAndJobsAndCancelDelegate() {
        assertSame(service.jobViewResult, controller.job(6L).getData());
        assertEquals(Long.valueOf(6L), service.jobViewed);

        DataTaskJobSearchRequest search = new DataTaskJobSearchRequest();
        service.jobViewsResult = Collections.singletonList(DataTaskJobView.builder().id(6L).build());
        PageResult<DataTaskJobView> jobs = controller.jobs(search);
        assertSame(service.jobViewsResult, jobs.getData());
        assertEquals(1, jobs.getPagination().getTotal());
        assertSame(search, service.jobsQueried);

        ResultEntity cancel = controller.cancel(6L);
        assertEquals(Integer.valueOf(0), cancel.getCode());
        assertEquals(Long.valueOf(6L), service.cancelled);
    }
}
