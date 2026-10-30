// Use of this source code is governed by a BSD-style license
package io.modelcontextprotocol.server.transport;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpServerSession;
import io.modelcontextprotocol.spec.McpServerTransportProvider;
import org.junit.BeforeClass;
import org.junit.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;
import reactor.core.publisher.Mono;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.Assert.*;

/**
 * Drives the dual-era Streamable HTTP transport through its package-private handlers with
 * hand-rolled fakes: a Proxy {@link ServerRequest}, a Proxy servlet response that captures
 * the rendered body, and a no-op transport provider feeding a real {@link McpSyncServer}.
 */
public class WebMvcStreamHttpServerProviderTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String GOOD_TOKEN = "good-token";

    private static WebMvcStreamHttpServerProvider provider;

    @BeforeClass
    public static void setUpProvider() {
        McpSyncServer server = McpServer.sync(new NoopTransportProvider())
                .serverInfo("test-server", "1.0.0")
                .capabilities(McpSchema.ServerCapabilities.builder().tools(true).build())
                .build();
        // registered out of order on purpose: tools/list must come back sorted by name
        server.addTool(new McpServerFeatures.SyncToolSpecification(
                new McpSchema.Tool("zeta", "second tool", "{\"type\":\"object\"}"),
                (exchange, arguments) -> new McpSchema.CallToolResult(
                        Collections.singletonList(new McpSchema.TextContent("zeta!")), null)));
        server.addTool(new McpServerFeatures.SyncToolSpecification(
                new McpSchema.Tool("alpha", "first tool", "{\"type\":\"object\"}"),
                (exchange, arguments) -> new McpSchema.CallToolResult(
                        Collections.singletonList(new McpSchema.TextContent("alpha:" + arguments.get("text"))),
                        null)));
        provider = new WebMvcStreamHttpServerProvider(MAPPER, new FixedAuthChecker(), "/mcp", server);
    }

    // ---------- legacy era (initialize handshake) ----------

    @Test
    public void legacyInitializeEchoesRequestedLegacyVersionAndAdvertisesToolsOnly() throws Exception {
        String body = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\","
                + "\"params\":{\"protocolVersion\":\"2025-06-18\",\"capabilities\":{},\"clientInfo\":{\"name\":\"c\",\"version\":\"1\"}}}";
        ServerResponse response = post(body, Collections.emptyMap());

        assertEquals(200, response.statusCode().value());
        assertEquals(MediaType.APPLICATION_JSON, response.headers().getContentType());
        JsonNode result = readResult(response);
        assertEquals("2025-06-18", result.path("protocolVersion").asText());
        assertTrue(result.path("capabilities").has("tools"));
        assertFalse(result.path("capabilities").has("resources"));
        assertEquals("test-server", result.path("serverInfo").path("name").asText());
    }

    @Test
    public void legacyInitializeUpgradesUnknownOrModernVersionToNewestLegacy() throws Exception {
        String unknown = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\","
                + "\"params\":{\"protocolVersion\":\"1999-01-01\"}}";
        assertEquals("2025-11-25", readResult(post(unknown, Collections.emptyMap())).path("protocolVersion").asText());

        String modern = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\","
                + "\"params\":{\"protocolVersion\":\"2026-07-28\"}}";
        assertEquals("2025-11-25", readResult(post(modern, Collections.emptyMap())).path("protocolVersion").asText());
    }

    @Test
    public void notificationIsAcceptedWithNoBodyInBothEras() throws Exception {
        String legacy = "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}";
        ServerResponse response = post(legacy, Collections.emptyMap());
        assertEquals(202, response.statusCode().value());
        assertEquals("", renderBody(response));

        String modern = "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/cancelled\",\"params\":{"
                + "\"_meta\":{\"io.modelcontextprotocol/protocolVersion\":\"2026-07-28\"}}}";
        assertEquals(202, post(modern, modernHeaders("notifications/cancelled")).statusCode().value());
    }

    @Test
    public void legacyToolsListIsSortedAndCarriesNoModernFields() throws Exception {
        String body = "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\"}";
        JsonNode result = readResult(post(body, Collections.emptyMap()));

        assertEquals(2, result.path("tools").size());
        assertEquals("alpha", result.path("tools").get(0).path("name").asText());
        assertEquals("zeta", result.path("tools").get(1).path("name").asText());
        assertFalse(result.has("resultType"));
        assertFalse(result.has("ttlMs"));
    }

    @Test
    public void legacyToolsCallExecutesTheTool() throws Exception {
        String body = "{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"tools/call\","
                + "\"params\":{\"name\":\"alpha\",\"arguments\":{\"text\":\"hi\"}}}";
        JsonNode result = readResult(post(body, Collections.emptyMap()));
        assertEquals("alpha:hi", result.path("content").get(0).path("text").asText());
        assertFalse(result.has("resultType"));
    }

    @Test
    public void legacyUnknownToolAndUnknownMethodAreJsonRpcErrorsWithHttp200() throws Exception {
        String unknownTool = "{\"jsonrpc\":\"2.0\",\"id\":4,\"method\":\"tools/call\","
                + "\"params\":{\"name\":\"nope\",\"arguments\":{}}}";
        ServerResponse response = post(unknownTool, Collections.emptyMap());
        assertEquals(200, response.statusCode().value());
        assertEquals(-32602, readError(response).path("code").asInt());

        String unknownMethod = "{\"jsonrpc\":\"2.0\",\"id\":5,\"method\":\"resources/list\"}";
        response = post(unknownMethod, Collections.emptyMap());
        assertEquals(200, response.statusCode().value());
        assertEquals(-32601, readError(response).path("code").asInt());
    }

    @Test
    public void bareRequestWithoutAnyVersionMarkerIsServedAsLegacy() throws Exception {
        String body = "{\"jsonrpc\":\"2.0\",\"id\":6,\"method\":\"tools/call\","
                + "\"params\":{\"name\":\"alpha\",\"arguments\":{\"text\":\"bare\"}}}";
        JsonNode result = readResult(post(body, Collections.emptyMap()));
        assertEquals("alpha:bare", result.path("content").get(0).path("text").asText());
    }

    // ---------- modern era (2026-07-28, stateless) ----------

    @Test
    public void modernDiscoverAdvertisesVersionsCapabilitiesAndServerInfo() throws Exception {
        String body = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"server/discover\",\"params\":{"
                + "\"_meta\":{\"io.modelcontextprotocol/protocolVersion\":\"2026-07-28\"}}}";
        ServerResponse response = post(body, modernHeaders("server/discover"));

        assertEquals(200, response.statusCode().value());
        JsonNode result = readResult(response);
        assertEquals("complete", result.path("resultType").asText());
        assertEquals(WebMvcStreamHttpServerProvider.SUPPORTED_PROTOCOL_VERSIONS.size(),
                result.path("supportedVersions").size());
        assertEquals("2026-07-28", result.path("supportedVersions")
                .get(result.path("supportedVersions").size() - 1).asText());
        assertTrue(result.path("capabilities").has("tools"));
        assertFalse(result.path("capabilities").has("resources"));
        assertEquals("test-server",
                result.path("_meta").path("io.modelcontextprotocol/serverInfo").path("name").asText());
        assertEquals(3600000L, result.path("ttlMs").asLong());
        assertEquals("public", result.path("cacheScope").asText());
    }

    @Test
    public void modernToolsListCarriesResultTypeAndCacheHints() throws Exception {
        String body = "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\",\"params\":{"
                + "\"_meta\":{\"io.modelcontextprotocol/protocolVersion\":\"2026-07-28\"}}}";
        JsonNode result = readResult(post(body, modernHeaders("tools/list")));

        assertEquals("alpha", result.path("tools").get(0).path("name").asText());
        assertEquals("complete", result.path("resultType").asText());
        assertEquals(0L, result.path("ttlMs").asLong());
        assertEquals("private", result.path("cacheScope").asText());
        assertEquals("test-server",
                result.path("_meta").path("io.modelcontextprotocol/serverInfo").path("name").asText());
    }

    @Test
    public void modernToolsCallReturnsResultWithResultType() throws Exception {
        String body = "{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"tools/call\",\"params\":{\"name\":\"alpha\","
                + "\"arguments\":{\"text\":\"yo\"},"
                + "\"_meta\":{\"io.modelcontextprotocol/protocolVersion\":\"2026-07-28\"}}}";
        JsonNode result = readResult(post(body, modernHeaders("tools/call", "alpha")));
        assertEquals("alpha:yo", result.path("content").get(0).path("text").asText());
        assertEquals("complete", result.path("resultType").asText());
    }

    @Test
    public void modernToolsCallAcceptsBase64SentinelMcpNameHeader() throws Exception {
        String body = "{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"tools/call\",\"params\":{\"name\":\"alpha\","
                + "\"arguments\":{\"text\":\"enc\"},"
                + "\"_meta\":{\"io.modelcontextprotocol/protocolVersion\":\"2026-07-28\"}}}";
        Map<String, String> headers = modernHeaders("tools/call");
        headers.put("Mcp-Name", "=?base64?"
                + Base64.getEncoder().encodeToString("alpha".getBytes(StandardCharsets.UTF_8)) + "?=");
        JsonNode result = readResult(post(body, headers));
        assertEquals("alpha:enc", result.path("content").get(0).path("text").asText());
    }

    @Test
    public void modernProtocolVersionHeaderMustMatchBodyMeta() throws Exception {
        String body = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\",\"params\":{"
                + "\"_meta\":{\"io.modelcontextprotocol/protocolVersion\":\"2025-06-18\"}}}";
        Map<String, String> headers = modernHeaders("tools/list");
        headers.put("MCP-Protocol-Version", "2026-07-28");
        ServerResponse response = post(body, headers);

        assertEquals(400, response.statusCode().value());
        assertEquals(-32020, readError(response).path("code").asInt());
    }

    @Test
    public void modernUnsupportedVersionListsSupportedVersions() throws Exception {
        String body = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\",\"params\":{"
                + "\"_meta\":{\"io.modelcontextprotocol/protocolVersion\":\"1900-01-01\"}}}";
        Map<String, String> headers = modernHeaders("tools/list");
        headers.put("MCP-Protocol-Version", "1900-01-01");
        ServerResponse response = post(body, headers);

        assertEquals(400, response.statusCode().value());
        JsonNode error = readError(response);
        assertEquals(-32022, error.path("code").asInt());
        JsonNode supported = error.path("data").path("supported");
        assertEquals(WebMvcStreamHttpServerProvider.SUPPORTED_PROTOCOL_VERSIONS.size(), supported.size());
        assertEquals("1900-01-01", error.path("data").path("requested").asText());
    }

    @Test
    public void modernRequiresMatchingMcpMethodHeader() throws Exception {
        String body = "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\",\"params\":{"
                + "\"_meta\":{\"io.modelcontextprotocol/protocolVersion\":\"2026-07-28\"}}}";
        Map<String, String> missing = new HashMap<>();
        missing.put("MCP-Protocol-Version", "2026-07-28");
        assertEquals(-32020, readError(post(body, missing)).path("code").asInt());

        Map<String, String> wrong = modernHeaders("server/discover");
        assertEquals(-32020, readError(post(body, wrong)).path("code").asInt());
    }

    @Test
    public void modernToolsCallRequiresMatchingMcpNameHeader() throws Exception {
        String body = "{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"tools/call\",\"params\":{\"name\":\"alpha\","
                + "\"arguments\":{},"
                + "\"_meta\":{\"io.modelcontextprotocol/protocolVersion\":\"2026-07-28\"}}}";
        Map<String, String> mismatch = modernHeaders("tools/call", "zeta");
        ServerResponse response = post(body, mismatch);

        assertEquals(400, response.statusCode().value());
        assertEquals(-32020, readError(response).path("code").asInt());
    }

    @Test
    public void modernUnknownMethodIsHttp404IncludingRemovedPing() throws Exception {
        String body = "{\"jsonrpc\":\"2.0\",\"id\":9,\"method\":\"ping\",\"params\":{"
                + "\"_meta\":{\"io.modelcontextprotocol/protocolVersion\":\"2026-07-28\"}}}";
        ServerResponse response = post(body, modernHeaders("ping"));

        assertEquals(404, response.statusCode().value());
        assertEquals(-32601, readError(response).path("code").asInt());
    }

    // ---------- transport-level behaviour shared by both eras ----------

    @Test
    public void missingOrInvalidTokenIsRejectedWith401() throws Exception {
        String body = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\"}";
        Map<String, String> headers = new HashMap<>();
        headers.put("Accept", "application/json, text/event-stream");

        ServerResponse response = provider.handlePost(request(headers, Collections.emptyMap(), body));
        assertEquals(401, response.statusCode().value());

        Map<String, String> badToken = new HashMap<>();
        badToken.put("token", "bad-token");
        response = post(body, Collections.emptyMap(), badToken);
        assertEquals(401, response.statusCode().value());
    }

    @Test
    public void tokenIsAlsoAcceptedAsBearerHeader() throws Exception {
        String body = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\"}";
        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Bearer " + GOOD_TOKEN);
        ServerResponse response = provider.handlePost(
                request(headers, Collections.emptyMap(), body));

        assertEquals(200, response.statusCode().value());
        assertEquals("alpha", readResult(response).path("tools").get(0).path("name").asText());
    }

    @Test
    public void batchedOrMalformedBodiesAreRejected() throws Exception {
        ServerResponse response = post("[{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\"}]",
                Collections.emptyMap());
        assertEquals(400, response.statusCode().value());

        response = post("{not json", Collections.emptyMap());
        assertEquals(400, response.statusCode().value());

        response = post("{\"jsonrpc\":\"2.0\",\"id\":1}", Collections.emptyMap());
        assertEquals(400, response.statusCode().value());
    }

    @Test
    public void acceptHeaderWithoutJsonOrEventStreamIsRejected() throws Exception {
        String body = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\"}";
        Map<String, String> headers = new HashMap<>();
        headers.put("Accept", "text/plain");
        assertEquals(400, post(body, headers).statusCode().value());
    }

    @Test
    public void getAndDeleteAreMethodNotAllowed() {
        assertEquals(405, provider.handleGet(request(Collections.emptyMap(), Collections.emptyMap(), ""))
                .statusCode().value());
        assertEquals(405, provider.handleDelete(request(Collections.emptyMap(), Collections.emptyMap(), ""))
                .statusCode().value());
    }

    // ---------- fixtures ----------

    private static final class NoopTransportProvider implements McpServerTransportProvider {

        @Override
        public void setSessionFactory(McpServerSession.Factory sessionFactory) {
        }

        @Override
        public Mono<Void> notifyClients(String method, Map<String, Object> params) {
            return Mono.empty();
        }

        @Override
        public Mono<Void> closeGracefully() {
            return Mono.empty();
        }
    }

    private static final class FixedAuthChecker implements WebMvcSseServerAuthChecker {

        @Override
        public String getTokenParamName() {
            return "token";
        }

        @Override
        public boolean checkTokenValid(String token) {
            return GOOD_TOKEN.equals(token);
        }
    }

    private static Map<String, String> modernHeaders(String method) {
        Map<String, String> headers = new HashMap<>();
        headers.put("MCP-Protocol-Version", "2026-07-28");
        headers.put("Mcp-Method", method);
        return headers;
    }

    private static Map<String, String> modernHeaders(String method, String toolName) {
        Map<String, String> headers = modernHeaders(method);
        headers.put("Mcp-Name", toolName);
        return headers;
    }

    /** POST with the good token as a query param plus the standard Accept header. */
    private static ServerResponse post(String body, Map<String, String> headers) {
        Map<String, String> params = new HashMap<>();
        params.put("token", GOOD_TOKEN);
        return post(body, headers, params);
    }

    private static ServerResponse post(String body, Map<String, String> headers, Map<String, String> params) {
        Map<String, String> allHeaders = new LinkedHashMap<>();
        allHeaders.put("Accept", "application/json, text/event-stream");
        allHeaders.putAll(headers);
        return provider.handlePost(request(allHeaders, params, body));
    }

    private static ServerRequest request(Map<String, String> headers, Map<String, String> params, String body) {
        HttpHeaders httpHeaders = new HttpHeaders();
        headers.forEach(httpHeaders::add);
        MultiValueMap<String, String> allParams = new LinkedMultiValueMap<>();
        params.forEach(allParams::add);
        return (ServerRequest) Proxy.newProxyInstance(WebMvcStreamHttpServerProviderTest.class.getClassLoader(),
                new Class<?>[]{ServerRequest.class}, (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "headers":
                            return proxyHeaders(httpHeaders);
                        case "param":
                            return Optional.ofNullable(params.get((String) args[0]));
                        case "params":
                            return allParams;
                        case "body":
                            return body;
                        default:
                            return defaultValue(method.getReturnType());
                    }
                });
    }

    private static ServerRequest.Headers proxyHeaders(HttpHeaders httpHeaders) {
        return (ServerRequest.Headers) Proxy.newProxyInstance(
                WebMvcStreamHttpServerProviderTest.class.getClassLoader(),
                new Class<?>[]{ServerRequest.Headers.class}, (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "asHttpHeaders":
                            return httpHeaders;
                        case "accept":
                            return httpHeaders.getAccept();
                        case "contentType":
                            return Optional.ofNullable(httpHeaders.getContentType());
                        default:
                            return defaultValue(method.getReturnType());
                    }
                });
    }

    /** Renders the response body the way the MVC adapter would: through a Jackson converter onto the servlet output stream. */
    private static String renderBody(ServerResponse response) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Map<String, List<String>> writtenHeaders = new LinkedHashMap<>();
        HttpServletResponse servletResponse = (HttpServletResponse) Proxy.newProxyInstance(
                WebMvcStreamHttpServerProviderTest.class.getClassLoader(),
                new Class<?>[]{HttpServletResponse.class}, (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getOutputStream":
                            return capturingOutputStream(out);
                        case "setHeader":
                        case "addHeader":
                            writtenHeaders.computeIfAbsent((String) args[0], key -> new ArrayList<>())
                                    .add((String) args[1]);
                            return null;
                        case "getHeaderNames":
                            return Collections.enumeration(writtenHeaders.keySet());
                        case "getHeader":
                            List<String> values = writtenHeaders.get((String) args[0]);
                            return values == null || values.isEmpty() ? null : values.get(0);
                        case "isCommitted":
                            return false;
                        default:
                            return defaultValue(method.getReturnType());
                    }
                });
        HttpServletRequest servletRequest = (HttpServletRequest) Proxy.newProxyInstance(
                WebMvcStreamHttpServerProviderTest.class.getClassLoader(),
                new Class<?>[]{HttpServletRequest.class},
                (proxy, method, args) -> "getMethod".equals(method.getName())
                        ? "POST" : defaultValue(method.getReturnType()));
        response.writeTo(servletRequest, servletResponse, () -> Collections.singletonList(
                (HttpMessageConverter<?>) new MappingJackson2HttpMessageConverter(MAPPER)));
        return out.toString(StandardCharsets.UTF_8);
    }

    private static ServletOutputStream capturingOutputStream(ByteArrayOutputStream out) {
        return new ServletOutputStream() {
            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setWriteListener(WriteListener writeListener) {
            }

            @Override
            public void write(int b) {
                out.write(b);
            }
        };
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive() || type == void.class) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == double.class) {
            return 0D;
        }
        if (type == float.class) {
            return 0F;
        }
        if (type == char.class) {
            return '\0';
        }
        if (type == byte.class) {
            return (byte) 0;
        }
        if (type == short.class) {
            return (short) 0;
        }
        return 0;
    }

    private static JsonNode readResult(ServerResponse response) throws Exception {
        JsonNode json = MAPPER.readTree(renderBody(response));
        assertFalse("expected a result, got: " + json, json.has("error"));
        return json.path("result");
    }

    private static JsonNode readError(ServerResponse response) throws Exception {
        JsonNode json = MAPPER.readTree(renderBody(response));
        assertTrue("expected an error, got: " + json, json.has("error"));
        return json.path("error");
    }
}
