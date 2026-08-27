# Usage

Language: [简体中文](../zh/usage.md) [English](usage.md)

The built-in management UI guides you through the main workflows (datasource → API assignment → publish). A dedicated
user guide is being prepared.

> Illustrated guide materials are staged under `docs/images/en_US/`, to be organized into the official guide.
---

## Async Data Tasks (DataTask)

Run a configured SQL query plus output reshaping as an **asynchronous job**: after
submission an executor-side background worker claims it, streams the result through the
declared column aliases / order / naming strategy / type formats, and hands batches to a
**delivery provider implemented as an external extension** — e.g. converting to xlsx and
uploading to object storage, or writing into a message queue. The framework owns
definition, scheduling, progress and state machines only; **no delivery provider ships
with the repository**, every concrete destination plugs in through the SPI below.
Frontends poll the job record endpoint for terminal status and the artifact reference.

> Push-style completion notification (WebSocket/SSE/Webhook) can be added by listening to
> the Spring event `com.cs.core.datatask.DataTaskEvent` in the host application; polling
> is the default interaction.

### Concepts

| Concept | Description |
| --- | --- |
| Definition | one SQL statement (MyBatis dynamic tags + `#{}` placeholders), input parameter declarations (`ItemParam`, same model as API assignments), output rules (naming strategy / column alias / column order / type formats / stringify cells), delivery target (`sinkType` + opaque `sinkConfig`) |
| Job | one row per submission; the definition content is **snapshotted** at submit time so later edits never mutate queued work; carries status, row counter, cancel flag and the artifact URI |
| Worker | switchable role inside executor: claims PENDING rows inside a transaction using `FOR UPDATE SKIP LOCKED`, refreshes rows/lease while scanning; RUNNING jobs of lost workers are reaped via `lease_expire_at` |
| Sink | external extension implementing `com.cs.common.datatask.DataTaskSink`, registered either as Spring bean or via `META-INF/services`; definitions select one by `sinkType` |

State machine: `PENDING → RUNNING → SUCCESS | FAILED | CANCELED`. Queued jobs cancel
directly; running ones are cancelled cooperatively (the worker honours `cancel_requested`
at its progress ticks).

### REST surface

Prefix `/datapoly/manager/api/v1/data-task` behind the gateway with the standard
manager login token:

| Method | Path | Purpose |
| --- | --- | --- |
| POST | `/parse?sql=...` | list SQL parameters (reuses the API-assignment capability) |
| POST | `/create` `/update` · GET `/detail/{id}` · DELETE `/delete/{id}` | definition CRUD |
| POST | `/list` | paged definition search (`page/size/searchText`) |
| POST | `/preview` | **dry-run**: executes synchronously and returns shaped headers plus up to N rows (default 50, max 200); no job row is created and sinks are untouched |
| POST | `/submit` | `{defId, params}` → `jobId`; required checks & converters match API assignments |
| GET | `/job/{id}` | job detail — frontends poll this for status and `artifactUri` |
| POST | `/jobs/search` | paged records `{defId?, status?}` |
| POST | `/job/{id}/cancel` | cancellation |

### Worker configuration (executor side, `datapoly.data-task.*`)

| Key | Default | Meaning |
| --- | --- | --- |
| `enabled` | `true`(executor) | when false the node serves only the API data plane |
| `workers` | `2` | per-node concurrency cap on a dedicated bounded pool |
| `poll-interval-ms` / `reap-interval-ms` | 5000 / 30000 | claim cadence / lost-worker reap cadence |
| `lease-seconds` | 600 | run lease length, renewed via heartbeats |
| `flush-interval-ms` | 5000 | minimum interval between progress/cancel/heartbeat ticks |
| `fetch-size` | 1000 | JDBC fetchSize for non-MySQL dialects (MySQL switches to streaming `Integer.MIN_VALUE`) |
| `query-timeout-seconds` | 1800 | statement-level timeout backstop |
| `max-rows-default` | 1000000 | global cap when a definition does not set `maxRows` |

### Authoring a delivery provider (e.g. "xlsx to OSS")

Implement two interfaces in a jar dropped onto the **executor classpath**:

```java
public class ExcelOssSink implements com.cs.common.datatask.DataTaskSink {
    public String type() { return "excel-oss"; }   // matches sinkType in definitions

    public SinkSession openSession(SinkRequest req) {
        // req.sinkConfig(): opaque JSON from the definition (passed through verbatim)
        // req.columns()/outputFormats()/submittedBy(): shaped headers, patterns, user
        return new ExcelOssSession(req);
    }
}
```

`SinkSession` semantics: consume positional cell values batch-by-batch in
`writeRows(Iterable<List<Object>>)` (returning false stops reading early); `complete()`
finishes and returns `SinkOutcome{artifactUri, info}` written back onto the job record;
on any failure path the engine calls `abort(cause)` for resource cleanup. Register
either as a host Spring bean or via
`META-INF/services/com.cs.common.datatask.DataTaskSink` (recommended — zero Spring
coupling). For per-cell tweaks (masking, unit conversion) implement
`com.cs.common.datatask.CellDecorator` as a bean; it runs right before delivery.

Security notes:
- `sink_config` is stored **in plain text** in the meta store and passed through
  verbatim — extensions should reference server-hosted credentials/environment settings
  instead of embedding access keys in definitions.
- Anyone able to author definitions effectively gains arbitrary query power over the
  target datasource; scope platform accounts accordingly.
- `${}` raw substitution stays disabled unless a definition explicitly opts in via
  `dollarAllowed=true`; prefer `#{}` parameterization.
- Keep the row cap and statement timeout backstops in place; filter/archival SQL is the
  better lever for very large result sets.
