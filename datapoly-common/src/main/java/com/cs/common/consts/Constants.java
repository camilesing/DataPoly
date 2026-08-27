// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.common.consts;

public abstract class Constants {

    public static final String API_PATH_PREFIX = "api";

    public static final String MANAGER_API_PREFIX = "/datapoly/manager/api";
    public static final String MANAGER_API_V1 = MANAGER_API_PREFIX + "/v1";

    public static final String REQUEST_HEADER_GATEWAY_IP = "Request-Gateway-IP";

    public static final String PARAM_PAGE_NUMBER = "apiPageNum";
    public static final String PARAM_PAGE_SIZE = "apiPageSize";

    public static final String GATEWAY_APPLICATION_NAME = "datapoly-gateway";

    public static final String MANAGER_APPLICATION_NAME = "datapoly-manager";

    public static final String CACHE_KEY_TOKEN_CLIENT = "token_client";

    public static final Long CLIENT_TOKEN_DURATION_SECONDS = 7200L;

    public static final Long CLIENT_TOKEN_LONGEVITY = -1L;

    public static final int SC_TOO_MANY_REQUESTS = 429;

    public static final String CACHE_NAME_API_RESPONSE = "response_result";

    public static final String CACHE_NAME_API_VAR = "variable_value";

    public static final String API_DOC_PATH_PREFIX = "/apidoc";

    public static final String SYS_PARAM_KEY_API_DOC_OPEN = "apiDocOpen";

    public static final String SYS_PARAM_KEY_SWAGGER_INFO_TITLE = "apiDocInfoTitle";

    public static final String SYS_PARAM_KEY_SWAGGER_INFO_VERSION = "apiDocInfoVersion";

    public static final String SYS_PARAM_KEY_SWAGGER_INFO_DESCRIPTION = "apiDocInfoDescription";

    public static final String SYS_PARAM_KEY_MCP_TOOL_LIST_PAGE_SIZE = "mcpToolListPageSize";

    public static final String DEFAULT_MCP_TOKEN_PRAM_NAME = "token";
    public static final String DEFAULT_STREAM_ENDPOINT = "/mcp";
    public static final String DEFAULT_SSE_ENDPOINT = "/mcp/sse";
    public static final String MESSAGE_ENDPOINT = "/mcp/message";
    public static final String MCP_SERVER_NAME = "datapoly-mcp-server";

    public static final String getResourceName(String method, String path) {
        return String.format("/%s/%s[%s]", Constants.API_PATH_PREFIX, path, method);
    }
}
