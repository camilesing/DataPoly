// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.manager.mcp;

import com.cs.core.dto.*;
import com.cs.core.service.*;
import com.cs.manager.service.McpManageService;
import com.google.common.collect.Lists;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.cs.manager.mcp.McpAdminToolSupport.Schema;

/**
 * 管理 MCP 工具集：将 manager 的实体管理 REST 能力(数据源/模块/分组/API定义/应用客户端/MCP配置)
 * 包装为 MCP tools，命名与 REST 接口语义保持一致(dp_{entity}_{action})，供外部 AI Agent 增删改查。
 * 注册于独立的 admin MCP Server，仅 manage 权限令牌可调用。
 */
@Slf4j
@Component
public class McpAdminTools {

    private static final String T_INTEGER = "integer";
    private static final String T_STRING = "string";
    private static final String T_BOOLEAN = "boolean";

    private static final String D_PAGE = "页码,从1开始";
    private static final String D_SIZE = "每页数量";
    private static final String D_SEARCH = "名称模糊搜索关键字";

    // package-private: datapoly-test 同包注入 fake 服务直接构建工具
    @Resource
    McpSyncServer mcpAdminSyncServer;
    @Resource
    DataSourceService dataSourceService;
    @Resource
    ApiModuleService apiModuleService;
    @Resource
    ApiGroupService apiGroupService;
    @Resource
    ApiAssignmentService apiAssignmentService;
    @Resource
    AppClientService appClientService;
    @Resource
    McpManageService mcpManageService;

    @EventListener(ApplicationReadyEvent.class)
    public void registerAdminTools() {
        List<SyncToolSpecification> tools = buildToolSpecifications();
        tools.forEach(mcpAdminSyncServer::addTool);
        log.info("Finish load total count [{}] admin mcp tools to memory.", tools.size());
    }

    List<SyncToolSpecification> buildToolSpecifications() {
        return Stream.of(buildDataSourceTools(), buildModuleTools(), buildGroupTools(),
                        buildApiTools(), buildClientTools(), buildMcpConfigTools())
                .flatMap(List::stream)
                .collect(Collectors.toList());
    }

    private SyncToolSpecification tool(String name, String description, Schema schema,
                                       Function<Map<String, Object>, Object> action) {
        return new SyncToolSpecification(
                new McpSchema.Tool(name, description, schema.toJson()),
                (exchange, arguments) -> {
                    try {
                        Object data = action.apply(arguments);
                        return McpAdminToolSupport.ok(data);
                    } catch (Throwable t) {
                        return McpAdminToolSupport.fail(t);
                    }
                });
    }

    private Schema pageSchema() {
        return Schema.object()
                .property("page", T_INTEGER, D_PAGE, false)
                .property("size", T_INTEGER, D_SIZE, false)
                .property("searchText", T_STRING, D_SEARCH, false);
    }

    private <T> T search(Map<String, Object> arguments, Class<T> type) {
        return McpAdminToolSupport.convert(arguments, type);
    }

    // ------------------------------------------------------------------
    // 数据源
    // ------------------------------------------------------------------

