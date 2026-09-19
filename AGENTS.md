# AGENTS.md — DataPoly 开发与部署安全约定

> 安全弱点上报见 SECURITY.md；内部评审细节不入库。

Maven 多模块，BSD-3-Clause。本机构建与 CI 统一 JDK 25（LTS；temurin/homebrew 均可），产物即成 Java 25 字节码；宿主机扩展构建（build-extension.sh）同样要求 JDK ≥25（编译目标钉在 25，低于 25 直接中止）。CI（temurin 25）经 `mvn test -pl datapoly-test -am` 运行测试：各模块测试统一集中在 datapoly-test 模块（JUnit 4 + 手写 fake，包名与被测模块保持同包以访问 package-private 成员），其余模块不含 src/test。
- lombok 1.18.48、groovy 4.0.33（org.apache.groovy）为钉版勿回退（JDK 25 基线验证可用）；注解处理器依赖必须走 `annotationProcessorPaths`。
- 三服务经 Eureka 互联：manager（8090，Liquibase 唯一迁移执行方）、executor（8092）、gateway（8091 唯一对外入口）。前端 datapoly-manager-ui 非 Maven：manager resources 下的 `index.html` 与 `static/` 为构建产物 **不入库**（已被 .gitignore 排除，勿提交/勿 git add -f），打包前由根目录 `build-ui.sh`（node:24-alpine 容器，本机无需 Node）生成——`build.sh` 与 `docker-maven-build.sh` 已在它之前前置 `build-extension.sh`（宿主扩展装配：本地已有 `datapoly-extension` 目录即构建，否则无操作）及该 UI 步骤；纯 `mvn package` 的 jar 不含 UI。`build-ui.sh debug` 产出 devtools 可用的调试构建（`DATAPOLY_UI_ENV=debug`，NODE_ENV=development），仅限本机联调、勿随发行版发布；`build.sh`/`docker-maven-build.sh` 会透传首参给 build-ui.sh。
- 许可头：新改文件只写 BSD 许可声明行，勿写个人 Copyright 头；vendored 文件（如 io.modelcontextprotocol.*）保留原版权声明。

## 模块速览与常用命令

SQL/DSL → RESTful API 的数据访问中间件（Boot 3.5.x LTS + Cloud 2025.0.x，jakarta 命名空间）。Maven 模块：common（通用定义）、mcp（LLM MCP 协议）、template（SQL 内容模板）、persistence（数据库持久化）、core（接口核心实现）、cache（执行缓存）、executor/gateway/manager（三服务，见下）、test（集中全部测试）、dist（发行打包）；`datapoly-manager-ui` 为前端（非 Maven）；`drivers/` 装配 20+ 数据库 JDBC 驱动；`build-docker/` 镜像与 compose 一键安装。改动前按需读 docs/{zh,en}/ 下 overview.md、build-deploy.md、data-task.md（涉及 DataTask 必读）。API 文档注解用 springdoc/swagger v3（io.swagger.v3.oas.annotations），勿引入 springfox。

- 全量测试（CI 同款）：`mvn test -pl datapoly-test -am`
- 发行构建：`./build.sh`（前置 build-extension.sh 与 build-ui.sh 再 mvn package）；容器内构建：`./docker-maven-build.sh`

## 一、网络分段（必须遵守）

- 仅 gateway 对外发布端口；manager 与 executor 除 Eureka 外无 HTTP 直连，新增直连须同步更新白名单与本文件。
- manager 内置两个 MCP Server（均走 `?token=` 查询参数鉴权，`/mcp/**` 豁免会话拦截器）：数据 MCP `/mcp`·`/mcp/sse`（任意 MCP 令牌，调用数据 API tools）；管理 MCP `/mcp/admin`·`/mcp/admin/sse`（仅 `mcp_client.manage_flag=1` 令牌，`dp_{entity}_{action}` 工具覆盖数据源/模块/分组/API/客户端等实体增删改查，见 `McpAdminTools`）。manage 令牌等同管理员（可读 appSecret 与 MCP 令牌明文），创建/授予须走已认证会话。
- executor `GatewaySourceFilter`（`/*`）：`trusted-cidrs` 仅 IPv4 CIDR（K8s 探针须加入）；`auth-token` 设置后必须带头 `X-DATAPOLY-Gateway-Token`（恒定时间比较，gateway 配 `DATAPOLY_GATEWAY_TOKEN` 注入）。gateway 未设 token 时注入哨兵 `UNSET`，勿改回空默认（SCG 启动会失败）。
- 来源判定只用 `getRemoteAddr()`，勿信任何转发头。

## 二、脚本沙箱

Groovy 沙箱默认启用但不是 JVM 隔离：勿把脚本编写权开放给不可信用户，编译期黑名单与命令执行双层拦截勿削弱。脚本跑独立有界线程池，超时默认 60s 即取消，并发过多返回 429。

