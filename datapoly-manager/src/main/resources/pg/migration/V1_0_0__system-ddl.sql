CREATE TABLE datapoly_system_user
(
    id          bigserial    not null,
    username    varchar(255) not null,
    password    varchar(128) not null,
    salt        varchar(128) not null,
    real_name   varchar(255) not null default '',
    email       varchar(255) not null default '',
    address     varchar(255) not null default '',
    locked      boolean      not null default false,
    create_time timestamp(6) not null default (CURRENT_TIMESTAMP(0))::timestamp(0) without time zone,
    update_time timestamp(6) not null default (CURRENT_TIMESTAMP(0))::timestamp(0) without time zone,
    PRIMARY KEY (id)
);
CREATE UNIQUE INDEX datapoly_system_user_username_idx ON datapoly_system_user (username);

CREATE TABLE datapoly_datasource
(
    id          bigserial    not null,
    name        varchar(200) not null,
    type        varchar(200) not null,
    version     varchar(255) not null,
    driver      varchar(200) not null,
    url         text,
    username    varchar(200) not null default '',
    password    varchar(200) not null default '',
    create_time timestamp(6) not null default (CURRENT_TIMESTAMP(0))::timestamp(0) without time zone,
    update_time timestamp(6) not null default (CURRENT_TIMESTAMP(0))::timestamp(0) without time zone,
    pool_config varchar(511) default null,
    primary key (id)
);
CREATE UNIQUE INDEX datapoly_datasource_name_idx ON datapoly_datasource (name);

CREATE TABLE datapoly_api_group
(
    id          bigserial    not null,
    name        varchar(255) not null,
    create_time timestamp(6) not null default (CURRENT_TIMESTAMP(0))::timestamp(0) without time zone,
    update_time timestamp(6) not null default (CURRENT_TIMESTAMP(0))::timestamp(0) without time zone,
    PRIMARY KEY (id)
);
CREATE UNIQUE INDEX datapoly_api_group_name_idx ON datapoly_api_group (name);

CREATE TABLE datapoly_api_module
(
    id          bigserial    not null,
    name        varchar(255) not null,
    create_time timestamp(6) not null default (CURRENT_TIMESTAMP(0))::timestamp(0) without time zone,
    update_time timestamp(6) not null default (CURRENT_TIMESTAMP(0))::timestamp(0) without time zone,
    PRIMARY KEY (id)
);
CREATE UNIQUE INDEX datapoly_api_module_name_idx ON datapoly_api_module (name);

CREATE TABLE datapoly_firewall_rules
(
    id          bigserial    not null,
    status      varchar(4)   not null default 'OFF',
    mode        varchar(16)  not null default 'BLACK',
    addresses   text                  default null,
    create_time timestamp(6) not null default (CURRENT_TIMESTAMP(0))::timestamp(0) without time zone,
    update_time timestamp(6) not null default (CURRENT_TIMESTAMP(0))::timestamp(0) without time zone,
    PRIMARY KEY (id)
);

CREATE TABLE datapoly_unify_alarm
(
    id             bigserial     not null,
    status         varchar(4)    not null default 'OFF',
    endpoint       varchar(256)  not null,
    content_type   varchar(128)  not null,
    input_template varchar(4096) not null,
    create_time    timestamp(6)  not null default (CURRENT_TIMESTAMP(0))::timestamp(0) without time zone,
    update_time    timestamp(6)  not null default (CURRENT_TIMESTAMP(0))::timestamp(0) without time zone,
    primary key (id)
);

CREATE TABLE datapoly_app_client
(
    id              bigserial    not null,
    name            varchar(255) not null,
    description     varchar(1024)         default null,
    app_key         varchar(64)  not null,
    app_secret      varchar(64)  not null,
    expire_duration varchar(16)  not null default 'FOR_EVER',
    expire_at       int8         not null default '0',
    access_token    varchar(64)           default null,
    token_alive     varchar(16)  not null default 'PERIOD',
    create_time     timestamp(6) not null default (CURRENT_TIMESTAMP(0))::timestamp(0) without time zone,
    update_time     timestamp(6) not null default (CURRENT_TIMESTAMP(0))::timestamp(0) without time zone,
    PRIMARY KEY (id)
);
CREATE UNIQUE INDEX datapoly_app_client_app_key_idx ON datapoly_app_client (app_key);
CREATE INDEX        datapoly_app_client_access_token_idx ON datapoly_app_client (access_token);

CREATE TABLE datapoly_client_group
(
    id        bigserial not null,
    client_id int8      not null,
    group_id  int8      not null,
    PRIMARY KEY (id)
);
CREATE UNIQUE INDEX datapoly_client_group_client_id_group_id_idx ON datapoly_client_group (client_id, group_id);

CREATE TABLE datapoly_access_record
(
    id            bigserial    not null,
    path          varchar(255)          default null,
    status        int8                  default null,
    duration      int8                  default null,
    ip_addr       varchar(64)           default null,
    user_agent    varchar(255)          default null,
    client_key    varchar(64)           default null,
    api_id        int8         not null,
    parameters    text                  default null,
    exception     varchar(1024)         default null,
    create_time   timestamp(6) not null default (CURRENT_TIMESTAMP(0))::timestamp(0) without time zone,
    executor_addr varchar(128)          default null,
    gateway_addr  varchar(128)          default null,
    PRIMARY KEY (id)
);

CREATE TABLE datapoly_system_param
(
    id          bigserial    NOT NULL,
    param_key   varchar(128) NOT NULL,
    param_type  varchar(64)  NOT NULL,
    param_value varchar(255) NOT NULL,
    PRIMARY KEY (id)
);
CREATE UNIQUE INDEX datapoly_system_param_param_key_idx ON datapoly_system_param (param_key);

