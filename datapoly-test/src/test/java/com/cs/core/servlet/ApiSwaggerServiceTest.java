// Use of this source code is governed by a BSD-style license
package com.cs.core.servlet;

import com.cs.common.consts.Constants;
import com.cs.common.dto.BaseParam;
import com.cs.common.dto.ItemParam;
import com.cs.common.dto.OutParam;
import com.cs.common.enums.DataTypeFormatEnum;
import com.cs.common.enums.HttpMethodEnum;
import com.cs.common.enums.ParamLocationEnum;
import com.cs.common.enums.ParamTypeEnum;
import com.cs.persistence.dao.ApiModuleDao;
import com.cs.persistence.dao.ApiOnlineDao;
import com.cs.persistence.dao.SystemParamDao;
import com.cs.persistence.entity.ApiAssignmentEntity;
import com.cs.persistence.entity.ApiModuleEntity;
import com.cs.persistence.entity.SystemParamEntity;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.parameters.HeaderParameter;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.parameters.QueryParameter;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.tags.Tag;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.*;

import static org.junit.Assert.*;

/**
 * Swagger doc generation from configured assignments: token endpoint shape,
 * module tags, parameter/body/response schema variants and the http method
 * mapping. DAOs are replaced by in-memory stubs, no servlet container needed
 * (the request argument is never read).
 */
public class ApiSwaggerServiceTest {

    private StubOnlineDao onlineDao;
    private StubModuleDao moduleDao;
    private StubParamDao paramDao;
    private ApiSwaggerService service;

    @Before
    public void setUp() throws Exception {
        onlineDao = new StubOnlineDao();
        moduleDao = new StubModuleDao();
        paramDao = new StubParamDao();
        service = new ApiSwaggerService();
        inject("apiOnlineDao", onlineDao);
        inject("apiModuleDao", moduleDao);
        inject("systemParamDao", paramDao);
    }

    // ------------------------------------------------------------------
    // info + token endpoint
    // ------------------------------------------------------------------

    @Test
    public void infoFallsBackToDefaultsWhenParamsMissing() {
        OpenAPI openAPI = service.getSwaggerJson(null);

        assertEquals("DataPoly在线接口文档", openAPI.getInfo().getTitle());
        assertEquals("1.0", openAPI.getInfo().getVersion());
        assertEquals("基于DataPoly配置生成的Swagger在线接口文档", openAPI.getInfo().getDescription());
    }

    @Test
    public void infoUsesConfiguredSystemParams() {
        paramDao.rows.put(Constants.SYS_PARAM_KEY_SWAGGER_INFO_TITLE,
                entity(Constants.SYS_PARAM_KEY_SWAGGER_INFO_TITLE, "定制标题"));
        paramDao.rows.put(Constants.SYS_PARAM_KEY_SWAGGER_INFO_VERSION,
                entity(Constants.SYS_PARAM_KEY_SWAGGER_INFO_VERSION, "2.5"));
        paramDao.rows.put(Constants.SYS_PARAM_KEY_SWAGGER_INFO_DESCRIPTION,
                entity(Constants.SYS_PARAM_KEY_SWAGGER_INFO_DESCRIPTION, "定制描述"));

        OpenAPI openAPI = service.getSwaggerJson(null);

        assertEquals("定制标题", openAPI.getInfo().getTitle());
        assertEquals("2.5", openAPI.getInfo().getVersion());
        assertEquals("定制描述", openAPI.getInfo().getDescription());
    }

