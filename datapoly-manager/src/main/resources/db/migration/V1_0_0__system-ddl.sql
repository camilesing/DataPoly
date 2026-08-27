
create table `DATAPOLY_SYSTEM_USER`
(
    `id`          bigint(20)            not null auto_increment   comment '主键id',
    `username`    varchar(255) not null comment '登录名称',
    `password`    varchar(128) not null comment '登录密码',
    `salt`        varchar(128) not null comment '密码盐值',
    `real_name`   varchar(255) not null default '' comment '实际姓名',
    `email`       varchar(255) not null default '' comment '电子邮箱',
    `address`     varchar(255) not null default '' comment '所在地址',
    `locked`      tinyint(1)            not null default 0        comment '是否锁定',
    `create_time` timestamp    not null default current_timestamp comment '创建时间',
    `update_time` timestamp    not null default current_timestamp on update current_timestamp comment '修改时间',
    primary key (`id`),
    unique key (`username`)
) engine=InnoDB character set = utf8 comment = '系统用户表';

create table `DATAPOLY_DATASOURCE`
(
    `id`          bigint(20)   unsigned not null auto_increment            comment '主键',
    `name`        varchar(200) not null default '' comment '连接名称',
    `type`        varchar(200) not null default '' comment '数据库类型',
    `version`     varchar(255) not null default '' comment '驱动版本',
    `driver`      varchar(200) not null default '' comment '驱动类名称',
    `url`         longtext comment 'jdbc-url连接串',
    `username`    varchar(200) not null default '' comment '连接账号',
    `password`    varchar(200) not null default '' comment '账号密码',
    `create_time` timestamp    not null default current_timestamp comment '创建时间',
    `update_time` timestamp    not null default current_timestamp on update current_timestamp comment '修改时间',
    `pool_config` varchar(511)          default null comment '连接池配置JSON',
    primary key (`id`),
    unique key (`name`)
) engine=InnoDB default charset=utf8 comment='数据库连接';

CREATE TABLE `DATAPOLY_API_GROUP`
(
    `id`          bigint(20)   unsigned auto_increment                     comment '主键',
    `name`        varchar(255) not null default '' comment '分组名称',
    `create_time` timestamp    not null default current_timestamp comment '创建时间',
    `update_time` timestamp    not null default current_timestamp on update current_timestamp comment '修改时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_name`(`name`)
) engine=InnoDB default charset=utf8 comment='接口分组表';

CREATE TABLE `DATAPOLY_API_MODULE`
(
    `id`          bigint(20)   unsigned auto_increment                     comment '主键',
    `name`        varchar(255) not null default '' comment '模块名称',
    `create_time` timestamp    not null default current_timestamp comment '创建时间',
    `update_time` timestamp    not null default current_timestamp on update current_timestamp comment '修改时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_name`(`name`)
) engine=InnoDB default charset=utf8 comment='接口模块表';

CREATE TABLE `DATAPOLY_FIREWALL_RULES`
(
    `id`          bigint(20)   unsigned auto_increment                     comment '主键',
    `status`      varchar(4)  not null default 'OFF' comment '状态：OFF-关闭;ON-启用',
    `mode`        varchar(16) not null default 'BLACK' comment '状态：WHITE-白名单;BLACK-黑名单',
    `addresses`   text                 default null comment '客户端地址(IP)列表',
    `create_time` timestamp   not null default current_timestamp comment '创建时间',
    `update_time` timestamp   not null default current_timestamp on update current_timestamp comment '修改时间',
    PRIMARY KEY (`id`)
) engine=InnoDB default charset=utf8 comment='防火墙规则表';

CREATE TABLE `DATAPOLY_UNIFY_ALARM`
(
    `id`             bigint(20)            not null auto_increment   comment '主键id',
    `status`         varchar(4)    not null default 'OFF' comment '状态：OFF-关闭;ON-启用',
    `endpoint`       varchar(256)  not null comment '告警系统地址',
    `content_type`   varchar(128)  not null comment '接口入参ContentType',
    `input_template` varchar(4096) not null comment '接口入参模板',
    `create_time`    timestamp     not null default current_timestamp comment '创建时间',
    `update_time`    timestamp     not null default current_timestamp on update current_timestamp comment '修改时间',
    primary key (`id`)
) engine=InnoDB character set = utf8 comment = '统一告警参数配置';

CREATE TABLE `DATAPOLY_APP_CLIENT`
(
    `id`              bigint(20)   unsigned auto_increment                     comment '主键',
    `name`            varchar(255) not null comment '应用客户端名称',
    `description`     varchar(1024)         default null comment '应用客户端描述',
    `app_key`         varchar(64)  not null comment '应用的账号',
    `app_secret`      varchar(64)  not null comment '应用的密钥',
    `expire_duration` varchar(16)  not null default 'FOR_EVER' comment '过期类型',
    `expire_at`       bigint(20)            not null default '0'               comment '过期时间',
    `access_token`    varchar(64)           default null comment '最近TOKEN',
    `token_alive`     varchar(16)           not null default 'PERIOD'          comment 'TOKEN的生命期',
    `create_time`     timestamp    not null default current_timestamp comment '创建时间',
    `update_time`     timestamp    not null default current_timestamp on update current_timestamp comment '修改时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_app_key`(`app_key`),
    INDEX             `idx_access_token` (`access_token`) USING BTREE
) engine=InnoDB default charset=utf8 comment='客户端应用表';