    private List<SyncToolSpecification> buildDataSourceTools() {
        String typeValues = "MYSQL, MARIADB, ORACLE, SQLSERVER, POSTGRESQL, DB2, DM, KINGBASE, OSCAR, GBASE8A, "
                + "SYBASE, HIVE, IMPALA, INCEPTOR, SQLITE3, OPENGAUSS, CLICKHOUSE, DORIS, STARROCKS, "
                + "OCEANBASE, TDENGINE, MONGODB, ELASTICSEARCH, ODPS, HTTP";
        Schema poolConfig = Schema.object()
                .property("maximumPoolSize", T_INTEGER, "最大连接数,默认10", false)
                .property("minimumIdle", T_INTEGER, "最小空闲连接数,默认10", false)
                .property("maxLifetime", T_INTEGER, "连接最大存活毫秒数,默认3600000", false)
                .property("connectionTimeout", T_INTEGER, "获取连接超时毫秒数,默认60000", false)
                .property("idleTimeout", T_INTEGER, "空闲超时毫秒数,默认60000", false);
        Schema saveSchema = Schema.object()
                .property("name", T_STRING, "数据源名称(唯一)", true)
                .property("type", T_STRING, "数据库类型,枚举: " + typeValues, true)
                .property("version", T_STRING, "驱动版本(先调用dp_datasource_types查询可选值)", true)
                .property("driver", T_STRING, "驱动类名(先调用dp_datasource_types查询可选值)", true)
                .property("url", T_STRING, "JDBC连接URL", true)
                .property("username", T_STRING, "用户名", true)
                .property("password", T_STRING, "密码(更新时省略表示保留原密码)", false)
                .property("poolConfig", poolConfig, "连接池配置", false);
        Schema updateSchema = saveSchema.property("id", T_INTEGER, "数据源ID", true);

        return Lists.newArrayList(
                tool("dp_datasource_types",
                        "查询支持的数据库类型及可用驱动(type/version/driver 可选值),创建数据源前先调用",
                        Schema.object(),
                        args -> dataSourceService.getTypes()),
                tool("dp_datasource_list", "分页查询数据源列表(密码已脱敏)",
                        pageSchema(),
                        args -> dataSourceService.searchList(search(args, EntitySearchRequest.class))),
                tool("dp_datasource_get", "按ID查询数据源详情(密码已脱敏)",
                        Schema.object().property("id", T_INTEGER, "数据源ID", true),
                        args -> dataSourceService.getDetailById(McpAdminToolSupport.getLong(args, "id"))),
                tool("dp_datasource_create", "创建数据源",
                        saveSchema,
                        args -> {
                            dataSourceService.createDataSource(search(args, DataSourceSaveRequest.class));
                            return null;
                        }),
                tool("dp_datasource_update", "更新数据源(省略password表示保留原密码)",
                        updateSchema,
                        args -> {
                            dataSourceService.updateDataSource(search(args, DataSourceSaveRequest.class));
                            return null;
                        }),
                tool("dp_datasource_delete", "删除数据源(被API引用时拒绝删除)",
                        Schema.object().property("id", T_INTEGER, "数据源ID", true),
                        args -> {
                            dataSourceService.deleteDataSource(McpAdminToolSupport.getLong(args, "id"));
                            return null;
                        }),
                tool("dp_datasource_test", "测试已保存数据源的连通性",
                        Schema.object().property("id", T_INTEGER, "数据源ID", true),
                        args -> {
                            dataSourceService.testDataSource(McpAdminToolSupport.getLong(args, "id"));
                            return null;
                        }),
                tool("dp_datasource_schemas", "查询数据源下的schema清单",
                        Schema.object().property("id", T_INTEGER, "数据源ID", true),
                        args -> dataSourceService.getDatasourceSchemas(McpAdminToolSupport.getLong(args, "id"))),
                tool("dp_datasource_tables", "查询数据源指定schema下的表清单",
                        Schema.object().property("id", T_INTEGER, "数据源ID", true)
                                .property("schema", T_STRING, "schema名称", true),
                        args -> dataSourceService.getSchemaTables(
                                McpAdminToolSupport.getLong(args, "id"),
                                McpAdminToolSupport.getString(args, "schema"))),
                tool("dp_datasource_columns", "查询数据源指定表的字段元信息(名称/类型),编写SQL前调用",
                        Schema.object().property("id", T_INTEGER, "数据源ID", true)
                                .property("schema", T_STRING, "schema名称", true)
                                .property("table", T_STRING, "表名", true),
                        args -> dataSourceService.getTableColumns(
                                McpAdminToolSupport.getLong(args, "id"),
                                McpAdminToolSupport.getString(args, "schema"),
                                McpAdminToolSupport.getString(args, "table"))));
    }

    // ------------------------------------------------------------------
    // 模块与分组
    // ------------------------------------------------------------------

    private List<SyncToolSpecification> buildModuleTools() {
        return Lists.newArrayList(
                tool("dp_module_list", "分页查询API模块(组织API的目录)列表",
                        pageSchema(),
                        args -> apiModuleService.listAll(search(args, EntitySearchRequest.class))),
                tool("dp_module_create", "创建API模块",
                        Schema.object().property("name", T_STRING, "模块名称(唯一)", true),
                        args -> {
                            apiModuleService.createModule(McpAdminToolSupport.getString(args, "name"));
                            return null;
                        }),
                tool("dp_module_update", "重命名API模块",
                        Schema.object().property("id", T_INTEGER, "模块ID", true)
                                .property("name", T_STRING, "新模块名称", true),
                        args -> {
                            apiModuleService.updateModule(McpAdminToolSupport.getLong(args, "id"),
                                    McpAdminToolSupport.getString(args, "name"));
                            return null;
                        }),
                tool("dp_module_delete", "删除API模块(被API引用时拒绝删除)",
                        Schema.object().property("id", T_INTEGER, "模块ID", true),
                        args -> {
                            apiModuleService.deleteModule(McpAdminToolSupport.getLong(args, "id"));
                            return null;
                        }));
    }

