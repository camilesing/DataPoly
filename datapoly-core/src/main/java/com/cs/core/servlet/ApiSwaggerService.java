// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.core.servlet;

import com.cs.common.consts.Constants;
import com.cs.common.dto.*;
import com.cs.common.enums.*;
import com.cs.persistence.dao.*;
import com.cs.persistence.entity.*;
import com.google.common.collect.ImmutableMap;
import io.swagger.v3.oas.models.*;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.*;
import io.swagger.v3.oas.models.parameters.*;
import io.swagger.v3.oas.models.responses.*;
import io.swagger.v3.oas.models.security.*;
import io.swagger.v3.oas.models.security.SecurityScheme.Type;
import io.swagger.v3.oas.models.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Triple;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Documentation format reference: https://openapi.apifox.cn/
 *
 * <p>SwaggerEditor: https://editor.swagger.io/
 */
@Slf4j
@Service
public class ApiSwaggerService {

    private static final String INFO_TITLE = "DataPoly在线接口文档";
    private static final String INFO_VERSION = "1.0";
    private static final String INFO_DESCRIPTION = "基于DataPoly配置生成的Swagger在线接口文档";
    private static final String TOKEN_MODEL = "TokenAuthentication&Authorization";
    private static final String APPLICATION_JSON = "application/json";
    private static final String AUTHORIZATION = "Authorization";

    private static final ThreadLocal<Integer> THREAD_LOCAL = new ThreadLocal<>();

    @Resource
    private ApiOnlineDao apiOnlineDao;
    @Resource
    private ApiModuleDao apiModuleDao;
    @Resource
    private SystemParamDao systemParamDao;

    private String getApiUrlPrefix() {
        return String.format("/%s/", Constants.API_PATH_PREFIX);
    }

    private void clearThreadLocal() {
        THREAD_LOCAL.remove();
    }

    private String genParamEntityName() {
        Integer oldIdx = Optional.ofNullable(THREAD_LOCAL.get()).orElse(0);
        Integer newIdx = oldIdx + 1;
        THREAD_LOCAL.set(newIdx);
        return "BeanEntity" + String.format("%05d", newIdx);
    }

    private Triple<String, String, String> getOpenApiInfo() {
        SystemParamEntity title =
                systemParamDao.getByParamKey(Constants.SYS_PARAM_KEY_SWAGGER_INFO_TITLE);
        SystemParamEntity version =
                systemParamDao.getByParamKey(Constants.SYS_PARAM_KEY_SWAGGER_INFO_VERSION);
        SystemParamEntity description =
                systemParamDao.getByParamKey(Constants.SYS_PARAM_KEY_SWAGGER_INFO_DESCRIPTION);
        return Triple.of(
                Optional.ofNullable(title).map(SystemParamEntity::getParamValue).orElse(INFO_TITLE),
                Optional.ofNullable(version).map(SystemParamEntity::getParamValue).orElse(INFO_VERSION),
                Optional.ofNullable(description)
                        .map(SystemParamEntity::getParamValue)
                        .orElse(INFO_DESCRIPTION));
    }

