# DataPoly
语言: [简体中文](README.zh.md) [English](README.md)

[![CI](https://github.com/camilesing/datapoly/actions/workflows/ci.yml/badge.svg)](https://github.com/camilesing/datapoly/actions/workflows/ci.yml)
[![License: BSD-3-Clause](https://img.shields.io/badge/License-BSD_3--Clause-blue.svg)](LICENSE)

> 将 SQL 操作转化为 RESTful API 的便捷工具

DataPoly 是一款开源的 SQL/DSL 数据访问中间件：只需选择数据源、配置 SQL 或脚本、设置路由，即可快速生成 RESTful API，无需编写后端代码。支持 20+ 种常见数据库及国产主流库，具备 MyBatis 动态 SQL、Groovy 脚本、Token 认证、Sentinel 流控、Hazelcast/Redis 缓存、在线接口文档、大模型 MCP 服务等能力。

## 文档导航

- [工具介绍](docs/zh/overview.md)：功能清单、数据库清单、模块结构
- [编译打包与部署](docs/zh/build-deploy.md)：环境要求、编译、安装部署、启动与访问
- [使用教程](docs/zh/usage.md)：向导式使用说明（编写中）
- [一键安装（docker-compose）](build-docker/install/README.md)
- [English Docs](docs/en/overview.md)

## 参与与安全

- 欢迎贡献：[贡献指南](CONTRIBUTING.md)；使用问题请在 issue 中反馈。
- 对外部署前请阅读 [SECURITY.md](SECURITY.md)；本项目基于 [BSD 3-Clause](LICENSE) 开源，第三方组件见 [NOTICE](NOTICE)。