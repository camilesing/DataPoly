# 异步数据任务（DataTask）使用指南

语言: [简体中文](data-task.md) [English](../en/data-task.md)

DataTask 把"一段配置好的 SQL 查询 + 出参整形规则 + 一个投递目的地"变成**异步作业**：提交后立刻拿到
`jobId`，executor 侧后台 worker 认领执行，结果流式整形后交给**外部扩展实现的投递组件（sink）**——转存
xlsx 上传对象存储、写入消息队列、生成 CSV 文件等。框架本身只负责定义管理、调度抢占、进度心跳、状态机与
完成事件，**仓库内置零个投递实现**，一切目的地都通过 [第 5 节](#5编写并注册投递目的地sink) 的 SPI 接入。

> 本文是完整教程；`usage.md` 的「异步数据任务」章节保留为速查表。

## 1、它解决什么问题

同步 API 适合小结果集、低延迟交互；当查询要跑几分钟、结果要落成文件/进消息队列、或消费方是后台系统时，
同步等待不再合适。DataTask 面向这类场景：

- **异步提交、轮询结果**：提交即返回 `jobId`，执行记录可查进度（已投递行数）与终态。
- **结果整形内建**：命名策略（驼峰/下划线/大小写）、列别名、列顺序/裁剪、类型格式化在投递前统一完成，
  sink 拿到的永远是"最终形态"的列头与单元格。
- **目的地可插拔**：投递逻辑不在框架里，由部署方按需扩展，互不干扰。
- **与现有部署模型一致**：manager 负责定义与记录 CRUD，executor 负责执行，两者之间**不新增任何 HTTP
  直连**——worker 直接轮询元库抢占任务（`FOR UPDATE SKIP LOCKED`），多 executor 天然分担且不会重复投递。
- **可靠性兜底**：运行租约 + 失联回收（reaper）、行数上限、语句超时、协作式取消。

整体时序：

```text
调用方                  manager                    executor worker
  │ POST /submit ───────▶ 校验 + 参数绑定 + 定义快照
  │ ◀── jobId ──────────        │
  │                       DATAPOLY_DATA_TASK_JOB (PENDING)
  │                                    │ 事务内 FOR UPDATE SKIP LOCKED 抢占 → RUNNING
  │                                    │ 渲染 SQL → 流式读取 → 列整形 → sink 会话分批投递
  │                                    │ （周期心跳：刷新 total_rows / 续租 / 检查取消）
  │ GET /job/{id} ───────▶ SUCCESS + artifactUri ◀── sink.complete() 回传产物描述
  │        （终态时 executor 发布 DataTaskEvent，可接入推送）
```

## 2、前置条件

| 条件 | 说明 |
| --- | --- |
| 服务已部署 | 三服务经 Eureka 互联、gateway（默认 `8091`）是唯一对外入口；executor 与 manager 不对外发布端口，下文所有请求都经 gateway |
| 登录态 | manager 统一鉴权，请求需带 `Authorization: Bearer <token>`；出厂演示账号 `admin/123456` **仅限演示，正式部署必须修改** |
| 已注册数据源 | 任务定义引用 `datasourceId`，需先在管理端登记目标库（含账号权限） |
| 元库迁移 | `log-v1.1.0`（`DATAPOLY_DATA_TASK_DEF` / `DATAPOLY_DATA_TASK_JOB` 两表，MySQL/PG 双份 DDL）由 manager 启动时经 Liquibase 自动执行，无需手工操作 |
| executor worker | 默认启用（`datapoly.data-task.enabled=true`）；若被显式关闭则任务一直排队，见[第 6 节](#6worker-配置参考) |
| 投递实现 | 至少一个 sink 扩展 jar 已部署到**每个 executor** 的 classpath（[第 5 节](#5编写并注册投递目的地sink)），否则任务执行到投递一步会以 `datatask.sink.unknown` 失败 |

## 3、核心概念

| 概念 | 说明 |
| --- | --- |
| 任务定义 definition | 一条 SQL（单语句，支持 MyBatis 动态标签与 `#{}` 占位符）、入参声明（与 API 配置同一套 `ItemParam` 模型）、出参整形规则、投递目标（`sinkType` + 私有配置 `sinkConfig`）；名称唯一，可启用/停用 |
| 执行记录 job | 每次提交产生一行；提交时对定义内容做**快照**，之后编辑甚至删除定义都不影响已排队/运行中的任务；记录状态、已投递行数、取消标记、产物地址、失败原因、执行节点等 |
| worker | executor 进程内可开关的后台角色：事务内 `FOR UPDATE SKIP LOCKED` 抢占 PENDING 记录（多节点安全，一个任务只被一个节点执行），执行期间按 `flush-interval-ms` 心跳刷新行数并续租；worker 失联（崩溃/网络分区）后，reaper 按 `lease_expire_at` 到期把其 RUNNING 任务记为 FAILED |
| 投递实现 sink | 实现 `com.cs.common.datatask.DataTaskSink` 的外部扩展，以 Spring Bean 或 `META-INF/services` 注册，任务定义按 `sinkType` 选择 |

**状态机**：`PENDING → RUNNING → SUCCESS | FAILED | CANCELED`，后三者为终态。

- 排队中（PENDING）取消立即生效；运行中（RUNNING）取消是**协作式**的：取消请求置位 `cancel_requested`，
  worker 在下一个进度心跳点（间隔 ≤ `flush-interval-ms`，默认 5 秒）识别后中止投递会话并记 CANCELED。
- 非查询语句（INSERT/UPDATE/DELETE）同样支持，影响行数记入 `artifact_info.updateCount`。

**产物描述**：sink 完成时返回 `SinkOutcome{artifactUri, info}`，原样写回执行记录——`artifactUri` 是前端
消费产物所需的任何标识（下载地址、对象存储 key 等）；`artifactInfo` 另含引擎侧统计（`sinkType`、
`deliveredRows`、`truncated`、`updateCount` 等）与 sink 自定义字段的合并 JSON。

## 4、端到端上手（curl）

以下示例假设 gateway 在 `http://localhost:8091`（docker-compose 默认），变量：

```bash
GW=http://localhost:8091
BASE=$GW/datapoly/manager/api/v1/data-task
```

**① 登录拿 token**（返回体取 `data.accessToken`）：

```bash
TOKEN=$(curl -s -X POST $GW/datapoly/manager/user/login \
  -H 'Content-Type: application/json' \
  -d '{"username": "admin", "password": "<你的口令>"}' | jq -r .data.accessToken)
AUTH="Authorization: Bearer $TOKEN"
```

**② 解析 SQL 入参**（可选；复用 API 配置同款解析器，返回占位符列表便于填写入参声明）：

```bash
curl -s -X POST "$BASE/parse?sql=SELECT%20order_id%2C%20amount%20FROM%20t_order%20WHERE%20dt%20%3E%3D%20%23%7Bdt%7D" -H "$AUTH"
```

**③ 创建任务定义**。示例：按日期导出已支付订单，列整形 + CSV 投递：

```bash
curl -s -X POST $BASE/create -H "$AUTH" -H 'Content-Type: application/json' -d '{
  "name": "paid-order-export",
  "description": "按日导出已支付订单",
  "datasourceId": 1,
  "sqlText": "SELECT order_id, order_no, amount, created_at FROM t_order WHERE created_at >= #{dt} AND status = #{status}",
  "params": [
    {"name": "dt", "type": "STRING", "location": "REQUEST_FORM", "required": true, "remark": "起始时间"},
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
}'        # → data 为新定义 id，例如 1
```

要点：

- 入参声明字段与 API 配置一致：`name/type/location/isArray/required/defaultValue/remark`，`type` 取
  `LONG/DOUBLE/STRING/DATE/TIME/BOOLEAN/OBJECT`（OBJECT 需声明 `children`，支持嵌套 Map 或 `parent.sub`
  扁平键提交）；提交时按声明做必填校验与类型转换，未声明的多余入参被忽略。
- 整形顺序为**命名策略 → 别名 → 列顺序**：`columnAlias` 的 key 匹配命名策略转换**之后**的列名；
  `columnOrder` 给出输出列的顺序与子集，未列出的列被丢弃。
- `formatMap` 中日期/时间类型给格式串，`BIG_DECIMAL` 给小数位数（HALF_UP，默认 6）；
  `applyFormatToString=true` 时单元格按格式转为字符串投递，`false` 时 sink 收到原始 JDBC 对象。
- `sinkConfig` 是**字符串形式的 JSON**，结构由对应 sink 自定义，原样透传（见[第 5 节](#5编写并注册投递目的地sink)）。
- 默认值：`namingStrategy=CAMEL_CASE`、`dollarAllowed=false`、`enabled=true`。

**④ 试运行**（dry-run：同步执行并返回整形后的列与前 N 行，不建任务、不触碰 sink）：

```bash
curl -s -X POST $BASE/preview -H "$AUTH" -H 'Content-Type: application/json' -d '{
  "defId": 1, "params": {"dt": "2026-01-01 00:00:00"}, "previewSize": 100
}'        # → data: {columns, rows, totalPreviewed, truncated}；previewSize 默认 50、上限 200
```

**⑤ 提交执行**：

```bash
curl -s -X POST $BASE/submit -H "$AUTH" -H 'Content-Type: application/json' -d '{
  "defId": 1, "params": {"dt": "2026-01-01 00:00:00"}
}'        # → data 为 jobId，例如 1001
```

**⑥ 轮询执行记录**至终态：

```bash
curl -s $BASE/job/1001 -H "$AUTH"
# data.status: PENDING → RUNNING → SUCCESS
# 终态示例：status=SUCCESS, totalRows=3281, artifactUri=file:///data/exports/orders-1001.csv,
#           artifactInfo={"sinkType":"csv","deliveredRows":3281,"truncated":false,...}
```

其余接口：`POST /list`（定义分页，`{page, size, searchText}`）、`GET /detail/{id}`、`DELETE /delete/{id}`
（存在 PENDING/RUNNING 任务时拒绝删除）、`POST /update`、`POST /jobs/search`
（`{defId?, status?, page, size}`）、`POST /job/{id}/cancel`。

## 5、编写并注册投递目的地（Sink）

投递组件是一个放在 executor classpath 上的 jar，实现两个接口。契约：

```java
public interface DataTaskSink {
    String type();                                  // 唯一注册标识 = 任务定义里的 sinkType
    SinkSession openSession(SinkRequest request);   // 每个任务执行开一个会话
}

public interface SinkSession extends Closeable {
    // 引擎分批喂入位置对应 SinkRequest.getColumns() 的单元格值；必须在本调用内消费完
    // 返回 false 可让引擎提前停止读取（例如按大小分卷后主动截断）
    boolean writeRows(Iterable<List<Object>> batch);
    SinkOutcome complete();                         // 收尾，返回产物描述
    default void abort(Throwable cause) { ... }     // 失败/取消路径的兜底清理，可覆盖
}
```

`SinkRequest` 携带：`jobId / taskName / sinkType / sinkConfig`（定义里的私有配置 JSON，原样透传）、
`columns`（**整形后**的列头）、`outputFormats`（任务声明的类型格式）、`submittedBy`。

完整示例（CSV 文件 sink，仅演示契约，生产请换真正的 CSV 库与流式写法）：

```java
public class CsvFileSink implements DataTaskSink {

    @Override
    public String type() { return "csv"; }

    @Override
    public SinkSession openSession(SinkRequest request) throws Exception {
        // sinkConfig 结构由本实现自定义；示例: {"outputDir": "...", "fileNamePrefix": "..."}
        JsonNode cfg = new ObjectMapper().readTree(request.getSinkConfig());
        Path dir = Paths.get(cfg.path("outputDir").asText("."));
        String prefix = cfg.path("fileNamePrefix").asText("data-task-" + request.getJobId());
        return new CsvSession(dir.resolve(prefix + "-" + request.getJobId() + ".csv"), request.getColumns());
    }

    static class CsvSession implements SinkSession {
        private final BufferedWriter writer;
        private long rows;

        CsvSession(Path file, List<String> columns) throws IOException {
            Files.createDirectories(file.getParent());
            writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8);
            writer.write(String.join(",", columns));
            writer.newLine();
        }

        @Override
        public boolean writeRows(Iterable<List<Object>> batch) throws IOException {
            for (List<Object> row : batch) {                    // 必须即时消费，不要持有引用
                writer.write(row.stream().map(String::valueOf).collect(Collectors.joining(",")));
                writer.newLine();
                rows++;
            }
            return true;                                        // false = 主动提前结束
        }

        @Override
        public SinkOutcome complete() throws IOException {
            writer.flush();
            return SinkOutcome.builder()
                    .artifactUri(file.toUri().toString())              // 前端消费产物所需的地址
                    .info(Collections.singletonMap("rows", rows))      // 合并进执行记录 artifactInfo
                    .build();
        }

        @Override
        public void abort(Throwable cause) {                   // 失败/取消时清理半成品
            SinkSession.super.abort(cause);                    // 默认实现即 close()，可在此先删文件
        }

        @Override
        public void close() throws IOException { writer.close(); }
    }
}
```

> 上例为节省篇幅省略了 import 与字段声明（`file`/`rows` 为会话成员变量）；语义以 `SinkSession` 契约为准。

注册二选一：

1. **`META-INF/services`（推荐，零 Spring 耦合）**：在扩展 jar 内新建文件
   `META-INF/services/com.cs.common.datatask.DataTaskSink`，内容为实现类全限定名（每行一个）。
2. **Spring Bean**：实现类加 `@Component` 并保证被 executor 的组件扫描覆盖，或用 `@Bean` 方法注册。

同名 `type` 冲突时 **Spring Bean 优先**于 ServiceLoader 发现的实现（注册时打 warn 日志）。扩展 jar 需要
放到**每一个** executor 上（否则该节点认领的任务投递失败）；manager 上可选放置，仅用于保存定义时对
`sinkType` 做本地可解析性检查（不可解析只告警不阻断）。

两个补充扩展点：

- **逐格加工**：实现 `com.cs.common.datatask.CellDecorator`（`decorate(column, columnIndex, value)`）注册为
  Spring Bean，作用于列整形之后、投递之前——脱敏、单位换算、富化等横切需求放这里，不必侵入每个 sink。
- **完成推送**：任务到达终态时，**执行它的 executor 节点**会发布 Spring 事件
  `com.cs.core.datatask.DataTaskEvent`（字段：`jobId/defId/defName/status/totalRows/artifactUri/errorMessage`）。
  在宿主应用监听即可对接 WebSocket/SSE/Webhook；默认交互仍是前端轮询 `/job/{id}`。

## 6、Worker 配置参考

executor 侧前缀 `datapoly.data-task.*`（`application.yaml` 均可用 `DATAPOLY_DATA_TASK_*` 环境变量覆盖）：

| 键 | 默认 | 说明 |
| --- | --- | --- |
| `enabled` | `true` | 是否启用 worker 角色；关闭后该节点只剩 API 数据面，不认领任务 |
| `workers` | `2` | 单节点并发执行上限（专用有界线程池，长查询不挤占其他调度线程）|
| `poll-interval-ms` | `5000` | 认领轮询周期 |
| `reap-interval-ms` | `30000` | 失联任务回收检查周期 |
| `lease-seconds` | `600` | 运行租约时长，随心跳续期；到期未续即被 reaper 记 FAILED |
| `flush-interval-ms` | `5000` | 进度刷新/取消检测/租约续期的最小间隔 |
| `fetch-size` | `1000` | JDBC fetchSize（MySQL 方言自动改用流式 `Integer.MIN_VALUE`）|
| `query-timeout-seconds` | `1800` | 语句级超时兜底 |
| `max-rows-default` | `1000000` | 定义未显式设置 `maxRows`（或 ≤0）时生效的全局行数上限 |

多节点语义：认领在元库事务内 `FOR UPDATE SKIP LOCKED` 完成，天然把 PENDING 任务分发给不同 executor 且
不重复；横向扩容只需加 executor 实例并保持 worker 开启。任务只会在**单个**节点上执行——引擎不做跨节点
断点续投，节点失联的任务按租约超时记 FAILED 后由调用方重新提交。

## 7、安全与运维约束

- `sink_config` 在元库中**明文存储**并原样透传：扩展应引用服务端托管的凭据/环境变量（如凭据别名、KMS
  引用），不要把 AccessKey、口令写进任务定义。
- 能创建任务定义的用户**等同拥有目标数据源的任意查询能力**（SQL 在数据源上原样执行），请用平台账号
  体系控制谁能建任务、谁能访问哪些数据源。
- `${}` 原生替换默认禁止（存在注入拼接风险），仅在定义显式 `dollarAllowed=true` 时放行；优先使用 `#{}`
  参数化。
- 行数上限（`maxRows` / `max-rows-default`）与语句超时（`query-timeout-seconds`）是资源兜底，**不建议
  移除或调到失控**；超大结果集应在 SQL 侧做归档/过滤。
- 定义存在 PENDING/RUNNING 任务时禁止删除；编辑定义不影响已提交任务（快照隔离）。
- 投递会话运行在 executor 进程内、脚本沙箱约束之外——sink 扩展属于**部署方自证代码**，请像对待生产
  依赖一样评审其来源与权限。

## 8、故障排查 FAQ

| 现象 | 原因与处置 |
| --- | --- |
| 任务一直 PENDING | 无 executor 存活、worker 被关闭（`DATAPOLY_DATA_TASK_ENABLED=false`）或并发已满（`workers`）；先看 executor 日志有无认领记录 |
| FAILED，错误含 `datatask.sink.unknown` | 认领该任务的节点 classpath 上没有对应 `sinkType` 的实现——把扩展 jar 部署到**每个** executor 后重新提交 |
| FAILED，`data task lease expired, executor lost` | worker 失联（进程崩溃、长时间 GC、与元库网络分区）被 reaper 回收；偶发可重提，频发需查 executor 稳定性或上调 `lease-seconds` |
| `artifactInfo.truncated=true` | 命中行数上限被截断（定义 `maxRows` 或 `max-rows-default`）；需要全量就调大上限或改写 SQL 分批 |
| 取消迟迟不生效 | RUNNING 任务的取消是协作式的，生效延迟 ≤ `flush-interval-ms`（默认 5 秒）；PENDING 任务取消立即生效 |
| preview 正常但正式任务失败 | preview 不触碰 sink——失败几乎必然在投递侧（sink 未部署、`sinkConfig` 不合法、目标端鉴权失败），看 `errorMessage` 与 executor 日志 |
| 多 executor 部署后任务重复投递？ | 不会。认领是元库事务内 `FOR UPDATE SKIP LOCKED` 原子操作，一条任务只会被一个节点置为 RUNNING |