    private List<SyncToolSpecification> buildGroupTools() {
        return Lists.newArrayList(
                tool("dp_group_list", "分页查询API授权分组列表(API按分组授权给应用客户端)",
                        pageSchema(),
                        args -> apiGroupService.listAll(search(args, EntitySearchRequest.class))),
                tool("dp_group_create", "创建API授权分组",
                        Schema.object().property("name", T_STRING, "分组名称(唯一)", true),
                        args -> {
                            apiGroupService.createGroup(McpAdminToolSupport.getString(args, "name"));
                            return null;
                        }),
                tool("dp_group_update", "重命名API授权分组",
                        Schema.object().property("id", T_INTEGER, "分组ID", true)
                                .property("name", T_STRING, "新分组名称", true),
                        args -> {
                            apiGroupService.updateGroup(McpAdminToolSupport.getLong(args, "id"),
                                    McpAdminToolSupport.getString(args, "name"));
                            return null;
                        }),
                tool("dp_group_delete", "删除API授权分组(被API引用时拒绝删除)",
                        Schema.object().property("id", T_INTEGER, "分组ID", true),
                        args -> {
                            apiGroupService.deleteGroup(McpAdminToolSupport.getLong(args, "id"));
                            return null;
                        }));
    }

    // ------------------------------------------------------------------
    // API 定义(Assignment)与上下线
    // ------------------------------------------------------------------

