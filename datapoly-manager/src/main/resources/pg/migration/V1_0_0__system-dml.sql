-- DataPoly 元库种子数据初始基线（v1.0.0，PostgreSQL 方言）：合并自历史迁移 V1_0_2 / V1_2_1 / V1_3_1，
-- 并吸收 V1_9_1 的 apiDocOpen 收口（直接种为 false，不再经历 true→false 演进）。
-- admin 的 bcrypt 哈希一字不改：与 AdminPasswordInitializer#DEFAULT_ADMIN_BCRYPT_HASH 相同，
-- 该常量据此识别"出厂默认口令"，改哈希会破坏启动告警逻辑。
-- 显式插入 id=1 的行（firewall_rules/unify_alarm）后用 setval 同步 bigserial 序列，
-- 避免首次运行时自增分配到已占用 id（历史版本存在该隐患）。
-- group/module/app_client 由序列隐式赋 id：全新安装首行为 1，client_group 授权行据此引用。

INSERT INTO datapoly_system_user (username, password, salt, real_name, locked, email)
VALUES ('admin', '$2a$10$eUanVjvzV27BBxAb4zuBCugwnngHkRZ7ZB4iI5tdx9ETJ2tnXJJDy', '$2a$10$eUanVjvzV27BBxAb4zuBCu',
        'Administrator', false, 'admin@126.com');

INSERT INTO datapoly_firewall_rules (id, status, mode)
VALUES ('1', 'OFF', 'BLACK');
SELECT setval(pg_get_serial_sequence('datapoly_firewall_rules', 'id'),
              (SELECT max(id) FROM datapoly_firewall_rules));

INSERT INTO datapoly_api_group (name)
VALUES ('DefaultGroup');

INSERT INTO datapoly_api_module (name)
VALUES ('DefaultModule');

-- access_token 统一为固定演示值，与 MySQL 方言一致（确定性便于部署核验）
INSERT INTO datapoly_app_client (name, description, app_key, app_secret, expire_duration, expire_at,
                                 access_token, token_alive)
VALUES ('test', 'usage for test', 'test', 'test', 'FOR_EVER', '-1', '9097ac1ab13198dfa4ddb2ecc1079693', 'PERIOD');

INSERT INTO datapoly_client_group (client_id, group_id)
VALUES ('1', '1');

INSERT INTO datapoly_system_param (param_key, param_type, param_value)
VALUES ('apiDocOpen', 'BOOLEAN', 'false');
INSERT INTO datapoly_system_param (param_key, param_type, param_value)
VALUES ('apiDocInfoTitle', 'STRING', 'Online interface document');
INSERT INTO datapoly_system_param (param_key, param_type, param_value)
VALUES ('apiDocInfoVersion', 'STRING', '1.0');
INSERT INTO datapoly_system_param (param_key, param_type, param_value)
VALUES ('apiDocInfoDescription', 'STRING', 'Swagger Online Document');
INSERT INTO datapoly_system_param (param_key, param_type, param_value)
VALUES ('mcpToolListPageSize', 'LONG', '1000');

INSERT INTO datapoly_unify_alarm (id, status, endpoint, content_type, input_template)
VALUES ('1', 'OFF', 'http://127.0.0.1:8000/api/v1/message/send', 'application/json', '{}');
SELECT setval(pg_get_serial_sequence('datapoly_unify_alarm', 'id'),
              (SELECT max(id) FROM datapoly_unify_alarm));