## 三、凭据外部化

真实值一律环境变量注入，禁止写回仓库 yaml：`DATAPOLY_ADMIN_PASSWORD`、`DATAPOLY_REDIS_PASSWORD`、`DATAPOLY_DS_AES_KEY`（轮换前须用旧密钥导出重录）、`DATAPOLY_CORS_ALLOWED_ORIGINS`、compose 的 `MYSQL_ROOT_PASSWORD`/`MYSQL_PASSWORD`（演示默认 123456）。演示凭据 admin/DataPoly@123456、test/test 正式部署必须修改；actuator 已收窄为 health,info。飞书登录凭证 `DATAPOLY_FEISHU_APP_ID`/`DATAPOLY_FEISHU_APP_SECRET` 同样只从环境变量注入：compose 取被忽略的 `install/.env`，发行版 `conf/config.ini` 留空即关闭（`datapolyctl.sh` 只在键有值时导出，空串会让布尔属性宽松绑定失败）。

## 四、端点防护

`/token/generate` 与 `/user/login`：按 clientId/IP 限流（默认 10、20 次/分）+ 连续失败锁定，内存实现重启清零。secret/token 比较一律 `MessageDigest.isEqual`；错误响应只回通用错误码，堆栈只进日志与 access_record。

## 五、其他约束

- 新增 JDBC 代码资源必须 try-with-resources；firewall 规则行被删时网关按"全拒绝"处理（fail-closed，属预期）。
- DataTask 投递 Sink 仓库内置零实现，外部以 Spring Bean / `META-INF/services` 注册（SPI `com.cs.common.datatask.DataTaskSink`；服务端导出类投递——如 MaxCompute `UNLOAD` 直写对象存储——另实现可选接口 `DataTaskStatementSink`：`handlesStatement` 判定、`executeStatement` 执行，引擎跳过整条行式管线并在阻塞期间代为续租，取消只在提交前拦得住）；宿主可自行维护本地扩展：在顶层 `datapoly-extension/`（已被 .gitignore 排除，独立 git 仓库）下用 `backend/` 放 Maven 扩展模块（依赖钉版在模块自身 pom、不进根 reactor，由入库脚本 build-extension.sh 在宿主机 JDK 25 构建后投放 lib-extra/，随发行版装配进各服务 classpath）、`front/` 放扩展 UI；API 扩展点 `ApiAssignmentPostProcessor` 注册方式相同、须同步执行且保持轻量。详见 docs/*/data-task.md。
- 默认前端扩展目录 `datapoly-extension/front`（同被 .gitignore 排除）经 datapoly-manager-ui 编译期装配：webpack `@extension` 别名自动探测该目录 `src/index.js`（见 build/webpack.base.conf.js）、`src/extension-stub` 为缺省回退、扩展路由与 i18n 词条在 manager-ui 入口深合并、登录页扩展区渲染 `@extension` 的 `loginExtras` 组件数组（`src/views/login/index.vue`，stub 为空数组）——这五处钩子（build/webpack.base.conf.js、src/extension-stub、src/router、src/main.js、src/views/login/index.vue 的 loginExtras 挂载点）勿移除或改名；目录不存在时 CI 与普通构建不受影响。front 自带 `package.json` 可直接启动（`npm run dev`，复用宿主 webpack 链，前置为宿主 node_modules 已安装、Node 24——dev server 与生产构建均已实测）。
- 用户角色见 `DATAPOLY_SYSTEM_USER.user_role`（`ADMIN`/`USER`，v1.4.0 迁移：新行默认 `USER`、存量行回填 `ADMIN`），登录响应 `AccessToken.role` 带回该值；角色只决定界面可见范围，鉴权仍以 token 为准，按角色的端点拦截须同时校验数据库中的角色而非前端传来的值。
- 宿主扩展 jar 经根目录 `lib-extra/` 投放点进入发行版 `lib/common/`（`package.xml` 打包该目录 `*.jar`；目录只占位入库，jar 永不入库）。扩展为独立 git 仓库（内部 GitLab，front+backend 一体）：`build-extension.sh` 不做任何 git 拉取，扩展仓库由使用者自行克隆/更新到 `datapoly-extension/`（仍被 .gitignore 排除），目录已存在则按本地工作区构建（宿主机 JDK 25 优先、低于 25 不可用）；`build.sh`/`docker-maven-build.sh` 会先调用该脚本，目录不存在时无操作（纯开源构建零影响）。本地环境变量注入（env.sh）与防误提交钩子集中在被忽略的 `dev-local/`。
- 一次性 token 在校验时即消费（含查库兜底路径；2026-09 修复兜底不消费导致的重放）；并发首用竞态下多 executor 仍可能各放行一次（无分布式锁，已知限制）。
