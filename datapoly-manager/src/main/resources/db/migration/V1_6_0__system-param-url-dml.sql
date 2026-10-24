-- v1.6.0：对外地址（gatewayUrl / managerUrl）入库可配。
-- 空串 = 未设置，读取时回退 DATAPOLY_GATEWAY_URL / DATAPOLY_MANAGER_URL 环境变量，
-- 再回退 Nacos 服务发现注册地址；存量部署升级后行为不变。
INSERT INTO `DATAPOLY_SYSTEM_PARAM` (`param_key`, `param_type`, `param_value`)
VALUES ('gatewayUrl', 'STRING', '');
INSERT INTO `DATAPOLY_SYSTEM_PARAM` (`param_key`, `param_type`, `param_value`)
VALUES ('managerUrl', 'STRING', '');