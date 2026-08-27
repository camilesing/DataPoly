# 使用教程

语言: [简体中文](usage.md) [English](../en/usage.md)

内置管理页面提供了完整的向导式流程（数据源配置 → 接口定义 → 发布上线），相关文档正在编写中。

> 图文教程素材已就位于 `docs/images/zh_CN/`，待整理为正式教程。
---

## 异步数据任务（DataTask）

把"一段配置好的 SQL 查询 + 出参格式调整"作为**异步作业**运行：提交后由 executor 后台 worker 认领执行，
查询结果按声明好的列改名/列顺序/命名策略/类型格式化整形后，交给**由扩展方实现的投递组件**处理——例如转存为
xlsx 并上传对象存储、写入消息队列等。框架只负责定义、调度、进度与状态机；**仓库内置零个投递实现**，
任何具体目的地都通过下文的 SPI 以扩展方式接入。前端拿到 `jobId` 后轮询执行记录接口即可获得完成状态与产物地址。

> 完成后的推送式通知（WebSocket/SSE/Webhook）可在宿主应用监听 Spring 事件 `com.cs.core.datatask.DataTaskEvent`
> 自行实现；默认交互方式为轮询。

### 概念

| 概念 | 说明 |
| --- | --- |
| 任务定义 definition | 一条 SQL（支持 MyBatis 动态标签与 `#{}` 占位）、入参声明（`ItemParam`，与 API 配置同一套模型）、出参调整规则（命名策略/列别名/列顺序/类型格式化/是否字符串单元格）、投递目标（`sinkType` + 私有配置 `sinkConfig`）|
| 执行记录 job | 提交一次产生一行；提交时对定义内容做**快照**，之后编辑/删除定义不影响已在排队的任务；包含状态、行数、取消标记、产物地址等 |
| worker | executor 进程内可开关的后台角色：事务内 `FOR UPDATE SKIP LOCKED` 抢占 PENDING 记录，执行期间周期刷新行数与租约；worker 失联后其 RUNNING 任务由 reaper 按 `lease_expire_at` 记为 FAILED |
| 投递实现 sink | 外部扩展实现 `com.cs.common.datatask.DataTaskSink`，以 Spring Bean 或 `META-INF/services` 注册，任务定义按 `sinkType` 选择 |

状态机：`PENDING → RUNNING → SUCCESS | FAILED | CANCELED`。排队中的任务可直接取消；
运行中的取消是协作式的（worker 在进度心跳点识别 `cancel_requested` 后中止投递会话并记 CANCELED）。

### REST 接口

前缀 `/datapoly/manager/api/v1/data-task`，经 gateway 访问，走统一登录态鉴权：

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/parse?sql=...` | 解析 SQL 入参列表（复用 API 配置同款能力）|
| POST | `/create` `/update` | 定义 CRUD |
| GET | `/detail/{id}` · DELETE `/delete/{id}` | |
| POST | `/list` | 定义分页搜索（`page/size/searchText`）|
| POST | `/preview` | **试运行**：同步执行并返回整形后的列名与前 N 行（默认 50、上限 200），不创建任务、不触碰投递组件 |
| POST | `/submit` | `{defId, params}` → `jobId`；入参缺省校验、类型转换与 API 配置一致 |
| GET | `/job/{id}` | 执行记录详情（前端轮询此接口拿状态与 `artifactUri`）|
| POST | `/jobs/search` | `{defId?, status?, page/size}` 分页 |
| POST | `/job/{id}/cancel` | 取消 |

典型时序：

```text
前端                    manager                     executor worker
 │ POST /submit ───────▶ 校验+参数绑定+快照 ──▶ DATAPOLY_DATA_TASK_JOB(PENDING)
 │ ◀── jobId ───────────                              │
 │                          ▶ … FOR UPDATE SKIP LOCKED 抢占为 RUNNING
 │                                          渲染 SqlMeta → 流式读取 → 列整形 → sink 会话批量投递
 │                                          （心跳刷新 total_rows/lease）
 │ GET /job/{id} ───────▶ SUCCESS(+artifactUri) ◀──── sink.complete() 回传产物描述
```

### Worker 配置（executor 侧，`datapoly.data-task.*`）

| 键 | 默认 | 说明 |
| --- | --- | --- |
| `enabled` | `true`(executor) | 关闭后该节点不认领任务，仅剩 API 数据面 |
| `workers` | `2` | 单节点并发任务上限（专用有界线程池，长查询不会饿死其他 @Scheduled 任务）|
| `poll-interval-ms` / `reap-interval-ms` | 5000 / 30000 | 认领轮询 / 失联回收周期 |
| `lease-seconds` | 600 | 运行租约时长，随心跳续期 |
| `flush-interval-ms` | 5000 | 进度/取消检测/心跳的最小间隔 |
| `fetch-size` | 1000 | 非 MySQL 方言的 JDBC fetchSize（MySQL 自动用流式 `Integer.MIN_VALUE`）|
| `query-timeout-seconds` | 1800 | 语句级超时兜底 |
| `max-rows-default` | 1000000 | 定义未显式设置 `maxRows` 时生效的全局上限 |

### 扩展一个投递目的地（如“xlsx 转存 OSS”）

仓库不内置任何投递实现；在**executor 的 classpath**上放一个 jar，实现两个接口即可接入：

```java
public class ExcelOssSink implements com.cs.common.datatask.DataTaskSink {
    public String type() { return "excel-oss"; }                 // 任务定义里的 sinkType

    public SinkSession openSession(SinkRequest req) {
        // req.sinkConfig(): 定义里填写的私有配置 JSON（原样透传）
        // req.columns()/outputFormats()/submittedBy()：整形后的列头、类型格式与人
        return new ExcelOssSession(req);                          // 例如用 POI/EasyExcel 写工作簿，
    }                                                             // complete() 时上传 OSS 并返回下载地址
}
```

`SinkSession` 语义：`writeRows(Iterable<List<Object>>)` 按批次消费位置对应的单元格值（返回 false 可提前结束读取）；
`complete()` 收尾并返回 `SinkOutcome{artifactUri, info}`（原样写回执行记录）；异常路径引擎会调用 `abort(cause)`
清理资源。注册二选一：宿主 Spring Bean，或新建
`META-INF/services/com.cs.common.datatask.DataTaskSink` 文件登记实现类（推荐，零 Spring 依赖）。

更细粒度的逐格加工（脱敏、单位换算等）可实现 `com.cs.common.datatask.CellDecorator` 注册为 Bean，作用于投影之后、投递之前。

安全须知：
- `sink_config` 在元库中是**明文**存储并原样透传，请让扩展引用服务端托管的凭据/环境变量，不要把 AccessKey 明文写进任务定义。
- 能创建任务定义的用户等同拥有目标数据源的任意查询能力，请结合平台账号管理控制授权范围。
- `${}` 原生替换默认禁止，仅在定义显式 `dollarAllowed=true` 时可用；请优先使用 `#{}` 参数化。
- 行数上限与语句超时兜底（见配置表）不建议移除；超大结果集建议在 SQL 侧做归档过滤。