    private List<SyncToolSpecification> buildApiTools() {
        // ItemParam 子参数为 BaseParam(无更深层嵌套),故 children 用独立的一层 schema
        Schema baseParam = Schema.object()
                .property("name", T_STRING, "参数名(SQL中#{name}或${name}占位)", true)
                .property("type", T_STRING, "参数类型,枚举: LONG, DOUBLE, STRING, DATE, TIME, BOOLEAN, OBJECT", true)
                .property("location", T_STRING,
                        "参数位置,枚举: REQUEST_HEADER(header头), REQUEST_FORM(query参数), REQUEST_BODY(body体)", true)
                .property("isArray", T_BOOLEAN, "是否数组", false)
                .property("required", T_BOOLEAN, "是否必填", false)
                .property("defaultValue", T_STRING, "默认值", false)
                .property("remark", T_STRING, "参数说明", false);
        Schema itemParam = baseParam.copy()
                .arrayProperty("children", baseParam, "OBJECT类型参数的子参数", false);
        Schema outParam = Schema.object()
                .property("name", T_STRING, "输出字段名", true)
                .property("type", T_STRING, "输出类型,枚举: LONG, DOUBLE, STRING, DATE, TIME, BOOLEAN, OBJECT", true)
                .property("isArray", T_BOOLEAN, "是否数组", false)
                .property("remark", T_STRING, "字段说明", false);
        Schema formatMap = Schema.object()
                .property("key", T_STRING, "类型,枚举: DATE, TIME, LOCAL_DATE, LOCAL_DATE_TIME, TIMESTAMP, "
                        + "BIG_DECIMAL, USE_SYSTEM_RESPONSE_FORMAT", true)
                .property("value", T_STRING, "格式串,如 yyyy-MM-dd HH:mm:ss", true)
                .property("remark", T_STRING, "说明", false);
        Schema apiSaveSchema = Schema.object()
                .property("groupId", T_INTEGER, "授权分组ID(先用dp_group_list查询)", true)
                .property("moduleId", T_INTEGER, "所属模块ID(先用dp_module_list查询)", true)
                .property("datasourceId", T_INTEGER, "数据源ID(先用dp_datasource_list查询)", true)
                .property("name", T_STRING, "API名称", true)
                .property("method", T_STRING, "HTTP方法,枚举: GET, HEAD, PUT, POST, DELETE(创建后不可修改)", true)
                .property("path", T_STRING, "接口路径,不以api/开头,如 /user/list(创建后不可修改)", true)
                .property("contentType", T_STRING,
                        "请求格式: application/x-www-form-urlencoded 或 application/json", true)
                .property("engine", T_STRING, "执行引擎,枚举: SQL, SCRIPT(Groovy脚本)", true)
                .arrayProperty("contextList", T_STRING,
                        "SQL/脚本内容数组,SQL中用#{name}预编译取参或${name}直接拼接(仅内部数据,open=false)", true)
                .property("description", T_STRING, "API描述", false)
                .property("open", T_BOOLEAN, "是否公开(公开可匿名访问)", true)
                .property("alarm", T_BOOLEAN, "是否启用统一告警", true)
                .arrayProperty("params", itemParam, "入参定义列表(可用dp_api_parse_sql解析SQL得到参数名)", false)
                .arrayProperty("outputs", outParam, "输出字段定义列表(不填则按查询结果自动映射)", false)
                .property("namingStrategy", T_STRING,
                        "响应字段命名策略,枚举: NONE, CAMEL_CASE, SNAKE_CASE, LOWER_CASE, UPPER_CASE,默认CAMEL_CASE", false)
                .arrayProperty("formatMap", formatMap, "响应类型格式映射列表", false)
                .property("flowStatus", T_BOOLEAN, "是否启用流控", true)
                .property("flowGrade", T_INTEGER, "流控级别(参考Sentinel:0-QPS/1-并发线程数)", false)
                .property("flowCount", T_INTEGER, "流控阈值", false)
                .property("cacheKeyType", T_STRING, "缓存键类型,枚举: NONE, AUTO, SPEL", false)
                .property("cacheKeyExpr", T_STRING, "SPEL缓存键表达式(cacheKeyType=SPEL时必填)", false)
                .property("cacheExpireSeconds", T_INTEGER, "缓存过期秒数(启用缓存时必填>0)", false);
        Schema apiUpdateSchema = apiSaveSchema.property("id", T_INTEGER, "API ID", true);

        return Lists.newArrayList(
                tool("dp_api_list", "分页查询API定义列表(支持按上线状态/分组/模块过滤)",
                        pageSchema()
                                .property("online", T_BOOLEAN, "过滤上线状态", false)
                                .property("groupId", T_INTEGER, "过滤授权分组ID", false)
                                .property("moduleId", T_INTEGER, "过滤模块ID", false)
                                .property("open", T_BOOLEAN, "过滤公开状态", false),
                        args -> apiAssignmentService.listAll(search(args, AssignmentSearchRequest.class))),
                tool("dp_api_get", "按ID查询API定义详情(含SQL与参数定义)",
                        Schema.object().property("id", T_INTEGER, "API ID", true),
                        args -> apiAssignmentService.detailAssignment(McpAdminToolSupport.getLong(args, "id"))),
                tool("dp_api_create", "创建API定义,返回新API的ID;上线需依次调用 dp_api_publish 与 dp_api_deploy",
                        apiSaveSchema,
                        args -> apiAssignmentService.createAssignment(search(args, ApiAssignmentSaveRequest.class))),
                tool("dp_api_update", "更新API定义(method与path不可修改);更新后需重新 publish+deploy 才生效到网关",
                        apiUpdateSchema,
                        args -> {
                            apiAssignmentService.updateAssignment(search(args, ApiAssignmentSaveRequest.class));
                            return null;
                        }),
                tool("dp_api_delete", "删除API定义(已上线时拒绝删除,需先 dp_api_retire 下线)",
                        Schema.object().property("id", T_INTEGER, "API ID", true),
                        args -> {
                            apiAssignmentService.deleteAssignment(McpAdminToolSupport.getLong(args, "id"));
                            return null;
                        }),
                tool("dp_api_parse_sql", "解析SQL中的#{}与${}占位符,返回参数名清单,编写params前调用",
                        Schema.object().property("sql", T_STRING, "SQL内容", true),
                        args -> apiAssignmentService.parseSqlParams(McpAdminToolSupport.getString(args, "sql"))),
                tool("dp_api_publish", "发布API:将当前定义保存为版本快照,随后用 dp_api_deploy 上线",
                        Schema.object().property("id", T_INTEGER, "API ID", true)
                                .property("description", T_STRING, "版本说明", false),
                        args -> {
                            apiAssignmentService.publish(search(args, AssignmentPublishRequest.class));
                            return null;
                        }),
                tool("dp_api_deploy", "上线API(将版本快照部署到网关,commitId省略时取最新版本)",
                        Schema.object().property("id", T_INTEGER, "API ID", true)
                                .property("commitId", T_INTEGER, "版本快照ID(省略取最新)", false),
                        args -> {
                            apiAssignmentService.deployAssignment(McpAdminToolSupport.getLong(args, "id"),
                                    McpAdminToolSupport.getLong(args, "commitId"));
                            return null;
                        }),
                tool("dp_api_retire", "下线API(从网关移除,定义保留)",
                        Schema.object().property("id", T_INTEGER, "API ID", true),
                        args -> {
                            apiAssignmentService.retireAssignment(McpAdminToolSupport.getLong(args, "id"));
                            return null;
                        }),
                tool("dp_api_set_group", "批量修改API的授权分组",
                        Schema.object().property("groupId", T_INTEGER, "目标分组ID", true)
                                .arrayProperty("ids", T_INTEGER, "API ID列表", true),
                        args -> {
                            apiAssignmentService.updateGroup(McpAdminToolSupport.getLong(args, "groupId"),
                                    McpAdminToolSupport.getLongList(args, "ids"));
                            return null;
                        }),
                tool("dp_api_versions", "查询API的版本快照列表",
                        Schema.object().property("id", T_INTEGER, "API ID", true),
                        args -> apiAssignmentService.listVersions(McpAdminToolSupport.getLong(args, "id"))),
                tool("dp_api_version_detail", "查询API指定版本快照的完整定义",
                        Schema.object().property("commitId", T_INTEGER, "版本快照ID", true),
                        args -> apiAssignmentService.showVersion(McpAdminToolSupport.getLong(args, "commitId"))),
                tool("dp_api_revert", "将API定义回退到指定版本快照(回退后需重新 publish+deploy)",
                        Schema.object().property("id", T_INTEGER, "API ID", true)
                                .property("commitId", T_INTEGER, "版本快照ID", true),
                        args -> {
                            apiAssignmentService.revertVersion(McpAdminToolSupport.getLong(args, "id"),
                                    McpAdminToolSupport.getLong(args, "commitId"));
                            return null;
                        }));
    }

