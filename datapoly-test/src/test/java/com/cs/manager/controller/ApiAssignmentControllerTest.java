// Use of this source code is governed by a BSD-style license
package com.cs.manager.controller;

import com.cs.common.dto.PageResult;
import com.cs.common.dto.ResultEntity;
import com.cs.common.enums.DataTypeFormatEnum;
import com.cs.common.enums.NamingStrategyEnum;
import com.cs.core.dto.*;
import com.cs.core.service.ApiAssignmentService;
import com.cs.core.service.ExportImportService;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.Before;
import org.junit.Test;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.util.*;

import static org.junit.Assert.*;

/**
 * Every controller endpoint is a thin delegation: the service result (or plain success)
 * is wrapped in a {@link ResultEntity} / {@link PageResult} and passed through untouched.
 */
public class ApiAssignmentControllerTest {

    private StubAssignmentService assignmentService;
    private StubExportImportService exportImportService;
    private ApiAssignmentController controller;

    @Before
    public void setUp() {
        assignmentService = new StubAssignmentService();
        exportImportService = new StubExportImportService();
        controller = new ApiAssignmentController();
        com.cs.core.datatask.DataTaskTestSupport.setField(controller, "apiAssignmentService", assignmentService);
        com.cs.core.datatask.DataTaskTestSupport.setField(controller, "exportImportService", exportImportService);
    }

