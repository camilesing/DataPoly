# AGENTS.md — DataPoly 开发与部署安全约定

> 安全弱点上报见 SECURITY.md；内部评审细节不入库。

Maven 多模块，BSD-3-Clause。本机构建 JDK 25，产物必须编译为 Java 8 字节码；CI（temurin 8）只跑 common/template/core/executor/gateway/manager 六模块的测试。
- lombok 1.18.46、groovy 4.0.33（org.apache.groovy）为 JDK 25 连带钉版勿回退；注解处理器依赖必须走 `annotationProcessorPaths`。
- 三服务经 Eureka 互联：manager（8090，Liquibase 唯一迁移执行方）、executor（8092）、gateway（8091 唯一对外入口）。前端 datapoly-manager-ui 非 Maven，产物需重建后同步进 manager resources。
- 许可头：新改文件只写 BSD 许可声明行，勿写个人 Copyright 头；vendored 文件（如 io.modelcontextprotocol.*）保留原版权声明。

## 一、网络分段（必须遵守）

- 仅 gateway 对外发布端口；manager 与 executor 除 Eureka 外无 HTTP 直连，新增直连须同步更新白名单与本文件。
- executor `GatewaySourceFilter`（`/*`）：`trusted-cidrs` 仅 IPv4 CIDR（K8s 探针须加入）；`auth-token` 设置后必须带头 `X-DATAPOLY-Gateway-Token`（恒定时间比较，gateway 配 `DATAPOLY_GATEWAY_TOKEN` 注入）。gateway 未设 token 时注入哨兵 `UNSET`，勿改回空默认（SCG 启动会失败）。
- 来源判定只用 `getRemoteAddr()`，勿信任何转发头。

## 二、脚本沙箱

Groovy 沙箱默认启用但不是 JVM 隔离：勿把脚本编写权开放给不可信用户，编译期黑名单与命令执行双层拦截勿削弱。脚本跑独立有界线程池，超时默认 60s 即取消，并发过多返回 429。

## 三、凭据外部化

真实值一律环境变量注入，禁止写回仓库 yaml：`DATAPOLY_ADMIN_PASSWORD`、`DATAPOLY_REDIS_PASSWORD`、`DATAPOLY_DS_AES_KEY`（轮换前须用旧密钥导出重录）、`DATAPOLY_CORS_ALLOWED_ORIGINS`、compose 的 `MYSQL_ROOT_PASSWORD`/`MYSQL_PASSWORD`（演示默认 123456）。演示凭据 admin/123456、test/test 正式部署必须修改；actuator 已收窄为 health,info。

## 四、端点防护

`/token/generate` 与 `/user/login`：按 clientId/IP 限流（默认 10、20 次/分）+ 连续失败锁定，内存实现重启清零。secret/token 比较一律 `MessageDigest.isEqual`；错误响应只回通用错误码，堆栈只进日志与 access_record。

## 五、其他约束

- 新增 JDBC 代码资源必须 try-with-resources；firewall 规则行被删时网关按"全拒绝"处理（fail-closed，属预期）。
- DataTask 投递 Sink 仓库内置零实现，外部以 Spring Bean / `META-INF/services` 注册（SPI `com.cs.common.datatask.DataTaskSink`）；宿主可自行维护本地扩展模块（如 `datapoly-extension-*`，已被 .gitignore 排除、依赖钉版在模块自身 pom、构建后装配进 executor classpath）；`SinkRequest.columnMetadata`（按列 JDBC 类型提示，经整形投影）与 `DataTaskEvent.sinkType` 为 2026-08 配套契约扩展；`sink_config` 勿存明文口令；`${}` 替换默认禁止；行数上限与语句超时兜底不得移除。API 扩展点 `ApiAssignmentPostProcessor` 注册方式相同、须同步执行且保持轻量。详见 docs/*/data-task.md。
- 本地前端扩展目录 `datapoly-extension-ui`（同被 .gitignore 排除）经 datapoly-manager-ui 编译期装配：webpack `@extension` 别名自动探测该目录、`src/extension-stub` 为缺省回退、扩展路由与 i18n 词条在 manager-ui 入口深合并——这四处钩子文件（build/webpack.base.conf.js、src/extension-stub、src/router、src/main.js）勿移除或改名；目录不存在时 CI 与普通构建不受影响。
- 多 executor 下一次性 token 每节点各可用一次（有查库兜底，已知限制）。