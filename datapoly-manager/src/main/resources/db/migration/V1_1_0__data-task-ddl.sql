-- asynchronous data task framework (definitions + job records)
CREATE TABLE `DATAPOLY_DATA_TASK_DEF`
(
    `id`                     bigint(20) unsigned auto_increment comment '主键',
    `name`                   varchar(255) not null               comment '任务名称',
    `description`            varchar(1024)          default null comment '任务描述',
    `datasource_id`          bigint(20) unsigned not null        comment '数据源ID',
    `sql_text`               longtext                            comment 'SQL语句(支持MyBatis动态标签与#{}占位)',
    `params`                 longtext                            comment '入参声明JSON',
    `naming_strategy`        varchar(32) default 'CAMEL_CASE'    comment '结果列命名策略',
    `response_format`        text                                comment '出参类型格式化配置JSON',
    `column_alias`           text                                comment '列改名映射JSON(key为命名转换后列名)',
    `column_order`           text                                comment '输出列顺序/子集JSON数组',
    `apply_format_to_string` tinyint(1)  not null default 0      comment '是否按格式化配置转字符串单元格',
    `dollar_allowed`         tinyint(1)  not null default 0      comment '是否允许${}原生替换(默认禁止)',
    `max_rows`               bigint(20)     default 0            comment '单次投递行数上限,0=引擎默认上限',
    `sink_type`              varchar(64) not null                comment '投递实现标识(由扩展提供)',
    `sink_config`            text                                comment '投递实现私有配置JSON(原样透传,勿存明文口令)',
    `enabled`                tinyint(1)  not null default 1      comment '是否启用',
    `create_time`            timestamp   not null default current_timestamp comment '创建时间',
    `update_time`            timestamp   not null default current_timestamp on update current_timestamp comment '修改时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_name` (`name`)
) engine=InnoDB default charset=utf8 comment='异步数据任务定义表';

CREATE TABLE `DATAPOLY_DATA_TASK_JOB`
(
    `id`               bigint(20) unsigned auto_increment comment '主键',
    `def_id`           bigint(20) unsigned not null       comment '任务定义ID',
    `def_name`         varchar(255) not null              comment '任务名称快照',
    `status`           varchar(16)  not null default 'PENDING' comment '状态:PENDING/RUNNING/SUCCESS/FAILED/CANCELED',
    `snapshot`         longtext                           comment '定义内容快照JSON(提交时定格)',
    `params_json`      longtext                           comment '绑定的入参值JSON',
    `cancel_requested` tinyint(1)   not null default 0    comment '运行中取消标记(协作式)',
    `total_rows`       bigint(20)   not null default 0    comment '已处理行数',
    `artifact_uri`     varchar(1024) default null         comment '产物引用地址(由投递实现返回)',
    `artifact_info`    text                               comment '产物附加信息JSON',
    `error_message`    longtext                           comment '失败原因',
    `worker_addr`      varchar(128) default null          comment '执行的worker地址',
    `submitted_by`     varchar(255) default null          comment '提交人',
    `start_time`       datetime      default null         comment '开始时间',
    `finish_time`      datetime      default null         comment '结束时间',
    `lease_expire_at`  datetime      default null         comment '运行租约到期时间(worker心跳刷新)',
    `create_time`      timestamp not null default current_timestamp comment '创建时间',
    `update_time`      timestamp not null default current_timestamp on update current_timestamp comment '修改时间',
    PRIMARY KEY (`id`),
    INDEX              `idx_status_id` (`status`, `id`),
    INDEX              `idx_def_id` (`def_id`)
) engine=InnoDB default charset=utf8 comment='异步数据任务执行记录表';
