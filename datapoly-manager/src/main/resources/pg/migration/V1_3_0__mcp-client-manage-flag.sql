-- 管理MCP服务权限标记：为 true 时该令牌可访问 /mcp/admin 管理端点（Agent 实体增删改查）
ALTER TABLE datapoly_mcp_client
    ADD COLUMN manage_flag boolean NOT NULL DEFAULT false;
