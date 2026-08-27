# AGENTS.md — DataPoly 开发与部署安全约定

> 安全弱点的上报渠道见 SECURITY.md；内部评审细节不入库。

## 〇、仓库速览

**DataPoly 是什么**：把 SQL/DSL 配置转化为 RESTful API 的数据访问中间件，BSD-3-Clause，
产物为 Java 8 字节码（本机构建工具链 JDK 25，见第六节），Maven 多模块（根 pom groupId `com.cs`）。三个可运行服务经 Eureka 互联，gateway 是唯一对外入口。

**模块地图**：
- 可运行服务：`datapoly-manager`（`ManagerApplication`：管理端 API + 元库写入 + Liquibase 唯一迁移执行方）、
  `datapoly-executor`（`ExecutorApplication`：SQL/脚本执行引擎，API 数据面）、`datapoly-gateway`
  （`GatewayApplication`：Spring Cloud Gateway 入口/防火墙）。端口与网络分段约束见第一节。
- 库模块：`datapoly-common`（公共契约与工具，含异步数据任务投递 SPI `com.cs.common.datatask`）、`datapoly-template`（MyBatis 动态 SQL 模板 `XmlSqlTemplate`）、
  `datapoly-core`（执行引擎/驱动加载/JDBC 与数据源工具；含异步数据任务引擎与 worker `com.cs.core.datatask`）、`datapoly-persistence`（元库 entity/dao/mapper）、
  `datapoly-cache`（hazelcast/redis 实现）、`datapoly-mcp`（聚合模块，子模块 `datapoly-mcp-core`/
  `datapoly-mcp-springmvc` 内含 vendored `io.modelcontextprotocol.*`，勿动其版权头）、`datapoly-dist`（发布打包）。
- 前端：`datapoly-manager-ui`（Vue2 + element-ui + axios，webpack 3 工具链，非 Maven 模块；产物须重建后同步进
  manager resources，见第六节）。

**测试与 CI**：仅 common/template/core/executor/gateway/manager 六个模块有 `src/test`；`.github/workflows/ci.yml`
跑的就是第六节那条 `mvn test -pl …` 命令（temurin JDK 8），新增测试放这六个模块内即可被 CI 覆盖。

**其他文档**：README / README.zh（精简入口，正文收敛于 docs/）、docs/en 与 docs/zh（双语镜像：产品介绍 →
`overview.md`，构建部署 → `build-deploy.md`，使用教程 → `usage.md`，DataTask 完整指南 → `data-task.md`，
两语内容须同步维护）、SECURITY.md（漏洞上报）、
NOTICE（第三方声明）、CHANGELOG.md。

## 一、网络分段要求（部署必须遵守）

- **唯一入口是 gateway（默认 8091）**。executor（8092）与 manager（8090）不得对外发布端口。
- executor 的 `GatewaySourceFilter`（`/*`，order=1）拒绝一切来源不在白名单的直连请求：
    - `datapoly.executor.gateway.trusted-cidrs`：TCP 远端地址白名单（仅 IPv4 CIDR；IPv6 仅精确匹配）。默认 `127.0.0.1,::1`。
    - `datapoly.executor.gateway.auth-token`：可选共享密钥；设置后请求必须携带 gateway 注入的头 `X-DATAPOLY-Gateway-Token`
      且常量时间匹配（gateway 侧配置同名环境变量 `DATAPOLY_GATEWAY_TOKEN` 自动注入并清除客户端伪造值；*
      *对可信来源（trusted-cidrs 命中）同样要求该头**——设置密钥后本机 curl 直连也需带）。注意 gateway 的 `AddRequestHeader`
      不允许空值：未设置 `DATAPOLY_GATEWAY_TOKEN` 时 gateway 注入哨兵值 `UNSET`（executor 未配置 auth-token 时忽略该头；配置了而不一致则
      403，fail-safe 方向正确）——三期冒烟发现空默认值会导致 gateway 启动时 SCG 绑定校验失败，切勿改回空默认。
    - `datapoly.executor.gateway.enabled=false` 可整体关闭（仅限排查问题时临时使用）。
- **不要信任任何转发头**（`X-Forwarded-For`、`Request-Gateway-IP` 等）做来源判定；过滤器仅使用 `getRemoteAddr()`。
- `build-docker/install/docker-compose.yml` 已改为专用 bridge 网络（`datapoly-net`，172.28.0.0/24，gateway 固定
  172.28.0.40），仅 gateway 发布端口。K8s/物理机部署需遵循同等分段，k8s 探针地址需加入 trusted-cidrs。