    // ------------------------------------------------------------------
    // 应用客户端(网关API调用方)
    // ------------------------------------------------------------------

    private List<SyncToolSpecification> buildClientTools() {
        return Lists.newArrayList(
                tool("dp_client_list", "分页查询应用客户端列表(网关API调用方)",
                        pageSchema().property("groupId", T_INTEGER, "过滤已授权分组ID", false),
                        args -> appClientService.searchList(search(args, AppClientSearchRequest.class))),
                tool("dp_client_create", "创建应用客户端,appKey需全局唯一,appSecret由服务端生成(用dp_client_secret查询)",
                        Schema.object()
                                .property("name", T_STRING, "客户端名称", true)
                                .property("appKey", T_STRING, "客户端标识(唯一)", true)
                                .property("description", T_STRING, "描述", false)
                                .property("expireTime", T_STRING, "凭证有效期,枚举: EXPIRE_FOR_EVER(永久), "
                                        + "EXPIRE_ONLY_ONCE(一次性), EXPIRE_05_MIN, EXPIRE_30_MIN, EXPIRE_01_HOUR, "
                                        + "EXPIRE_12_HOUR, EXPIRE_01_DAY, EXPIRE_15_DAY, EXPIRE_01_MOUTH", true)
                                .property("tokenAlive", T_STRING, "token存活策略,枚举: PERIOD(短期,2小时), "
                                        + "LONGEVITY(长期)", true),
                        args -> {
                            appClientService.create(search(args, AppClientSaveRequest.class));
                            return null;
                        }),
                tool("dp_client_delete", "删除应用客户端",
                        Schema.object().property("id", T_INTEGER, "客户端ID", true),
                        args -> {
                            appClientService.delete(McpAdminToolSupport.getLong(args, "id"));
                            return null;
                        }),
                tool("dp_client_secret", "查询应用客户端的appSecret(用于换取网关访问token)",
                        Schema.object().property("id", T_INTEGER, "客户端ID", true),
                        args -> appClientService.getSecret(McpAdminToolSupport.getLong(args, "id"))),
                tool("dp_client_get_groups", "查询应用客户端已授权的API分组",
                        Schema.object().property("id", T_INTEGER, "客户端ID", true),
                        args -> appClientService.getGroupAuth(McpAdminToolSupport.getLong(args, "id"))),
                tool("dp_client_set_groups", "设置应用客户端的授权分组(全量覆盖)",
                        Schema.object().property("id", T_INTEGER, "客户端ID", true)
                                .arrayProperty("groupIds", T_INTEGER, "分组ID列表", true),
                        args -> {
                            appClientService.createGroupAuth(search(args, AppClientGroupRequest.class));
                            return null;
                        }));
    }

