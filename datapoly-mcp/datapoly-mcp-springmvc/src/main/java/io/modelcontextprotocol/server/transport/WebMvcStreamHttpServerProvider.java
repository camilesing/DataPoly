/*
 * Copyright 2024-2024 the original author or authors.
 */

package io.modelcontextprotocol.server.transport;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.server.McpServerFeatures.AsyncToolSpecification;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.spec.McpError;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.JSONRPCResponse;
import io.modelcontextprotocol.spec.McpSchema.JSONRPCResponse.JSONRPCError;
import io.modelcontextprotocol.util.Assert;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.RouterFunctions;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * MCP Streamable HTTP transport, single stateless POST endpoint, serving both protocol eras
 * on the same URL: legacy clients (2025-11-25 and earlier, {@code initialize} handshake) and
 * modern clients (2026-07-28, per-request version metadata and {@code server/discover}).
 * Message handlers are package-private so datapoly-test can drive them without the Servlet
 * machinery.
 */
@Slf4j
public class WebMvcStreamHttpServerProvider {

    public static final String DEFAULT_SSE_ENDPOINT = "/mcp";

    public static final String MODERN_PROTOCOL_VERSION = "2026-07-28";

    public static final String NEWEST_LEGACY_PROTOCOL_VERSION = "2025-11-25";

    /** The one list of protocol versions this transport serves; discover, initialize negotiation and version errors all read it. */
    public static final List<String> SUPPORTED_PROTOCOL_VERSIONS = Collections.unmodifiableList(
            Arrays.asList("2024-11-05", "2025-03-26", "2025-06-18", NEWEST_LEGACY_PROTOCOL_VERSION,
                    MODERN_PROTOCOL_VERSION));

    private static final String META_FIELD = "_meta";
    private static final String META_PROTOCOL_VERSION = "io.modelcontextprotocol/protocolVersion";
    private static final String META_SERVER_INFO = "io.modelcontextprotocol/serverInfo";
    private static final String HEADER_PROTOCOL_VERSION = "MCP-Protocol-Version";
    private static final String HEADER_METHOD = "Mcp-Method";
    private static final String HEADER_NAME = "Mcp-Name";
    private static final String HEADER_BEARER_PREFIX = "Bearer ";
    private static final String BASE64_SENTINEL_PREFIX = "=?base64?";
    private static final String BASE64_SENTINEL_SUFFIX = "?=";
    private static final String RESULT_TYPE_COMPLETE = "complete";
    private static final String CACHE_SCOPE_PRIVATE = "private";
    private static final String CACHE_SCOPE_PUBLIC = "public";
    private static final long DISCOVER_TTL_MS = 3_600_000L;
    private static final long LIST_TTL_MS = 0L;
    private static final int ERROR_HEADER_MISMATCH = -32020;
    private static final int ERROR_UNSUPPORTED_PROTOCOL_VERSION = -32022;
    // bound the wait so a hung tool handler cannot pin the MVC worker thread forever
    private static final Duration TOOL_CALL_TIMEOUT = Duration.ofSeconds(60);

    private final ObjectMapper objectMapper;

    private final WebMvcSseServerAuthChecker serverAuthChecker;

    private final String mcpEndpoint;

    private final McpSyncServer mcpSyncServer;

    private final RouterFunction<ServerResponse> routerFunction;

    public WebMvcStreamHttpServerProvider(ObjectMapper objectMapper,
                                          WebMvcSseServerAuthChecker serverAuthChecker, String mcpEndpoint,
                                          McpSyncServer mcpSyncServer) {
        Assert.notNull(objectMapper, "ObjectMapper must not be null");
        Assert.notNull(mcpEndpoint, "MCP endpoint must not be null");

        this.objectMapper = objectMapper;
        this.serverAuthChecker = serverAuthChecker;
        this.mcpEndpoint = mcpEndpoint;
        this.mcpSyncServer = mcpSyncServer;
        this.routerFunction = RouterFunctions.route()
                .GET(this.mcpEndpoint, this::handleGet)
                .POST(this.mcpEndpoint, this::handlePost)
                .DELETE(this.mcpEndpoint, this::handleDelete)
                .build();
    }

    public RouterFunction<ServerResponse> getRouterFunction() {
        return routerFunction;
    }

    ServerResponse handleGet(ServerRequest request) {
        return ServerResponse.status(HttpStatus.METHOD_NOT_ALLOWED).body("GET method is not allowed.");
    }

    ServerResponse handleDelete(ServerRequest request) {
        return ServerResponse.status(HttpStatus.METHOD_NOT_ALLOWED).body("DELETE method is not allowed.");
    }