    @Test
    public void tokenGenerateEndpointShape() {
        OpenAPI openAPI = service.getSwaggerJson(null);

        // only the token endpoint when nothing is online
        assertEquals(Collections.singleton("/token/generate"), openAPI.getPaths().keySet());

        Operation operation = openAPI.getPaths().get("/token/generate").getPost();
        assertNotNull(operation);
        assertEquals("0", operation.getOperationId());
        assertEquals("TokenAuthentication&Authorization", operation.getSummary());
        assertEquals(Collections.singletonList("TokenAuthentication&Authorization"), operation.getTags());

        // request body references the first generated entity
        assertTrue(operation.getRequestBody().getRequired());
        MediaType requestMediaType = operation.getRequestBody().getContent().get("application/json");
        assertEquals("#/components/schemas/BeanEntity00001", requestMediaType.getSchema().get$ref());
        Schema<?> inputEntity = openAPI.getComponents().getSchemas().get("BeanEntity00001");
        assertEquals("string", inputEntity.getProperties().get("clientId").getType());
        assertEquals("string", inputEntity.getProperties().get("secret").getType());

        // responses: 200 with the output entity plus the fixed status codes
        ApiResponses responses = operation.getResponses();
        assertEquals("#/components/schemas/BeanEntity00002",
                responses.get("200").getContent().get("application/json").getSchema().get$ref());
        assertEquals("Created", responses.get("201").getDescription());
        assertEquals("Unauthorized", responses.get("401").getDescription());
        assertEquals("Forbidden", responses.get("403").getDescription());
        assertEquals("Not Found", responses.get("404").getDescription());

        Schema<?> outputEntity = openAPI.getComponents().getSchemas().get("BeanEntity00002");
        assertEquals("string", outputEntity.getProperties().get("accessToken").getType());
        assertEquals("number", outputEntity.getProperties().get("expireSeconds").getType());
    }

    @Test
    public void bearerSecuritySchemeIsRegistered() {
        OpenAPI openAPI = service.getSwaggerJson(null);

        SecurityScheme scheme = openAPI.getComponents().getSecuritySchemes().get("Authorization");
        assertNotNull(scheme);
        assertEquals(SecurityScheme.Type.HTTP, scheme.getType());
        assertEquals("bearer", scheme.getScheme());
        assertEquals("JWT", scheme.getBearerFormat());
    }

    // ------------------------------------------------------------------
    // modules + method mapping
    // ------------------------------------------------------------------

    @Test
    public void moduleRowsBecomeTagsAndMissingModuleFallsBackToAssignmentName() {
        moduleDao.modules.add(module(1L, "module-a"));
        moduleDao.modules.add(module(2L, "module-b"));
        onlineDao.assignments.add(assignment(10L, 1L, HttpMethodEnum.GET, "one", null));
        // module 99 was deleted after the assignment referenced it
        onlineDao.assignments.add(assignment(11L, 99L, HttpMethodEnum.POST, "orphan-api", null));

        OpenAPI openAPI = service.getSwaggerJson(null);

        List<String> tagNames = new ArrayList<>();
        for (Tag tag : openAPI.getTags()) {
            tagNames.add(tag.getName());
        }
        assertEquals(Arrays.asList("module-a", "module-b"), tagNames);

        assertEquals(Collections.singletonList("module-a"),
                openAPI.getPaths().get("/api/one").getGet().getTags());
        assertEquals(Collections.singletonList("api-orphan-api"),
                openAPI.getPaths().get("/api/orphan-api").getPost().getTags());
    }

    @Test
    public void duplicateModuleIdsKeepFirstRowForTagLookup() {
        moduleDao.modules.add(module(1L, "first"));
        moduleDao.modules.add(module(1L, "second"));
        onlineDao.assignments.add(assignment(10L, 1L, HttpMethodEnum.GET, "one", null));

        OpenAPI openAPI = service.getSwaggerJson(null);

        // both rows are exported as tags, but the lookup map deduplicates to the first
        assertEquals(2, openAPI.getTags().size());
        assertEquals(Collections.singletonList("first"),
                openAPI.getPaths().get("/api/one").getGet().getTags());
    }