    // ------------------------------------------------------------------
    // MCP 配置(数据工具服务注册表 + MCP 令牌)
    // ------------------------------------------------------------------

    private List<SyncToolSpecification> buildMcpConfigTools() {
        return Lists.newArrayList(
                tool("dp_mcp_tool_list", "分页查询数据MCP服务已注册的工具(把在线API暴露给大模型)",
                        pageSchema(),
                        args -> mcpManageService.listToolAll(search(args, EntitySearchRequest.class))),
                tool("dp_mcp_tool_create", "将已上线的API注册为数据MCP服务的工具(供大模型调用)",
                        Schema.object()
                                .property("name", T_STRING, "工具名(唯一)", true)
                                .property("description", T_STRING, "工具描述(写给大模型看)", true)
                                .property("apiId", T_INTEGER, "已上线的API ID", true),
                        args -> {
                            mcpManageService.createTool(search(args, McpToolSaveRequest.class));
                            return null;
                        }),
                tool("dp_mcp_tool_update", "更新数据MCP服务的工具定义",
                        Schema.object()
                                .property("id", T_INTEGER, "工具ID", true)
                                .property("name", T_STRING, "工具名(唯一)", true)
                                .property("description", T_STRING, "工具描述", true)
                                .property("apiId", T_INTEGER, "已上线的API ID", true),
                        args -> {
                            mcpManageService.updateTool(search(args, McpToolSaveRequest.class));
                            return null;
                        }),
                tool("dp_mcp_tool_delete", "删除数据MCP服务的工具",
                        Schema.object().property("id", T_INTEGER, "工具ID", true),
                        args -> {
                            mcpManageService.deleteTool(McpAdminToolSupport.getLong(args, "id"));
                            return null;
                        }),
                tool("dp_mcp_client_list", "分页查询MCP连接令牌列表(manage=true的令牌可访问本管理服务)",
                        pageSchema(),
                        args -> mcpManageService.listClientAll(search(args, EntitySearchRequest.class))),
                tool("dp_mcp_client_create", "创建MCP连接令牌,manage=true时该令牌可访问本管理服务(等同管理员,谨慎授予)",
                        Schema.object()
                                .property("name", T_STRING, "令牌名称(唯一)", true)
                                .property("manage", T_BOOLEAN, "是否管理权限,默认false", false),
                        args -> {
                            Boolean manage = McpAdminToolSupport.getBoolean(args, "manage");
                            mcpManageService.createClient(McpAdminToolSupport.getString(args, "name"),
                                    Boolean.TRUE.equals(manage));
                            return null;
                        }),
                tool("dp_mcp_client_update", "更新MCP连接令牌(名称或manage权限)",
                        Schema.object()
                                .property("id", T_INTEGER, "令牌ID", true)
                                .property("name", T_STRING, "新令牌名称", true)
                                .property("manage", T_BOOLEAN, "是否管理权限(省略表示不变)", false),
                        args -> {
                            mcpManageService.updateClient(McpAdminToolSupport.getLong(args, "id"),
                                    McpAdminToolSupport.getString(args, "name"),
                                    McpAdminToolSupport.getBoolean(args, "manage"));
                            return null;
                        }),
                tool("dp_mcp_client_delete", "删除MCP连接令牌",
                        Schema.object().property("id", T_INTEGER, "令牌ID", true),
                        args -> {
                            mcpManageService.deleteClient(McpAdminToolSupport.getLong(args, "id"));
                            return null;
                        }),
                tool("dp_mcp_client_token", "查询MCP连接令牌的明文(用于配置Agent客户端连接)",
                        Schema.object().property("id", T_INTEGER, "令牌ID", true),
                        args -> mcpManageService.getClientToken(McpAdminToolSupport.getLong(args, "id"))),
                tool("dp_mcp_endpoint", "查询数据MCP服务与管理MCP服务的连接地址前缀",
                        Schema.object(),
                        args -> mcpManageService.getMcpServerEndpoint()));
    }
}
