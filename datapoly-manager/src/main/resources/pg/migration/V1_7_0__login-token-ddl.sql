-- login session tokens (v1.7.0): the manager login state used to live only in the process-local
-- CacheUtils (2h sliding window, gone on restart); it is persisted here instead, so a restart no
-- longer logs everyone out. Lifetime = issued_at + datapoly.security.login-token-ttl-seconds
-- (604800 = 7 days by default) and is absolute: activity never extends it.
-- create_at / expire_at are epoch seconds, the same unit as datapoly_app_client.expire_at, so both
-- database flavors share one comparison path without timezone or timestamp-type differences.
-- Deleting the row is what invalidates a session: logout, password changes (all sessions of the
-- account) and the lazy removal of a row found expired; a startup and a nightly cleanup in the
-- manager sweep rows nobody comes back for.
CREATE TABLE datapoly_login_token
(
    id           bigserial    not null,
    access_token varchar(64)  not null,
    username     varchar(255) not null,
    create_at    bigint       not null,
    expire_at    bigint       not null,
    primary key (id)
);
CREATE UNIQUE INDEX datapoly_login_token_token_idx ON datapoly_login_token (access_token);
CREATE INDEX datapoly_login_token_username_idx ON datapoly_login_token (username);
CREATE INDEX datapoly_login_token_expire_idx ON datapoly_login_token (expire_at);