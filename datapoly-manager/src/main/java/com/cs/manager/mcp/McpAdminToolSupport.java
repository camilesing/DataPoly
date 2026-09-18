// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.manager.mcp;

import cn.hutool.core.exceptions.ExceptionUtil;
import com.cs.common.exception.CommonException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.Lists;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;

import java.util.List;
import java.util.Map;

/**
 * 管理 MCP 工具的公共支撑：JSON Schema 构建、参数提取与转换、结果包装。
 */
public final class McpAdminToolSupport {

    // Agent 传参可能带多余字段,与 Spring Boot 默认一致地忽略未知属性
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private static final String FN_TYPE = "type";
    private static final String FN_ID = "id";
    private static final String FN_DESCRIPTION = "description";
    private static final String FN_PROPERTIES = "properties";
    private static final String FN_REQUIRED = "required";
    private static final String FN_ITEMS = "items";
    private static final String FV_ID = "urn:jsonschema:Operation";
    private static final String FV_OBJECT = "object";
    private static final String FV_ARRAY = "array";

    private McpAdminToolSupport() {
    }

    /**
     * Fluent JSON Schema(object) 构建器，输出 MCP Tool 的 inputSchema 字符串。
     * 嵌套 schema 被引用时按当前内容快照导出，引用后继续修改原 schema 不影响已生成的字段。
     */
    public static final class Schema {

        private final ObjectNode root = OBJECT_MAPPER.createObjectNode();
        private final ObjectNode properties = OBJECT_MAPPER.createObjectNode();
        private final ArrayNode required = OBJECT_MAPPER.createArrayNode();

        private Schema() {
            root.put(FN_TYPE, FV_OBJECT);
            root.put(FN_ID, FV_ID);
        }

        public static Schema object() {
            return new Schema();
        }

        public Schema property(String name, String type, String description, boolean required) {
            ObjectNode node = OBJECT_MAPPER.createObjectNode();
            node.put(FN_TYPE, type);
            node.put(FN_DESCRIPTION, description);
            return setProperty(name, node, required);
        }

        public Schema property(String name, Schema nested, String description, boolean required) {
            ObjectNode node = OBJECT_MAPPER.createObjectNode();
            node.put(FN_TYPE, FV_OBJECT);
            node.put(FN_DESCRIPTION, description);
            node.set(FN_PROPERTIES, nested.properties.deepCopy());
            node.set(FN_REQUIRED, nested.required.deepCopy());
            return setProperty(name, node, required);
        }

        public Schema arrayProperty(String name, String itemType, String description, boolean required) {
            ObjectNode node = OBJECT_MAPPER.createObjectNode();
            node.put(FN_TYPE, FV_ARRAY);
            node.put(FN_DESCRIPTION, description);
            ObjectNode items = OBJECT_MAPPER.createObjectNode();
            items.put(FN_TYPE, itemType);
            node.set(FN_ITEMS, items);
            return setProperty(name, node, required);
        }

        public Schema arrayProperty(String name, Schema itemSchema, String description, boolean required) {
            ObjectNode node = OBJECT_MAPPER.createObjectNode();
            node.put(FN_TYPE, FV_ARRAY);
            node.put(FN_DESCRIPTION, description);
            ObjectNode items = OBJECT_MAPPER.createObjectNode();
            items.put(FN_TYPE, FV_OBJECT);
            items.set(FN_PROPERTIES, itemSchema.properties.deepCopy());
            items.set(FN_REQUIRED, itemSchema.required.deepCopy());
            node.set(FN_ITEMS, items);
            return setProperty(name, node, required);
        }

        public Schema copy() {
            Schema copied = new Schema();
            this.properties.fields().forEachRemaining(
                    entry -> copied.properties.set(entry.getKey(), entry.getValue().deepCopy()));
            copied.required.addAll(this.required);
            return copied;
        }

        private Schema setProperty(String name, ObjectNode node, boolean required) {
            this.properties.set(name, node);
            if (required) {
                this.required.add(name);
            }
            return this;
        }

        public String toJson() {
            root.set(FN_PROPERTIES, properties);
            root.set(FN_REQUIRED, required);
            return root.toString();
        }
    }

    public static <T> T convert(Map<String, Object> arguments, Class<T> type) {
        return OBJECT_MAPPER.convertValue(arguments, type);
    }

    public static Long getLong(Map<String, Object> arguments, String key) {
        Object value = arguments.get(key);
        return null == value ? null : ((Number) value).longValue();
    }

    public static String getString(Map<String, Object> arguments, String key) {
        Object value = arguments.get(key);
        return null == value ? null : value.toString();
    }

    public static Boolean getBoolean(Map<String, Object> arguments, String key) {
        Object value = arguments.get(key);
        return null == value ? null : (Boolean) value;
    }

    public static List<Long> getLongList(Map<String, Object> arguments, String key) {
        Object value = arguments.get(key);
        if (null == value) {
            return null;
        }
        return OBJECT_MAPPER.convertValue(value, new TypeReference<List<Long>>() {
        });
    }

    public static CallToolResult ok(Object data) {
        String text = null == data
                ? "操作成功"
                : "操作成功，JSON格式的响应数据为:\n " + writeJson(data);
        return new CallToolResult(Lists.newArrayList(new TextContent(text)), false);
    }

    public static CallToolResult fail(Throwable t) {
        String message;
        if (t instanceof CommonException) {
            CommonException exception = (CommonException) t;
            message = null != exception.getCode()
                    ? exception.getCode().getCode() + ":" + t.getMessage()
                    : t.getMessage();
        } else {
            message = null != t.getMessage() ? t.getMessage() : ExceptionUtil.getRootCauseMessage(t);
        }
        return new CallToolResult(Lists.newArrayList(new TextContent("操作异常: " + message)), true);
    }

    private static String writeJson(Object data) {
        try {
            return OBJECT_MAPPER.writeValueAsString(data);
        } catch (Exception e) {
            return String.valueOf(data);
        }
    }
}
