// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.manager.model;

import cn.hutool.core.exceptions.ExceptionUtil;
import cn.hutool.extra.spring.SpringUtil;
import com.cs.common.consts.Constants;
import com.cs.common.dto.*;
import com.cs.common.enums.ParamTypeEnum;
import com.cs.core.exec.ApiExecuteService;
import com.cs.core.service.SystemParamService;
import com.cs.core.util.JacksonUtils;
import com.cs.persistence.entity.ApiAssignmentEntity;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.*;
import com.google.common.collect.Lists;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import org.apache.commons.collections4.CollectionUtils;

import java.util.*;

/**
 * https://mcp-docs.cn/docs/concepts/tools
 */
public class McpToolCallHandler {

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final String FN_TYPE = "type";
    private static final String FN_ID = "id";
    private static final String FN_DESCRIPTION = "description";
    private static final String FN_PROPERTIES = "properties";
    private static final String FN_REQUIRED = "required";
    private static final String FN_ITEMS = "items";
    private static final String FV_ID = "urn:jsonschema:Operation";
    private static final String FV_OBJECT = "object";
    private static final String FV_ARRAY = "array";

    private final String toolName;
    private final String toolDescription;
    private final ApiAssignmentEntity config;
    private final ApiExecuteService apiExecuteService;
    private final int defaultPageSize;

    public McpToolCallHandler(String toolName, String description, ApiAssignmentEntity config) {
        this.toolName = toolName;
        this.toolDescription = description;
        this.config = config;
        this.apiExecuteService = SpringUtil.getBean(ApiExecuteService.class);
        this.defaultPageSize = SpringUtil.getBean(SystemParamService.class)
                .getIntByParamKey(Constants.SYS_PARAM_KEY_MCP_TOOL_LIST_PAGE_SIZE, 1000);
    }

    public McpSchema.Tool getMcpToolSchema() {
        List<ItemParam> params = config.getParams();
        ObjectNode rootNode = objectMapper.createObjectNode();
        rootNode.put(FN_TYPE, FV_OBJECT);
        rootNode.put(FN_ID, FV_ID);
        ObjectNode propertiesNode = objectMapper.createObjectNode();
        ArrayNode rootRequired = objectMapper.createArrayNode();
        if (CollectionUtils.isNotEmpty(params)) {
            for (ItemParam param : params) {
                ObjectNode node = objectMapper.createObjectNode();
                if (param.getIsArray()) {
                    rootRequired.add(param.getName());
                    ObjectNode items = objectMapper.createObjectNode();
                    items.put(FN_TYPE, param.getType().getJsType());
                    node.put(FN_TYPE, FV_ARRAY);
                    node.put(FN_DESCRIPTION, param.getRemark());
                    node.set(FN_ITEMS, items);
                } else {
                    if (param.getRequired()) {
                        rootRequired.add(param.getName());
                    }
                    node.put(FN_TYPE, param.getType().getJsType());
                    node.put(FN_DESCRIPTION, param.getRemark());
                    if (CollectionUtils.isNotEmpty(param.getChildren())) {
                        ArrayNode subRequired = objectMapper.createArrayNode();
                        ObjectNode properties = objectMapper.createObjectNode();
                        for (BaseParam subParam : param.getChildren()) {
                            if (subParam.getRequired()) {
                                subRequired.add(subParam.getName());
                            }
                            ObjectNode item = objectMapper.createObjectNode();
                            item.put(FN_TYPE, subParam.getType().getJsType());
                            item.put(FN_DESCRIPTION, subParam.getRemark());
                            properties.set(subParam.getName(), item);
                        }
                        node.set(FN_PROPERTIES, properties);
                        node.set(FN_REQUIRED, subRequired);
                    }
                }
                propertiesNode.set(param.getName(), node);
            }
        }

        rootNode.set(FN_PROPERTIES, propertiesNode);
        rootNode.set(FN_REQUIRED, rootRequired);

        try {
            String schema = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(rootNode);
            return new McpSchema.Tool(toolName, toolDescription, schema);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }

    }

    public CallToolResult executeTool(McpSyncServerExchange exchange, Map<String, Object> arguments) {
        prepareArgumentsPageSizeParameter(arguments);
        try {
            ResultEntity<Object> resultEntity = apiExecuteService.execute(config, arguments, true);
            String json = JacksonUtils.toJsonStr(resultEntity.getData(), config.getResponseFormat());
            McpSchema.TextContent content = new McpSchema.TextContent("操作成功，JSON格式的响应数据为:\n " + json);
            return new McpSchema.CallToolResult(Lists.newArrayList(content), false);
        } catch (Throwable t) {
            String message = null != t.getMessage() ? t.getMessage() : ExceptionUtil.getRootCauseMessage(t);
            McpSchema.TextContent content = new McpSchema.TextContent("操作异常，JSON格式的响应数据为:\n " + message);
            return new McpSchema.CallToolResult(Lists.newArrayList(content), true);
        }
    }

    private void prepareArgumentsPageSizeParameter(Map<String, Object> arguments) {
        if (CollectionUtils.isNotEmpty(config.getParams())) {
            for (ItemParam param : config.getParams()) {
                String name = param.getName();
                ParamTypeEnum type = param.getType();
                Boolean required = param.getRequired();
                String defaultValue = param.getDefaultValue();
                if (!required && !arguments.containsKey(param.getName())) {
                    if (!type.isObject()) {
                        arguments.put(name, type.getConverter().apply(defaultValue));
                    }
                }
            }
        }
        if (!arguments.containsKey(Constants.PARAM_PAGE_SIZE)) {
            arguments.put(Constants.PARAM_PAGE_SIZE, defaultPageSize);
        }
    }
}
