// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.core.service;

import com.cs.common.consts.Constants;
import com.cs.common.dto.ApiIdVersion;
import com.cs.common.dto.ItemParam;
import com.cs.common.dto.PageResult;
import com.cs.common.dto.ParamValue;
import com.cs.common.enums.*;
import com.cs.common.exception.*;
import com.cs.core.datatask.DataTaskTestSupport;
import com.cs.core.driver.DriverLoadService;
import com.cs.core.dto.ApiAssignmentBaseResponse;
import com.cs.core.dto.ApiAssignmentDetailResponse;
import com.cs.core.dto.ApiAssignmentSaveRequest;
import com.cs.core.dto.ApiDebugExecuteRequest;
import com.cs.core.dto.ApiOnlineSearchRequest;
import com.cs.core.dto.AssignmentPublishRequest;
import com.cs.core.dto.AssignmentSearchRequest;
import com.cs.core.dto.DataTypeFormatMapValue;
import com.cs.core.dto.EntitySearchRequest;
import com.cs.core.dto.SqlParamParseResponse;
import com.cs.core.dto.VersionCommitResponse;
import com.cs.core.dto.VersionDetailResponse;
import com.cs.core.extension.ApiAssignmentPostProcessor;
import com.cs.core.extension.ApiAssignmentPostProcessors;
import com.cs.core.extension.ApiAssignmentUpdateEvent;
import com.cs.core.extension.ApiDebugPostContext;
import com.cs.core.extension.ApiUpdatePostContext;
import com.cs.persistence.dao.ApiAssignmentDao;
import com.cs.persistence.dao.ApiGroupDao;
import com.cs.persistence.dao.ApiModuleDao;
import com.cs.persistence.dao.ApiOnlineDao;
import com.cs.persistence.dao.DataSourceDao;
import com.cs.persistence.dao.VersionCommitDao;
import com.cs.persistence.entity.ApiAssignmentEntity;
import com.cs.persistence.entity.ApiContextEntity;
import com.cs.persistence.entity.ApiGroupEntity;
import com.cs.persistence.entity.ApiModuleEntity;
import com.cs.persistence.entity.ApiOnlineEntity;
import com.cs.persistence.entity.DataSourceEntity;
import com.cs.persistence.entity.VersionCommitEntity;
import com.cs.persistence.util.JsonUtils;
import com.github.pagehelper.PageHelper;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.*;

import static org.junit.Assert.*;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.env.Environment;

import jakarta.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.*;

/**
 * Deploy/rollback version ownership validation test (A3): a commit that does not exist or belongs to another API must be rejected,
 * so another API's version content can no longer be deployed/rolled back onto this API, and the NPE 500 is gone.
 * Also covers the postUpdate extension hook of {@code updateAssignment}, and that the list endpoint serves
 * drafts and online interfaces out of the assignment table alone - the {@code online} flag is a tri-state
 * filter over that one source, never a switch to the online snapshot table.
 * The {@link Harness} based tests below additionally cover create/update validation, cache key contracts,
 * the GRAPHQL save-time contract, detail/delete/publish/deploy/retire/group updates, version browsing and
 * the debug-execute parameter pipeline (validation, mapping and execution failure reporting).
 */
public class ApiAssignmentServiceTest {

    private static final String VALID_SDL = "type Query {\n"
            + "  users(id: Long): [User]\n"
            + "    @sql(sql: \"\"\"SELECT id, name FROM t_user WHERE 1=1\n"
            + "      <if test=\"id != null\"> AND id = #{id}</if>\"\"\")\n"
            + "}\n"
            + "type User { id: Long  name: String }\n";

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
    public void updateAssignmentStoresPlainSqlVerbatim() {
        ApiAssignmentEntity exists = ApiAssignmentEntity.builder()
                .id(1L).method(HttpMethodEnum.GET).path("/demo").build();
        UpdateHarness harness = wireUpdateService(exists);
        // an author's statement, dynamic tags and comparison operators both written as they read
        String sql = "select id from t_order <where>"
                + "<if test='dt != null'> and dt >= #{dt} </if>"
                + "<if test='amount != null and amount <= 100'> and amount <= #{amount} </if>"
                + "</where> and name like 'A&B%'";
        ApiAssignmentSaveRequest request = validUpdateRequest();
        request.setContextList(Collections.singletonList(sql));

        harness.service.updateAssignment(request);

        Assert.assertEquals(1, harness.updated.size());
        Assert.assertEquals(sql, harness.updated.get(0).getContextList().get(0).getSqlText());
    }

    @Test
    public void createAssignmentAcceptsGraphqlVariablesObjectWithoutChildren() {
        CreateHarness harness = wireCreateService();

        harness.service.createAssignment(validGraphqlCreateRequest());

        Assert.assertEquals(1, harness.inserted.size());
        List<ItemParam> stored = harness.inserted.get(0).getParams();
        Assert.assertEquals(2, stored.size());
        Assert.assertEquals(Constants.PARAM_GRAPHQL_QUERY, stored.get(0).getName());
        Assert.assertEquals(ParamTypeEnum.STRING, stored.get(0).getType());
        Assert.assertEquals(Constants.PARAM_GRAPHQL_VARIABLES, stored.get(1).getName());
        Assert.assertEquals(ParamTypeEnum.OBJECT, stored.get(1).getType());
        Assert.assertTrue(stored.get(1).getChildren().isEmpty());
    }

    @Test
    public void updateAssignmentAcceptsGraphqlVariablesObjectWithoutChildren() {
        ApiAssignmentEntity exists = ApiAssignmentEntity.builder()
                .id(1L).method(HttpMethodEnum.POST).path("/demo").build();
        UpdateHarness harness = wireUpdateService(exists);

        harness.service.updateAssignment(validGraphqlUpdateRequest());

        Assert.assertEquals(1, harness.updated.size());
        List<ItemParam> stored = harness.updated.get(0).getParams();
        Assert.assertEquals(2, stored.size());
        Assert.assertEquals(ParamTypeEnum.OBJECT, stored.get(1).getType());
        Assert.assertTrue(stored.get(1).getChildren().isEmpty());
    }

    @Test
    public void createAssignmentStillRejectsObjectParamWithoutChildrenForSqlEngine() {
        CreateHarness harness = wireCreateService();

        ApiAssignmentSaveRequest request = validGraphqlCreateRequest();
        request.setEngine(ExecuteEngineEnum.SQL);
        request.setParams(graphqlSubmittedParams());
        try {
            harness.service.createAssignment(request);
            Assert.fail("Expected CommonException for object param without children.");
        } catch (CommonException e) {
            Assert.assertEquals(ResponseErrorCode.ERROR_INVALID_ARGUMENT, e.getCode());
        }
        Assert.assertTrue(harness.inserted.isEmpty());
    }

    @Test
    public void parseSqlParamsAcceptsPlainComparisonOperators() {
        List<SqlParamParseResponse> parsed = new ApiAssignmentService()
                .parseSqlParams("select id from t_order "
                        + "where dt >= #{dt} and amount <= #{amount} and status <> #{status} and name like 'A&B%'");

        List<String> names = new ArrayList<>();
        for (SqlParamParseResponse response : parsed) {
            names.add(response.getName());
        }
        Assert.assertEquals(Arrays.asList("dt", "amount", "status"), names);
    }

    @Test
    public void parseSqlParamsNestsObjectChildrenUnderTheirParent() {
        List<SqlParamParseResponse> parsed = new ApiAssignmentService()
                .parseSqlParams("select * from t_order where user = #{user.name} and id = #{id}");

        SqlParamParseResponse parent = null;
        SqlParamParseResponse plain = null;
        for (SqlParamParseResponse response : parsed) {
            if ("user".equals(response.getName())) {
                parent = response;
            }
            if ("id".equals(response.getName())) {
                plain = response;
            }
        }
        Assert.assertNotNull("dotted parameter must introduce its object root", parent);
        Assert.assertEquals(1, parent.getChildren().size());
        Assert.assertEquals("name", parent.getChildren().get(0).getName());
        Assert.assertNotNull(plain);
        Assert.assertTrue("a plain parameter must not adopt children", plain.getChildren().isEmpty());
    }