    public OpenAPI getSwaggerJson(HttpServletRequest request) {
        clearThreadLocal();

        Triple<String, String, String> entity = getOpenApiInfo();
        Info info =
                new Info()
                        .title(entity.getLeft())
                        .version(entity.getMiddle())
                        .description(entity.getRight());

        OpenAPI openAPI = new OpenAPI();
        openAPI.info(info);
        openAPI.setComponents(new Components());

        // Modules
        List<ApiModuleEntity> moduleEntities = apiModuleDao.listAll();
        for (ApiModuleEntity module : moduleEntities) {
            openAPI.addTagsItem(new Tag().name(module.getName()));
        }

        // Token generation endpoint
        openAPI.path("/token/generate", getTokenGeneratePathItem(openAPI));

        // Custom API endpoints
        Map<Long, ApiModuleEntity> moduleIdMap =
                moduleEntities.stream()
                        .collect(Collectors.toMap(ApiModuleEntity::getId, Function.identity(), (a, b) -> a));
        String urlPrefix = getApiUrlPrefix();
        for (ApiAssignmentEntity assignment : apiOnlineDao.listAll()) {
            String path = urlPrefix + assignment.getPath();
            HttpMethodEnum method = assignment.getMethod();

            PathItem pathItem = new PathItem();
            pathItem.setSummary(assignment.getName());
            pathItem.setDescription(
                    StringUtils.defaultIfBlank(assignment.getDescription(), assignment.getName()));

            Operation operation = new Operation();
            operation.setOperationId(String.valueOf(assignment.getId()));
            operation.addTagsItem(moduleIdMap.get(assignment.getModuleId()).getName());
            // FIX: when importing the API doc into apifox, the method name failed to display as the API name
            operation.setSummary(assignment.getName());
            operation.setDescription(
                    StringUtils.defaultIfBlank(assignment.getDescription(), assignment.getName()));

            // Input parameters
            List<ItemParam> params = assignment.getParams();
            if (!CollectionUtils.isEmpty(params)) {
                List<ItemParam> paramList =
                        params.stream().filter(i -> i.getLocation().isParameter()).collect(Collectors.toList());
                for (ItemParam param : paramList) {
                    ParamTypeEnum type = param.getType();

                    Parameter parameter =
                            param.getLocation().isHeader() ? new HeaderParameter() : new QueryParameter();
                    parameter.setName(param.getName());

                    Schema schema = new Schema().type(type.getJsType());
                    if (param.getIsArray()) {
                        ArraySchema arraySchema = new ArraySchema().items(schema);
                        parameter.setSchema(arraySchema);
                    } else {
                        parameter.setSchema(schema);
                    }
                    parameter.setDescription(param.getRemark());
                    parameter.setRequired(param.getRequired());
                    parameter.setDeprecated(false);
                    parameter.allowEmptyValue(true);
                    parameter.setExample(param.getType().getExample());

                    operation.addParametersItem(parameter);
                }

                List<ItemParam> requestBodyList =
                        params.stream()
                                .filter(i -> i.getLocation().isRequestBody())
                                .collect(Collectors.toList());
                if (requestBodyList.size() > 0) {
                    RequestBody requestBody = new RequestBody();
                    requestBody.setRequired(true);
                    requestBody.setDescription(assignment.getName());

                    Schema objectSchema = new ObjectSchema().name("type");
                    for (ItemParam param : requestBodyList) {
                        ParamTypeEnum type = param.getType();

                        Schema schema = new Schema().type(type.getJsType()).description(param.getRemark());
                        if (param.getIsArray()) {
                            if (type.isObject()) {
                                Schema subObjectSchema =
                                        new ObjectSchema().name(param.getName()).description(param.getRemark());
                                if (null != param.getChildren()) {
                                    for (BaseParam baseParam : param.getChildren()) {
                                        Schema subSchema =
                                                new Schema()
                                                        .type(baseParam.getType().getJsType())
                                                        .description(baseParam.getRemark());
                                        if (Optional.ofNullable(baseParam.getIsArray()).orElse(false)) {
                                            ArraySchema subArraySchema = new ArraySchema().items(subSchema);
                                            subObjectSchema.addProperties(baseParam.getName(), subArraySchema);
                                        } else {
                                            subObjectSchema.addProperties(baseParam.getName(), subSchema);
                                        }
                                    }
                                }
                                ArraySchema arraySchema = new ArraySchema().items(subObjectSchema);
                                objectSchema.addProperties(param.getName(), arraySchema);
                            } else {
                                ArraySchema arraySchema = new ArraySchema().items(schema);
                                objectSchema.addProperties(param.getName(), arraySchema);
                            }
                        } else {
                            if (type.isObject()) {
                                Schema subObjectSchema =
                                        new ObjectSchema().name(param.getName()).description(param.getRemark());
                                if (null != param.getChildren()) {
                                    for (BaseParam baseParam : param.getChildren()) {
                                        Schema subSchema =
                                                new Schema()
                                                        .type(baseParam.getType().getJsType())
                                                        .description(baseParam.getRemark());
                                        if (Optional.ofNullable(baseParam.getIsArray()).orElse(false)) {
                                            ArraySchema subArraySchema = new ArraySchema().items(subSchema);
                                            subObjectSchema.addProperties(baseParam.getName(), subArraySchema);
                                        } else {
                                            subObjectSchema.addProperties(baseParam.getName(), subSchema);
                                        }
                                    }
                                }
                                objectSchema.addProperties(param.getName(), subObjectSchema);
                            } else {
                                objectSchema.addProperties(param.getName(), schema);
                            }
                        }
                    }

                    Content content = new Content();
                    Schema inputSchema = new Schema();
                    String inputEntityName = genParamEntityName();
                    openAPI.getComponents().addSchemas(inputEntityName, objectSchema);
                    inputSchema.set$ref("#/components/schemas/" + inputEntityName);
                    content.addMediaType(assignment.getContentType(), new MediaType().schema(inputSchema));
                    requestBody.setContent(content);
                    operation.setRequestBody(requestBody);
                }
            }

            if (HttpMethodEnum.GET == method) {
                pathItem.setGet(operation);
            } else if (HttpMethodEnum.HEAD == method) {
                pathItem.setHead(operation);
            } else if (HttpMethodEnum.PUT == method) {
                pathItem.setPut(operation);
            } else if (HttpMethodEnum.POST == method) {
                pathItem.setPost(operation);
            } else if (HttpMethodEnum.DELETE == method) {
                pathItem.setDelete(operation);
            } else {
                continue;
            }

            // Response
            operation.setResponses(getApiResponses(assignment, openAPI));
            operation.security(
                    Collections.singletonList(new SecurityRequirement().addList(AUTHORIZATION)));

            openAPI.path(path, pathItem);
        }

        Map<String, SecurityScheme> securitySchemes =
                ImmutableMap.of(
                        AUTHORIZATION,
                        new SecurityScheme().type(Type.HTTP).scheme("bearer").bearerFormat("JWT"));
        openAPI.getComponents().securitySchemes(securitySchemes);

        return openAPI;
    }

