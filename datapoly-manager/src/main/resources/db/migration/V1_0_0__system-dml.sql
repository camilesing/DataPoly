-- DataPoly 元库种子数据初始基线（v1.0.0）：合并自历史迁移 V1_0_2 / V1_2_1 / V1_3_1，
-- 并吸收 V1_9_1 的 apiDocOpen 收口（直接种为 false，不再经历 true→false 演进）。
-- admin 的 bcrypt 哈希一字不改：与 AdminPasswordInitializer#DEFAULT_ADMIN_BCRYPT_HASH 相同，
-- 该常量据此识别"出厂默认口令"，改哈希会破坏启动告警逻辑。

insert into `DATAPOLY_SYSTEM_USER`(`username`, `password`, `salt`, `real_name`, `locked`, `email`)
VALUES ('admin', '$2a$10$eUanVjvzV27BBxAb4zuBCugwnngHkRZ7ZB4iI5tdx9ETJ2tnXJJDy', '$2a$10$eUanVjvzV27BBxAb4zuBCu',
        'Administrator', 0, 'admin@126.com');

INSERT INTO `DATAPOLY_FIREWALL_RULES` (`id`, `status`, `mode`)
VALUES ('1', 'OFF', 'BLACK');

INSERT INTO `DATAPOLY_API_GROUP` (`id`, `name`)
VALUES ('1', 'DefaultGroup');

INSERT INTO `DATAPOLY_API_MODULE` (`id`, `name`)
VALUES ('1', 'DefaultModule');

-- access_token 两侧统一为固定演示值（原 MySQL 侧 md5(uuid()) 为随机值，确定性便于部署核验）
INSERT INTO `DATAPOLY_APP_CLIENT` (`id`, `name`, `description`, `app_key`, `app_secret`, `expire_duration`, `expire_at`,
                                  `access_token`)
VALUES ('1', 'test', 'usage for test', 'test', 'test', 'FOR_EVER', '-1', '9097ac1ab13198dfa4ddb2ecc1079693');

INSERT INTO `DATAPOLY_CLIENT_GROUP` (`id`, `client_id`, `group_id`)
VALUES ('1', '1', '1');

INSERT INTO `DATAPOLY_SYSTEM_PARAM` (`param_key`, `param_type`, `param_value`)
VALUES ('apiDocOpen', 'BOOLEAN', 'false');
INSERT INTO `DATAPOLY_SYSTEM_PARAM` (`param_key`, `param_type`, `param_value`)
VALUES ('apiDocInfoTitle', 'STRING', 'Online interface document');
INSERT INTO `DATAPOLY_SYSTEM_PARAM` (`param_key`, `param_type`, `param_value`)
VALUES ('apiDocInfoVersion', 'STRING', '1.0');
INSERT INTO `DATAPOLY_SYSTEM_PARAM` (`param_key`, `param_type`, `param_value`)
VALUES ('apiDocInfoDescription', 'STRING', 'Swagger Online Document');
INSERT INTO `DATAPOLY_SYSTEM_PARAM` (`param_key`, `param_type`, `param_value`)
VALUES ('mcpToolListPageSize', 'LONG', '1000');

INSERT INTO `DATAPOLY_UNIFY_ALARM` (`id`, `status`, `endpoint`, `content_type`, `input_template`)
VALUES ('1', 'OFF', 'http://127.0.0.1:8000/api/v1/message/send', 'application/json', '{}');