- manager 与 executor 之间除 Eureka 注册外无 HTTP 直连；新增直连功能时须同步更新白名单与本文件。

## 二、脚本沙箱与超时

- executor/manager 的 Groovy 脚本引擎默认启用沙箱（`ScriptSandboxConfiguration`）：编译期黑名单拦截系统调用、
  反射、文件 IO、原生网络、动态类加载、嵌套脚本与动态依赖等高危向量；命令执行（字符串/集合上的
  `execute()`/`exec()`）在编译期与运行时双层拦截；`ThreadInterrupt` 编译增强使死循环可被中断。拦截明细见
  `ScriptSandboxConfiguration` 源码。
- 超时：脚本在独立有界线程池执行，`datapoly.executor.script.timeout-seconds`（默认 60 秒）到期即取消并返回错误；并发脚本过多时直接拒绝（429）。
- 沙箱是黑名单机制而非 JVM 级隔离：**不应将脚本编写权开放给不可信用户**；绕过面与缓解措施的评审细节由维护者内部记录，不入库。

## 三、凭据外部化约定

以下配置一律经环境变量注入，禁止将真实值写回仓库内 yaml：

| 用途                       | 环境变量                                     | 配置键                             | 默认值（兼容存量）                  |
|--------------------------|------------------------------------------|---------------------------------|----------------------------|
| 种子 admin 口令覆盖            | `DATAPOLY_ADMIN_PASSWORD`                | `datapoly.admin.password`       | 空（未设置且未改默认口令时启动告警）         |
| Redis 密码                 | `DATAPOLY_REDIS_PASSWORD`                | `datapoly.cache.redis.password` | 空                          |
| 数据源口令 AES 密钥             | `DATAPOLY_DS_AES_KEY`                    | `datapoly.datasource.aes-key`   | 原内置密钥（见 `DataSourceUtils`） |
| MySQL root/业务口令（compose） | `MYSQL_ROOT_PASSWORD` / `MYSQL_PASSWORD` | —                               | 123456（仅 compose 演示默认）     |
| CORS 白名单                 | `DATAPOLY_CORS_ALLOWED_ORIGINS`          | `datapoly.cors.allowed-origins` | `*`（显式白名单时才允许凭据）           |

- AES 密钥轮换后，旧密文将无法解密，需先在旧密钥下导出、再以新密钥重录。
- actuator 暴露已收窄为 `health,info`；如需恢复监控端点，请通过环境变量按需放开并保证端点不对外。
- 种子数据中的 admin/123456、app client `test/test` 属出厂演示凭据，正式部署必须修改。

## 四、端点防护

- `/token/generate`：
    - executor 侧 `ClientTokenGuard`：按 clientId+IP 每分钟限流（默认 10 次，
      `datapoly.executor.token.rate-limit-per-minute`）+ 连续失败锁定（默认 5 次锁 300 秒，`fail-lock-threshold`/
      `fail-lock-seconds`）；超限返回 429。
    - gateway 侧每 IP 限流（默认 20 次/分钟，`datapoly.gateway.token-rate-limit-per-minute`，设为 0 关闭）。
    - 限流与锁定为内存实现，进程重启清零。
- `/user/login`：`LoginGuard` 频控 + 失败锁定 + 防枚举（见五D）。
- secret/token 比较均为恒定时间（`MessageDigest.isEqual`）。
- 错误响应体只回通用错误码与消息，异常原文/堆栈只进日志与 access_record（H1）。

## 五、其他已知事项