    private PathItem getTokenGeneratePathItem(OpenAPI openAPI) {
        PathItem pathItem = new PathItem();
        pathItem.setSummary(TOKEN_MODEL);
        pathItem.setDescription(TOKEN_MODEL);

        RequestBody requestBody = new RequestBody();
        requestBody.setRequired(true);
        requestBody.setDescription(TOKEN_MODEL);
        Content content = new Content();
        Schema requestSchema =
                new ObjectSchema()
                        .name("type")
                        .addProperties("clientId", new StringSchema().description("client id"))
                        .addProperties("secret", new StringSchema().description("client secret"));
        Schema inputSchema = new Schema();
        String inputEntityName = genParamEntityName();
        inputSchema.set$ref("#/components/schemas/" + inputEntityName);
        openAPI.getComponents().addSchemas(inputEntityName, requestSchema);
        content.addMediaType(APPLICATION_JSON, new MediaType().schema(inputSchema));
        requestBody.setContent(content);

        Operation operation = new Operation();
        operation.setOperationId("0");
        operation.addTagsItem(TOKEN_MODEL);
        operation.setRequestBody(requestBody);
        // FIX: when importing the API doc into apifox, the method name failed to display as the API name
        operation.setSummary(TOKEN_MODEL);
        operation.setDescription(TOKEN_MODEL);

        Schema rootSchema =
                new Schema()
                        .name("type")
                        .type("object")
                        .addProperties("accessToken", new StringSchema().description("access token"))
                        .addProperties("expireSeconds", new NumberSchema().description("expire seconds"));

        ApiResponses apiResponses = new ApiResponses();
        Schema outputSchema = new Schema();
        String outputEntityName = genParamEntityName();
        outputSchema.set$ref("#/components/schemas/" + outputEntityName);
        openAPI.getComponents().addSchemas(outputEntityName, rootSchema);
        apiResponses.addApiResponse(
                "200",
                new ApiResponse()
                        .description("OK")
                        .content(
                                new Content()
                                        .addMediaType("application/json", new MediaType().schema(outputSchema))));
        apiResponses.addApiResponse("201", new ApiResponse().description("Created"));
        apiResponses.addApiResponse("401", new ApiResponse().description("Unauthorized"));
        apiResponses.addApiResponse("403", new ApiResponse().description("Forbidden"));
        apiResponses.addApiResponse("404", new ApiResponse().description("Not Found"));
        operation.setResponses(apiResponses);

        pathItem.setPost(operation);

        return pathItem;
    }

