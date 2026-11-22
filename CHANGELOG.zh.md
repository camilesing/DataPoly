# 变更日志

本文件记录本项目所有值得关注的变更，仅按大版本收录。
格式基于 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)；英文版见 [CHANGELOG.md](CHANGELOG.md)。

### 说明

- 已知限制与运维约定见 `AGENTS.md`。

## [1.10.0] - 2026-11-22

### 新增

- GraphQL 引擎：每个接口就是一份 SDL 文档，`type Query` 根字段经 `@sql` 指令绑定 SQL 模板——SQL、SCRIPT 之外的第三种执行引擎。
- 管理 MCP 服务 `/mcp/admin`（实体增删改查工具，仅 manage 权限令牌可用）；`/mcp` 升级为 dual-era Streamable HTTP 单端点（兼容 legacy initialize 客户端与 2026-07-28 无状态版）。
- 登录会话落库 `DATAPOLY_LOGIN_TOKEN`（7 天绝对有效期）：manager 重启不再把所有人踢下线。
- 对外网关/Manager 地址可在管理页修改，逐请求查库、即时生效。
- 接口列表：草稿与在线合并为一张表（状态下拉筛选），新增历史版本查看与快速上线。
- DataTask 语句级投递 Sink：MaxCompute `UNLOAD` 等源端导出跳过行式管线、直接在源引擎内执行，阻塞期间代为续租。
- 用户角色、登录页扩展钩子；管理 UI 暗色重构、侧边菜单重组、同因错误弹窗去重。

### 变更

- 基线升级：Boot 3.5.16 + Cloud 2025.0.3 + JDK 25；测试集中到 `datapoly-test` 模块（覆盖率 26% → 47%）。
- 模板 SQL 按原样书写：裸 `<`、`&` 无需转义；解析失败以 400 返回具体原因。
- 执行过的语句连同绑定参数以 INFO 级别落日志。

### 修复

- 安全：数据源口令以密文导出（未启用加密时直接剥离）、分页大小上限 1000、脚本 `env` 模块白名单化，封堵 SSRF、接口文档解码与沙箱等多处绕过。
- 正确性：Sentinel 限流计数、Redis/Hazelcast 缓存操作、`Instant` JSON 序列化、任务时间戳北京时间、连接测试透出驱动报错、调度线程池优雅停机、UI 裁切。

## [1.9.0] - 2026-09-19

### 新增

- DataTask Sink 契约（净扩展）：`SinkRequest` 新增 `columnMetadata`（逐列 JDBC 类型提示，与列走同一套投影整形），终态 `DataTaskEvent` 携带 `sinkType`——类型化渲染器（表格类目标）可为采样值全为 null 的列定型，推送通知方可按提供方过滤。
- 文档：`docs/{zh,en}/data-task.md` 记录扩展后的 Sink 契约（`columnMetadata`、`DataTaskEvent.sinkType`）。
- 新增数据源类型 ODPS（阿里云 MaxCompute）：`ProductTypeEnum` 注册 `ODPS`（驱动 `com.aliyun.odps.jdbc.OdpsDriver`，URL 模板 `jdbc:odps:http(s)://{host}/api?project=...`，项目名即 schema）。连接属性携带 MaxCompute 2.0 类型开关（`odps.sql.type.system.odps2=true`），连接预检执行真实 `SELECT 1 FROM dual`，即刻验证 AccessKey 鉴权。不做 SQL 级分页改写：MaxCompute 仅支持与 `ORDER BY` 连用的 `LIMIT m OFFSET n`，`apiPageNum`/`apiPageSize` 落到驱动侧结果上限（默认 1 万行）。驱动构件位于 `drivers/odps/odps-3/`；UI 新增 ODPS 类型图标与 About 词条。

### 修复

- 数据任务流式内存与完整性：单条语句现在在显式事务中执行（归还连接前恢复自动提交，DML 先提交），PostgreSQL 系驱动（含 Hologres）因此尊重 `fetch-size`、按有界批次拉取，而不是把整个结果集缓冲在堆里——大导出曾因此报 `Java heap space` / 反复 `GC overhead limit exceeded`。同时修复结果集正常结束时不足 1000 行的尾批被丢弃的问题；两项均由 `DataTaskJobEngineStreamQueryTest` 锁定。
- 部署时区对齐：compose MySQL 以 `--default-time-zone=+08:00` 启动（`build-docker/install`、`.devcontainer`），devcontainer PostgreSQL 设 `timezone=Asia/Shanghai`，与 JDBC `serverTimezone=Asia/Shanghai` 及容器 `TZ=Asia/Shanghai` 一致。此前 MySQL 会话时区默认 UTC，`TIMESTAMP` 列（如 `DATAPOLY_DATA_TASK_DEF.create_time`/`update_time`）读回偏移 8 小时。
- 异步数据任务框架（DataTask）：manager 侧 `/datapoly/manager/api/v1/data-task/**` 端点覆盖任务定义（SQL + 入参声明 + 输出整形：命名策略/列别名/列顺序/类型格式）与执行记录（提交/状态轮询/协作取消）。executor 经认领 worker 基于 `DATAPOLY_DATA_TASK_JOB`（`FOR UPDATE SKIP LOCKED`，租约 + 回收）执行任务，不新增 manager 与 executor 之间的直连。投递目标一律由外部 `com.cs.common.datatask.DataTaskSink` 扩展提供（Spring bean 或 `META-INF/services`）；完成情况可经标准 Spring `DataTaskEvent` 与轮询任务记录端点观察。Liquibase 迁移 `log-v1.1.0` 建两张元数据表（MySQL 与 PostgreSQL 双份）。
- 文档：docs（中/英）新增「异步数据任务」章节，覆盖 REST 接口面、生命周期、worker 配置键（`datapoly.data-task.*`）与 Sink 扩展指南。
- 文档：独立 DataTask 指南 `docs/{zh,en}/data-task.md`（前置条件、端到端 curl 演练、Sink 编写与注册、worker 配置、安全约束与排错 FAQ）。
- 接口配置后扩展点：管理面 `debug` 与 `update` 操作现在扇出到已注册的 `com.cs.core.extension.ApiAssignmentPostProcessor` 提供方（宿主 Spring bean 按 `@Order` 排序，或 `META-INF/services`，重复注册时 bean 优先；逐钩子异常隔离，扩展抛错不影响接口流程），并发布携带完整结果的 Spring 事件 `ApiAssignmentDebugEvent` / `ApiAssignmentUpdateEvent`（调试答案/日志/类型/错误/耗时，更新请求 + 保存后实体快照）。docs（中/英）usage 章节已记录。

### 修复

- `DataTaskSinkRegistry` 缺少 Spring 构型注解，manager/executor 启动即报 bean 无法解析；现已改为 `@Component` 并注入可选的 sink 列表，且重复 `type()` 时 Spring bean 优先于 ServiceLoader 发现的实现（与文档一致）。