    ServerResponse handlePost(ServerRequest request) {
        ServerResponse rejected = validateAccept(request);
        if (rejected != null) {
            return rejected;
        }
        rejected = checkToken(request);
        if (rejected != null) {
            return rejected;
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(request.body(String.class));
        } catch (Exception e) {
            return badRequestMcpError("Malformed JSON-RPC message body");
        }
        if (root == null || !root.isObject()) {
            return badRequestMcpError("JSON-RPC batching is not supported; the body must be a single message object");
        }
        JsonNode methodNode = root.get("method");
        if (methodNode == null || !methodNode.isTextual()) {
            return badRequestMcpError("JSON-RPC message must carry a 'method' field");
        }
        String method = methodNode.asText();
        JsonNode idNode = root.get("id");
        boolean notification = idNode == null || idNode.isNull();

        // transport mechanics for a notification POST are the same in both eras
        if (notification) {
            return ServerResponse.accepted().build();
        }

        String methodVersion = metaProtocolVersion(root);
        String headerVersion = trimToNull(request.headers().asHttpHeaders().getFirst(HEADER_PROTOCOL_VERSION));
        boolean modern = !McpSchema.METHOD_INITIALIZE.equals(method)
                && (headerVersion != null || methodVersion != null);

        try {
            if (modern) {
                return handleModern(request, root, method, requestId(idNode), headerVersion, methodVersion);
            }
            return handleLegacy(root, method, requestId(idNode));
        } catch (Exception e) {
            log.error("Error handling message: {}", e.getMessage(), e);
            return ServerResponse.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new McpError(e.getMessage()));
        }
    }

    // modern era (2026-07-28): stateless requests carrying per-request version metadata

    private ServerResponse handleModern(ServerRequest request, JsonNode root, String method, Object id,
            String headerVersion, String methodVersion) {
        if (headerVersion != null && methodVersion != null && !headerVersion.equals(methodVersion)) {
            return modernBadRequest(id, ERROR_HEADER_MISMATCH,
                    "Header mismatch: " + HEADER_PROTOCOL_VERSION + " header '" + headerVersion
                            + "' does not match the body _meta value '" + methodVersion + "'");
        }
        String version = headerVersion != null ? headerVersion : methodVersion;
        if (!SUPPORTED_PROTOCOL_VERSIONS.contains(version)) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("supported", SUPPORTED_PROTOCOL_VERSIONS);
            data.put("requested", version);
            return modernBadRequest(id, ERROR_UNSUPPORTED_PROTOCOL_VERSION, "Unsupported protocol version", data);
        }
        ServerResponse mismatch = validateStandardHeaders(request, root, method, id);
        if (mismatch != null) {
            return mismatch;
        }

        switch (method) {
            case "server/discover":
                return jsonResponse(HttpStatus.OK, response(id, discoverResult(), null));
            case McpSchema.METHOD_TOOLS_LIST:
                return jsonResponse(HttpStatus.OK, response(id, toolsListResult(), null));
            case McpSchema.METHOD_TOOLS_CALL: {
                ToolCallOutcome outcome = executeTool(root.get("params"));
                return outcome.error() != null
                        ? jsonResponse(HttpStatus.OK, response(id, null, outcome.error()))
                        : jsonResponse(HttpStatus.OK, response(id, modernToolResult(outcome.result()), null));
            }
            default:
                // modern clients recognise a 404 + JSON-RPC -32601 as "modern server, unknown method"
                return jsonResponse(HttpStatus.NOT_FOUND,
                        response(id, null, error(McpSchema.ErrorCodes.METHOD_NOT_FOUND,
                                "Unknown method: '" + method + "'")));
        }
    }

    private ServerResponse validateStandardHeaders(ServerRequest request, JsonNode root, String method, Object id) {
        HttpHeaders headers = request.headers().asHttpHeaders();
        String methodHeader = trimToNull(headers.getFirst(HEADER_METHOD));
        if (methodHeader == null || !methodHeader.equals(method)) {
            return modernBadRequest(id, ERROR_HEADER_MISMATCH,
                    "Header mismatch: " + HEADER_METHOD + " header '" + methodHeader
                            + "' does not match the body method '" + method + "'");
        }
        if (McpSchema.METHOD_TOOLS_CALL.equals(method)) {
            JsonNode nameNode = root.path("params").path("name");
            if (nameNode.isTextual()) {
                String nameHeader = trimToNull(headers.getFirst(HEADER_NAME));
                String decoded = nameHeader == null ? null : decodeHeaderValue(nameHeader);
                if (decoded == null || !decoded.equals(nameNode.asText())) {
                    return modernBadRequest(id, ERROR_HEADER_MISMATCH,
                            "Header mismatch: " + HEADER_NAME + " header '" + nameHeader
                                    + "' does not match the body tool name '" + nameNode.asText() + "'");
                }
            }
        }
        return null;
    }

    private Map<String, Object> discoverResult() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("resultType", RESULT_TYPE_COMPLETE);
        result.put("supportedVersions", SUPPORTED_PROTOCOL_VERSIONS);
        result.put("capabilities", toolsOnlyCapabilities());
        result.put("ttlMs", DISCOVER_TTL_MS);
        result.put("cacheScope", CACHE_SCOPE_PUBLIC);
        result.put(META_FIELD, serverInfoMeta());
        return result;
    }

    private Map<String, Object> toolsListResult() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("tools", tools());
        result.put("resultType", RESULT_TYPE_COMPLETE);
        result.put("ttlMs", LIST_TTL_MS);
        result.put("cacheScope", CACHE_SCOPE_PRIVATE);
        result.put(META_FIELD, serverInfoMeta());
        return result;
    }

    private Map<String, Object> modernToolResult(McpSchema.CallToolResult callResult) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (callResult.getContent() != null) {
            result.put("content", callResult.getContent());
        }
        if (callResult.getIsError() != null) {
            result.put("isError", callResult.getIsError());
        }
        result.put("resultType", RESULT_TYPE_COMPLETE);
        result.put(META_FIELD, serverInfoMeta());
        return result;
    }

    // legacy era (initialize handshake, 2025-11-25 and earlier)

    private ServerResponse handleLegacy(JsonNode root, String method, Object id) {
        switch (method) {
            case McpSchema.METHOD_INITIALIZE: {
                String requested = root.path("params").path("protocolVersion").asText(null);
                String negotiated = requested != null && isLegacyProtocolVersion(requested)
                        ? requested : NEWEST_LEGACY_PROTOCOL_VERSION;
                McpSchema.InitializeResult result = new McpSchema.InitializeResult(negotiated,
                        toolsOnlyCapabilities(), mcpSyncServer.getServerInfo(), null);
                return jsonResponse(HttpStatus.OK, response(id, result, null));
            }
            case McpSchema.METHOD_TOOLS_LIST: {
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("tools", tools());
                return jsonResponse(HttpStatus.OK, response(id, result, null));
            }
            case McpSchema.METHOD_TOOLS_CALL: {
                ToolCallOutcome outcome = executeTool(root.get("params"));
                return outcome.error() != null
                        ? jsonResponse(HttpStatus.OK, response(id, null, outcome.error()))
                        : jsonResponse(HttpStatus.OK, response(id, outcome.result(), null));
            }
            case McpSchema.METHOD_PING:
            case McpSchema.METHOD_LOGGING_SET_LEVEL:
                return jsonResponse(HttpStatus.OK, response(id, Collections.emptyMap(), null));
            default:
                return jsonResponse(HttpStatus.OK, response(id, null, error(McpSchema.ErrorCodes.METHOD_NOT_FOUND,
                        "Unknown method: '" + method + "'")));
        }
    }

    private static boolean isLegacyProtocolVersion(String version) {
        return !MODERN_PROTOCOL_VERSION.equals(version) && SUPPORTED_PROTOCOL_VERSIONS.contains(version);
    }

    // pieces shared by both eras

    private ToolCallOutcome executeTool(JsonNode paramsNode) {
        if (paramsNode == null || !paramsNode.isObject()) {
            return ToolCallOutcome.failure("params are required for tools/call");
        }
        CallToolRequest callRequest;
        try {
            callRequest = objectMapper.convertValue(paramsNode, CallToolRequest.class);
        } catch (Exception e) {
            return ToolCallOutcome.failure("Invalid tools/call params");
        }
        if (callRequest.getName() == null || callRequest.getName().isEmpty()) {
            return ToolCallOutcome.failure("Tool name is required");
        }
        Optional<AsyncToolSpecification> toolSpecification = mcpSyncServer.listTool().stream()
                .filter(tr -> callRequest.getName().equals(tr.getTool().getName()))
                .findAny();
        if (!toolSpecification.isPresent()) {
            return ToolCallOutcome.failure("Tool not found: '" + callRequest.getName() + "'");
        }
        McpSchema.CallToolResult result = toolSpecification.get().getCall()
                .apply(null, callRequest.getArguments())
                .block(TOOL_CALL_TIMEOUT);
        return ToolCallOutcome.success(result != null ? result
                : new McpSchema.CallToolResult(Collections.emptyList(), null));
    }

    private List<McpSchema.Tool> tools() {
        return mcpSyncServer.listTool().stream()
                .map(AsyncToolSpecification::getTool)
                .sorted(Comparator.comparing(McpSchema.Tool::getName))
                .collect(Collectors.toList());
    }

    private McpSchema.ServerCapabilities toolsOnlyCapabilities() {
        return new McpSchema.ServerCapabilities(null, null, null, null,
                new McpSchema.ServerCapabilities.ToolCapabilities(null));
    }

    private Map<String, Object> serverInfoMeta() {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put(META_SERVER_INFO, mcpSyncServer.getServerInfo());
        return meta;
    }

    private ServerResponse validateAccept(ServerRequest request) {
        List<MediaType> acceptHeaders = request.headers().asHttpHeaders().getAccept();
        boolean acceptable = acceptHeaders.isEmpty() || acceptHeaders.stream().anyMatch(mediaType ->
                mediaType.includes(MediaType.APPLICATION_JSON) || mediaType.includes(MediaType.TEXT_EVENT_STREAM));
        return acceptable ? null : ServerResponse.badRequest()
                .body(new McpError("Invalid Accept headers; expected application/json and/or text/event-stream"));
    }

    private ServerResponse checkToken(ServerRequest request) {
        if (this.serverAuthChecker == null) {
            return null;
        }
        String token = request.param(this.serverAuthChecker.getTokenParamName()).orElse(null);
        if (token == null) {
            String authorization = request.headers().asHttpHeaders().getFirst(HttpHeaders.AUTHORIZATION);
            if (authorization != null && authorization.regionMatches(true, 0, HEADER_BEARER_PREFIX, 0,
                    HEADER_BEARER_PREFIX.length())) {
                token = authorization.substring(HEADER_BEARER_PREFIX.length()).trim();
            }
        }
        if (token == null) {
            return ServerResponse.status(HttpStatus.UNAUTHORIZED).body(new McpError("MCP token missing"));
        }
        if (!this.serverAuthChecker.checkTokenValid(token)) {
            return ServerResponse.status(HttpStatus.UNAUTHORIZED).body(new McpError("MCP token invalid"));
        }
        return null;
    }

    private String decodeHeaderValue(String value) {
        if (value.startsWith(BASE64_SENTINEL_PREFIX) && value.endsWith(BASE64_SENTINEL_SUFFIX)) {
            String encoded = value.substring(BASE64_SENTINEL_PREFIX.length(),
                    value.length() - BASE64_SENTINEL_SUFFIX.length());
            try {
                return new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);
            } catch (IllegalArgumentException e) {
                // fall through and compare as-is, which fails the match below
            }
        }
        return value;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String metaProtocolVersion(JsonNode root) {
        JsonNode version = root.path("params").path("_meta").path(META_PROTOCOL_VERSION);
        return version.isTextual() ? version.asText() : null;
    }

    private Object requestId(JsonNode idNode) {
        return objectMapper.convertValue(idNode, Object.class);
    }

    private static JSONRPCError error(int code, String message) {
        return new JSONRPCError(code, message, null);
    }

    private static JSONRPCResponse response(Object id, Object result, JSONRPCError error) {
        JSONRPCResponse jsonrpcResponse = new JSONRPCResponse();
        jsonrpcResponse.setJsonrpc(McpSchema.JSONRPC_VERSION);
        jsonrpcResponse.setId(id);
        jsonrpcResponse.setResult(result);
        jsonrpcResponse.setError(error);
        return jsonrpcResponse;
    }

    private static ServerResponse jsonResponse(HttpStatus status, JSONRPCResponse body) {
        return ServerResponse.status(status).contentType(MediaType.APPLICATION_JSON).body(body);
    }

    private ServerResponse modernBadRequest(Object id, int code, String message) {
        return modernBadRequest(id, code, message, null);
    }

    private ServerResponse modernBadRequest(Object id, int code, String message, Object data) {
        return ServerResponse.badRequest().contentType(MediaType.APPLICATION_JSON)
                .body(response(id, null, new JSONRPCError(code, message, data)));
    }

    private static ServerResponse badRequestMcpError(String message) {
        return ServerResponse.badRequest().body(new McpError(message));
    }

    private record ToolCallOutcome(McpSchema.CallToolResult result, JSONRPCError error) {

        static ToolCallOutcome success(McpSchema.CallToolResult result) {
            return new ToolCallOutcome(result, null);
        }

        static ToolCallOutcome failure(String message) {
            return new ToolCallOutcome(null,
                    new JSONRPCError(McpSchema.ErrorCodes.INVALID_PARAMS, message, null));
        }
    }
}