CREATE TABLE datapoly_api_assignment
(
    id                  bigserial    not null,
    group_id            int8         not null,
    module_id           int8         not null,
    datasource_id       int8         not null,
    name                varchar(255) not null,
    description         varchar(1024)         default null,
    method              varchar(16)  not null default 'GET',
    path                varchar(255) not null default '',
    params              text null,
    outputs             text null,
    status              boolean      not null default false,
    open                boolean      not null default false,
    alarm               boolean      not null default false,
    engine              varchar(16)  not null default 'SQL',
    response_format     text                  default null,
    naming_strategy     varchar(16)  not null default 'NONE',
    flow_status         boolean      not null default false,
    flow_grade          int8         not null,
    flow_count          int8         not null,
    content_type        varchar(50)  not null default '',
    cache_key_type      varchar(16)  not null default 'NONE',
    cache_key_expr      varchar(255) null,
    cache_expire_seconds int8        not null default 0,
    create_time         timestamp(6) not null default (CURRENT_TIMESTAMP(0))::timestamp(0) without time zone,
    update_time         timestamp(6) not null default (CURRENT_TIMESTAMP(0))::timestamp(0) without time zone,
    PRIMARY KEY (id),
    CONSTRAINT fk_assignment_group      foreign key (group_id)      references datapoly_api_group (id) on delete cascade on update cascade,
    CONSTRAINT fk_assignment_module     foreign key (module_id)     references datapoly_api_module (id) on delete cascade on update cascade,
    CONSTRAINT fk_assignment_datasource foreign key (datasource_id) references datapoly_datasource (id) on delete cascade on update cascade
);
CREATE UNIQUE INDEX datapoly_api_assignment_method_path_idx ON datapoly_api_assignment (method, path);

CREATE TABLE datapoly_api_context
(
    id       bigserial not null,
    api_id   int8      not null,
    sql_text text      not null,
    primary key (id),
    CONSTRAINT fk_context_assignment foreign key (api_id) references datapoly_api_assignment (id) on delete cascade on update cascade
);

CREATE TABLE datapoly_mcp_client
(
    id          bigserial    not null,
    name        varchar(256) not null,
    token       varchar(64)  not null,
    create_time timestamp(6) not null default (CURRENT_TIMESTAMP(0))::timestamp(0) without time zone,
    update_time timestamp(6) not null default (CURRENT_TIMESTAMP(0))::timestamp(0) without time zone,
    primary key (id)
);
CREATE UNIQUE INDEX datapoly_mcp_client_name_idx ON datapoly_mcp_client (name);
CREATE UNIQUE INDEX datapoly_mcp_client_token_idx ON datapoly_mcp_client (token);

CREATE TABLE datapoly_mcp_tool
(
    id          bigserial     not null,
    name        varchar(256)  not null,
    description varchar(1024) not null,
    api_id      int8          not null,
    create_time timestamp(6)  not null default (CURRENT_TIMESTAMP(0))::timestamp(0) without time zone,
    update_time timestamp(6)  not null default (CURRENT_TIMESTAMP(0))::timestamp(0) without time zone,
    primary key (id),
    CONSTRAINT fk_mcp_tool_assignment FOREIGN KEY (api_id) REFERENCES datapoly_api_assignment (id) ON DELETE CASCADE ON UPDATE CASCADE
);
CREATE UNIQUE INDEX datapoly_mcp_tool_name_idx ON datapoly_mcp_tool (name);

CREATE TABLE datapoly_version_commit
(
    id          bigserial    not null,
    biz_id      int8         not null,
    version     int8         not null,
    description varchar(256) default null,
    content     text         not null,
    create_time timestamp(6) not null default (CURRENT_TIMESTAMP(0))::timestamp(0) without time zone,
    PRIMARY KEY (id)
);
CREATE UNIQUE INDEX datapoly_version_commit_biz_id_version_idx ON datapoly_version_commit (biz_id, version);

CREATE TABLE datapoly_api_online
(
    id            bigserial    not null,
    name          varchar(255) not null,
    method        varchar(16)  not null,
    path          varchar(255) not null,
    api_id        int8         not null,
    group_id      int8         not null,
    module_id     int8         not null,
    datasource_id int8         not null,
    open          boolean      not null default false,
    alarm         boolean      not null default false,
    flow_status   boolean      not null default false,
    commit_id     int8         not null,
    version       int8         not null,
    content       text         not null,
    create_time   timestamp(6) not null default (CURRENT_TIMESTAMP(0))::timestamp(0) without time zone,
    update_time   timestamp(6) not null default (CURRENT_TIMESTAMP(0))::timestamp(0) without time zone,
    PRIMARY KEY (id),
    CONSTRAINT fk_online_assignment FOREIGN KEY (api_id) REFERENCES datapoly_api_assignment (id) ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT fk_online_group      FOREIGN KEY (group_id) REFERENCES datapoly_api_group (id) ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT fk_online_module     FOREIGN KEY (module_id) REFERENCES datapoly_api_module (id) ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT fk_online_datasource FOREIGN KEY (datasource_id) REFERENCES datapoly_datasource (id) ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT fk_online_commit     FOREIGN KEY (commit_id) REFERENCES datapoly_version_commit (id) ON DELETE CASCADE ON UPDATE CASCADE
);
CREATE UNIQUE INDEX datapoly_api_online_method_path_idx ON datapoly_api_online (method, path);
CREATE INDEX        datapoly_api_online_api_id_idx ON datapoly_api_online (api_id);
CREATE INDEX        datapoly_api_online_group_id_idx ON datapoly_api_online (group_id);
CREATE INDEX        datapoly_api_online_module_id_idx ON datapoly_api_online (module_id);
CREATE INDEX        datapoly_api_online_datasource_id_idx ON datapoly_api_online (datasource_id);
