// Use of this source code is governed by a BSD-style license
package com.cs.manager.mcp;

import com.cs.common.exception.CommonException;
import com.cs.common.exception.ResponseErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

public class McpAdminToolSupportTest {

    private static String textOf(CallToolResult result) {
        return ((io.modelcontextprotocol.spec.McpSchema.TextContent) result.getContent().get(0)).getText();
    }

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    public void schemaToJsonContainsPropertiesAndRequired() throws Exception {
        String json = McpAdminToolSupport.Schema.object()
                .property("id", "integer", "主键", true)
                .property("name", "string", "名称", false)
                .toJson();

        JsonNode root = MAPPER.readTree(json);
        assertEquals("object", root.get("type").asText());
        assertTrue(root.get("properties").has("id"));
        assertTrue(root.get("properties").has("name"));
        assertTrue(root.get("properties").get("id").get("type").asText().equals("integer"));
        assertEquals(1, root.get("required").size());
        assertEquals("id", root.get("required").get(0).asText());
    }

    @Test
    public void nestedObjectAndArraySchemasAreEmbedded() throws Exception {
        McpAdminToolSupport.Schema item = McpAdminToolSupport.Schema.object()
                .property("key", "string", "类型", true)
                .property("value", "string", "格式串", true);
        String json = McpAdminToolSupport.Schema.object()
                .property("pool", item, "嵌套对象", false)
                .arrayProperty("items", item, "对象数组", false)
                .arrayProperty("codes", "string", "字符串数组", false)
                .toJson();

        JsonNode root = MAPPER.readTree(json);
        JsonNode pool = root.get("properties").get("pool");
        assertEquals("object", pool.get("type").asText());
        assertTrue(pool.get("properties").has("key"));
        assertEquals(2, pool.get("required").size());

        JsonNode items = root.get("properties").get("items");
        assertEquals("array", items.get("type").asText());
        assertEquals("object", items.get("items").get("type").asText());
        assertTrue(items.get("items").get("properties").has("key"));

        JsonNode codes = root.get("properties").get("codes");
        assertEquals("array", codes.get("type").asText());
        assertEquals("string", codes.get("items").get("type").asText());
    }

    @Test
    public void schemaCopyIsIndependentFromOriginal() {
        McpAdminToolSupport.Schema original = McpAdminToolSupport.Schema.object()
                .property("name", "string", "名称", true);
        McpAdminToolSupport.Schema copied = original.copy()
                .property("children", "string", "子参数", false);

        assertFalse(original.toJson().contains("children"));
        assertTrue(copied.toJson().contains("children"));
        assertTrue(original.toJson().contains("name"));
    }

    @Test
    public void longListConversionHandlesJsonNumbers() {
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("ids", Arrays.asList(1, 2, 3));

        List<Long> ids = McpAdminToolSupport.getLongList(arguments, "ids");

        assertEquals(Arrays.asList(1L, 2L, 3L), ids);
        assertNull(McpAdminToolSupport.getLongList(arguments, "missing"));
    }

    @Test
    public void typedGettersReadArguments() {
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("id", 7);
        arguments.put("name", "demo");
        arguments.put("flag", Boolean.TRUE);

        assertEquals(Long.valueOf(7), McpAdminToolSupport.getLong(arguments, "id"));
        assertEquals("demo", McpAdminToolSupport.getString(arguments, "name"));
        assertEquals(Boolean.TRUE, McpAdminToolSupport.getBoolean(arguments, "flag"));
        assertNull(McpAdminToolSupport.getLong(arguments, "none"));
    }

    @Test
    public void okWrapsNullAsPlainTextAndDataAsJson() {
        CallToolResult plain = McpAdminToolSupport.ok(null);
        assertFalse(plain.getIsError());
        assertEquals("操作成功", textOf(plain));

        CallToolResult withData = McpAdminToolSupport.ok(Collections.singletonMap("id", 42L));
        assertFalse(withData.getIsError());
        assertTrue(textOf(withData).contains("\"id\":42"));
    }

    @Test
    public void failCarriesErrorCodeAndMessage() {
        CallToolResult result = McpAdminToolSupport.fail(
                new CommonException(ResponseErrorCode.ERROR_RESOURCE_NOT_EXISTS, "id not found"));

        assertTrue(result.getIsError());
        String text = textOf(result);
        assertTrue(text.contains("3"));
        assertTrue(text.contains("id not found"));
    }

    @Test
    public void convertBuildsDtoFromArgumentMapAndIgnoresUnknownFields() {
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("searchText", "demo");
        arguments.put("page", 2);
        arguments.put("hallucinatedField", "agents may send extra keys");
        com.cs.core.dto.EntitySearchRequest request =
                McpAdminToolSupport.convert(arguments, com.cs.core.dto.EntitySearchRequest.class);

        assertEquals("demo", request.getSearchText());
        assertEquals(Integer.valueOf(2), request.getPage());
    }
}