CREATE TABLE `DATAPOLY_CLIENT_GROUP`
(
    `id`        bigint(20)  unsigned auto_increment  comment '主键',
    `client_id` bigint(20)  not null                 comment '客户端应用ID',
    `group_id`  bigint(20)  not null                 comment 'API分组ID',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_client_group`(`client_id`,`group_id`)
) engine=InnoDB default charset=utf8 comment='客户端应用授权分组配置表';

CREATE TABLE `DATAPOLY_ACCESS_RECORD`
(
    `id`            bigint(20)   unsigned auto_increment       comment '主键',
    `path`          varchar(255)       default null comment '路径',
    `status`        bigint(11)         default null            comment 'HTTP状态码',
    `duration`      bigint(20)         default null            comment '处理时间',
    `ip_addr`       varchar(64)        default null comment '客户端IP',
    `user_agent`    varchar(255)       default null comment '客户端UA',
    `client_key`    varchar(64)        default null comment '客户端Key',
    `api_id`        varchar(50)        default null comment 'API接口ID',
    `parameters`    longtext           default null comment '请求入参',
    `exception`     longtext           default null comment '错误日志',
    `create_time`   timestamp not null default current_timestamp comment '创建时间',
    `executor_addr` varchar(128)       null comment '执行器的IP地址',
    `gateway_addr`  varchar(128)       null comment '网关的IP地址',
    PRIMARY KEY (`id`)
) engine=InnoDB default charset=utf8 comment='客户端应用接口访问日志表';

CREATE TABLE `DATAPOLY_SYSTEM_PARAM`
(
    `id`          bigint(20)        NOT NULL AUTO_INCREMENT  COMMENT '主键id',
    `param_key`   varchar(128) NOT NULL COMMENT '参数KEY',
    `param_type`  varchar(64)  NOT NULL COMMENT '值类型',
    `param_value` varchar(255) NOT NULL COMMENT '参数值',
    PRIMARY KEY (`id`),
    UNIQUE KEY `param_key` (`param_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='系统参数表';

CREATE TABLE `DATAPOLY_API_ASSIGNMENT`
(
    `id`                  bigint(20)   unsigned not null auto_increment            comment '主键',
    `group_id`            bigint(20)   unsigned not null                           comment '分组ID',
    `module_id`           bigint(20)   unsigned not null                           comment '模块ID',
    `datasource_id`       bigint(20)   unsigned not null                           comment '数据源ID',
    `name`                varchar(255) not null default '' comment '接口名称',
    `description`         varchar(1024)         default null comment '接口描述',
    `method`              varchar(16)  not null default 'GET' comment '请求方法',
    `path`                varchar(255) not null default '' comment '请求路径',
    `params`              text null                           comment '入参JSON列表',
    `outputs`             text null                           comment '出参JSON列表',
    `status`              tinyint(1)            not null default 0                 comment '是否发布',
    `open`                tinyint(1)            not null default 0                 comment '是否公开',
    `alarm`               tinyint(1)            not null default 0                 comment '是否启用告警',
    `engine`              varchar(16)  not null default 'SQL' comment '执行引擎',
    `response_format`     tinytext              default null comment '响应格式配置',
    `naming_strategy`     varchar(16)  not null default 'NONE' comment '响应命名策略',
    `flow_status`         tinyint(1)            not null default 0                 comment '是否开启流量控制',
    `flow_grade`          bigint(20)   unsigned          default null              comment '流控类型',
    `flow_count`          bigint(20)   unsigned          default null              comment '流控阈值',
    `content_type`        varchar(50)  not null default '' comment 'ContentType',
    `cache_key_type`      varchar(16)           not null default 'NONE'            comment '缓存键类型',
    `cache_key_expr`      varchar(255)          null                               comment '缓存key的SpEL表达式',
    `cache_expire_seconds` bigint(20)  unsigned          not null default 0        comment '缓存过期时长(秒)',
    `create_time`         timestamp    not null default current_timestamp comment '创建时间',
    `update_time`         timestamp    not null default current_timestamp on update current_timestamp comment '修改时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_method_path`(`method`,`path`),
    CONSTRAINT            `fk_assignment_group`      foreign key (`group_id`)      references `DATAPOLY_API_GROUP` (`id`) on delete cascade on update cascade,
    CONSTRAINT            `fk_assignment_module`     foreign key (`module_id`)     references `DATAPOLY_API_MODULE` (`id`) on delete cascade on update cascade,
    CONSTRAINT            `fk_assignment_datasource` foreign key (`datasource_id`) references `DATAPOLY_DATASOURCE` (`id`) on delete cascade on update cascade
) engine=InnoDB auto_increment=1 default charset=utf8 comment='接口配置表';

CREATE TABLE `DATAPOLY_API_CONTEXT`
(
    `id`       bigint(20)   unsigned auto_increment             comment '主键',
    `api_id`   bigint(20)   unsigned not null                   comment 'API接口ID',
    `sql_text` text not null comment 'SQL内容',
    primary key (`id`),
    CONSTRAINT `fk_context_assignment` foreign key (`api_id`) references `DATAPOLY_API_ASSIGNMENT` (`id`) on delete cascade on update cascade
) engine=InnoDB default charset=utf8 comment='接口sql表';

CREATE TABLE `DATAPOLY_MCP_CLIENT`
(
    `id`          bigint(20)            not null auto_increment   comment '主键id',
    `name`        varchar(256) not null comment '客户端名称',
    `token`       varchar(64)  not null comment '连接TOKEN',
    `create_time` timestamp    not null default current_timestamp comment '创建时间',
    `update_time` timestamp    not null default current_timestamp on update current_timestamp comment '修改时间',
    primary key (`id`),
    unique key `name` (`name`),
    KEY           `idx_token` (`token`) USING BTREE
) engine=InnoDB character set = utf8 comment = 'MCP连接客户端表';

CREATE TABLE `DATAPOLY_MCP_TOOL`
(
    `id`          bigint(20)            not null auto_increment   comment '主键id',
    `name`        varchar(256)  not null comment '工具名',
    `description` varchar(1024) not null comment '工具描述',
    `api_id`      bigint(20) unsigned   not null                  comment 'API接口ID',
    `create_time` timestamp     not null default current_timestamp comment '创建时间',
    `update_time` timestamp     not null default current_timestamp on update current_timestamp comment '修改时间',
    primary key (`id`),
    unique key `name` (`name`),
    CONSTRAINT    `fk_mcp_tool_assignment` FOREIGN KEY (`api_id`) REFERENCES `DATAPOLY_API_ASSIGNMENT` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
) engine=InnoDB character set = utf8 comment = 'MCP工具配置';

CREATE TABLE `DATAPOLY_VERSION_COMMIT`
(
    `id`          bigint(20) unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
    `biz_id`      bigint(20) unsigned NOT NULL COMMENT '对象ID',
    `version`     bigint(20) unsigned NOT NULL COMMENT '版本号',
    `description` varchar(256) NULL COMMENT '描述',
    `content`     longtext  NOT NULL COMMENT '内容',
    `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `api_id_version` (`biz_id`,`version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='版本管理表';

CREATE TABLE `DATAPOLY_API_ONLINE`
(
    `id`            bigint(20) unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
    `name`          varchar(255) NOT NULL DEFAULT '' COMMENT '接口名称',
    `method`        varchar(16)  NOT NULL DEFAULT 'GET' COMMENT '请求方法',
    `path`          varchar(255) NOT NULL DEFAULT '' COMMENT '请求路径',
    `api_id`        bigint(20) unsigned NOT NULL COMMENT '分组ID',
    `group_id`      bigint(20) unsigned NOT NULL COMMENT '分组ID',
    `module_id`     bigint(20) unsigned NOT NULL COMMENT '模块ID',
    `datasource_id` bigint(20) unsigned NOT NULL COMMENT '数据源ID',
    `open`          tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否公开',
    `alarm`         tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否启用告警',
    `flow_status`   tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否开启流量控制',
    `commit_id`     bigint(20) unsigned NOT NULL COMMENT '版本CommitId',
    `version`       bigint(20) unsigned NOT NULL COMMENT '版本号',
    `content`       longtext     NOT NULL COMMENT '详细内容JSON',
    `create_time`   timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`   timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_method_path` (`method`,`path`),
    KEY             `api_id` (`api_id`),
    KEY             `group_id` (`group_id`),
    KEY             `module_id` (`module_id`),
    KEY             `datasource_id` (`datasource_id`),
    CONSTRAINT      `fk_online_assignment` FOREIGN KEY (`api_id`) REFERENCES `DATAPOLY_API_ASSIGNMENT` (`id`) ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT      `fk_online_group` FOREIGN KEY (`group_id`) REFERENCES `DATAPOLY_API_GROUP` (`id`) ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT      `fk_online_module` FOREIGN KEY (`module_id`) REFERENCES `DATAPOLY_API_MODULE` (`id`) ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT      `fk_online_datasource` FOREIGN KEY (`datasource_id`) REFERENCES `DATAPOLY_DATASOURCE` (`id`) ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT      `fk_online_commit` FOREIGN KEY (`commit_id`) REFERENCES `DATAPOLY_VERSION_COMMIT` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='接口在线表';
