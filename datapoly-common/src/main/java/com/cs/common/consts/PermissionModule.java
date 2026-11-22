// Use of this source code is governed by a BSD-style license
package com.cs.common.consts;

/**
 * One row of the manager's module grid: the business areas a {@code @RequirePermission} mark can
 * gate. Each value owns one controller family, and the manager-ui menu entry of the same name is
 * shown or hidden by the same module key — one list, both sides. User management and
 * secret-plaintext endpoints are deliberately absent: they are hardcoded ADMIN and can never be
 * granted through the matrix.
 */
public enum PermissionModule {

    /** Data sources: CRUD plus connection testing and schema browsing. */
    DATASOURCE,

    /** API assignments: CRUD, import/export, publish/deploy/retire, parse and debug. */
    API,

    /** API groups. */
    API_GROUP,

    /** API modules and the module tree. */
    API_MODULE,

    /** App clients and their group auth; the appSecret plaintext endpoint is Admin-only, not here. */
    APP_CLIENT,

    /** Data tasks: CRUD plus parse/preview/submit/cancel and job queries. */
    DATA_TASK,

    /** Firewall rules: read the effective list, save a new one. */
    FIREWALL,

    /** MCP client tokens; the token plaintext endpoint is Admin-only, not here. */
    MCP_CLIENT,

    /** MCP tools. */
    MCP_TOOL,

    /** Node addresses: gateway/prefix/topology reads and the url-config write. */
    NODE,

    /** Dashboard statistics (read-only). */
    OVERVIEW,

    /** System parameters: query one value, update one value. */
    SYSTEM_PARAM,

    /** Unified alarm config: read, test-send, save. */
    ALARM,

    /** API version snapshots: list/show and revert. */
    VERSION
}
