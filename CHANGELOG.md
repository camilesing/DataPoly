# Changelog

All notable changes to this project are documented in this file.
The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

### Notes

- Known limitations and operational conventions are documented in
  `AGENTS.md`.

## [Unreleased] - 2026-08-27

### Added

- DataTask statement sinks (optional capability, backward compatible): the new
  interface `com.cs.common.datatask.DataTaskStatementSink` lets a delivery
  provider claim a definition and run its rendered statement itself, for
  exports that must complete inside the source engine (MaxCompute
  `UNLOAD ... INTO LOCATION 'oss://...'`) instead of streaming rows through the
  executor. `DataTaskJobEngine` consults the sink once, right after rendering
  and before any JDBC work: on a claim it skips the whole row pipeline (no
  session, no result set, no row limit or reshaping) and records the returned
  `SinkOutcome` like any other artifact. Because such a statement can outlive
  `lease-seconds`, a lazily started daemon refreshes the job lease every
  `lease-seconds/3` while it blocks; the cancel probe can only stop a statement
  before submission, so a late cancel keeps the artifact and is recorded as
  `artifactInfo.cancelRequested`. Existing sinks are untouched — a sink that
  never claims a definition behaves exactly as before. Documented in
  `docs/{zh,en}/data-task.md` §5.
- DataTask sink contract (net-neutral extension): `SinkRequest` now carries
  `columnMetadata` (per-column JDBC type hints, shaped through the same
  projection as the columns) and the terminal `DataTaskEvent` includes the
  `sinkType`, so typed renderers (spreadsheet-style targets) can format columns
  whose sampled values are null and push notifiers can filter per provider.
- Documentation: `docs/{zh,en}/data-task.md` documents the extended sink
  contract (`columnMetadata`, `DataTaskEvent.sinkType`).
- New data source type ODPS (Alibaba Cloud MaxCompute): `ProductTypeEnum`
  registers `ODPS` (driver `com.aliyun.odps.jdbc.OdpsDriver`, URL template
  `jdbc:odps:http(s)://{host}/api?project=...`, the project name serving as the
  schema). Connections carry the MaxCompute 2.0 data type setting
  (`odps.sql.type.system.odps2=true`) via connection properties, and the
  connection pre-test issues a real `SELECT 1 FROM dual` so AccessKey
  authentication is verified eagerly. No SQL-level pagination rewrite is
  applied: MaxCompute supports `LIMIT m OFFSET n` only together with
  `ORDER BY`, so `apiPageNum`/`apiPageSize` fall through to the driver-side
  result cap (10k rows by default). Driver artifacts live under
  `drivers/odps/odps-3/`; the UI gains an ODPS type icon and About entry.

### Fixed

- Data task streaming memory and completeness: the single statement now executes
  in an explicit transaction (auto-commit restored when handing the connection
  back, DML committed first) so PostgreSQL-family drivers — including Hologres —
  honor `fetch-size` and pull rows in bounded batches instead of buffering the
  whole result set in heap, which caused `Java heap space` / repeated
  `GC overhead limit exceeded` failures on large exports. Also fixed a dropped
  trailing partial batch (fewer than 1000 rows) whenever a result set ended
  normally; both are locked by `DataTaskJobEngineStreamQueryTest`.
- Deployment timezone alignment: the compose MySQL now starts with
  `--default-time-zone=+08:00` (`build-docker/install`, `.devcontainer`) and the devcontainer
  PostgreSQL sets `timezone=Asia/Shanghai`, matching the JDBC `serverTimezone=Asia/Shanghai`
  and the container `TZ=Asia/Shanghai`. Previously the MySQL session timezone defaulted to UTC,
  so `TIMESTAMP` columns (e.g. `DATAPOLY_DATA_TASK_DEF.create_time`/`update_time`) read back
  with an 8-hour shift.
- Data task timestamps now serialize as Beijing time: the `DataTaskJobView` fields
  (`/data-task/jobs/search`, `/data-task/job/{id}`) declared their `@JsonFormat`
  pattern without a time zone, so Jackson's UTC default applied and a job started at
  10:48 Beijing was returned as `02:48`; the `/data-task/list` times were raw epoch
  millis. Both now use `yyyy-MM-dd HH:mm:ss` at `GMT+8` like the other management
  DTOs, and `DataTaskTimeZoneJsonTest` locks the rendering.
- Datasource connection test failures now report the driver's reason: `testConnection`/
  `updateTestConnection` let the driver exception escape as a generic internal error, so the
  ODPS "Access Denied / NO privilege" text never reached the UI. Failures are now raised as
  the business error `10` (HTTP 200) with the deepest cause message (whitespace collapsed,
  capped at 500 chars) under the new `datasource.connect.failed` i18n key; functional
  `CommonException`s still pass through untouched, and
  `DataSourceServiceConnectFailureTest` locks the extraction.
- Asynchronous data task framework (DataTask): manager endpoints under
  `/datapoly/manager/api/v1/data-task/**` for task definitions (SQL + input parameter
  declarations + output reshaping: naming strategy / column aliases / column order /
  type formats) and execution records (submit / status polling / cooperative cancel).
  Executors run jobs through a claiming worker over `DATAPOLY_DATA_TASK_JOB`
  (`FOR UPDATE SKIP LOCKED`, lease + reaper), so no new direct connection between
  manager and executor is introduced. Delivery targets are provided exclusively by
  external `com.cs.common.datatask.DataTaskSink` extensions (Spring bean or
  `META-INF/services`); completion is observable via the standard Spring
  `DataTaskEvent` and via polling the job record endpoint. Liquibase migration
  `log-v1.1.0` adds the two meta-store tables (MySQL & PostgreSQL).
- Documentation: new "异步数据任务 / Async Data Tasks" chapter in docs (zh & en)
  covering the REST surface, lifecycle, worker configuration keys
  (`datapoly.data-task.*`) and the sink extension guide.
- Documentation: full standalone DataTask guide `docs/{zh,en}/data-task.md`
  (prerequisites, end-to-end curl walkthrough, sink authoring/registration,
  worker configuration, security constraints and troubleshooting FAQ).
- API assignment post-extension points: the management-plane `debug` and `update`
  operations now fan out to registered `com.cs.core.extension.ApiAssignmentPostProcessor`
  providers (host Spring bean ordered by `@Order`, or `META-INF/services` with bean
  precedence on duplicate registration; per-hook exception isolation so a throwing
  extension never breaks the API flow) and publish the Spring events
  `ApiAssignmentDebugEvent` / `ApiAssignmentUpdateEvent` carrying the full outcome
  (debug answer/logs/types/error/elapsed, update request + saved entity snapshot).
  Documented in the docs (zh & en) usage chapters.

### Fixed

- `DataTaskSinkRegistry` was missing its Spring stereotype, so manager/executor
  failed to boot with an unresolvable bean; it is now a `@Component` with an
  optional sink list, and Spring-bean sinks take precedence over
  ServiceLoader-discovered ones on duplicate `type()` (as documented).
