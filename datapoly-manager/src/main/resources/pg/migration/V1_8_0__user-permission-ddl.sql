-- per-user permission matrix (v1.8.0): the admin finely configures what a USER-role account may
-- do, per module x action, with rows attached directly to the user (no custom roles). The single
-- judge is the manager's PermissionService:
--   ADMIN passes everything and never reads this table;
--   a USER with no row for a module gets the read-only default (R); a row narrows the module to
--   exactly the actions it lists (R included only when granted).
-- actions is a comma set of com.cs.common.consts.PermissionAction names (R/C/U/D/X); module is a
-- com.cs.common.consts.PermissionModule name, matching the @RequirePermission marks on the manager
-- controllers. User management and secret-plaintext endpoints stay out of the matrix (hardcoded
-- ADMIN). Rows are re-read per request, like the DATAPOLY_SYSTEM_USER account row, so a grant or
-- revocation takes effect on the next request. create_at is epoch seconds, same basis as
-- datapoly_login_token; (username, module) is unique and the save-permissions endpoint replaces
-- a user's rows with a transactional delete+insert.
CREATE TABLE datapoly_user_permission
(
    id        bigserial   not null,
    username  varchar(255) not null,
    module    varchar(64) not null,
    actions   varchar(64) not null,
    create_at bigint      not null,
    primary key (id)
);
CREATE UNIQUE INDEX datapoly_user_permission_username_module_idx ON datapoly_user_permission (username, module);
