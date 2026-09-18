-- 管理MCP服务权限标记：为1时该令牌可访问 /mcp/admin 管理端点（Agent 实体增删改查）
ALTER TABLE `DATAPOLY_MCP_CLIENT`
    ADD COLUMN `manage_flag` tinyint(1) NOT NULL DEFAULT 0 COMMENT '管理MCP服务权限' AFTER `token`;
