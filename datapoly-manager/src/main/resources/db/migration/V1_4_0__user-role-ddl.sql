-- 系统用户角色（v1.4.0）：ADMIN=管理员（可增删改查），USER=普通用户（只读/调用）。
-- 先以默认值 USER 建列（新行安全，扩展自动开通的账号即普通用户），再把存量账号（含 admin）
-- 回填为 ADMIN：升级后既有登录者的权限与升级前完全一致，不会把自己锁死。
ALTER TABLE `DATAPOLY_SYSTEM_USER`
    ADD COLUMN `user_role` varchar(32) not null default 'USER' comment '角色:ADMIN/USER';

UPDATE `DATAPOLY_SYSTEM_USER`
SET `user_role` = 'ADMIN';