    @Test
    public void eachHttpMethodLandsOnItsPathItemOperation() {
        onlineDao.assignments.add(assignment(10L, null, HttpMethodEnum.GET, "get-api", null));
        onlineDao.assignments.add(assignment(11L, null, HttpMethodEnum.HEAD, "head-api", null));
        onlineDao.assignments.add(assignment(12L, null, HttpMethodEnum.PUT, "put-api", null));
        onlineDao.assignments.add(assignment(13L, null, HttpMethodEnum.POST, "post-api", null));
        onlineDao.assignments.add(assignment(14L, null, HttpMethodEnum.DELETE, "delete-api", null));

        OpenAPI openAPI = service.getSwaggerJson(null);

        assertNotNull(openAPI.getPaths().get("/api/get-api").getGet());
        assertNotNull(openAPI.getPaths().get("/api/head-api").getHead());
        assertNotNull(openAPI.getPaths().get("/api/put-api").getPut());
        assertNotNull(openAPI.getPaths().get("/api/post-api").getPost());
        assertNotNull(openAPI.getPaths().get("/api/delete-api").getDelete());
        assertNull(openAPI.getPaths().get("/api/get-api").getPost());
        assertNull(openAPI.getPaths().get("/api/post-api").getGet());
    }

    @Test
    public void assignmentWithoutMethodIsSkipped() {
        // a hand-edited row without a method must not break the whole doc
        onlineDao.assignments.add(assignment(10L, null, null, "broken-api", null));
        onlineDao.assignments.add(assignment(11L, null, HttpMethodEnum.GET, "good-api", null));

        OpenAPI openAPI = service.getSwaggerJson(null);

        assertFalse(openAPI.getPaths().containsKey("/api/broken-api"));
        assertTrue(openAPI.getPaths().containsKey("/api/good-api"));
    }

    @Test
    public void blankDescriptionFallsBackToApiName() {
        ApiAssignmentEntity assignment = assignment(10L, null, HttpMethodEnum.GET, "name-only", "   ");
        onlineDao.assignments.add(assignment);

        OpenAPI openAPI = service.getSwaggerJson(null);

        assertEquals("api-name-only", openAPI.getPaths().get("/api/name-only").getSummary());
        assertEquals("api-name-only", openAPI.getPaths().get("/api/name-only").getDescription());
        Operation operation = openAPI.getPaths().get("/api/name-only").getGet();
        assertEquals("api-name-only", operation.getSummary());
        assertEquals("api-name-only", operation.getDescription());

        ApiAssignmentEntity described = assignment(11L, null, HttpMethodEnum.GET, "two", "real description");
        onlineDao.assignments.add(described);
        OpenAPI regenerated = service.getSwaggerJson(null);
        assertEquals("real description", regenerated.getPaths().get("/api/two").getDescription());
    }

    // ------------------------------------------------------------------
    // input parameters
    // ------------------------------------------------------------------

    @Test
    public void headerAndQueryParametersWithArrayAndSkipInvalidEntries() {
        ApiAssignmentEntity assignment = assignment(10L, null, HttpMethodEnum.GET, "params-api", "d");
        List<ItemParam> params = new ArrayList<>();
        ItemParam traceHeader = param("X-Trace", ParamTypeEnum.LONG, ParamLocationEnum.REQUEST_HEADER,
                true, "trace id");
        traceHeader.setIsArray(true);
        params.add(traceHeader);
        params.add(param("city", ParamTypeEnum.STRING, ParamLocationEnum.REQUEST_FORM, false, "city name"));
        // legacy/hand-edited rows without location or type are dropped
        params.add(param("no-location", ParamTypeEnum.STRING, null, false, null));
        params.add(param("no-type", null, ParamLocationEnum.REQUEST_FORM, false, null));
        assignment.setParams(params);
        onlineDao.assignments.add(assignment);

        Operation operation = service.getSwaggerJson(null).getPaths().get("/api/params-api").getGet();

        assertEquals(2, operation.getParameters().size());

        Parameter header = operation.getParameters().get(0);
        assertTrue(header instanceof HeaderParameter);
        assertEquals("X-Trace", header.getName());
        assertEquals("header", header.getIn());
        assertTrue(header.getRequired());
        assertFalse(header.getDeprecated());
        assertTrue(header.getAllowEmptyValue());
        assertEquals("trace id", header.getDescription());
        assertTrue(header.getSchema() instanceof ArraySchema);
        assertEquals("number", ((ArraySchema) header.getSchema()).getItems().getType());
        assertEquals(Long.valueOf(0L), header.getExample());

        Parameter query = operation.getParameters().get(1);
        assertTrue(query instanceof QueryParameter);
        assertEquals("city", query.getName());
        assertEquals("query", query.getIn());
        assertFalse(query.getRequired());
        assertEquals("string", query.getSchema().getType());
        assertEquals("", query.getExample());
        assertNull(operation.getRequestBody());
    }

