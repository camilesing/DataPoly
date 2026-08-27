# Changelog

All notable changes to this project are documented in this file.
The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

### Notes

- Known limitations and operational conventions are documented in
  `AGENTS.md`.

## [Unreleased] - 2026-08-27

### Added

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