- `H2`：`SqlJdbcUtils` 的 PreparedStatement 已 try-with-resources；新增 JDBC 代码必须同样处理。
- `S6` 依赖线：guava 32.1.3-jre / hutool 5.8.38 / mysql-connector 8.0.33 / sentinel 由 SCA 2021.0.4.0 BOM 管理（1.8.5，
  `FlowRule` 无 `id` 字段属预期）。Boot/SC/SCA 整线升代是独立决策，未在本期处理。
  **2026-08-27 本机构建工具链升 JDK 25 连带钉版（产物仍编译为 1.8 字节码）**：lombok `1.18.46`（Boot 2.5.6 BOM 托管的
  1.18.20 在 JDK 21+ javac 下报 `JCImport.qualid` NoSuchFieldError；lombok 1.18.x 最低要求仍为 JDK 8，CI temurin 8 不受影响）——
  且 JDK 23+ javac 不再从 classpath 隐式发现注解处理器，根 pom 的 maven-compiler-plugin（3.15.0）已配 `annotationProcessorPaths`
  显式传入 lombok，**以后新增注解处理器依赖时须同样走 annotationProcessorPaths**；groovy `3.0.20 → 4.0.33`、groupId 由
  `org.codehaus.groovy` 改为 `org.apache.groovy`（4.x 起迁移，模块 pom 里的引用已同步），因 3.0.20 的 ASM 读不了 JDK 25 的
  classfile 69（脚本编译与沙箱测试报 `Unsupported class file major version`）；groovy 4.x 最低要求仍为 JDK 8，CI 不受影响。
- token map（Hazelcast 单机）不跨节点共享：多 executor 下一次性 token 每节点各可用一次，属已知限制（token 校验有
  查库兜底，不影响正确性；远期候选切 Redis 共享）。
- 元数据库每请求查询（A2）已在三期以本地短 TTL 缓存收敛，见五C。
- `DataSourceUtils` 的 `classLoaderMap` 与 `DriverLoadService` 的 drivers map 只增不减（进程生命周期内驻留，量级=驱动目录数，有界），属已知限制，无需清理。
- firewall 规则行被删除（种子 id=1 不存在）时网关按"全拒绝"处理（fail-closed），属预期。
- **异步数据任务（DataTask，2026-08 新增）**：manager 的 `/datapoly/manager/api/v1/data-task/**` 提供任务定义
  （SQL + 入参声明 + 出参调整[命名策略/列别名/列顺序/类型格式化] + 投递目标）与执行记录的 CRUD/提交/取消/轮询；
  executor 以 worker 轮询认领元库表 `DATAPOLY_DATA_TASK_JOB`（事务内 `FOR UPDATE SKIP LOCKED` 原子抢占，
  租约超时由 reaper 记 FAILED），manager 与 executor 之间不新增任何直连。迁移在 Liquibase log-v1.1.0
  （MySQL/PG 双份 DDL）。投递目的地一律由外部扩展实现 `com.cs.common.datatask.DataTaskSink` 注册
  （宿主 Spring Bean 或 `META-INF/services`；**仓库内置零实现**）；完成通知走 Spring 事件 `DataTaskEvent`
  加前端轮询执行记录接口。配置前缀 `datapoly.data-task.*`（executor 启用 worker：enabled/workers/
  poll-interval-ms/reap-interval-ms/lease-seconds/flush-interval-ms/fetch-size/query-timeout-seconds/
  max-rows-default）。约束：`sink_config` 原文入库、勿存明文口令；任务定义作者等同获得目标数据源的查询能力；
  `${}` 替换默认禁止（定义级 `dollar_allowed` 显式开启才可用）；行数上限与语句超时兜底不得移除；
  运行中取消为协作式（worker 在进度心跳点识别 cancel_requested）。完整教程与扩展指南见 docs/*/data-task.md
  （usage.md「异步数据任务」为速查）。
- **API 配置后置扩展点（2026-08 新增）**：管理端 `/assignment/debug`、`/assignment/update` 动作完成后调用
  `com.cs.core.extension.ApiAssignmentPostProcessor`（`postDebug` 携带完整调试产物 answer/logs/types/errorMessage/耗时，
  成功与失败终态均回调；`postUpdate` 携带通过校验的请求与落库实体快照，触发时 DAO 事务已提交）。注册通道同
  DataTaskSink（宿主 Spring Bean 或 `META-INF/services`，同名类以 Spring Bean 为准、Bean 间按 @Order 排序），
  并同步发布 Spring 事件 `ApiAssignmentDebugEvent`/`ApiAssignmentUpdateEvent`；处理器异常仅 warn 不影响主流程与其他处理器，
  钩子于 manager 请求线程同步执行（实现须保持轻量），扩展 jar 属宿主信任域（调试上下文含完整查询结果）。
  装配类 `ApiAssignmentExtensionConfiguration` 在 `com.cs.core.extension` 包（manager 已扫描）。用法见 docs/*/usage.md「API 配置扩展点」。