    @Test
    public void requestBodySchemaVariants() {
        ApiAssignmentEntity assignment = assignment(10L, null, HttpMethodEnum.POST, "body-api", "d");
        assignment.setContentType("application/json");

        List<ItemParam> params = new ArrayList<>();
        // scalar body param
        params.add(param("keyword", ParamTypeEnum.STRING, ParamLocationEnum.REQUEST_BODY, false, "kw"));
        // object body param with typed/array/untyped children
        ItemParam objectParam = param("filter", ParamTypeEnum.OBJECT, ParamLocationEnum.REQUEST_BODY, false, "filter");
        List<BaseParam> children = new ArrayList<>();
        children.add(child("userId", ParamTypeEnum.LONG, false));
        children.add(child("labels", ParamTypeEnum.STRING, true));
        children.add(child("untyped", null, false));
        children.add(child("unknownArrayFlag", ParamTypeEnum.DATE, null));
        objectParam.setChildren(children);
        params.add(objectParam);
        // object body param without children
        params.add(param("opaque", ParamTypeEnum.OBJECT, ParamLocationEnum.REQUEST_BODY, false, null));
        // array of objects with children
        ItemParam objectArray = param("rows", ParamTypeEnum.OBJECT, ParamLocationEnum.REQUEST_BODY, false, null);
        objectArray.setIsArray(true);
        objectArray.setChildren(Arrays.asList(
                child("dt", ParamTypeEnum.DATE, false),
                child("flags", ParamTypeEnum.BOOLEAN, true)));
        params.add(objectArray);
        // plain scalar array
        ItemParam scalarArray = param("scores", ParamTypeEnum.DOUBLE, ParamLocationEnum.REQUEST_BODY, false, null);
        scalarArray.setIsArray(true);
        params.add(scalarArray);
        assignment.setParams(params);
        onlineDao.assignments.add(assignment);

        OpenAPI openAPI = service.getSwaggerJson(null);
        Operation operation = openAPI.getPaths().get("/api/body-api").getPost();

        assertTrue(operation.getRequestBody().getRequired());
        assertEquals("api-body-api", operation.getRequestBody().getDescription());
        MediaType media = operation.getRequestBody().getContent().get("application/json");
        // token endpoint consumed BeanEntity00001/00002, this body is the third entity
        assertEquals("#/components/schemas/BeanEntity00003", media.getSchema().get$ref());

        ObjectSchema body = (ObjectSchema) openAPI.getComponents().getSchemas().get("BeanEntity00003");
        assertEquals(5, body.getProperties().size());

        assertEquals("string", body.getProperties().get("keyword").getType());
        assertEquals("kw", body.getProperties().get("keyword").getDescription());

        ObjectSchema filter = (ObjectSchema) body.getProperties().get("filter");
        assertEquals("number", filter.getProperties().get("userId").getType());
        assertTrue(filter.getProperties().get("labels") instanceof ArraySchema);
        assertEquals("string", ((ArraySchema) filter.getProperties().get("labels")).getItems().getType());
        // untyped child keeps a null js type, null isArray flag reads as scalar
        assertNull(filter.getProperties().get("untyped").getType());
        assertEquals("string", filter.getProperties().get("unknownArrayFlag").getType());

        // object without children contributes an empty object schema
        ObjectSchema opaque = (ObjectSchema) body.getProperties().get("opaque");
        assertTrue(opaque.getProperties() == null || opaque.getProperties().isEmpty());

        // array of objects wraps the child schema in an items node
        ArraySchema rows = (ArraySchema) body.getProperties().get("rows");
        assertEquals("string", ((ObjectSchema) rows.getItems()).getProperties().get("dt").getType());

        ArraySchema scores = (ArraySchema) body.getProperties().get("scores");
        assertEquals("number", scores.getItems().getType());
    }

