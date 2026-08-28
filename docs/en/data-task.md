# Async Data Tasks (DataTask) — A Usage Guide

Language: [简体中文](../zh/data-task.md) [English](data-task.md)

A DataTask turns "one configured SQL query + output reshaping rules + one delivery target" into an
**asynchronous job**: submission returns a `jobId` immediately, an executor-side background worker claims and runs it,
and the reshaped result is streamed to a **delivery provider (sink) implemented as an external extension** — xlsx to
object storage, message-queue producers, CSV files, and so on. The framework itself owns definition management,
claim-based scheduling, progress heartbeats, the state machine and the completion event; **no delivery provider ships
with the repository** — every destination plugs in through the SPI in [Section 5](#5-authoring-and-registering-a-sink).

> This is the full guide; the "Async Data Tasks" chapter in `usage.md` remains as a quick reference.

## 1. What problem it solves

Synchronous APIs fit small result sets and interactive latency. When a query runs for minutes, its output is a file or
a queue message, or the consumer is a backend system, waiting synchronously stops making sense. DataTask targets that
space:

- **Submit async, poll for the outcome**: submission returns a `jobId`; the job record exposes progress (delivered
  rows) and terminal status.
- **Reshaping is built in**: naming strategy (camel/underline/case), column aliases, column order/subset and type
  formatting are applied before delivery, so sinks always receive headers and cells in their final shape.
- **Pluggable destinations**: delivery logic lives outside the framework; deployers extend it per destination without
  touching each other.
- **Consistent with the deployment model**: manager owns definition/record CRUD, executor executes, and **no new HTTP
  connection is introduced** between them — workers claim jobs by polling the meta store (`FOR UPDATE SKIP LOCKED`),
  so multiple executors share load naturally and never double-deliver.
- **Reliability backstops**: run lease + lost-worker reaping, row caps, statement timeout, cooperative cancellation.

End-to-end sequence:

```text
caller                  manager                    executor worker
  │ POST /submit ───────▶ validate + bind params + snapshot definition
  │ ◀── jobId ──────────        │
  │                       DATAPOLY_DATA_TASK_JOB (PENDING)
  │                                    │ claim inside a transaction via FOR UPDATE SKIP LOCKED → RUNNING
  │                                    │ render SQL → stream rows → reshape → sink session in batches
  │                                    │ (periodic heartbeat: refresh total_rows / renew lease / check cancel)
  │ GET /job/{id} ───────▶ SUCCESS + artifactUri ◀── sink.complete() reports the artifact
  │        (executor publishes DataTaskEvent on terminal states; wire push from there)
```

## 2. Prerequisites

| Requirement | Notes |
| --- | --- |
| Services deployed | the three services interconnect via Eureka with the gateway (default `8091`) as the only external entry; executor and manager publish no ports — every request below goes through the gateway |
| Login token | manager enforces unified auth; requests carry `Authorization: Bearer <token>`. The factory demo account `admin/123456` is **for demos only — change it for any real deployment** |
| Registered datasource | definitions reference a `datasourceId`; register the target database (and its account permissions) in the management UI first |
| Meta-store migration | `log-v1.1.0` (tables `DATAPOLY_DATA_TASK_DEF` / `DATAPOLY_DATA_TASK_JOB`, MySQL & PostgreSQL DDL) is applied automatically by manager via Liquibase at startup — no manual step |
| executor worker | enabled by default (`datapoly.data-task.enabled=true`); if explicitly disabled, jobs queue forever — see [Section 6](#6-worker-configuration-reference) |
| A delivery provider | at least one sink extension jar on **every** executor's classpath ([Section 5](#5-authoring-and-registering-a-sink)), otherwise jobs fail at the delivery step with `datatask.sink.unknown` |

## 3. Core concepts

| Concept | Description |
| --- | --- |
| Definition | one SQL statement (MyBatis dynamic tags + `#{}` placeholders), input parameter declarations (same `ItemParam` model as API assignments), output reshaping rules, and the delivery target (`sinkType` + opaque `sinkConfig`); names are unique; definitions can be enabled/disabled |
| Job | one row per submission; the definition content is **snapshotted** at submit time so later edits — even deletion — never affect queued or running work; carries status, delivered-row counter, cancel flag, artifact URI, failure reason, executing node |
| Worker | switchable role inside executor: claims PENDING rows inside a transaction using `FOR UPDATE SKIP LOCKED` (multi-node safe, one job per node), refreshes rows and renews the lease every `flush-interval-ms`; RUNNING jobs of lost workers (crash/network partition) are marked FAILED by the reaper once `lease_expire_at` passes |
| Sink | external extension implementing `com.cs.common.datatask.DataTaskSink`, registered as a Spring bean or via `META-INF/services`; definitions select one by `sinkType` |

**State machine**: `PENDING → RUNNING → SUCCESS | FAILED | CANCELED` (the last three are terminal).

- Cancelling a PENDING job takes effect immediately; cancelling a RUNNING job is **cooperative**: the request sets
  `cancel_requested` and the worker aborts the delivery session at its next progress tick (≤ `flush-interval-ms`,
  5 s by default), recording CANCELED.
- Non-query statements (INSERT/UPDATE/DELETE) are supported too; the affected count lands in `artifact_info.updateCount`.

**Artifact description**: on success the sink returns `SinkOutcome{artifactUri, info}`, written back onto the job
record verbatim — `artifactUri` is whatever the frontend needs to consume the payload (download URL, object-store
key, ...); `artifactInfo` is a merged JSON of engine-side statistics (`sinkType`, `deliveredRows`, `truncated`,
`updateCount`, ...) plus sink-defined fields.

## 4. End-to-end walkthrough (curl)

Examples assume the gateway at `http://localhost:8091` (docker-compose default):

```bash
GW=http://localhost:8091
BASE=$GW/datapoly/manager/api/v1/data-task
```

**① Log in for a token** (take `data.accessToken` from the response):

```bash
TOKEN=$(curl -s -X POST $GW/datapoly/manager/user/login \
  -H 'Content-Type: application/json' \
  -d '{"username": "admin", "password": "<your password>"}' | jq -r .data.accessToken)
AUTH="Authorization: Bearer $TOKEN"
```

**② Parse SQL parameters** (optional; reuses the API-assignment parser and lists the placeholders to ease authoring
the parameter declarations):

```bash
curl -s -X POST "$BASE/parse?sql=SELECT%20order_id%2C%20amount%20FROM%20t_order%20WHERE%20dt%20%3E%3D%20%23%7Bdt%7D" -H "$AUTH"
```

**③ Create a definition**. Example: export paid orders by date, with reshaping and CSV delivery:

```bash
curl -s -X POST $BASE/create -H "$AUTH" -H 'Content-Type: application/json' -d '{
  "name": "paid-order-export",
  "description": "Export paid orders by day",
  "datasourceId": 1,
  "sqlText": "SELECT order_id, order_no, amount, created_at FROM t_order WHERE created_at >= #{dt} AND status = #{status}",
  "params": [
    {"name": "dt", "type": "STRING", "location": "REQUEST_FORM", "required": true, "remark": "start time"},
    {"name": "status", "type": "STRING", "location": "REQUEST_FORM", "required": false, "defaultValue": "PAID"}
  ],
  "namingStrategy": "CAMEL_CASE",
  "columnAlias": {"amount": "orderAmount"},
  "columnOrder": ["orderNo", "orderId", "orderAmount", "createdAt"],
  "formatMap": [
    {"key": "TIMESTAMP", "value": "yyyy-MM-dd HH:mm:ss"},
    {"key": "BIG_DECIMAL", "value": "2"}
  ],
  "applyFormatToString": true,
  "maxRows": 50000,
  "sinkType": "csv",
  "sinkConfig": "{\"outputDir\": \"/data/exports\", \"fileNamePrefix\": \"orders\"}"
}'        # → data is the new definition id, e.g. 1
```

Notes:

- Parameter declarations share the API-assignment model: `name/type/location/isArray/required/defaultValue/remark`;
  `type` is one of `LONG/DOUBLE/STRING/DATE/TIME/BOOLEAN/OBJECT` (OBJECT requires `children`; submit accepts nested
  maps or flat `parent.sub` keys). Submission enforces required checks and type conversion; undeclared extra
  parameters are ignored.
- Reshaping order is **naming strategy → alias → column order**: `columnAlias` keys match column names **after** the
  naming strategy is applied; `columnOrder` fixes the output order and subset — unlisted columns are dropped.
- In `formatMap`, date/time types take a pattern string and `BIG_DECIMAL` takes a scale (HALF_UP, default 6); with
  `applyFormatToString=true` cells are delivered as formatted strings, otherwise sinks receive raw JDBC objects.
- `sinkConfig` is a **JSON string** whose schema belongs to the sink; it is passed through verbatim
  (see [Section 5](#5-authoring-and-registering-a-sink)).
- Defaults: `namingStrategy=CAMEL_CASE`, `dollarAllowed=false`, `enabled=true`.

**④ Dry-run preview** (executes synchronously and returns the shaped headers plus up to N rows; no job row, no sink):

```bash
curl -s -X POST $BASE/preview -H "$AUTH" -H 'Content-Type: application/json' -d '{
  "defId": 1, "params": {"dt": "2026-01-01 00:00:00"}, "previewSize": 100
}'        # → data: {columns, rows, totalPreviewed, truncated}; previewSize defaults to 50, max 200
```

**⑤ Submit the job**:

```bash
curl -s -X POST $BASE/submit -H "$AUTH" -H 'Content-Type: application/json' -d '{
  "defId": 1, "params": {"dt": "2026-01-01 00:00:00"}
}'        # → data is the jobId, e.g. 1001
```

**⑥ Poll the job record** until a terminal state:

```bash
curl -s $BASE/job/1001 -H "$AUTH"
# data.status: PENDING → RUNNING → SUCCESS
# terminal example: status=SUCCESS, totalRows=3281, artifactUri=file:///data/exports/orders-1001.csv,
#                  artifactInfo={"sinkType":"csv","deliveredRows":3281,"truncated":false,...}
```

Remaining endpoints: `POST /list` (paged definitions, `{page, size, searchText}`), `GET /detail/{id}`,
`DELETE /delete/{id}` (refused while PENDING/RUNNING jobs exist), `POST /update`, `POST /jobs/search`
(`{defId?, status?, page, size}`), `POST /job/{id}/cancel`.

## 5. Authoring and registering a sink

A delivery provider is a jar on the executor classpath implementing two interfaces. The contract:

```java
public interface DataTaskSink {
    String type();                                  // unique registration id = sinkType in definitions
    SinkSession openSession(SinkRequest request);   // one session per job invocation
}

public interface SinkSession extends Closeable {
    // The engine feeds batches of positional cell values matching SinkRequest.getColumns();
    // consume them within the call. Return false to stop the engine reading further rows.
    boolean writeRows(Iterable<List<Object>> batch);
    SinkOutcome complete();                         // finish and report the artifact
    default void abort(Throwable cause) { ... }     // best-effort cleanup on failure/cancel; overridable
}
```

`SinkRequest` carries: `jobId / taskName / sinkType / sinkConfig` (the definition's opaque config JSON, verbatim),
`columns` (headers **after reshaping**), `columnMetadata` (per-column JDBC type hints — `jdbcType` /
`className` — parallel to `columns` and shaped through the same alias/order projection; entries may be null
and the list may be empty on drivers without type metadata), `outputFormats` (patterns declared on the task),
`submittedBy`.

A complete example (CSV-file sink, contract illustration only — use a real CSV library and streaming writes in
production):

```java
public class CsvFileSink implements DataTaskSink {

    @Override
    public String type() { return "csv"; }

    @Override
    public SinkSession openSession(SinkRequest request) throws Exception {
        // the sinkConfig schema belongs to this implementation; example: {"outputDir": "...", "fileNamePrefix": "..."}
        JsonNode cfg = new ObjectMapper().readTree(request.getSinkConfig());
        Path dir = Paths.get(cfg.path("outputDir").asText("."));
        String prefix = cfg.path("fileNamePrefix").asText("data-task-" + request.getJobId());
        return new CsvSession(dir.resolve(prefix + "-" + request.getJobId() + ".csv"), request.getColumns());
    }

    static class CsvSession implements SinkSession {
        private final Path file;
        private final BufferedWriter writer;
        private long rows;

        CsvSession(Path file, List<String> columns) throws IOException {
            this.file = file;
            Files.createDirectories(file.getParent());
            writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8);
            writer.write(String.join(",", columns));
            writer.newLine();
        }

        @Override
        public boolean writeRows(Iterable<List<Object>> batch) throws IOException {
            for (List<Object> row : batch) {                    // consume eagerly; never hold the reference
                writer.write(row.stream().map(String::valueOf).collect(Collectors.joining(",")));
                writer.newLine();
                rows++;
            }
            return true;                                        // false = stop reading early
        }

        @Override
        public SinkOutcome complete() throws IOException {
            writer.flush();
            return SinkOutcome.builder()
                    .artifactUri(file.toUri().toString())              // whatever the frontend needs
                    .info(Collections.singletonMap("rows", rows))      // merged into the job's artifactInfo
                    .build();
        }

        @Override
        public void abort(Throwable cause) {                   // cleanup on failure/cancel
            SinkSession.super.abort(cause);                    // default implementation just close()s; delete the file here
        }

        @Override
        public void close() throws IOException { writer.close(); }
    }
}
```

Registration — one of the two:

1. **`META-INF/services` (recommended, zero Spring coupling)**: inside the extension jar create
   `META-INF/services/com.cs.common.datatask.DataTaskSink` listing implementation class names (one per line).
2. **Spring bean**: annotate the implementation with `@Component` covered by the executor's component scan, or
   register it via a `@Bean` method.

On duplicate `type`s, the **Spring bean wins** over ServiceLoader-discovered implementations (a warn is logged at
registration). The extension jar must be deployed on **every** executor (otherwise jobs claimed by that node fail at
delivery); placing it on manager is optional and only enables the local resolvability check when saving definitions
(unresolvable types warn, never block).

Two auxiliary extension points:

- **Per-cell processing**: implement `com.cs.common.datatask.CellDecorator`
  (`decorate(column, columnIndex, value)`) as a Spring bean; it runs after reshaping and before delivery — put
  masking, unit conversion or enrichment here instead of repeating it inside every sink.
- **Completion push**: when a job reaches a terminal state, the executor node that ran it publishes the Spring event
  `com.cs.core.datatask.DataTaskEvent` (fields: `jobId/defId/defName/status/totalRows/artifactUri/errorMessage/sinkType`).
  Listen for it in the host application to wire WebSocket/SSE/webhooks; the default interaction remains frontend
  polling of `/job/{id}`.

## 6. Worker configuration reference

Executor-side prefix `datapoly.data-task.*` (every key can be overridden with a `DATAPOLY_DATA_TASK_*` environment
variable in `application.yaml`):

| Key | Default | Meaning |
| --- | --- | --- |
| `enabled` | `true` | activates the worker role; when false the node only serves the API data plane and claims nothing |
| `workers` | `2` | per-node concurrency cap on a dedicated bounded pool (long queries never starve other scheduled threads) |
| `poll-interval-ms` | `5000` | claim polling cadence |
| `reap-interval-ms` | `30000` | lost-worker reap cadence |
| `lease-seconds` | `600` | run lease length, renewed via heartbeats; expired leases are reaped to FAILED |
| `flush-interval-ms` | `5000` | minimum interval between progress/cancel-check/lease-renewal ticks |
| `fetch-size` | `1000` | JDBC fetchSize (MySQL dialects switch to streaming `Integer.MIN_VALUE`) |
| `query-timeout-seconds` | `1800` | statement-level timeout backstop |
| `max-rows-default` | `1000000` | global row cap when a definition sets no `maxRows` (or ≤ 0) |

Multi-node semantics: claiming happens inside a meta-store transaction with `FOR UPDATE SKIP LOCKED`, which
distributes PENDING jobs across executors without duplication; scaling out is just more executor instances with the
worker enabled. A job executes on a **single** node only — the engine does not resume partial deliveries across
nodes; jobs of lost workers are marked FAILED once the lease expires and callers resubmit.

## 7. Security and operational constraints

- `sink_config` is stored **in plain text** in the meta store and passed through verbatim: extensions should reference
  server-hosted credentials/environment settings (credential aliases, KMS references) rather than embedding access
  keys or passwords in definitions.
- Anyone able to author definitions effectively gains **arbitrary query power over the target datasource** (the SQL
  runs as-is); use the platform account model to control who may create tasks and reach which datasources.
- `${}` raw substitution is disabled by default (injection/concatenation risk) and only allowed when a definition
  explicitly opts in via `dollarAllowed=true`; prefer `#{}` parameterization.
- The row cap (`maxRows` / `max-rows-default`) and statement timeout (`query-timeout-seconds`) are resource
  backstops — **do not remove or unbound them**; filter or archive in SQL for very large result sets.
- Definitions with PENDING/RUNNING jobs cannot be deleted; edits never affect submitted jobs (snapshot isolation).
- Delivery sessions run inside the executor process, outside the script sandbox — sink extensions are
  **deployer-vetted code**: review their provenance and permissions like any production dependency.

## 8. Troubleshooting FAQ

| Symptom | Cause and fix |
| --- | --- |
| Job stuck in PENDING | no executor alive, the worker disabled (`DATAPOLY_DATA_TASK_ENABLED=false`), or concurrency full (`workers`); check executor logs for claim activity first |
| FAILED with `datatask.sink.unknown` | the claiming node has no implementation for that `sinkType` on its classpath — deploy the extension jar on **every** executor and resubmit |
| FAILED with `data task lease expired, executor lost` | the worker went missing (crash, long GC, network partition to the meta store) and the reaper collected the job; resubmit for one-offs, investigate executor stability or raise `lease-seconds` if frequent |
| `artifactInfo.truncated=true` | output hit the row cap (definition `maxRows` or `max-rows-default`); raise the cap or split the SQL into batches for full extracts |
| Cancellation is slow to take effect | cancelling a RUNNING job is cooperative and lands within ≤ `flush-interval-ms` (5 s by default); PENDING jobs cancel immediately |
| Preview works but the submitted job fails | preview never touches the sink — the failure is almost certainly on the delivery side (sink missing, invalid `sinkConfig`, target-side auth); check `errorMessage` and executor logs |
| Duplicate deliveries after deploying multiple executors? | Not possible: claiming is an atomic `FOR UPDATE SKIP LOCKED` operation in the meta store — a job is RUNNING on at most one node |