    private ApiResponses getApiResponses(ApiAssignmentEntity assignment, OpenAPI openAPI) {
        List<OutParam> outputs = assignment.getOutputs();

        // Check the USE_SYSTEM_RESPONSE_FORMAT setting
        Map<DataTypeFormatEnum, String> responseFormat = assignment.getResponseFormat();
        String useSystemResponseFormatValue =
                responseFormat != null
                        ? responseFormat.get(DataTypeFormatEnum.USE_SYSTEM_RESPONSE_FORMAT)
                        : null;
        boolean useSystemFormat = !"false".equals(useSystemResponseFormatValue);
        Schema rootSchema;
        if (useSystemFormat) {
            // System standard format: code/message/data envelope
            rootSchema =
                    new ObjectSchema()
                            .addProperties("code", new NumberSchema().description("code"))
                            .addProperties("message", new StringSchema().description("message"));

            if (!CollectionUtils.isEmpty(outputs)) {
                ObjectSchema dataSchema = new ObjectSchema();
                dataSchema.setDescription("data");
                for (OutParam param : outputs) {
                    ParamTypeEnum typeItem = param.getType();
                    if (Optional.ofNullable(param.getIsArray()).orElse(false)) {
                        Schema subSchema =
                                new Schema()
                                        .type(param.getType().getJsType())
                                        .description(param.getRemark())
                                        .format(getTypeFormat(param.getType()));
                        ArraySchema subArraySchema = new ArraySchema().items(subSchema);
                        dataSchema.addProperties(param.getName(), subArraySchema);
                    } else {
                        Schema propertiesItem;
                        if (Optional.ofNullable(typeItem.isObject()).orElse(false)) {
                            propertiesItem = new ObjectSchema().description(param.getRemark());
                            if (!CollectionUtils.isEmpty(param.getChildren())) {
                                for (OutParam subParam : param.getChildren()) {
                                    Schema subSchema =
                                            new Schema()
                                                    .type(subParam.getType().getJsType())
                                                    .description(subParam.getRemark())
                                                    .format(getTypeFormat(subParam.getType()));
                                    if (Optional.ofNullable(subParam.getIsArray()).orElse(false)) {
                                        ArraySchema subArraySchema = new ArraySchema().items(subSchema);
                                        propertiesItem.addProperties(subParam.getName(), subArraySchema);
                                    } else {
                                        propertiesItem.addProperties(subParam.getName(), subSchema);
                                    }
                                }
                            }
                        } else {
                            propertiesItem =
                                    new Schema()
                                            .type(typeItem.getJsType())
                                            .description(param.getRemark())
                                            .format(getTypeFormat(typeItem));
                        }
                        dataSchema.addProperties(param.getName(), propertiesItem);
                    }
                }
                rootSchema.addProperties("data", dataSchema);
            }
        } else {
            // Raw data format: no code/message envelope
            if (!CollectionUtils.isEmpty(outputs)) {
                rootSchema = new ObjectSchema();
                for (OutParam param : outputs) {
                    ParamTypeEnum typeItem = param.getType();
                    if (Optional.ofNullable(param.getIsArray()).orElse(false)) {
                        Schema subSchema =
                                new Schema()
                                        .type(param.getType().getJsType())
                                        .description(param.getRemark())
                                        .format(getTypeFormat(param.getType()));
                        ArraySchema subArraySchema = new ArraySchema().items(subSchema);
                        rootSchema.addProperties(param.getName(), subArraySchema);
                    } else {
                        Schema propertiesItem;
                        if (Optional.ofNullable(typeItem.isObject()).orElse(false)) {
                            propertiesItem = new ObjectSchema().description(param.getRemark());
                            if (!CollectionUtils.isEmpty(param.getChildren())) {
                                for (OutParam subParam : param.getChildren()) {
                                    Schema subSchema =
                                            new Schema()
                                                    .type(subParam.getType().getJsType())
                                                    .description(subParam.getRemark())
                                                    .format(getTypeFormat(subParam.getType()));
                                    if (Optional.ofNullable(subParam.getIsArray()).orElse(false)) {
                                        ArraySchema subArraySchema = new ArraySchema().items(subSchema);
                                        propertiesItem.addProperties(subParam.getName(), subArraySchema);
                                    } else {
                                        propertiesItem.addProperties(subParam.getName(), subSchema);
                                    }
                                }
                            }
                        } else {
                            propertiesItem =
                                    new Schema()
                                            .type(typeItem.getJsType())
                                            .description(param.getRemark())
                                            .format(getTypeFormat(typeItem));
                        }
                        rootSchema.addProperties(param.getName(), propertiesItem);
                    }
                }
            } else {
                // Return an empty object if there are no output parameters
                rootSchema = new ObjectSchema();
            }
        }

        ApiResponses apiResponses = new ApiResponses();
        Schema outputSchema = new Schema();
        String outputEntityName = genParamEntityName();
        outputSchema.set$ref("#/components/schemas/" + outputEntityName);
        openAPI.getComponents().addSchemas(outputEntityName, rootSchema);
        apiResponses.addApiResponse(
                "200",
                new ApiResponse()
                        .description("OK")
                        .content(
                                new Content()
                                        .addMediaType("application/json", new MediaType().schema(outputSchema))));
        apiResponses.addApiResponse("201", new ApiResponse().description("Created"));
        apiResponses.addApiResponse("401", new ApiResponse().description("Unauthorized"));
        apiResponses.addApiResponse("403", new ApiResponse().description("Forbidden"));
        apiResponses.addApiResponse("404", new ApiResponse().description("Not Found"));
        return apiResponses;
    }

    private String getTypeFormat(ParamTypeEnum type) {
        String format = null;
        switch (type) {
            case LONG:
                format = "int64";
                break;
            case DOUBLE:
                format = "float32";
                break;
            case DATE:
                format = "date-time";
                break;
            case TIME:
                format = "time";
                break;
            case BOOLEAN:
            default:
                break;
        }
        return format;
    }
}