    // ------------------------------------------------------------------
    // responses
    // ------------------------------------------------------------------

    @Test
    public void systemResponseFormatEnvelopesOutputs() {
        ApiAssignmentEntity assignment = assignment(10L, null, HttpMethodEnum.POST, "out-api", null);
        assignment.setContentType("application/json");
        List<OutParam> outputs = new ArrayList<>();
        outputs.add(out("total", ParamTypeEnum.LONG, false, null));
        OutParam ratio = out("ratio", ParamTypeEnum.DOUBLE, true, "ratio");
        outputs.add(ratio);
        OutParam user = out("user", ParamTypeEnum.OBJECT, false, "the user");
        List<OutParam> userChildren = new ArrayList<>();
        userChildren.add(out("id", ParamTypeEnum.LONG, false, null));
        userChildren.add(out("born", ParamTypeEnum.DATE, false, null));
        OutParam wake = out("wake", ParamTypeEnum.TIME, true, null);
        userChildren.add(wake);
        userChildren.add(out("active", ParamTypeEnum.BOOLEAN, false, null));
        userChildren.add(out("loose", null, false, null));
        user.setChildren(userChildren);
        outputs.add(user);
        // untyped output rows are skipped instead of breaking the doc
        outputs.add(out("junk", null, false, null));
        assignment.setOutputs(outputs);
        onlineDao.assignments.add(assignment);

        OpenAPI openAPI = service.getSwaggerJson(null);
        Operation operation = openAPI.getPaths().get("/api/out-api").getPost();

        // the api requires bearer auth
        List<SecurityRequirement> security = operation.getSecurity();
        assertEquals(1, security.size());
        assertTrue(security.get(0).containsKey("Authorization"));

        // token endpoint took 00001/00002, this response is the third entity
        assertEquals("#/components/schemas/BeanEntity00003",
                operation.getResponses().get("200").getContent().get("application/json")
                        .getSchema().get$ref());
        assertEquals("Created", operation.getResponses().get("201").getDescription());

        ObjectSchema root = (ObjectSchema) openAPI.getComponents().getSchemas().get("BeanEntity00003");
        assertEquals("number", root.getProperties().get("code").getType());
        assertEquals("string", root.getProperties().get("message").getType());

        ObjectSchema data = (ObjectSchema) root.getProperties().get("data");
        assertEquals(3, data.getProperties().size());
        assertEquals("number", data.getProperties().get("total").getType());
        assertEquals("int64", data.getProperties().get("total").getFormat());

        ArraySchema ratioArray = (ArraySchema) data.getProperties().get("ratio");
        assertEquals("number", ratioArray.getItems().getType());
        assertEquals("float32", ratioArray.getItems().getFormat());

        ObjectSchema userSchema = (ObjectSchema) data.getProperties().get("user");
        assertEquals("the user", userSchema.getDescription());
        assertEquals("int64", userSchema.getProperties().get("id").getFormat());
        assertEquals("date-time", userSchema.getProperties().get("born").getFormat());
        ArraySchema wakeArray = (ArraySchema) userSchema.getProperties().get("wake");
        assertEquals("time", wakeArray.getItems().getFormat());
        // boolean maps to a null format (default branch of getTypeFormat)
        assertNull(userSchema.getProperties().get("active").getFormat());
    }