    private static HttpServletResponse response() {
        return (HttpServletResponse) Proxy.newProxyInstance(ApiAssignmentControllerTest.class.getClassLoader(),
                new Class<?>[]{HttpServletResponse.class},
                (p, m, a) -> {
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

    private static MultipartFile file() {
        return (MultipartFile) Proxy.newProxyInstance(ApiAssignmentControllerTest.class.getClassLoader(),
                new Class<?>[]{MultipartFile.class},
                (p, m, a) -> {
                    if ("isEmpty".equals(m.getName())) {
                        return false;
                    }
                    if ("getName".equals(m.getName())) {
                        return "file";
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
    public void completionsDelegates() {
        List<ScriptEditorCompletion> completions = Collections.singletonList(
                ScriptEditorCompletion.builder().caption("foreach").value("for( ){}").build());
        assignmentService.completionsResult = completions;
        ResultEntity result = controller.completions();
        assertEquals(Integer.valueOf(0), result.getCode());
        assertSame(completions, result.getData());
    }

    @Test
    public void responseNamingStrategyListsAllStrategies() {
        ResultEntity result = controller.responseNamingStrategy();
        List<NameValueBaseResponse> data = (List<NameValueBaseResponse>) result.getData();
        assertEquals(NamingStrategyEnum.values().length, data.size());
        assertEquals(NamingStrategyEnum.CAMEL_CASE.name(), data.get(1).getKey());
        assertEquals(NamingStrategyEnum.CAMEL_CASE.getDescription(), data.get(1).getValue());
    }

    @Test
    public void responseTypeFormatListsAllFormats() {
        ResultEntity result = controller.responseTypeFormat();
        List<NameValueRemarkResponse> data = (List<NameValueRemarkResponse>) result.getData();
        assertEquals(DataTypeFormatEnum.values().length, data.size());
        for (NameValueRemarkResponse entry : data) {
            assertNotNull(DataTypeFormatEnum.valueOf(entry.getKey()));
            assertEquals(DataTypeFormatEnum.valueOf(entry.getKey()).getDefault(), entry.getValue());
        }
    }

    @Test
    public void parseDelegatesSql() {
        List<SqlParamParseResponse> parsed = Collections.singletonList(new SqlParamParseResponse("id", false));
        assignmentService.parseResult = parsed;
        ResultEntity result = controller.parse("select 1 from t where id = #{id}");
        assertEquals("select 1 from t where id = #{id}", assignmentService.parsedSql);
        assertSame(parsed, result.getData());
    }

    @Test
    public void debugDelegatesToService() throws IOException {
        ApiDebugExecuteRequest request = new ApiDebugExecuteRequest();
        HttpServletResponse response = response();
        controller.debug(request, response);
        assertSame(request, assignmentService.debugRequest);
        assertSame(response, assignmentService.debugResponse);
    }

    @Test
    public void createReturnsGeneratedId() {
        ApiAssignmentSaveRequest request = new ApiAssignmentSaveRequest();
        assignmentService.createResult = 42L;
        ResultEntity result = controller.create(request);
        assertSame(request, assignmentService.createRequest);
        assertEquals(42L, result.getData());
    }

    @Test
    public void updateReturnsPlainSuccess() {
        ApiAssignmentSaveRequest request = new ApiAssignmentSaveRequest();
        ResultEntity result = controller.update(request);
        assertSame(request, assignmentService.updateRequest);
        assertEquals(Integer.valueOf(0), result.getCode());
        assertNull(result.getData());
    }

    @Test
    public void detailDelegatesId() {
        ApiAssignmentDetailResponse detail = new ApiAssignmentDetailResponse();
        assignmentService.detailResult = detail;
        ResultEntity result = controller.detail(9L);
        assertEquals(Long.valueOf(9L), assignmentService.detailId);
        assertSame(detail, result.getData());
    }

    @Test
    public void deleteDelegatesId() {
        ResultEntity result = controller.delete(5L);
        assertEquals(Long.valueOf(5L), assignmentService.deletedId);
        assertEquals(Integer.valueOf(0), result.getCode());
    }

    @Test
    public void listAllDelegatesSearchRequest() {
        PageResult<ApiAssignmentBaseResponse> page = new PageResult<>();
        assignmentService.listResult = page;
        AssignmentSearchRequest request = new AssignmentSearchRequest();
        assertSame(page, controller.listAll(request));
        assertSame(request, assignmentService.listRequest);
    }

    @Test
    public void searchDelegatesOnlineSearchRequest() {
        PageResult<ApiAssignmentBaseResponse> page = new PageResult<>();
        assignmentService.searchResultPage = page;
        ApiOnlineSearchRequest request = new ApiOnlineSearchRequest();
        assertSame(page, controller.search(request));
        assertSame(request, assignmentService.onlineSearchRequest);
    }

    @Test
    public void updateGroupDelegatesGroupAndIds() {
        List<Long> ids = Arrays.asList(1L, 2L);
        ResultEntity result = controller.updateGroup(6L, ids);
        assertEquals(Long.valueOf(6L), assignmentService.updatedGroupId);
        assertSame(ids, assignmentService.updatedGroupIds);
        assertEquals(Integer.valueOf(0), result.getCode());
    }

    @Test
    public void publishDelegatesRequest() {
        AssignmentPublishRequest request = new AssignmentPublishRequest();
        request.setId(3L);
        ResultEntity result = controller.publish(request);
        assertSame(request, assignmentService.publishRequest);
        assertEquals(Integer.valueOf(0), result.getCode());
    }

    @Test
    public void deployDelegatesIdAndCommit() {
        controller.deploy(4L, 40L);
        assertEquals(Long.valueOf(4L), assignmentService.deployId);
        assertEquals(Long.valueOf(40L), assignmentService.deployCommitId);

        controller.deploy(4L, null);
        assertNull(assignmentService.deployCommitId);
    }

    @Test
    public void retireDelegatesId() {
        controller.retire(8L);
        assertEquals(Long.valueOf(8L), assignmentService.retiredId);
    }

    @Test
    public void exportDelegatesIds() {
        List<Long> ids = Arrays.asList(1L, 2L);
        HttpServletResponse response = response();
        controller.exportAssignments(ids, response);
        assertSame(ids, exportImportService.exportIds);
        assertSame(response, exportImportService.exportResponse);
    }

    @Test
    public void importDelegatesFile() throws IOException {
        MultipartFile upload = file();
        ResultEntity<String> result = controller.importAssignments(upload);
        assertSame(upload, exportImportService.importFile);
        assertEquals(Integer.valueOf(0), result.getCode());
    }

    private static class StubAssignmentService extends ApiAssignmentService {
        List<ScriptEditorCompletion> completionsResult = Collections.emptyList();
        List<SqlParamParseResponse> parseResult = Collections.emptyList();
        Long createResult;
        ApiAssignmentDetailResponse detailResult;
        PageResult<ApiAssignmentBaseResponse> listResult;
        PageResult<ApiAssignmentBaseResponse> searchResultPage;

        String parsedSql;
        ApiDebugExecuteRequest debugRequest;
        HttpServletResponse debugResponse;
        ApiAssignmentSaveRequest createRequest;
        ApiAssignmentSaveRequest updateRequest;
        Long detailId;
        Long deletedId;
        AssignmentSearchRequest listRequest;
        ApiOnlineSearchRequest onlineSearchRequest;
        Long updatedGroupId;
        List<Long> updatedGroupIds;
        AssignmentPublishRequest publishRequest;
        Long deployId;
        Long deployCommitId;
        Long retiredId;

        @Override
        public List<ScriptEditorCompletion> completions() {
            return completionsResult;
        }

        @Override
        public List<SqlParamParseResponse> parseSqlParams(String sqlOrXml) {
            parsedSql = sqlOrXml;
            return parseResult;
        }

        @Override
        public void debugExecute(ApiDebugExecuteRequest request, HttpServletResponse response) {
            debugRequest = request;
            debugResponse = response;
        }

        @Override
        public Long createAssignment(ApiAssignmentSaveRequest request) {
            createRequest = request;
            return createResult;
        }

        @Override
        public void updateAssignment(ApiAssignmentSaveRequest request) {
            updateRequest = request;
        }

        @Override
        public ApiAssignmentDetailResponse detailAssignment(Long id) {
            detailId = id;
            return detailResult;
        }

        @Override
        public void deleteAssignment(Long id) {
            deletedId = id;
        }

        @Override
        public PageResult<ApiAssignmentBaseResponse> listAll(AssignmentSearchRequest request) {
            listRequest = request;
            return listResult;
        }

        @Override
        public PageResult<ApiAssignmentBaseResponse> search(ApiOnlineSearchRequest request) {
            onlineSearchRequest = request;
            return searchResultPage;
        }

        @Override
        public void updateGroup(Long groupId, List<Long> ids) {
            updatedGroupId = groupId;
            updatedGroupIds = ids;
        }

        @Override
        public void publish(AssignmentPublishRequest request) {
            publishRequest = request;
        }

        @Override
        public void deployAssignment(Long id, Long commitId) {
            deployId = id;
            deployCommitId = commitId;
        }

        @Override
        public void retireAssignment(Long id) {
            retiredId = id;
        }
    }

    private static class StubExportImportService extends ExportImportService {
        List<Long> exportIds;
        HttpServletResponse exportResponse;
        MultipartFile importFile;

        @Override
        public void exportAssignments(List<Long> ids, HttpServletResponse response) {
            exportIds = ids;
            exportResponse = response;
        }

        @Override
        public void importAssignments(MultipartFile file) {
            importFile = file;
        }
    }
}