    @Test
    public void completionsExposeScriptSyntaxAndCommentedModuleMethods() {
        List<com.cs.core.dto.ScriptEditorCompletion> completions = new ApiAssignmentService().completions();

        Assert.assertFalse(completions.isEmpty());
        boolean hasSyntax = false;
        boolean hasDbModule = false;
        for (com.cs.core.dto.ScriptEditorCompletion completion : completions) {
            if ("foreach".equals(completion.getCaption())) {
                hasSyntax = true;
            }
            if (null != completion.getCaption() && completion.getCaption().startsWith("db.")) {
                hasDbModule = true;
                Assert.assertTrue("method completion value should be insertable",
                        completion.getValue().startsWith("db."));
            }
        }
        Assert.assertTrue("script syntax completions must be served", hasSyntax);
        Assert.assertTrue("@Comment annotated module methods must be served", hasDbModule);
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

    /////////////////////////////////////////////////////////////////////////////////////
    // createAssignment
    /////////////////////////////////////////////////////////////////////////////////////

    @Test
    public void createAssignmentRequiresPathAndMethod() {
        Harness harness = new Harness();
        ApiAssignmentSaveRequest request = validCreateRequest();
        request.setPath(" ");
        try {
            harness.service.createAssignment(request);
            Assert.fail("blank path must be rejected");
        } catch (CommonException e) {
            Assert.assertEquals(ResponseErrorCode.ERROR_INVALID_ARGUMENT, e.getCode());
        }
        Assert.assertTrue(harness.assignment.inserted.isEmpty());
    }

    @Test
    public void createAssignmentRejectsDuplicateUniqueKey() {
        Harness harness = new Harness();
        harness.assignment.byUk = ApiAssignmentEntity.builder().id(5L).build();
        try {
            harness.service.createAssignment(validCreateRequest());
            Assert.fail("duplicate method+path must be rejected");
        } catch (CommonException e) {
            Assert.assertEquals(ResponseErrorCode.ERROR_RESOURCE_ALREADY_EXISTS, e.getCode());
        }
    }

    @Test
    public void createAssignmentRejectsBodyParamOnGet() {
        Harness harness = new Harness();
        ApiAssignmentSaveRequest request = validCreateRequest();
        request.setMethod(HttpMethodEnum.GET);
        com.cs.common.dto.ItemParam bodyParam = new com.cs.common.dto.ItemParam();
        bodyParam.setName("body");
        bodyParam.setType(ParamTypeEnum.STRING);
        bodyParam.setLocation(ParamLocationEnum.REQUEST_BODY);
        request.setParams(new ArrayList<>(Collections.singletonList(bodyParam)));
        try {
            harness.service.createAssignment(request);
            Assert.fail("GET with body param must be rejected");
        } catch (CommonException e) {
            Assert.assertEquals(ResponseErrorCode.ERROR_INVALID_ARGUMENT, e.getCode());
        }
    }

    @Test
    public void createAssignmentRejectsInvalidItemParam() {
        Harness harness = new Harness();
        ApiAssignmentSaveRequest request = validCreateRequest();
        com.cs.common.dto.ItemParam unnamed = new com.cs.common.dto.ItemParam();
        unnamed.setName(" ");
        request.setParams(new ArrayList<>(Collections.singletonList(unnamed)));
        try {
            harness.service.createAssignment(request);
            Assert.fail("blank param name must be rejected");
        } catch (CommonException e) {
            Assert.assertEquals(ResponseErrorCode.ERROR_INTERNAL_ERROR, e.getCode());
        }
    }

    @Test
    public void createAssignmentRejectsInvalidOutputParam() {
        Harness harness = new Harness();
        ApiAssignmentSaveRequest request = validCreateRequest();
        com.cs.common.dto.OutParam output = new com.cs.common.dto.OutParam();
        output.setName(" ");
        request.setOutputs(new ArrayList<>(Collections.singletonList(output)));
        try {
            harness.service.createAssignment(request);
            Assert.fail("blank output name must be rejected");
        } catch (CommonException e) {
            Assert.assertEquals(ResponseErrorCode.ERROR_INTERNAL_ERROR, e.getCode());
        }
    }

    @Test
    public void createAssignmentRejectsUnknownDatasource() {
        Harness harness = new Harness();
        harness.dataSource.byId = null;
        try {
            harness.service.createAssignment(validCreateRequest());
            Assert.fail("missing datasource must be rejected");
        } catch (CommonException e) {
            Assert.assertEquals(ResponseErrorCode.ERROR_INVALID_ARGUMENT, e.getCode());
        }

        ApiAssignmentSaveRequest nullDs = validCreateRequest();
        nullDs.setDatasourceId(null);
        try {
            harness.service.createAssignment(nullDs);
            Assert.fail("null datasource must be rejected");
        } catch (CommonException e) {
            Assert.assertEquals(ResponseErrorCode.ERROR_INVALID_ARGUMENT, e.getCode());
        }
    }

    @Test
    public void createAssignmentRequiresContextList() {
        Harness harness = new Harness();
        ApiAssignmentSaveRequest request = validCreateRequest();
        request.setContextList(Collections.emptyList());
        try {
            harness.service.createAssignment(request);
            Assert.fail("empty context list must be rejected");
        } catch (CommonException e) {
            Assert.assertEquals(ResponseErrorCode.ERROR_INVALID_ARGUMENT, e.getCode());
        }
    }

    @Test
    public void createAssignmentGraphqlRequiresSingleContext() {
        Harness harness = new Harness();
        ApiAssignmentSaveRequest request = validGraphqlRequest();
        request.setContextList(Arrays.asList(VALID_SDL, VALID_SDL));
        try {
            harness.service.createAssignment(request);
            Assert.fail("two SDL documents must be rejected");
        } catch (CommonException e) {
            Assert.assertEquals(ResponseErrorCode.ERROR_INVALID_ARGUMENT, e.getCode());
        }
    }

    @Test
    public void createAssignmentGraphqlRequiresPost() {
        Harness harness = new Harness();
        ApiAssignmentSaveRequest request = validGraphqlRequest();
        request.setMethod(HttpMethodEnum.GET);
        try {
            harness.service.createAssignment(request);
            Assert.fail("non-POST graphql must be rejected");
        } catch (CommonException e) {
            Assert.assertEquals(ResponseErrorCode.ERROR_INVALID_ARGUMENT, e.getCode());
        }
    }

    @Test
    public void createAssignmentGraphqlRejectsInvalidSdl() {
        Harness harness = new Harness();
        ApiAssignmentSaveRequest request = validGraphqlRequest();
        request.setContextList(Collections.singletonList("type Query { broken"));
        try {
            harness.service.createAssignment(request);
            Assert.fail("invalid SDL must be rejected");
        } catch (CommonException e) {
            Assert.assertEquals(ResponseErrorCode.ERROR_INVALID_ARGUMENT, e.getCode());
        }
    }

    @Test
    public void createAssignmentGraphqlReplacesParamsWithFixedQueryAndVariables() {
        Harness harness = new Harness();
        ApiAssignmentSaveRequest request = validGraphqlRequest();
        com.cs.common.dto.ItemParam custom = new com.cs.common.dto.ItemParam();
        custom.setName("custom");
        custom.setType(ParamTypeEnum.STRING);
        custom.setLocation(ParamLocationEnum.REQUEST_FORM);
        request.setParams(new ArrayList<>(Collections.singletonList(custom)));

        Long id = harness.service.createAssignment(request);

        Assert.assertNotNull(id);
        Assert.assertEquals(1, harness.assignment.inserted.size());
        List<com.cs.common.dto.ItemParam> params = harness.assignment.inserted.get(0).getParams();
        Assert.assertEquals(2, params.size());
        Assert.assertEquals("query", params.get(0).getName());
        Assert.assertEquals(ParamTypeEnum.STRING, params.get(0).getType());
        Assert.assertEquals(ParamLocationEnum.REQUEST_BODY, params.get(0).getLocation());
        Assert.assertEquals(Boolean.TRUE, params.get(0).getRequired());
        Assert.assertEquals("variables", params.get(1).getName());
        Assert.assertEquals(ParamTypeEnum.OBJECT, params.get(1).getType());
        Assert.assertEquals(Boolean.FALSE, params.get(1).getRequired());
    }

    @Test
    public void createAssignmentAutoCacheRequiresPositiveExpireSeconds() {
        Harness harness = new Harness();
        ApiAssignmentSaveRequest request = validCreateRequest();
        request.setCacheKeyType(CacheKeyTypeEnum.AUTO);
        request.setCacheExpireSeconds(0L);
        try {
            harness.service.createAssignment(request);
            Assert.fail("cache without expire seconds must be rejected");
        } catch (CommonException e) {
            Assert.assertEquals(ResponseErrorCode.ERROR_INVALID_ARGUMENT, e.getCode());
        }

        request.setCacheExpireSeconds(null);
        try {
            harness.service.createAssignment(request);
            Assert.fail("null cache expire seconds must be rejected");
        } catch (CommonException e) {
            Assert.assertEquals(ResponseErrorCode.ERROR_INVALID_ARGUMENT, e.getCode());
        }
    }

    @Test
    public void createAssignmentSpelCacheRequiresExpression() {
        Harness harness = new Harness();
        ApiAssignmentSaveRequest request = validCreateRequest();
        request.setCacheKeyType(CacheKeyTypeEnum.SPEL);
        request.setCacheExpireSeconds(60L);
        request.setCacheKeyExpr(" ");
        try {
            harness.service.createAssignment(request);
            Assert.fail("SPEL cache without expression must be rejected");
        } catch (CommonException e) {
            Assert.assertEquals(ResponseErrorCode.ERROR_INVALID_ARGUMENT, e.getCode());
        }
    }

    @Test
    public void createAssignmentAutoCacheDropsExpressionAndKeepsExpire() {
        Harness harness = new Harness();
        ApiAssignmentSaveRequest request = validCreateRequest();
        request.setCacheKeyType(CacheKeyTypeEnum.AUTO);
        request.setCacheExpireSeconds(120L);
        request.setCacheKeyExpr("#{stray}");

        harness.service.createAssignment(request);

        ApiAssignmentEntity saved = harness.assignment.inserted.get(0);
        Assert.assertEquals(CacheKeyTypeEnum.AUTO, saved.getCacheKeyType());
        Assert.assertNull("AUTO derives its key, a submitted expression must not persist", saved.getCacheKeyExpr());
        Assert.assertEquals(Long.valueOf(120L), saved.getCacheExpireSeconds());
    }

    @Test
    public void createAssignmentWithoutCacheZeroesExpireAndExpression() {
        Harness harness = new Harness();
        ApiAssignmentSaveRequest request = validCreateRequest();
        request.setCacheKeyType(null);
        request.setCacheExpireSeconds(999L);
        request.setCacheKeyExpr("#{stray}");

        Long id = harness.service.createAssignment(request);

        Assert.assertNotNull(id);
        ApiAssignmentEntity saved = harness.assignment.inserted.get(0);
        Assert.assertEquals(CacheKeyTypeEnum.NONE, saved.getCacheKeyType());
        Assert.assertNull(saved.getCacheKeyExpr());
        Assert.assertEquals(Long.valueOf(0L), saved.getCacheExpireSeconds());
    }

    @Test
    public void createAssignmentSpelKeepsExpression() {
        Harness harness = new Harness();
        ApiAssignmentSaveRequest request = validCreateRequest();
        request.setCacheKeyType(CacheKeyTypeEnum.SPEL);
        request.setCacheExpireSeconds(60L);
        request.setCacheKeyExpr("#user.id");

        harness.service.createAssignment(request);

        ApiAssignmentEntity saved = harness.assignment.inserted.get(0);
        Assert.assertEquals("#user.id", saved.getCacheKeyExpr());
        Assert.assertEquals(Long.valueOf(60L), saved.getCacheExpireSeconds());
    }

    @Test
    public void createAssignmentStripsLeadingSlashesAppliesDefaultsAndMapsFields() {
        Harness harness = new Harness();
        ApiAssignmentSaveRequest request = validCreateRequest();
        request.setPath("//demo");
        request.setOpen(null);
        request.setAlarm(null);
        request.setFlowStatus(null);
        request.setNamingStrategy(null);
        request.setCacheKeyType(null);
        request.setContextList(Arrays.asList("select 1", " "));
        request.setFormatMap(new ArrayList<>(Collections.singletonList(
                DataTypeFormatMapValue.builder().key(DataTypeFormatEnum.DATE).value("yyyy-MM-dd").build())));

        Long id = harness.service.createAssignment(request);

        ApiAssignmentEntity saved = harness.assignment.inserted.get(0);
        Assert.assertEquals(id, saved.getId());
        Assert.assertEquals("demo", saved.getPath());
        Assert.assertEquals(Boolean.FALSE, saved.getOpen());
        Assert.assertEquals(Boolean.FALSE, saved.getAlarm());
        Assert.assertEquals(Boolean.FALSE, saved.getFlowStatus());
        Assert.assertEquals(NamingStrategyEnum.CAMEL_CASE, saved.getNamingStrategy());
        Assert.assertEquals(CacheKeyTypeEnum.NONE, saved.getCacheKeyType());
        Assert.assertEquals(1, saved.getContextList().size());
        Assert.assertEquals("select 1", saved.getContextList().get(0).getSqlText());
        Assert.assertEquals(Collections.singletonMap(DataTypeFormatEnum.DATE, "yyyy-MM-dd"), saved.getResponseFormat());
        Assert.assertEquals(Long.valueOf(2L), saved.getDatasourceId());
        Assert.assertEquals(HttpMethodEnum.POST, saved.getMethod());
    }

    @Test
    public void createAssignmentAcceptsQueryParamsAndOutputsOnGet() {
        Harness harness = new Harness();
        ApiAssignmentSaveRequest request = validCreateRequest();
        request.setMethod(HttpMethodEnum.GET);
        com.cs.common.dto.ItemParam queryParam = new com.cs.common.dto.ItemParam();
        queryParam.setName("id");
        queryParam.setType(ParamTypeEnum.LONG);
        queryParam.setLocation(ParamLocationEnum.REQUEST_FORM);
        request.setParams(new ArrayList<>(Collections.singletonList(queryParam)));
        com.cs.common.dto.OutParam output = new com.cs.common.dto.OutParam();
        output.setName("count");
        output.setType(ParamTypeEnum.LONG);
        request.setOutputs(new ArrayList<>(Collections.singletonList(output)));

        harness.service.createAssignment(request);

        ApiAssignmentEntity saved = harness.assignment.inserted.get(0);
        assertEquals(1, saved.getParams().size());
        assertEquals("id", saved.getParams().get(0).getName());
        assertEquals(1, saved.getOutputs().size());
        assertEquals("count", saved.getOutputs().get(0).getName());
    }

    @Test
    public void createAssignmentMergesDuplicateFormatKeysFirstWins() {
        Harness harness = new Harness();
        ApiAssignmentSaveRequest request = validCreateRequest();
        request.setFormatMap(new ArrayList<>(Arrays.asList(
                DataTypeFormatMapValue.builder().key(DataTypeFormatEnum.DATE).value("yyyy-MM-dd").build(),
                DataTypeFormatMapValue.builder().key(DataTypeFormatEnum.DATE).value("yyyy/MM/dd").build())));

        harness.service.createAssignment(request);

        ApiAssignmentEntity saved = harness.assignment.inserted.get(0);
        assertEquals("duplicate format keys keep the first value",
                Collections.singletonMap(DataTypeFormatEnum.DATE, "yyyy-MM-dd"), saved.getResponseFormat());
    }

    @Test
    public void createAssignmentOnScriptEngineSkipsSqlDollarWarning() {
        Harness harness = new Harness();
        ApiAssignmentSaveRequest request = validCreateRequest();
        request.setEngine(ExecuteEngineEnum.SCRIPT);
        request.setContextList(Collections.singletonList("return db.selectOne('select ${x} from t')"));

        Long id = harness.service.createAssignment(request);

        // the ${} warning only applies to SQL/GRAPHQL engines; SCRIPT is saved silently
        Assert.assertNotNull(id);
        assertEquals(1, harness.assignment.inserted.size());
    }

    @Test
    public void createAssignmentWarnsOnDollarSubstitutionForPrivateApisToo() {
        Harness harness = new Harness();
        ApiAssignmentSaveRequest request = validCreateRequest();
        request.setOpen(false);
        request.setContextList(Collections.singletonList("select * from t where name = '${name}'"));

        Long id = harness.service.createAssignment(request);

        Assert.assertNotNull(id);
        assertEquals(1, harness.assignment.inserted.size());
    }

    @Test
    public void createAssignmentWarnsButAcceptsDollarSubstitution() {
        Harness harness = new Harness();
        ApiAssignmentSaveRequest request = validCreateRequest();
        request.setOpen(true);
        request.setContextList(Collections.singletonList("select * from t where name = '${name}'"));

        Long id = harness.service.createAssignment(request);

        // warn-only: the row is still saved, execution time enforces the policy
        Assert.assertNotNull(id);
        Assert.assertEquals(1, harness.assignment.inserted.size());
    }

    /////////////////////////////////////////////////////////////////////////////////////
    // updateAssignment (validation beyond what the wireUpdateService tests cover)
    /////////////////////////////////////////////////////////////////////////////////////

    @Test
    public void updateAssignmentRequiresExistingRow() {
        Harness harness = new Harness();
        harness.assignment.byId = null;
        try {
            harness.service.updateAssignment(validUpdateRequest());
            Assert.fail("missing assignment must be rejected");
        } catch (CommonException e) {
            Assert.assertEquals(ResponseErrorCode.ERROR_RESOURCE_NOT_EXISTS, e.getCode());
        }
    }

    @Test
    public void updateAssignmentRejectsPathChange() {
        Harness harness = new Harness();
        harness.assignment.byId = ApiAssignmentEntity.builder()
                .id(1L).method(HttpMethodEnum.GET).path("/other").build();
        try {
            harness.service.updateAssignment(validUpdateRequest());
            Assert.fail("path change must be rejected");
        } catch (CommonException e) {
            Assert.assertEquals(ResponseErrorCode.ERROR_INVALID_ARGUMENT, e.getCode());
        }
    }

    @Test
    public void updateAssignmentRequiresContextList() {
        Harness harness = new Harness();
        harness.assignment.byId = ApiAssignmentEntity.builder()
                .id(1L).method(HttpMethodEnum.GET).path("/demo").build();
        ApiAssignmentSaveRequest request = validUpdateRequest();
        request.setContextList(Collections.emptyList());
        try {
            harness.service.updateAssignment(request);
            Assert.fail("empty context list must be rejected");
        } catch (CommonException e) {
            Assert.assertEquals(ResponseErrorCode.ERROR_INVALID_ARGUMENT, e.getCode());
        }
    }

    @Test
    public void updateAssignmentRejectsUnknownDatasource() {
        Harness harness = new Harness();
        harness.assignment.byId = ApiAssignmentEntity.builder()
                .id(1L).method(HttpMethodEnum.GET).path("/demo").build();
        harness.dataSource.byId = null;
        try {
            harness.service.updateAssignment(validUpdateRequest());
            Assert.fail("missing datasource must be rejected");
        } catch (CommonException e) {
            Assert.assertEquals(ResponseErrorCode.ERROR_INVALID_ARGUMENT, e.getCode());
        }
    }

    @Test
    public void updateAssignmentRejectsCacheWithoutExpire() {
        Harness harness = new Harness();
        harness.assignment.byId = ApiAssignmentEntity.builder()
                .id(1L).method(HttpMethodEnum.GET).path("/demo").build();
        ApiAssignmentSaveRequest request = validUpdateRequest();
        request.setCacheKeyType(CacheKeyTypeEnum.AUTO);
        request.setCacheExpireSeconds(0L);
        try {
            harness.service.updateAssignment(request);
            Assert.fail("cache without expire seconds must be rejected");
        } catch (CommonException e) {
            Assert.assertEquals(ResponseErrorCode.ERROR_INVALID_ARGUMENT, e.getCode());
        }
    }

    @Test
    public void updateAssignmentRequiresPathAndMethod() {
        Harness harness = new Harness();
        ApiAssignmentSaveRequest request = validUpdateRequest();
        request.setPath("");
        try {
            harness.service.updateAssignment(request);
            Assert.fail("blank path must be rejected on update");
        } catch (CommonException e) {
            Assert.assertEquals(ResponseErrorCode.ERROR_INVALID_ARGUMENT, e.getCode());
        }
    }

    @Test
    public void updateAssignmentRejectsBodyParamOnGet() {
        Harness harness = new Harness();
        harness.assignment.byId = ApiAssignmentEntity.builder()
                .id(1L).method(HttpMethodEnum.GET).path("/demo").build();
        ApiAssignmentSaveRequest request = validUpdateRequest();
        com.cs.common.dto.ItemParam bodyParam = new com.cs.common.dto.ItemParam();
        bodyParam.setName("body");
        bodyParam.setType(ParamTypeEnum.STRING);
        bodyParam.setLocation(ParamLocationEnum.REQUEST_BODY);
        request.setParams(new ArrayList<>(Collections.singletonList(bodyParam)));
        try {
            harness.service.updateAssignment(request);
            Assert.fail("GET with body param must be rejected on update");
        } catch (CommonException e) {
            Assert.assertEquals(ResponseErrorCode.ERROR_INVALID_ARGUMENT, e.getCode());
        }
    }

    @Test
    public void updateAssignmentAcceptsValidParamsOutputsAndCacheConfig() {
        Harness harness = new Harness();
        harness.assignment.byId = ApiAssignmentEntity.builder()
                .id(1L).method(HttpMethodEnum.GET).path("/demo").build();
        ApiAssignmentSaveRequest request = validUpdateRequest();
        com.cs.common.dto.ItemParam queryParam = new com.cs.common.dto.ItemParam();
        queryParam.setName("id");
        queryParam.setType(ParamTypeEnum.LONG);
        queryParam.setLocation(ParamLocationEnum.REQUEST_FORM);
        request.setParams(new ArrayList<>(Collections.singletonList(queryParam)));
        com.cs.common.dto.OutParam output = new com.cs.common.dto.OutParam();
        output.setName("count");
        output.setType(ParamTypeEnum.LONG);
        request.setOutputs(new ArrayList<>(Collections.singletonList(output)));
        request.setCacheKeyType(CacheKeyTypeEnum.SPEL);
        request.setCacheExpireSeconds(60L);
        request.setCacheKeyExpr("#id");
        request.setFormatMap(new ArrayList<>(Arrays.asList(
                DataTypeFormatMapValue.builder().key(DataTypeFormatEnum.DATE).value("yyyy-MM-dd").build(),
                DataTypeFormatMapValue.builder().key(DataTypeFormatEnum.DATE).value("yyyy/MM/dd").build())));

        harness.service.updateAssignment(request);

        ApiAssignmentEntity saved = harness.assignment.updated.get(0);
        assertEquals(CacheKeyTypeEnum.SPEL, saved.getCacheKeyType());
        assertEquals("#id", saved.getCacheKeyExpr());
        assertEquals(Long.valueOf(60L), saved.getCacheExpireSeconds());
        assertEquals(1, saved.getParams().size());
        assertEquals(1, saved.getOutputs().size());
        assertEquals(Collections.singletonMap(DataTypeFormatEnum.DATE, "yyyy-MM-dd"), saved.getResponseFormat());
    }

    @Test
    public void updateAssignmentSpelCacheRequiresExpression() {
        Harness harness = new Harness();
        harness.assignment.byId = ApiAssignmentEntity.builder()
                .id(1L).method(HttpMethodEnum.GET).path("/demo").build();
        ApiAssignmentSaveRequest request = validUpdateRequest();
        request.setCacheKeyType(CacheKeyTypeEnum.SPEL);
        request.setCacheExpireSeconds(60L);
        request.setCacheKeyExpr("");
        try {
            harness.service.updateAssignment(request);
            Assert.fail("SPEL cache without expression must be rejected on update");
        } catch (CommonException e) {
            Assert.assertEquals(ResponseErrorCode.ERROR_INVALID_ARGUMENT, e.getCode());
        }
    }

    @Test
    public void updateAssignmentAutoCacheDropsExpression() {
        Harness harness = new Harness();
        harness.assignment.byId = ApiAssignmentEntity.builder()
                .id(1L).method(HttpMethodEnum.GET).path("/demo").build();
        ApiAssignmentSaveRequest request = validUpdateRequest();
        request.setCacheKeyType(CacheKeyTypeEnum.AUTO);
        request.setCacheExpireSeconds(60L);
        request.setCacheKeyExpr("#{stray}");

        harness.service.updateAssignment(request);

        ApiAssignmentEntity saved = harness.assignment.updated.get(0);
        Assert.assertNull("AUTO derives its key, the stray expression must not persist", saved.getCacheKeyExpr());
        Assert.assertEquals(CacheKeyTypeEnum.AUTO, saved.getCacheKeyType());
    }

    @Test
    public void updateAssignmentGraphqlValidatesSdl() {
        Harness harness = new Harness();
        harness.assignment.byId = ApiAssignmentEntity.builder()
                .id(1L).method(HttpMethodEnum.POST).path("/demo").build();
        ApiAssignmentSaveRequest request = validUpdateRequest();
        request.setMethod(HttpMethodEnum.POST);
        request.setEngine(ExecuteEngineEnum.GRAPHQL);
        request.setContextList(Collections.singletonList("type Query {"));
        try {
            harness.service.updateAssignment(request);
            Assert.fail("invalid SDL must be rejected on update");
        } catch (CommonException e) {
            Assert.assertEquals(ResponseErrorCode.ERROR_INVALID_ARGUMENT, e.getCode());
        }
    }

    @Test
    public void updateAssignmentGraphqlReplacesParamsOnValidSdl() {
        Harness harness = new Harness();
        harness.assignment.byId = ApiAssignmentEntity.builder()
                .id(1L).method(HttpMethodEnum.POST).path("/demo").build();
        ApiAssignmentSaveRequest request = validUpdateRequest();
        request.setMethod(HttpMethodEnum.POST);
        request.setEngine(ExecuteEngineEnum.GRAPHQL);
        request.setContextList(Collections.singletonList(VALID_SDL));

        harness.service.updateAssignment(request);

        ApiAssignmentEntity saved = harness.assignment.updated.get(0);
        assertEquals("a valid SDL update stores the fixed query/variables params",
                2, saved.getParams().size());
        assertEquals("query", saved.getParams().get(0).getName());
    }

    /////////////////////////////////////////////////////////////////////////////////////
    // detail / delete
    /////////////////////////////////////////////////////////////////////////////////////

    @Test
    public void detailAssignmentMissingThrows() {
        Harness harness = new Harness();
        harness.assignment.byId = null;
        try {
            harness.service.detailAssignment(1L);
            Assert.fail("missing assignment must be rejected");
        } catch (CommonException e) {
            Assert.assertEquals(ResponseErrorCode.ERROR_RESOURCE_NOT_EXISTS, e.getCode());
        }
    }

    @Test
    public void detailAssignmentOnlineCarriesStatusVersionAndCommit() {
        Harness harness = new Harness();
        harness.assignment.byId = assignmentEntityWithFullState();
        harness.online.filterOnlineSingle = ApiIdVersion.builder().apiId(1L).commitId(9L).version(5).build();

        ApiAssignmentDetailResponse detail = harness.service.detailAssignment(1L);

        Assert.assertTrue(detail.getStatus());
        Assert.assertEquals(Integer.valueOf(5), detail.getVersion());
        Assert.assertEquals(Long.valueOf(9L), detail.getCommitId());
        Assert.assertEquals(1, detail.getSqlList().size());
        Assert.assertEquals("select 1", detail.getSqlList().get(0).getSqlText());
        Assert.assertEquals(1, detail.getFormatMap().size());
        Assert.assertEquals(DataTypeFormatEnum.DATE, detail.getFormatMap().get(0).getKey());
        Assert.assertNotNull(detail.getFormatMap().get(0).getRemark());
    }

    @Test
    public void detailAssignmentOfflineHasNoVersion() {
        Harness harness = new Harness();
        harness.assignment.byId = assignmentEntityWithFullState();
        harness.online.filterOnlineSingle = null;

        ApiAssignmentDetailResponse detail = harness.service.detailAssignment(1L);

        Assert.assertFalse(detail.getStatus());
        Assert.assertNull(detail.getVersion());
        Assert.assertNull(detail.getCommitId());
    }

    @Test
    public void deleteAssignmentMissingIsNoOp() {
        Harness harness = new Harness();
        harness.assignment.byId = null;
        harness.service.deleteAssignment(1L);
        Assert.assertTrue(harness.assignment.deleted.isEmpty());
    }

    @Test
    public void deleteAssignmentOnlineIsRejected() {
        Harness harness = new Harness();
        harness.assignment.byId = ApiAssignmentEntity.builder().id(1L).build();
        harness.online.byApiId = ApiAssignmentEntity.builder().id(1L).build();
        try {
            harness.service.deleteAssignment(1L);
            Assert.fail("online assignment must not be deletable");
        } catch (CommonException e) {
            Assert.assertEquals(ResponseErrorCode.ERROR_RESOURCE_ALREADY_USED, e.getCode());
        }
        Assert.assertTrue(harness.assignment.deleted.isEmpty());
    }

    @Test
    public void deleteAssignmentRemovesOfflineRow() {
        Harness harness = new Harness();
        harness.assignment.byId = ApiAssignmentEntity.builder().id(1L).build();
        harness.service.deleteAssignment(1L);
        Assert.assertEquals(Collections.singletonList(1L), harness.assignment.deleted);
    }

    /////////////////////////////////////////////////////////////////////////////////////
    // publish / deploy / retire
    /////////////////////////////////////////////////////////////////////////////////////

    @Test
    public void publishRequiresExistingAssignment() {
        Harness harness = new Harness();
        harness.assignment.byId = null;
        AssignmentPublishRequest request = new AssignmentPublishRequest();
        request.setId(1L);
        try {
            harness.service.publish(request);
            Assert.fail("missing assignment must be rejected");
        } catch (CommonException e) {
            Assert.assertEquals(ResponseErrorCode.ERROR_RESOURCE_NOT_EXISTS, e.getCode());
        }
    }

    @Test
    public void publishStoresCurrentStateAsNewVersion() {
        Harness harness = new Harness();
        harness.assignment.byId = assignmentEntityWithFullState();
        AssignmentPublishRequest request = new AssignmentPublishRequest();
        request.setId(1L);
        request.setDescription("first release");

        harness.service.publish(request);

        Assert.assertEquals(1, harness.versionCommit.created.size());
        Object[] call = harness.versionCommit.created.get(0);
        Assert.assertEquals(1L, call[0]);
        Assert.assertEquals("first release", call[1]);
        String content = (String) call[2];
        Assert.assertTrue("snapshot must embed the assignment state", content.contains("\"name\":\"demo\""));
    }

    @Test
    public void deployRequiresExistingAssignment() {
        Harness harness = new Harness();
        harness.assignment.byId = null;
        try {
            harness.service.deployAssignment(1L, 100L);
            Assert.fail("missing assignment must be rejected");
        } catch (CommonException e) {
            Assert.assertEquals(ResponseErrorCode.ERROR_RESOURCE_NOT_EXISTS, e.getCode());
        }
    }

    @Test
    public void deployRejectsMissingAndForeignCommits() {
        Harness harness = new Harness();
        harness.assignment.byId = ApiAssignmentEntity.builder().id(1L).build();

        harness.versionCommit.byCommitId = null;
        try {
            harness.service.deployAssignment(1L, 100L);
            Assert.fail("missing commit must be rejected");
        } catch (CommonException e) {
            Assert.assertEquals(ResponseErrorCode.ERROR_RESOURCE_NOT_EXISTS, e.getCode());
        }

        harness.versionCommit.byCommitId = VersionCommitEntity.builder()
                .id(100L).bizId(2L).version(1).content("{}").build();
        try {
            harness.service.deployAssignment(1L, 100L);
            Assert.fail("foreign commit must be rejected");
        } catch (CommonException e) {
            Assert.assertEquals(ResponseErrorCode.ERROR_INVALID_ARGUMENT, e.getCode());
        }
        Assert.assertTrue(harness.online.upserted.isEmpty());
    }

    @Test
    public void deployUsesExplicitCommitContentAndWritesOnlineRow() {
        Harness harness = new Harness();
        harness.assignment.byId = ApiAssignmentEntity.builder().id(1L).build();
        ApiAssignmentEntity snapshot = assignmentEntityWithFullState();
        snapshot.setFlowStatus(true);
        harness.versionCommit.byCommitId = VersionCommitEntity.builder()
                .id(100L).bizId(1L).version(4).description("v4")
                .content(JsonUtils.toJsonString(snapshot)).build();

        harness.service.deployAssignment(1L, 100L);

        Assert.assertEquals(1, harness.online.upserted.size());
        ApiOnlineEntity online = harness.online.upserted.get(0);
        Assert.assertEquals("demo", online.getName());
        Assert.assertEquals(HttpMethodEnum.POST, online.getMethod());
        Assert.assertEquals("demo", online.getPath());
        Assert.assertEquals(Long.valueOf(1L), online.getApiId());
        Assert.assertEquals(Long.valueOf(10L), online.getGroupId());
        Assert.assertEquals(Long.valueOf(20L), online.getModuleId());
        Assert.assertEquals(Long.valueOf(2L), online.getDatasourceId());
        Assert.assertEquals(Boolean.TRUE, online.getOpen());
        Assert.assertEquals(Boolean.TRUE, online.getFlowStatus());
        Assert.assertEquals(Long.valueOf(100L), online.getCommitId());
        Assert.assertEquals(Integer.valueOf(4), online.getVersion());
        Assert.assertTrue(online.getContent().contains("\"name\":\"demo\""));
        Assert.assertEquals("explicit commit lookup should have run once", 1, harness.versionCommit.byCommitIdCalls);
    }

    @Test
    public void deployWithoutCommitFallsBackToLatestVersion() {
        Harness harness = new Harness();
        harness.assignment.byId = ApiAssignmentEntity.builder().id(1L).build();
        harness.assignment.byId.setMethod(HttpMethodEnum.GET);
        harness.assignment.byId.setPath("/latest");
        ApiAssignmentEntity snapshot = assignmentEntityWithFullState();
        harness.versionCommit.latest = VersionCommitEntity.builder()
                .id(200L).bizId(1L).version(7).content(JsonUtils.toJsonString(snapshot)).build();

        harness.service.deployAssignment(1L, null);

        Assert.assertEquals(1, harness.online.upserted.size());
        Assert.assertEquals(Long.valueOf(200L), harness.online.upserted.get(0).getCommitId());
        Assert.assertEquals("explicit commit lookup must not run when commitId is absent",
                0, harness.versionCommit.byCommitIdCalls);
    }

    @Test
    public void retireRemovesOnlineRowOnlyWhenAssignmentExists() {
        Harness harness = new Harness();
        harness.assignment.byId = ApiAssignmentEntity.builder().id(1L).build();
        harness.service.retireAssignment(1L);
        Assert.assertEquals(Collections.singletonList(1L), harness.online.deletedByApiId);

        Harness missing = new Harness();
        missing.assignment.byId = null;
        missing.service.retireAssignment(2L);
        Assert.assertTrue(missing.online.deletedByApiId.isEmpty());
    }

    /////////////////////////////////////////////////////////////////////////////////////
    // updateGroup
    /////////////////////////////////////////////////////////////////////////////////////

    @Test
    public void updateGroupRequiresExistingGroup() {
        Harness harness = new Harness();
        harness.group.byId = null;
        try {
            harness.service.updateGroup(5L, Collections.singletonList(1L));
            Assert.fail("missing group must be rejected");
        } catch (CommonException e) {
            Assert.assertEquals(ResponseErrorCode.ERROR_RESOURCE_NOT_EXISTS, e.getCode());
        }
        try {
            harness.service.updateGroup(null, Collections.singletonList(1L));
            Assert.fail("null group must be rejected");
        } catch (CommonException e) {
            Assert.assertEquals(ResponseErrorCode.ERROR_RESOURCE_NOT_EXISTS, e.getCode());
        }
    }

    @Test
    public void updateGroupResetsAndReassignsBothTables() {
        Harness harness = new Harness();
        List<Long> ids = Arrays.asList(1L, 2L);

        harness.service.updateGroup(5L, ids);

        Assert.assertEquals(Collections.singletonList(5L), harness.assignment.resetGroups);
        Assert.assertEquals(Collections.singletonList(5L), harness.online.resetGroups);
        Assert.assertEquals(1, harness.assignment.groupUpdates.size());
        Assert.assertArrayEquals(new Object[]{5L, ids}, harness.assignment.groupUpdates.get(0));
        Assert.assertEquals(1, harness.online.groupUpdates.size());
        Assert.assertArrayEquals(new Object[]{5L, ids}, harness.online.groupUpdates.get(0));
    }

    /////////////////////////////////////////////////////////////////////////////////////
    // online search
    /////////////////////////////////////////////////////////////////////////////////////

    @Test
    public void searchMapsModuleGroupNamesAndOnlineStatus() {
        Harness harness = new Harness();
        harness.module.all = Collections.singletonList(ApiModuleEntity.builder().id(20L).name("orders").build());
        harness.group.all = Collections.singletonList(ApiGroupEntity.builder().id(10L).name("core").build());
        harness.online.searchResult = Collections.singletonList(ApiAssignmentEntity.builder()
                .id(1L).name("demo").method(HttpMethodEnum.GET).path("demo")
                .moduleId(20L).groupId(10L).build());
        harness.online.filterOnlineList = new ArrayList<>(Arrays.asList(
                ApiIdVersion.builder().apiId(1L).commitId(100L).version(3).build(),
                ApiIdVersion.builder().apiId(1L).commitId(999L).version(8).build()));
        ApiOnlineSearchRequest request = new ApiOnlineSearchRequest();
        request.setModuleIds(Collections.singletonList(20L));
        request.setSearchText("dem");
        ((EntitySearchRequest) request).setPage(1);
        ((EntitySearchRequest) request).setSize(10);

        PageResult<ApiAssignmentBaseResponse> page;
        try {
            page = harness.service.search(request);
        } finally {
            PageHelper.clearPage();
        }

        Assert.assertEquals(1, harness.online.onlineSearchCalls.size());
        Object[] call = harness.online.onlineSearchCalls.get(0);
        Assert.assertNull(call[0]);
        Assert.assertEquals(Collections.singletonList(20L), call[1]);
        Assert.assertNull(call[2]);
        Assert.assertEquals("dem", call[3]);

        Assert.assertEquals(1, page.getData().size());
        ApiAssignmentBaseResponse row = page.getData().get(0);
        Assert.assertEquals("orders", row.getModuleName());
        Assert.assertEquals("core", row.getGroupName());
        Assert.assertEquals("/api/demo", row.getPath());
        Assert.assertTrue(row.getStatus());
        Assert.assertEquals(Long.valueOf(100L), row.getCommitId());
        Assert.assertEquals(Integer.valueOf(3), row.getVersion());
    }

    @Test
    public void listAllResolvesDuplicateOnlineRowsFirstWins() {
        Harness harness = new Harness();
        harness.assignment.searchResult = Collections.singletonList(ApiAssignmentEntity.builder()
                .id(1L).name("demo").method(HttpMethodEnum.GET).path("demo").build());
        harness.online.filterOnlineList = new ArrayList<>(Arrays.asList(
                ApiIdVersion.builder().apiId(1L).commitId(100L).version(3).build(),
                ApiIdVersion.builder().apiId(1L).commitId(777L).version(9).build()));
        AssignmentSearchRequest request = new AssignmentSearchRequest();
        request.setPage(1);
        request.setSize(10);

        PageResult<ApiAssignmentBaseResponse> page;
        try {
            page = harness.service.listAll(request);
        } finally {
            PageHelper.clearPage();
        }

        ApiAssignmentBaseResponse row = page.getData().get(0);
        assertTrue(row.getStatus());
        assertEquals("duplicate online rows keep the first version", Integer.valueOf(3), row.getVersion());
        assertEquals(Long.valueOf(100L), row.getCommitId());
    }

    /////////////////////////////////////////////////////////////////////////////////////
    // version browsing
    /////////////////////////////////////////////////////////////////////////////////////

    @Test
    public void listVersionsRequiresAssignment() {
        Harness harness = new Harness();
        harness.assignment.byId = null;
        try {
            harness.service.listVersions(1L);
            Assert.fail("missing assignment must be rejected");
        } catch (CommonException e) {
            Assert.assertEquals(ResponseErrorCode.ERROR_RESOURCE_NOT_EXISTS, e.getCode());
        }
    }

    @Test
    public void listVersionsMarksTheOnlineCommit() {
        Harness harness = new Harness();
        harness.assignment.byId = ApiAssignmentEntity.builder()
                .id(1L).method(HttpMethodEnum.GET).path("/demo").build();
        harness.versionCommit.versions = new ArrayList<>(Arrays.asList(
                VersionCommitEntity.builder().id(101L).bizId(1L).version(2).description("v2").build(),
                VersionCommitEntity.builder().id(100L).bizId(1L).version(1).description("v1").build()));
        harness.online.commitIdByUk = 101L;

        List<VersionCommitResponse> versions = harness.service.listVersions(1L);

        Assert.assertEquals(2, versions.size());
        Assert.assertEquals(Long.valueOf(101L), versions.get(0).getCommitId());
        Assert.assertTrue("the deployed commit is the online one", versions.get(0).getOnline());
        Assert.assertFalse(versions.get(1).getOnline());
        Assert.assertEquals(Long.valueOf(1L), versions.get(0).getApiId());
        Assert.assertEquals("v2", versions.get(0).getDescription());
    }

    @Test
    public void showVersionRendersSnapshotDetailAndOnlineState() {
        Harness harness = new Harness();
        ApiAssignmentEntity snapshot = assignmentEntityWithFullState();
        harness.versionCommit.byCommitId = VersionCommitEntity.builder()
                .id(100L).bizId(1L).version(3).description("v3")
                .content(JsonUtils.toJsonString(snapshot)).build();
        harness.online.filterOnlineSingle = ApiIdVersion.builder().apiId(1L).commitId(100L).version(3).build();

        VersionDetailResponse response = harness.service.showVersion(100L);

        Assert.assertEquals(Long.valueOf(100L), response.getCommitId());
        Assert.assertEquals(Integer.valueOf(3), response.getVersion());
        Assert.assertNotNull(response.getDetail());
        Assert.assertTrue("online flag mirrors the snapshot's deployment state", response.getOnline());
        Assert.assertEquals("demo", response.getDetail().getName());
        Assert.assertEquals("select 1", response.getDetail().getSqlList().get(0).getSqlText());
        Assert.assertEquals(1, response.getDetail().getFormatMap().size());
    }

    @Test
    public void revertVersionRestoresSnapshotOntoTheAssignment() {
        Harness harness = new Harness();
        ApiAssignmentEntity snapshot = assignmentEntityWithFullState();
        snapshot.setId(999L);
        harness.versionCommit.byCommitId = VersionCommitEntity.builder()
                .id(100L).bizId(1L).version(2).content(JsonUtils.toJsonString(snapshot)).build();

        harness.service.revertVersion(1L, 100L);

        Assert.assertEquals(1, harness.assignment.updated.size());
        ApiAssignmentEntity restored = harness.assignment.updated.get(0);
        Assert.assertEquals("rollback targets the current assignment id", Long.valueOf(1L), restored.getId());
        Assert.assertEquals("demo", restored.getName());
        Assert.assertEquals("select 1", restored.getContextList().get(0).getSqlText());
    }

    @Test
    public void revertVersionRejectsForeignCommit() {
        Harness harness = new Harness();
        harness.versionCommit.byCommitId = VersionCommitEntity.builder()
                .id(100L).bizId(2L).version(1).content("{}").build();
        try {
            harness.service.revertVersion(1L, 100L);
            Assert.fail("foreign commit must be rejected");
        } catch (CommonException e) {
            Assert.assertEquals(ResponseErrorCode.ERROR_INVALID_ARGUMENT, e.getCode());
        }
        Assert.assertTrue(harness.assignment.updated.isEmpty());
    }

    /////////////////////////////////////////////////////////////////////////////////////
    // debugExecute
    /////////////////////////////////////////////////////////////////////////////////////

    @Test
    public void debugExecuteRejectsMissingDatasource() throws Exception {
        Harness harness = new Harness();
        harness.dataSource.byId = null;
        ApiDebugExecuteRequest request = debugRequest();
        request.setDataSourceId(404L);
        try {
            harness.service.debugExecute(request, response(null).proxy);
            Assert.fail("missing datasource must be rejected");
        } catch (CommonException e) {
            Assert.assertEquals(ResponseErrorCode.ERROR_RESOURCE_NOT_EXISTS, e.getCode());
        }
    }

    @Test
    public void debugExecuteRejectsBlankParamName() throws Exception {
        Harness harness = new Harness();
        ApiDebugExecuteRequest request = debugRequest();
        ParamValue param = new ParamValue();
        param.setName(" ");
        param.setType(ParamTypeEnum.STRING);
        request.setParamValues(new ArrayList<>(Collections.singletonList(param)));
        try {
            harness.service.debugExecute(request, response(null).proxy);
            Assert.fail("blank param name must be rejected");
        } catch (CommonException e) {
            Assert.assertEquals(ResponseErrorCode.ERROR_INTERNAL_ERROR, e.getCode());
        }
    }

    @Test
    public void debugExecuteRejectsNullParamType() throws Exception {
        Harness harness = new Harness();
        ApiDebugExecuteRequest request = debugRequest();
        ParamValue param = new ParamValue();
        param.setName("p");
        param.setType(null);
        request.setParamValues(new ArrayList<>(Collections.singletonList(param)));
        try {
            harness.service.debugExecute(request, response(null).proxy);
            Assert.fail("null param type must be rejected");
        } catch (CommonException e) {
            Assert.assertEquals(ResponseErrorCode.ERROR_INTERNAL_ERROR, e.getCode());
        }
    }

    @Test
    public void debugExecuteRejectsBlankRequiredParam() throws Exception {
        Harness harness = new Harness();
        ApiDebugExecuteRequest request = debugRequest();
        ParamValue param = new ParamValue();
        param.setName("p");
        param.setType(ParamTypeEnum.STRING);
        param.setRequired(true);
        param.setValue(" ");
        request.setParamValues(new ArrayList<>(Collections.singletonList(param)));
        try {
            harness.service.debugExecute(request, response(null).proxy);
            Assert.fail("blank required param must be rejected");
        } catch (CommonException e) {
            Assert.assertEquals(ResponseErrorCode.ERROR_INVALID_ARGUMENT, e.getCode());
            Assert.assertEquals("api.required.param.empty", e.getMessage());
        }
    }

    @Test
    public void debugExecuteRejectsEmptyRequiredArrayParam() throws Exception {
        Harness harness = new Harness();
        ApiDebugExecuteRequest request = debugRequest();
        ParamValue param = new ParamValue();
        param.setName("ids");
        param.setType(ParamTypeEnum.LONG);
        param.setRequired(true);
        param.setIsArray(true);
        param.setArrayValues(Collections.emptyList());
        request.setParamValues(new ArrayList<>(Collections.singletonList(param)));
        try {
            harness.service.debugExecute(request, response(null).proxy);
            Assert.fail("empty required array must be rejected");
        } catch (CommonException e) {
            Assert.assertEquals(ResponseErrorCode.ERROR_INVALID_ARGUMENT, e.getCode());
            Assert.assertEquals("api.required.param.empty", e.getMessage());
        }
    }

    @Test
    public void debugExecuteRejectsBlankRequiredObjectChild() throws Exception {
        Harness harness = new Harness();
        ApiDebugExecuteRequest request = debugRequest();
        ParamValue param = new ParamValue();
        param.setName("user");
        param.setType(ParamTypeEnum.OBJECT);
        param.setRequired(false);
        ParamValue.BaseParamValue child = new ParamValue.BaseParamValue();
        child.setName("age");
        child.setType(ParamTypeEnum.LONG);
        child.setRequired(true);
        param.setChildren(new ArrayList<>(Collections.singletonList(child)));
        request.setParamValues(new ArrayList<>(Collections.singletonList(param)));
        try {
            harness.service.debugExecute(request, response(null).proxy);
            Assert.fail("blank required object child must be rejected");
        } catch (CommonException e) {
            Assert.assertEquals(ResponseErrorCode.ERROR_INVALID_ARGUMENT, e.getCode());
        }

        // a required array-typed child without values is rejected the same way
        ParamValue withArrayChild = new ParamValue();
        withArrayChild.setName("user");
        withArrayChild.setType(ParamTypeEnum.OBJECT);
        ParamValue.BaseParamValue arrayChild = new ParamValue.BaseParamValue();
        arrayChild.setName("ids");
        arrayChild.setType(ParamTypeEnum.LONG);
        arrayChild.setRequired(true);
        arrayChild.setIsArray(true);
        arrayChild.setArrayValues(Collections.emptyList());
        withArrayChild.setChildren(new ArrayList<>(Collections.singletonList(arrayChild)));
        request.setParamValues(new ArrayList<>(Collections.singletonList(withArrayChild)));
        try {
            harness.service.debugExecute(request, response(null).proxy);
            Assert.fail("empty required array child must be rejected");
        } catch (CommonException e) {
            Assert.assertEquals(ResponseErrorCode.ERROR_INVALID_ARGUMENT, e.getCode());
        }
    }

    @Test
    public void debugExecuteMapsEveryParamShapeThenReportsEngineFailure() throws Exception {
        Harness harness = new Harness();
        // entity without id: the pool helper rejects it, which lands in the failed-result branch
        // after the whole parameter mapping pipeline has run
        harness.dataSource.byId = DataSourceEntity.builder().name("ds").type(ProductTypeEnum.MYSQL).build();
        ApiDebugExecuteRequest request = debugRequest();
        List<ParamValue> params = new ArrayList<>();

        ParamValue requiredScalar = new ParamValue();
        requiredScalar.setName("req");
        requiredScalar.setType(ParamTypeEnum.STRING);
        requiredScalar.setRequired(true);
        requiredScalar.setValue("v");
        params.add(requiredScalar);

        ParamValue optionalWithDefault = new ParamValue();
        optionalWithDefault.setName("def");
        optionalWithDefault.setType(ParamTypeEnum.LONG);
        optionalWithDefault.setRequired(false);
        optionalWithDefault.setValue(" ");
        optionalWithDefault.setDefaultValue("42");
        params.add(optionalWithDefault);

        ParamValue optionalWithoutValue = new ParamValue();
        optionalWithoutValue.setName("empty");
        optionalWithoutValue.setType(ParamTypeEnum.STRING);
        optionalWithoutValue.setRequired(false);
        params.add(optionalWithoutValue);

        ParamValue arrayScalar = new ParamValue();
        arrayScalar.setName("ids");
        arrayScalar.setType(ParamTypeEnum.LONG);
        arrayScalar.setIsArray(true);
        arrayScalar.setArrayValues(Arrays.asList("1", "2"));
        params.add(arrayScalar);

        ParamValue emptyArrayScalar = new ParamValue();
        emptyArrayScalar.setName("none");
        emptyArrayScalar.setType(ParamTypeEnum.LONG);
        emptyArrayScalar.setIsArray(true);
        emptyArrayScalar.setArrayValues(Collections.emptyList());
        params.add(emptyArrayScalar);

        ParamValue objectParam = new ParamValue();
        objectParam.setName("obj");
        objectParam.setType(ParamTypeEnum.OBJECT);
        objectParam.setRequired(false);
        ParamValue.BaseParamValue scalarChild = new ParamValue.BaseParamValue();
        scalarChild.setName("age");
        scalarChild.setType(ParamTypeEnum.LONG);
        scalarChild.setRequired(false);
        scalarChild.setValue(" ");
        scalarChild.setDefaultValue("18");
        ParamValue.BaseParamValue requiredChild = new ParamValue.BaseParamValue();
        requiredChild.setName("level");
        requiredChild.setType(ParamTypeEnum.LONG);
        requiredChild.setRequired(true);
        requiredChild.setValue("9");
        ParamValue.BaseParamValue valuedChild = new ParamValue.BaseParamValue();
        valuedChild.setName("note");
        valuedChild.setType(ParamTypeEnum.STRING);
        valuedChild.setRequired(false);
        valuedChild.setValue("hi");
        ParamValue.BaseParamValue arrayChild = new ParamValue.BaseParamValue();
        arrayChild.setName("tags");
        arrayChild.setType(ParamTypeEnum.STRING);
        arrayChild.setIsArray(true);
        arrayChild.setArrayValues(Arrays.asList("a", "b"));
        ParamValue.BaseParamValue emptyArrayChild = new ParamValue.BaseParamValue();
        emptyArrayChild.setName("emptyTags");
        emptyArrayChild.setType(ParamTypeEnum.STRING);
        emptyArrayChild.setIsArray(true);
        emptyArrayChild.setArrayValues(Collections.emptyList());
        objectParam.setChildren(new ArrayList<>(
                Arrays.asList(scalarChild, requiredChild, valuedChild, arrayChild, emptyArrayChild)));
        params.add(objectParam);

        ParamValue objectArrayParam = new ParamValue();
        objectArrayParam.setName("objs");
        objectArrayParam.setType(ParamTypeEnum.OBJECT);
        objectArrayParam.setIsArray(true);
        objectArrayParam.setChildren(new ArrayList<>(
                Arrays.asList(scalarChild, requiredChild, arrayChild, emptyArrayChild)));
        params.add(objectArrayParam);

        // object params whose children never arrive map to nothing in the first pass,
        // then the optional-empty pass fills them from their default value
        ParamValue objectWithoutChildren = new ParamValue();
        objectWithoutChildren.setName("bare");
        objectWithoutChildren.setType(ParamTypeEnum.OBJECT);
        objectWithoutChildren.setRequired(false);
        params.add(objectWithoutChildren);

        request.setParamValues(params);
        // duplicate format keys exercise the first-wins merge of the debug response format map
        request.setFormatMap(new ArrayList<>(Arrays.asList(
                DataTypeFormatMapValue.builder().key(DataTypeFormatEnum.DATE).value("yyyy-MM-dd").build(),
                DataTypeFormatMapValue.builder().key(DataTypeFormatEnum.DATE).value("yyyy/MM/dd").build())));

        StringWriter body = new StringWriter();
        RecordingResponse response = response(body);
        harness.service.debugExecute(request, response.proxy);

        String json = body.toString();
        Assert.assertTrue("engine failure must be reported as a failed result",
                json.contains("\"code\"") && json.toLowerCase().contains("datasource entity id"));
        Assert.assertEquals(1, harness.debugHooks.size());
        ApiDebugPostContext context = harness.debugHooks.get(0);
        Assert.assertFalse(context.isSuccess());
        Assert.assertNotNull(context.getErrorMessage());
        Assert.assertTrue(context.getElapsedMs() >= 0);
    }

    @Test
    public void debugExecuteSurfacesValueConversionErrors() throws Exception {
        Harness harness = new Harness();
        ApiDebugExecuteRequest request = debugRequest();
        ParamValue param = new ParamValue();
        param.setName("num");
        param.setType(ParamTypeEnum.LONG);
        param.setRequired(false);
        param.setValue("not-a-number");
        request.setParamValues(new ArrayList<>(Collections.singletonList(param)));
        try {
            harness.service.debugExecute(request, response(null).proxy);
            Assert.fail("invalid numeric value must surface a conversion error");
        } catch (RuntimeException e) {
            Assert.assertTrue(e.getMessage(), e.getMessage().contains("[num] value type invalid"));
        }
    }

    @Test
    public void debugExecuteRunsAgainstH2AndWritesSuccessJson() throws Exception {
        Harness harness = new Harness();
        DataSourceEntity entity = DataSourceEntity.builder()
                .id(4242L).name("h2").type(ProductTypeEnum.MYSQL)
                .version("1").driver("org.h2.Driver")
                .url("jdbc:h2:mem:assignmentDebug;MODE=MySQL;DB_CLOSE_DELAY=-1")
                .username("sa").password("").build();
        harness.dataSource.byId = entity;

        HikariDataSource h2 = new HikariDataSource();
        h2.setJdbcUrl(entity.getUrl());
        h2.setUsername("sa");
        h2.setPassword("");
        Map<Long, Object> datasourceMap = staticMap(DataSourceUtilsHolder.datasourceMapField());
        datasourceMap.put(entity.getId(),
                org.apache.commons.lang3.tuple.Pair.of((Object) entity, (Object) h2));
        Object previousBeanFactory = installEnvironmentOnlyBeanFactory();
        try {
            ApiDebugExecuteRequest request = debugRequest();
            request.setContextList(Arrays.asList("select 1", "select 2"));

            StringWriter body = new StringWriter();
            RecordingResponse response = response(body);
            harness.service.debugExecute(request, response.proxy);

            Assert.assertEquals(200, response.status);
            Assert.assertEquals("application/json", response.contentType);
            String json = body.toString();
            Assert.assertTrue("success payload must carry the answer", json.contains("answer"));
            Assert.assertTrue(json.contains("logs"));
            Assert.assertTrue(json.contains("types"));
            Assert.assertEquals(1, harness.debugHooks.size());
            ApiDebugPostContext context = harness.debugHooks.get(0);
            Assert.assertTrue(context.isSuccess());
            Assert.assertNotNull(context.getAnswer());
            Assert.assertNull(context.getErrorMessage());
        } finally {
            datasourceMap.remove(entity.getId());
            h2.close();
            restoreBeanFactory(previousBeanFactory);
        }
    }

    // ------------------------------------------------------------------ remaining branch corners

    @Test
    public void parseSqlParamsNestsDottedParamsUnderASynthesizedParent() {
        // the template layer always emits the root name too, so #{lonely.child} nests under
        // a synthesized "lonely" parent even when only the dotted form appears in the SQL
        List<SqlParamParseResponse> parsed = new ApiAssignmentService()
                .parseSqlParams("SELECT 1 FROM t WHERE c = #{lonely.child}");
        Assert.assertEquals(1, parsed.size());
        Assert.assertEquals("lonely", parsed.get(0).getName());
        Assert.assertEquals(1, parsed.get(0).getChildren().size());
        Assert.assertEquals("child", parsed.get(0).getChildren().get(0).getName());
    }

    @Test
    public void createAssignmentRejectsNullMethodWithAValidPath() {
        Harness harness = new Harness();
        ApiAssignmentSaveRequest request = validCreateRequest();
        request.setPath("demo");
        request.setMethod(null);
        try {
            harness.service.createAssignment(request);
            Assert.fail("null method must be rejected");
        } catch (CommonException e) {
            Assert.assertEquals(ResponseErrorCode.ERROR_INVALID_ARGUMENT, e.getCode());
        }
    }

    @Test
    public void updateAssignmentRejectsNullMethodWithAValidPath() {
        Harness harness = new Harness();
        ApiAssignmentSaveRequest request = validUpdateRequest();
        request.setMethod(null);
        try {
            harness.service.updateAssignment(request);
            Assert.fail("null method must be rejected on update");
        } catch (CommonException e) {
            Assert.assertEquals(ResponseErrorCode.ERROR_INVALID_ARGUMENT, e.getCode());
        }
    }

    @Test
    public void updateAssignmentRejectsNullDatasourceId() {
        Harness harness = new Harness();
        harness.assignment.byId = ApiAssignmentEntity.builder()
                .id(1L).method(HttpMethodEnum.GET).path("/demo").build();
        ApiAssignmentSaveRequest request = validUpdateRequest();
        request.setDatasourceId(null);
        try {
            harness.service.updateAssignment(request);
            Assert.fail("null datasource id must be rejected");
        } catch (CommonException e) {
            Assert.assertEquals(ResponseErrorCode.ERROR_INVALID_ARGUMENT, e.getCode());
        }
    }

    @Test
    public void updateAssignmentAcceptsPostBodyParamsAndKeepsExplicitNaming() {
        Harness harness = new Harness();
        harness.assignment.byId = ApiAssignmentEntity.builder()
                .id(1L).method(HttpMethodEnum.POST).path("/demo").build();
        ApiAssignmentSaveRequest request = validUpdateRequest();
        request.setMethod(HttpMethodEnum.POST);
        // POST keeps REQUEST_BODY parameters legal: the no-body guard is skipped
        com.cs.common.dto.ItemParam bodyParam = new com.cs.common.dto.ItemParam();
        bodyParam.setName("payload");
        bodyParam.setType(ParamTypeEnum.STRING);
        bodyParam.setLocation(ParamLocationEnum.REQUEST_BODY);
        request.setParams(new ArrayList<>(Collections.singletonList(bodyParam)));
        request.setNamingStrategy(NamingStrategyEnum.SNAKE_CASE); // explicit: defaulting skipped

        harness.service.updateAssignment(request);

        ApiAssignmentEntity saved = harness.assignment.updated.get(0);
        Assert.assertEquals(NamingStrategyEnum.SNAKE_CASE, saved.getNamingStrategy());
        Assert.assertEquals(1, saved.getParams().size());
        Assert.assertEquals(ParamLocationEnum.REQUEST_BODY, saved.getParams().get(0).getLocation());
    }

    @Test
    public void publishWithAnEmptyContextListSkipsTheDollarWarning() {
        Harness harness = new Harness();
        ApiAssignmentEntity entity = assignmentEntityWithFullState();
        entity.setContextList(new ArrayList<ApiContextEntity>()); // SQL engine, nothing to scan
        harness.assignment.byId = entity;
        harness.versionCommit.byCommitId = VersionCommitEntity.builder().id(900L).bizId(1L).build();

        AssignmentPublishRequest publishRequest = new AssignmentPublishRequest();
        publishRequest.setId(1L);
        harness.service.publish(publishRequest);

        // the empty context list short-circuits warnDollarSubstitution; the version is still cut
        Assert.assertEquals(1, harness.versionCommit.created.size());
    }

    @Test
    public void listAllHandlesEmptyPagesAndOfflineRows() {
        Harness harness = new Harness();
        AssignmentSearchRequest request = new AssignmentSearchRequest();
        request.setPage(1);
        request.setSize(10);
        try {
            harness.assignment.searchResult = Collections.emptyList();
            PageResult<ApiAssignmentBaseResponse> empty = harness.service.listAll(request);
            Assert.assertTrue("an empty search must produce an empty page", empty.getData().isEmpty());
        } finally {
            PageHelper.clearPage();
        }

        try {
            harness.assignment.searchResult = Collections.singletonList(assignmentEntityWithFullState());
            // filterOnlineList stays empty: the row is a draft, not online
            PageResult<ApiAssignmentBaseResponse> page = harness.service.listAll(request);
            Assert.assertEquals(1, page.getData().size());
            Assert.assertFalse("no online row means status false", page.getData().get(0).getStatus());
            Assert.assertNull(page.getData().get(0).getVersion());
        } finally {
            PageHelper.clearPage();
        }
    }

    @Test
    public void searchHandlesEmptyPagesAndOfflineRows() {
        Harness harness = new Harness();
        ApiOnlineSearchRequest request = new ApiOnlineSearchRequest();
        request.setPage(1);
        request.setSize(10);
        try {
            harness.online.searchResult = Collections.emptyList();
            Assert.assertTrue(harness.service.search(request).getData().isEmpty());
        } finally {
            PageHelper.clearPage();
        }

        try {
            harness.online.searchResult = Collections.singletonList(assignmentEntityWithFullState());
            PageResult<ApiAssignmentBaseResponse> page = harness.service.search(request);
            Assert.assertEquals(1, page.getData().size());
            Assert.assertFalse("offline rows carry no version", page.getData().get(0).getStatus());
        } finally {
            PageHelper.clearPage();
        }
    }

    @Test
    public void debugExecuteMapsTheFullParameterMatrixAgainstH2() throws Exception {
        Harness harness = new Harness();
        DataSourceEntity entity = DataSourceEntity.builder()
                .id(4243L).name("h2-full").type(ProductTypeEnum.MYSQL)
                .version("1").driver("org.h2.Driver")
                .url("jdbc:h2:mem:assignmentDebugFull;MODE=MySQL;DB_CLOSE_DELAY=-1")
                .username("sa").password("").build();
        harness.dataSource.byId = entity;

        HikariDataSource h2 = new HikariDataSource();
        h2.setJdbcUrl(entity.getUrl());
        h2.setUsername("sa");
        h2.setPassword("");
        Map<Long, Object> datasourceMap = staticMap(DataSourceUtilsHolder.datasourceMapField());
        datasourceMap.put(entity.getId(),
                org.apache.commons.lang3.tuple.Pair.of((Object) entity, (Object) h2));
        Object previousBeanFactory = installEnvironmentOnlyBeanFactory();
        try {
            ApiDebugExecuteRequest request = debugRequest();
            // one script: the answer is the single result object, and an explicit strategy skips defaulting
            request.setContextList(Collections.singletonList("select 1"));
            request.setNamingStrategy(NamingStrategyEnum.SNAKE_CASE);
            List<ParamValue> params = new ArrayList<>();

            // required top-level array with values passes validation and binds element-wise
            ParamValue requiredArray = new ParamValue();
            requiredArray.setName("ids");
            requiredArray.setType(ParamTypeEnum.LONG);
            requiredArray.setRequired(true);
            requiredArray.setIsArray(true);
            requiredArray.setArrayValues(Arrays.asList("1", "2"));
            params.add(requiredArray);

            // required OBJECT whose children include a filled required array child
            ParamValue object = new ParamValue();
            object.setName("user");
            object.setType(ParamTypeEnum.OBJECT);
            object.setRequired(true);
            ParamValue.BaseParamValue arrayChild = new ParamValue.BaseParamValue();
            arrayChild.setName("tags");
            arrayChild.setType(ParamTypeEnum.STRING);
            arrayChild.setRequired(true);
            arrayChild.setIsArray(true);
            arrayChild.setArrayValues(Collections.singletonList("t"));
            ParamValue.BaseParamValue scalarChild = new ParamValue.BaseParamValue();
            scalarChild.setName("level");
            scalarChild.setType(ParamTypeEnum.LONG);
            scalarChild.setRequired(true);
            scalarChild.setValue("9");
            ParamValue.BaseParamValue optionalChild = new ParamValue.BaseParamValue();
            optionalChild.setName("note");
            optionalChild.setType(ParamTypeEnum.STRING);
            optionalChild.setRequired(false);
            optionalChild.setValue("hi"); // non-blank optional value is used as-is
            object.setChildren(new ArrayList<>(Arrays.asList(arrayChild, scalarChild, optionalChild)));
            params.add(object);

            // object-array WITH children: the non-blank optional child value is taken directly
            ParamValue objectArray = new ParamValue();
            objectArray.setName("objs");
            objectArray.setType(ParamTypeEnum.OBJECT);
            objectArray.setIsArray(true);
            objectArray.setChildren(new ArrayList<>(Collections.singletonList(optionalChild)));
            params.add(objectArray);

            // object-array WITHOUT children maps to nothing (no put, no throw)
            ParamValue bareArrayObject = new ParamValue();
            bareArrayObject.setName("bareObjs");
            bareArrayObject.setType(ParamTypeEnum.OBJECT);
            bareArrayObject.setIsArray(true);
            params.add(bareArrayObject);

            // ... and one with an explicitly EMPTY children list: the child loop never runs
            ParamValue emptyChildrenArrayObject = new ParamValue();
            emptyChildrenArrayObject.setName("emptyKidsObjs");
            emptyChildrenArrayObject.setType(ParamTypeEnum.OBJECT);
            emptyChildrenArrayObject.setIsArray(true);
            emptyChildrenArrayObject.setChildren(new ArrayList<ParamValue.BaseParamValue>());
            params.add(emptyChildrenArrayObject);

            request.setParamValues(params);

            StringWriter body = new StringWriter();
            RecordingResponse response = response(body);
            harness.service.debugExecute(request, response.proxy);

            String json = body.toString();
            Assert.assertTrue("the full matrix must run green: " + json, json.contains("\"code\""));
            Assert.assertEquals(1, harness.debugHooks.size());
            Assert.assertTrue(harness.debugHooks.get(0).isSuccess());
            Assert.assertNotNull(harness.debugHooks.get(0).getAnswer());
        } finally {
            datasourceMap.remove(entity.getId());
            h2.close();
            restoreBeanFactory(previousBeanFactory);
        }
    }

    @Test
    public void debugExecuteWithoutScriptsYieldsANullAnswer() throws Exception {
        Harness harness = new Harness();
        DataSourceEntity entity = DataSourceEntity.builder()
                .id(4245L).name("h2-empty").type(ProductTypeEnum.MYSQL)
                .version("1").driver("org.h2.Driver")
                .url("jdbc:h2:mem:assignmentDebugEmpty;MODE=MySQL;DB_CLOSE_DELAY=-1")
                .username("sa").password("").build();
        harness.dataSource.byId = entity;

        HikariDataSource h2 = new HikariDataSource();
        h2.setJdbcUrl(entity.getUrl());
        h2.setUsername("sa");
        h2.setPassword("");
        Map<Long, Object> datasourceMap = staticMap(DataSourceUtilsHolder.datasourceMapField());
        datasourceMap.put(entity.getId(),
                org.apache.commons.lang3.tuple.Pair.of((Object) entity, (Object) h2));
        Object previousBeanFactory = installEnvironmentOnlyBeanFactory();
        try {
            ApiDebugExecuteRequest request = debugRequest();
            request.setContextList(Collections.emptyList()); // zero scripts: zero results

            StringWriter body = new StringWriter();
            harness.service.debugExecute(request, response(body).proxy);

            Assert.assertTrue(harness.debugHooks.get(0).isSuccess());
            Assert.assertNull("no script means no answer payload",
                    harness.debugHooks.get(0).getAnswer());
        } finally {
            datasourceMap.remove(entity.getId());
            h2.close();
            restoreBeanFactory(previousBeanFactory);
        }
    }

    @Test
    public void debugExecuteOnHttpDatasourceWithAScriptEngineKeepsScalarAnswers() throws Exception {
        Harness harness = new Harness();
        DataSourceEntity entity = DataSourceEntity.builder()
                .id(4246L).name("http-script").type(ProductTypeEnum.HTTP)
                .version("1").driver("ignored")
                .url("jdbc:restful://http://fakehost:8080")
                .username("user").password("").build();
        harness.dataSource.byId = entity;

        HikariDataSource h2 = new HikariDataSource();
        h2.setJdbcUrl("jdbc:h2:mem:assignmentDebugHttpScript;MODE=MySQL;DB_CLOSE_DELAY=-1");
        h2.setUsername("sa");
        h2.setPassword("");
        Map<Long, Object> datasourceMap = staticMap(DataSourceUtilsHolder.datasourceMapField());
        datasourceMap.put(entity.getId(),
                org.apache.commons.lang3.tuple.Pair.of((Object) entity, (Object) h2));
        Object previousBeanFactory = installEnvironmentOnlyBeanFactory();
        try {
            ApiDebugExecuteRequest request = debugRequest();
            request.setEngine(ExecuteEngineEnum.SCRIPT);
            request.setContextList(Collections.singletonList("1+1")); // evaluates to a scalar

            StringWriter body = new StringWriter();
            harness.service.debugExecute(request, response(body).proxy);

            // an HTTP datasource with a non-list answer: the unwrap branch must not fire
            Assert.assertTrue(harness.debugHooks.get(0).isSuccess());
            Assert.assertEquals(2, harness.debugHooks.get(0).getAnswer());
        } finally {
            datasourceMap.remove(entity.getId());
            h2.close();
            restoreBeanFactory(previousBeanFactory);
        }
    }

    @Test
    public void debugExecuteOnHttpDatasourcesUnwrapsListAnswers() throws Exception {
        Harness harness = new Harness();
        DataSourceEntity entity = DataSourceEntity.builder()
                .id(4244L).name("http").type(ProductTypeEnum.HTTP)
                .version("1").driver("ignored")
                .url("jdbc:restful://http://fakehost:8080")
                .username("user").password("").build();
        harness.dataSource.byId = entity;

        HikariDataSource h2 = new HikariDataSource();
        h2.setJdbcUrl("jdbc:h2:mem:assignmentDebugHttp;MODE=MySQL;DB_CLOSE_DELAY=-1");
        h2.setUsername("sa");
        h2.setPassword("");
        Map<Long, Object> datasourceMap = staticMap(DataSourceUtilsHolder.datasourceMapField());
        datasourceMap.put(entity.getId(),
                org.apache.commons.lang3.tuple.Pair.of((Object) entity, (Object) h2));
        Object previousBeanFactory = installEnvironmentOnlyBeanFactory();
        try {
            // two scripts on an HTTP datasource: the list answer is unwrapped to its first entry
            ApiDebugExecuteRequest twoScripts = debugRequest();
            twoScripts.setContextList(Arrays.asList("select 1", "select 2"));
            StringWriter body = new StringWriter();
            harness.service.debugExecute(twoScripts, response(body).proxy);
            Assert.assertTrue("multi-script HTTP debug must succeed: " + body,
                    harness.debugHooks.get(0).isSuccess());

            // one script: the single result is not a list and passes through unchanged
            ApiDebugExecuteRequest oneScript = debugRequest();
            oneScript.setContextList(Collections.singletonList("select 1"));
            StringWriter singleBody = new StringWriter();
            harness.service.debugExecute(oneScript, response(singleBody).proxy);
            Assert.assertTrue(harness.debugHooks.get(1).isSuccess());
            Assert.assertNotNull(harness.debugHooks.get(1).getAnswer());
        } finally {
            datasourceMap.remove(entity.getId());
            h2.close();
            restoreBeanFactory(previousBeanFactory);
        }
    }

    private static final class DataSourceUtilsHolder {
        private static Field DATASOURCE_MAP_FIELD;

        static Field datasourceMapField() throws Exception {
            if (null == DATASOURCE_MAP_FIELD) {
                DATASOURCE_MAP_FIELD = com.cs.core.util.DataSourceUtils.class.getDeclaredField("datasourceMap");
                DATASOURCE_MAP_FIELD.setAccessible(true);
            }
            return DATASOURCE_MAP_FIELD;
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<Long, Object> staticMap(Field field) throws Exception {
        return (Map<Long, Object>) field.get(null);
    }

    /**
     * Installs a SpringUtil bean factory that can only serve an {@link Environment} whose
     * properties are all unset - enough for DataSourceUtils.decrypt() to skip AES work while
     * nothing else in the debug path consults the container.
     */
    private static Object installEnvironmentOnlyBeanFactory() throws Exception {
        Object environment = Proxy.newProxyInstance(ApiAssignmentServiceTest.class.getClassLoader(),
                new Class<?>[]{Environment.class},
                (p, m, a) -> {
                    if (m.getName().equals("getProperty")) {
                        // Environment.getProperty(key, defaultValue) must never return null
                        // (JacksonUtils relies on that); single-arg lookups report "unset"
                        if (a != null && a.length == 2 && a[1] instanceof String) {
                            return a[1];
                        }
                        return null;
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
        Object beanFactory = Proxy.newProxyInstance(ApiAssignmentServiceTest.class.getClassLoader(),
                new Class<?>[]{org.springframework.beans.factory.config.ConfigurableListableBeanFactory.class},
                (p, m, a) -> {
                    if ("getBean".equals(m.getName()) && a != null && a.length > 0 && a[0] == Environment.class) {
                        return environment;
                    }
                    if ("getBeansOfType".equals(m.getName())) {
                        // script modules resolve through the container: none are registered here
                        return new java.util.HashMap<>();
                    }
                    if ("hashCode".equals(m.getName())) {
                        return System.identityHashCode(p);
                    }
                    if ("equals".equals(m.getName())) {
                        return p == a[0];
                    }
                    if ("toString".equals(m.getName())) {
                        return "fakeBeanFactory";
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
        Field field = cn.hutool.extra.spring.SpringUtil.class.getDeclaredField("beanFactory");
        field.setAccessible(true);
        Object previous = field.get(null);
        field.set(null, beanFactory);
        return previous;
    }

    private static void restoreBeanFactory(Object previous) throws Exception {
        Field field = cn.hutool.extra.spring.SpringUtil.class.getDeclaredField("beanFactory");
        field.setAccessible(true);
        field.set(null, previous);
    }

    private static ApiDebugExecuteRequest debugRequest() {
        ApiDebugExecuteRequest request = new ApiDebugExecuteRequest();
        request.setDataSourceId(2L);
        request.setEngine(ExecuteEngineEnum.SQL);
        request.setContextList(Collections.singletonList("select 1"));
        request.setFormatMap(new ArrayList<>());
        return request;
    }

    private static RecordingResponse response(StringWriter body) {
        RecordingResponse recorder = new RecordingResponse();
        if (null == body) {
            recorder.body = new StringWriter();
        } else {
            recorder.body = body;
        }
        recorder.proxy = (HttpServletResponse) Proxy.newProxyInstance(
                ApiAssignmentServiceTest.class.getClassLoader(),
                new Class<?>[]{HttpServletResponse.class},
                (p, m, a) -> {
                    switch (m.getName()) {
                        case "getWriter":
                            return new PrintWriter(recorder.body, true);
                        case "setStatus":
                            recorder.status = (Integer) a[0];
                            return null;
                        case "getStatus":
                            return recorder.status;
                        case "setContentType":
                            recorder.contentType = String.valueOf(a[0]);
                            return null;
                        case "setCharacterEncoding":
                            recorder.characterEncoding = String.valueOf(a[0]);
                            return null;
                        case "setHeader":
                            recorder.headers.put(String.valueOf(a[0]), String.valueOf(a[1]));
                            return null;
                        case "hashCode":
                            return System.identityHashCode(p);
                        case "equals":
                            return p == a[0];
                        case "toString":
                            return "recordingResponse";
                        default:
                            Class<?> rt = m.getReturnType();
                            if (rt == boolean.class) {
                                return false;
                            }
                            if (rt == int.class) {
                                return 0;
                            }
                            return null;
                    }
                });
        return recorder;
    }

    private static final class RecordingResponse {
        StringWriter body;
        HttpServletResponse proxy;
        int status = 200;
        String contentType;
        String characterEncoding;
        final Map<String, String> headers = new LinkedHashMap<>();
    }

    /////////////////////////////////////////////////////////////////////////////////////
    // harness
    /////////////////////////////////////////////////////////////////////////////////////

    private static ApiAssignmentSaveRequest validCreateRequest() {
        ApiAssignmentSaveRequest request = new ApiAssignmentSaveRequest();
        request.setGroupId(10L);
        request.setModuleId(20L);
        request.setDatasourceId(2L);
        request.setName("demo");
        request.setMethod(HttpMethodEnum.POST);
        request.setContentType("application/json");
        request.setPath("/demo");
        request.setOpen(false);
        request.setAlarm(false);
        request.setEngine(ExecuteEngineEnum.SQL);
        request.setContextList(Collections.singletonList("select 1"));
        request.setFormatMap(new ArrayList<DataTypeFormatMapValue>());
        return request;
    }

    private static ApiAssignmentSaveRequest validGraphqlRequest() {
        ApiAssignmentSaveRequest request = validCreateRequest();
        request.setEngine(ExecuteEngineEnum.GRAPHQL);
        request.setMethod(HttpMethodEnum.POST);
        request.setContextList(Collections.singletonList(VALID_SDL));
        return request;
    }

    private static ApiAssignmentEntity assignmentEntityWithFullState() {
        ApiAssignmentEntity entity = ApiAssignmentEntity.builder()
                .id(1L)
                .groupId(10L)
                .moduleId(20L)
                .datasourceId(2L)
                .name("demo")
                .method(HttpMethodEnum.POST)
                .path("demo")
                .open(true)
                .alarm(false)
                .engine(ExecuteEngineEnum.SQL)
                .build();
        Map<DataTypeFormatEnum, String> responseFormat = new EnumMap<>(DataTypeFormatEnum.class);
        responseFormat.put(DataTypeFormatEnum.DATE, "yyyy-MM-dd");
        entity.setResponseFormat(responseFormat);
        ApiContextEntity context = new ApiContextEntity("select 1");
        entity.setContextList(new ArrayList<>(Collections.singletonList(context)));
        return entity;
    }

    private static final class FakeAssignmentDao extends ApiAssignmentDao {
        ApiAssignmentEntity byId;
        ApiAssignmentEntity byUk;
        List<ApiAssignmentEntity> searchResult = Collections.emptyList();
        final List<ApiAssignmentEntity> inserted = new ArrayList<>();
        final List<ApiAssignmentEntity> updated = new ArrayList<>();
        final List<Long> deleted = new ArrayList<>();
        final List<Long> resetGroups = new ArrayList<>();
        final List<Object[]> groupUpdates = new ArrayList<>();
        private long nextId = 777L;

        @Override
        public ApiAssignmentEntity getById(Long id, boolean withSql) {
            return byId;
        }

        @Override
        public ApiAssignmentEntity getByUk(HttpMethodEnum method, String path) {
            return byUk;
        }

        @Override
        public void insert(ApiAssignmentEntity entity) {
            if (null == entity.getId()) {
                entity.setId(nextId++);
            }
            inserted.add(entity);
        }

        @Override
        public void update(ApiAssignmentEntity entity) {
            updated.add(entity);
        }

        @Override
        public void deleteById(Long id) {
            deleted.add(id);
        }

        @Override
        public void resetGroupByGroupId(Long groupId) {
            resetGroups.add(groupId);
        }

        @Override
        public void updateGroup(Long groupId, List<Long> ids) {
            groupUpdates.add(new Object[]{groupId, ids});
        }

        @Override
        public List<ApiAssignmentEntity> searchAll(Long groupId, Long moduleId, Boolean open,
                                                   String searchText, Boolean online) {
            return searchResult;
        }
    }

    private static final class FakeOnlineDao extends ApiOnlineDao {
        ApiAssignmentEntity byApiId;
        ApiIdVersion filterOnlineSingle;
        List<ApiIdVersion> filterOnlineList = Collections.emptyList();
        List<ApiAssignmentEntity> searchResult = Collections.emptyList();
        Long commitIdByUk;
        final List<ApiOnlineEntity> upserted = new ArrayList<>();
        final List<Long> deletedByApiId = new ArrayList<>();
        final List<Long> resetGroups = new ArrayList<>();
        final List<Object[]> groupUpdates = new ArrayList<>();
        final List<Object[]> onlineSearchCalls = new ArrayList<>();

        @Override
        public ApiAssignmentEntity getByApiId(Long apiId) {
            return byApiId;
        }

        @Override
        public ApiIdVersion filterOnline(Long apiId) {
            return filterOnlineSingle;
        }

        @Override
        public List<ApiIdVersion> filterOnline(List<Long> apiIds) {
            return filterOnlineList;
        }

        @Override
        public Long getCommitIdByUk(HttpMethodEnum method, String path) {
            return commitIdByUk;
        }

        @Override
        public void upsert(ApiOnlineEntity entity) {
            upserted.add(entity);
        }

        @Override
        public void deleteByApiId(Long apiId) {
            deletedByApiId.add(apiId);
        }

        @Override
        public void resetGroupByGroupId(Long groupId) {
            resetGroups.add(groupId);
        }

        @Override
        public void updateGroup(Long groupId, List<Long> ids) {
            groupUpdates.add(new Object[]{groupId, ids});
        }

        @Override
        public List<ApiAssignmentEntity> searchAll(List<Long> groupIds, List<Long> moduleIds, Boolean open,
                                                   String searchText) {
            onlineSearchCalls.add(new Object[]{groupIds, moduleIds, open, searchText});
            return searchResult;
        }
    }

    private static final class FakeVersionCommitDao extends VersionCommitDao {
        VersionCommitEntity byCommitId;
        VersionCommitEntity latest;
        List<VersionCommitEntity> versions = Collections.emptyList();
        final List<Object[]> created = new ArrayList<>();
        int byCommitIdCalls;

        @Override
        public VersionCommitEntity createVersion(Long bizId, String description, String content) {
            created.add(new Object[]{bizId, description, content});
            return VersionCommitEntity.builder().id(900L).bizId(bizId).version(1)
                    .description(description).content(content).build();
        }

        @Override
        public VersionCommitEntity getByCommitId(Long commitId) {
            byCommitIdCalls++;
            return byCommitId;
        }

        @Override
        public VersionCommitEntity getLatestVersion(Long bizId) {
            return latest;
        }

        @Override
        public List<VersionCommitEntity> getVersionList(Long bizId, boolean withContent) {
            return versions;
        }
    }

    private static final class FakeDataSourceDao extends DataSourceDao {
        DataSourceEntity byId = DataSourceEntity.builder()
                .id(2L).name("ds").type(ProductTypeEnum.MYSQL).build();
        List<DataSourceEntity> all = Collections.emptyList();

        @Override
        public DataSourceEntity getById(Long id) {
            return byId;
        }

        @Override
        public List<DataSourceEntity> listAll() {
            return all;
        }
    }

    private static final class FakeGroupDao extends ApiGroupDao {
        ApiGroupEntity byId = ApiGroupEntity.builder().id(10L).name("g").build();
        List<ApiGroupEntity> all = Collections.emptyList();

        @Override
        public ApiGroupEntity getById(Long id) {
            return byId;
        }

        @Override
        public List<ApiGroupEntity> listAll() {
            return all;
        }
    }

    private static final class FakeModuleDao extends ApiModuleDao {
        List<ApiModuleEntity> all = Collections.emptyList();

        @Override
        public List<ApiModuleEntity> listAll() {
            return all;
        }
    }

    private static final class FakeDriverLoadService extends DriverLoadService {
        @Override
        public File getVersionDriverFile(ProductTypeEnum dbTypeEnum, String driverVersion) {
            return new File(".");
        }
    }

    private static final class Harness {
        final FakeAssignmentDao assignment = new FakeAssignmentDao();
        final FakeOnlineDao online = new FakeOnlineDao();
        final FakeVersionCommitDao versionCommit = new FakeVersionCommitDao();
        final FakeDataSourceDao dataSource = new FakeDataSourceDao();
        final FakeGroupDao group = new FakeGroupDao();
        final FakeModuleDao module = new FakeModuleDao();
        final List<ApiDebugPostContext> debugHooks = new ArrayList<>();
        ApiAssignmentService service;

        Harness() {
            service = new ApiAssignmentService();
            DataTaskTestSupport.setField(service, "apiAssignmentDao", assignment);
            DataTaskTestSupport.setField(service, "apiOnlineDao", online);
            DataTaskTestSupport.setField(service, "versionCommitDao", versionCommit);
            DataTaskTestSupport.setField(service, "dataSourceDao", dataSource);
            DataTaskTestSupport.setField(service, "apiGroupDao", group);
            DataTaskTestSupport.setField(service, "apiModuleDao", module);
            DataTaskTestSupport.setField(service, "driverLoadService", new FakeDriverLoadService());
            DataTaskTestSupport.setField(service, "postProcessors", new ApiAssignmentPostProcessors(
                    Collections.singletonList(new ApiAssignmentPostProcessor() {
                        @Override
                        public void postDebug(ApiDebugPostContext context) {
                            debugHooks.add(context);
                        }
                    }), null));
        }
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

    private static final String GRAPHQL_SDL = "type Query { users: [User] @sql(sql: \"select 1\") }\n"
            + "type User { id: Long }";

    /**
     * The exact pair the manager UI submits for a GraphQL interface: query is the document,
     * variables is an opaque JSON object (OBJECT without children) bound by the engine itself.
     */
    private static List<ItemParam> graphqlSubmittedParams() {
        ItemParam query = new ItemParam();
        query.setName(Constants.PARAM_GRAPHQL_QUERY);
        query.setType(ParamTypeEnum.STRING);
        query.setLocation(ParamLocationEnum.REQUEST_BODY);
        query.setRequired(true);
        ItemParam variables = new ItemParam();
        variables.setName(Constants.PARAM_GRAPHQL_VARIABLES);
        variables.setType(ParamTypeEnum.OBJECT);
        variables.setLocation(ParamLocationEnum.REQUEST_BODY);
        variables.setRequired(false);
        variables.setChildren(Collections.emptyList());
        return new ArrayList<>(Arrays.asList(query, variables));
    }

    private static ApiAssignmentSaveRequest validGraphqlCreateRequest() {
        ApiAssignmentSaveRequest request = new ApiAssignmentSaveRequest();
        request.setGroupId(10L);
        request.setModuleId(20L);
        request.setDatasourceId(2L);
        request.setName("gql-demo");
        request.setMethod(HttpMethodEnum.POST);
        request.setContentType("application/json");
        request.setPath("gql/demo");
        request.setOpen(false);
        request.setAlarm(false);
        request.setEngine(ExecuteEngineEnum.GRAPHQL);
        request.setContextList(Collections.singletonList(GRAPHQL_SDL));
        request.setParams(graphqlSubmittedParams());
        request.setFormatMap(new ArrayList<DataTypeFormatMapValue>());
        return request;
    }

    private static ApiAssignmentSaveRequest validGraphqlUpdateRequest() {
        ApiAssignmentSaveRequest request = validGraphqlCreateRequest();
        request.setId(1L);
        request.setPath("/demo");
        return request;
    }

    private static CreateHarness wireCreateService() {
        CreateHarness harness = new CreateHarness();
        ApiAssignmentService service = new ApiAssignmentService();
        DataTaskTestSupport.setField(service, "apiAssignmentDao", new ApiAssignmentDao() {
            @Override
            public ApiAssignmentEntity getByUk(HttpMethodEnum method, String path) {
                return null;
            }

            @Override
            public void insert(ApiAssignmentEntity entity) {
                harness.inserted.add(entity);
            }
        });
        DataTaskTestSupport.setField(service, "dataSourceDao", new DataSourceDao() {
            @Override
            public DataSourceEntity getById(Long id) {
                return new DataSourceEntity();
            }
        });
        harness.service = service;
        return harness;
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

    private static final class CreateHarness {
        final List<ApiAssignmentEntity> inserted = new ArrayList<>();
        ApiAssignmentService service;
    }
}