    @Test
    public void systemFormatWithoutOutputsOmitsDataProperty() {
        ApiAssignmentEntity assignment = assignment(10L, null, HttpMethodEnum.GET, "noout-api", null);
        assignment.setOutputs(Collections.emptyList());
        onlineDao.assignments.add(assignment);

        OpenAPI openAPI = service.getSwaggerJson(null);
        ObjectSchema root = outputRootSchema(openAPI, 3);

        assertEquals(Arrays.asList("code", "message"), new ArrayList<>(root.getProperties().keySet()));
    }

    @Test
    public void explicitSystemFormatTrueBehavesLikeDefault() {
        ApiAssignmentEntity assignment = assignment(10L, null, HttpMethodEnum.GET, "sysfmt-api", null);
        assignment.setResponseFormat(Collections.singletonMap(
                DataTypeFormatEnum.USE_SYSTEM_RESPONSE_FORMAT, "true"));
        assignment.setOutputs(Collections.singletonList(out("n", ParamTypeEnum.LONG, false, null)));
        onlineDao.assignments.add(assignment);

        ObjectSchema root = outputRootSchema(service.getSwaggerJson(null), 3);
        assertEquals(Arrays.asList("code", "message", "data"), new ArrayList<>(root.getProperties().keySet()));
    }

    @Test
    public void rawResponseFormatSkipsTheEnvelope() {
        ApiAssignmentEntity assignment = assignment(10L, null, HttpMethodEnum.GET, "raw-api", null);
        assignment.setResponseFormat(Collections.singletonMap(
                DataTypeFormatEnum.USE_SYSTEM_RESPONSE_FORMAT, "false"));
        List<OutParam> outputs = new ArrayList<>();
        outputs.add(out("count", ParamTypeEnum.LONG, false, null));
        OutParam tagList = out("tags", ParamTypeEnum.STRING, true, null);
        outputs.add(tagList);
        OutParam nested = out("meta", ParamTypeEnum.OBJECT, false, "meta");
        nested.setChildren(Arrays.asList(
                out("flag", ParamTypeEnum.BOOLEAN, false, null),
                out("untyped-child", null, false, null),
                out("times", ParamTypeEnum.TIME, true, null)));
        outputs.add(nested);
        // untyped top-level output rows are skipped in raw mode too
        outputs.add(out("junk", null, false, null));
        assignment.setOutputs(outputs);
        onlineDao.assignments.add(assignment);

        OpenAPI openAPI = service.getSwaggerJson(null);
        ObjectSchema root = outputRootSchema(openAPI, 3);

        // no code/message/data envelope in raw mode
        assertEquals(Arrays.asList("count", "tags", "meta"), new ArrayList<>(root.getProperties().keySet()));
        assertEquals("int64", root.getProperties().get("count").getFormat());
        assertEquals("string", ((ArraySchema) root.getProperties().get("tags")).getItems().getType());
        ObjectSchema meta = (ObjectSchema) root.getProperties().get("meta");
        assertNull(meta.getProperties().get("flag").getFormat());
        assertEquals("time", ((ArraySchema) meta.getProperties().get("times")).getItems().getFormat());
    }

    @Test
    public void rawResponseFormatWithoutOutputsIsEmptyObject() {
        ApiAssignmentEntity assignment = assignment(10L, null, HttpMethodEnum.GET, "rawempty-api", null);
        assignment.setResponseFormat(Collections.singletonMap(
                DataTypeFormatEnum.USE_SYSTEM_RESPONSE_FORMAT, "false"));
        assignment.setOutputs(null);
        onlineDao.assignments.add(assignment);

        OpenAPI openAPI = service.getSwaggerJson(null);
        ObjectSchema root = outputRootSchema(openAPI, 3);
        assertTrue(root.getProperties() == null || root.getProperties().isEmpty());
    }

