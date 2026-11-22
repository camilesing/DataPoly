-- 用户权限矩阵（v1.8.0）：管理员按「模块×操作」精细配置 USER 角色能做什么，权限行直接挂用户
-- （不引入自定义角色）。判定语义只有一份（manager 的 PermissionService）：
--   ADMIN 全通过，不读本表；
--   USER 在某模块无配置行 → 默认只读（R）；有配置行 → 按行内 actions 精确生效（可收回 R）。
-- actions 为逗号集，取值见 com.cs.common.consts.PermissionAction（R/C/U/D/X）。
-- module 取值见 com.cs.common.consts.PermissionModule，与 manager 各 controller 的
-- @RequirePermission 标注一一对应；用户管理与敏感明文端点不进矩阵，硬编码 ADMIN。
-- 行按请求回查（与 DATAPOLY_SYSTEM_USER 账号行同策略）：管理员改完，下一个请求即生效。
-- create_at 为 epoch 秒，与 DATAPOLY_LOGIN_TOKEN 同口径；(username, module) 唯一，整表替换
-- 由管理端「保存权限」事务内 delete+insert 完成。
CREATE TABLE `DATAPOLY_USER_PERMISSION`
(
    `id`        bigint(20) unsigned not null auto_increment comment '主键',
    `username`  varchar(255)        not null comment '账号（DATAPOLY_SYSTEM_USER.username）',
    `module`    varchar(64)         not null comment '模块名（PermissionModule 枚举名）',
    `actions`   varchar(64)         not null comment '操作集合，逗号分隔（PermissionAction 枚举名）',
    `create_at` bigint(20)          not null comment '创建时间(epoch 秒)',
    primary key (`id`),
    unique key `uk_username_module` (`username`, `module`)
) engine=InnoDB character set = utf8 comment = '用户权限矩阵表';
