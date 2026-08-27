-- asynchronous data task framework (definitions + job records)
CREATE TABLE datapoly_data_task_def
(
    id                     bigserial    not null,
    name                   varchar(255) not null,
    description            varchar(1024),
    datasource_id          bigint       not null,
    sql_text               text,
    params                 text,
    naming_strategy        varchar(32) default 'CAMEL_CASE',
    response_format        text,
    column_alias           text,
    column_order           text,
    apply_format_to_string boolean     not null default false,
    dollar_allowed         boolean     not null default false,
    max_rows               bigint      default 0,
    sink_type              varchar(64) not null,
    sink_config            text,
    enabled                boolean     not null default true,
    create_time            timestamp(6) not null default (CURRENT_TIMESTAMP(0))::timestamp(0) without time zone,
    update_time            timestamp(6) not null default (CURRENT_TIMESTAMP(0))::timestamp(0) without time zone,
    primary key (id)
);
CREATE UNIQUE INDEX datapoly_data_task_def_name_idx ON datapoly_data_task_def (name);

CREATE TABLE datapoly_data_task_job
(
    id               bigserial   not null,
    def_id           bigint      not null,
    def_name         varchar(255) not null,
    status           varchar(16) not null default 'PENDING',
    snapshot         text,
    params_json      text,
    cancel_requested boolean     not null default false,
    total_rows       bigint      not null default 0,
    artifact_uri     varchar(1024),
    artifact_info    text,
    error_message    text,
    worker_addr      varchar(128),
    submitted_by     varchar(255),
    start_time       timestamp(6),
    finish_time      timestamp(6),
    lease_expire_at  timestamp(6),
    create_time      timestamp(6) not null default (CURRENT_TIMESTAMP(0))::timestamp(0) without time zone,
    update_time      timestamp(6) not null default (CURRENT_TIMESTAMP(0))::timestamp(0) without time zone,
    primary key (id)
);
CREATE INDEX datapoly_data_task_job_status_id_idx ON datapoly_data_task_job (status, id);
CREATE INDEX datapoly_data_task_job_def_id_idx ON datapoly_data_task_job (def_id);