    @Test
    public void entityCounterResetsBetweenCallsAndIncrementsPerApi() {
        onlineDao.assignments.add(assignment(10L, null, HttpMethodEnum.GET, "first", null));
        onlineDao.assignments.add(assignment(11L, null, HttpMethodEnum.GET, "second", null));

        OpenAPI openAPI = service.getSwaggerJson(null);
        // token endpoint: 00001 + 00002, then one response entity per assignment
        assertTrue(openAPI.getComponents().getSchemas().containsKey("BeanEntity00001"));
        assertTrue(openAPI.getComponents().getSchemas().containsKey("BeanEntity00004"));
        assertEquals(4, openAPI.getComponents().getSchemas().size());

        // second call restarts numbering: the ThreadLocal counter is cleared first
        OpenAPI again = service.getSwaggerJson(null);
        assertTrue(again.getComponents().getSchemas().containsKey("BeanEntity00001"));
        assertEquals(4, again.getComponents().getSchemas().size());
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    /** Root response schema of the single assignment: the token endpoint consumes 2 ids. */
    private ObjectSchema outputRootSchema(OpenAPI openAPI, int entityOrdinal) {
        String expectedName = String.format("BeanEntity%05d", entityOrdinal);
        String ref = null;
        for (Map.Entry<String, io.swagger.v3.oas.models.PathItem> entry : openAPI.getPaths().entrySet()) {
            if ("/token/generate".equals(entry.getKey())) {
                continue;
            }
            Operation operation = entry.getValue().getGet() != null
                    ? entry.getValue().getGet() : entry.getValue().getPost();
            ApiResponse ok = operation.getResponses().get("200");
            ref = ok.getContent().get("application/json").getSchema().get$ref();
        }
        assertNotNull("no 200 response found", ref);
        assertEquals("#/components/schemas/" + expectedName, ref);
        return (ObjectSchema) openAPI.getComponents().getSchemas().get(expectedName);
    }

    private void inject(String name, Object value) throws Exception {
        Field field = ApiSwaggerService.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(service, value);
    }

    private static SystemParamEntity entity(String key, String value) {
        return SystemParamEntity.builder().paramKey(key).paramValue(value).build();
    }

    private static ApiModuleEntity module(Long id, String name) {
        return ApiModuleEntity.builder().id(id).name(name).build();
    }

    private static ApiAssignmentEntity assignment(Long id, Long moduleId, HttpMethodEnum method,
                                                  String path, String description) {
        return ApiAssignmentEntity.builder()
                .id(id)
                .moduleId(moduleId)
                .method(method)
                .path(path)
                .name("api-" + path)
                .description(description)
                .contentType("application/json")
                .build();
    }

    private static ItemParam param(String name, ParamTypeEnum type, ParamLocationEnum location,
                                   boolean required, String remark) {
        ItemParam param = new ItemParam();
        param.setName(name);
        param.setType(type);
        param.setLocation(location);
        param.setIsArray(false);
        param.setRequired(required);
        param.setRemark(remark);
        return param;
    }

    private static BaseParam child(String name, ParamTypeEnum type, Boolean isArray) {
        BaseParam baseParam = new BaseParam();
        baseParam.setName(name);
        baseParam.setType(type);
        baseParam.setIsArray(isArray);
        return baseParam;
    }

    private static OutParam out(String name, ParamTypeEnum type, boolean isArray, String remark) {
        OutParam outParam = new OutParam();
        outParam.setName(name);
        outParam.setType(type);
        outParam.setIsArray(isArray);
        outParam.setRemark(remark);
        return outParam;
    }

    private static final class StubOnlineDao extends ApiOnlineDao {
        private final List<ApiAssignmentEntity> assignments = new ArrayList<>();

        @Override
        public List<ApiAssignmentEntity> listAll() {
            return assignments;
        }
    }

    private static final class StubModuleDao extends ApiModuleDao {
        private final List<ApiModuleEntity> modules = new ArrayList<>();

        @Override
        public List<ApiModuleEntity> listAll(String searchText) {
            return modules;
        }
    }

    private static final class StubParamDao extends SystemParamDao {
        private final Map<String, SystemParamEntity> rows = new HashMap<>();

        @Override
        public SystemParamEntity getByParamKey(String paramKey) {
            return rows.get(paramKey);
        }
    }
}
