-- 登录会话令牌（v1.7.0）：管理端登录态从「只活在本进程的 CacheUtils（2 小时滑动、重启即丢）」
-- 改为落库，manager 重启不再把所有人踢下线。有效期 = 签发时刻 + datapoly.security.login-token-ttl-seconds
-- （缺省 604800 = 7 天），**绝对过期**：不随使用延长，满期即需重新登录。
-- create_at / expire_at 都是 epoch 秒，与 DATAPOLY_APP_CLIENT.expire_at 同一口径（避开 MySQL/PG
-- 时间戳与连接时区差异，比较逻辑两库一份）。
-- 删行即失效：退出登录、改口令/重置口令（吊销该账号全部会话）、过期行的惰性删除；
-- 另有 manager 启动与每晚的清理任务兜底扫掉没人再来取的过期行。
CREATE TABLE `DATAPOLY_LOGIN_TOKEN`
(
    `id`           bigint(20) unsigned not null auto_increment comment '主键',
    `access_token` varchar(64)         not null comment '会话令牌（登录响应里的 accessToken）',
    `username`     varchar(255)        not null comment '账号（DATAPOLY_SYSTEM_USER.username）',
    `create_at`    bigint(20)          not null comment '签发时间(epoch 秒)',
    `expire_at`    bigint(20)          not null comment '过期时间(epoch 秒，绝对过期)',
    primary key (`id`),
    unique key `uk_access_token` (`access_token`),
    key `idx_username` (`username`),
    key `idx_expire_at` (`expire_at`)
) engine=InnoDB character set = utf8 comment = '登录会话令牌表';