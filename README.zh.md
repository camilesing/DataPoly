# DataPoly
语言: [简体中文](README.zh.md) [English](README.md)

[![CI](https://github.com/camilesing/datapoly/actions/workflows/ci.yml/badge.svg)](https://github.com/camilesing/datapoly/actions/workflows/ci.yml)
[![License: BSD-3-Clause](https://img.shields.io/badge/License-BSD_3--Clause-blue.svg)](LICENSE)

> 将 SQL 操作转化为 RESTful API 的便捷工具

DataPoly 是一款开源的 SQL/DSL 数据访问中间件：只需选择数据源、配置 SQL 或脚本、设置路由，即可快速生成 RESTful API，无需编写后端代码。支持 20+ 种常见数据库及国产主流库，具备 MyBatis 动态 SQL、Groovy 脚本、Token 认证、Sentinel 流控、Hazelcast/Redis 缓存、在线接口文档、大模型 MCP 服务等能力。

## 使用 Docker Compose 快速启动
```
sh ./build.sh
sh ./docker-maven-build.sh
cd build-docker/install
docker compose up -d
```
打开 `http://127.0.0.1:8091/#/login`，使用用户名 `admin`、密码 `DataPoly@123456` 登录。

## 从源码构建 DataPoly

构建 DataPoly 的前置要求：

* 类 Unix 环境（我们使用 Linux、Mac OS X；Windows 用户可直接运行 `build.cmd`）
* Git
* Maven（要求 3.6 及以上）
* Java（25，LTS）
* Docker（仅用于构建管理界面，本机无需 Node.js）

### 基本构建步骤

首先克隆仓库：

```
git clone https://github.com/camilesing/datapoly.git
cd datapoly
```

然后根据你的环境选择以下命令之一：

**Linux / Mac OS X（标准发行构建）**

```
sh ./build.sh
# 如果你想debug的话
sh ./build.sh debug
```

**容器内 Maven 构建（Docker）**

```
sh ./docker-maven-build.sh
```

**Windows**

```
build.cmd
```

构建过程可能需要几分钟，首次构建会下载全部 Maven 依赖。
完成后发行包生成在 `target/datapoly-release-<version>.tar.gz`。

### 注意事项

* 确认 `JAVA_HOME` 指向 JDK 25——构建产物即 Java 25 字节码
* `build.sh`（及 `docker-maven-build.sh`）会先装配可选本地扩展（`build-extension.sh`，目录 `datapoly-extension/` 不存在时无操作），再在 `node:24-alpine` 容器内构建管理界面（`build-ui.sh`），最后执行 `mvn clean package -DskipTests`
* 纯 `mvn package` 不会构建 UI——得到的 jar 不含管理界面
* 向 `build.sh` / `docker-maven-build.sh` 传入 `debug` 可产出 devtools 调试版 UI，仅限本机联调
* 全量测试（CI 同款）：`mvn test -pl datapoly-test -am`——所有测试集中在 `datapoly-test` 模块

## 开发 DataPoly

推荐使用 IntelliJ IDEA 开发 DataPoly 代码。

IDE 的最低要求：
* 支持 Java 25 与 Lombok 注解处理
* 支持 Maven

### IntelliJ IDEA

IntelliJ 原生支持 Maven；请确认已启用 Lombok 插件与注解处理。

* IntelliJ 下载：[https://www.jetbrains.com/idea/](https://www.jetbrains.com/idea/)

详见[贡献指南](CONTRIBUTING.md)与[编译打包与部署](docs/zh/build-deploy.md)文档。

## 文档导航

- [工具介绍](docs/zh/overview.md)：功能清单、数据库清单、模块结构
- [编译打包与部署](docs/zh/build-deploy.md)：环境要求、编译、安装部署、启动与访问
- [使用教程](docs/zh/usage.md)：向导式使用说明（编写中）
- [异步数据任务（DataTask）指南](docs/zh/data-task.md)：端到端上手、投递扩展开发、worker 配置与故障排查
- [一键安装（docker-compose）](build-docker/install/README.md)
- [English Docs](docs/en/overview.md)

## 参与与安全

- 欢迎贡献：[贡献指南](CONTRIBUTING.md)；使用问题请在 issue 中反馈。
- 对外部署前请阅读 [SECURITY.md](SECURITY.md)；本项目基于 [BSD 3-Clause](LICENSE) 开源，第三方组件见 [NOTICE](NOTICE)。