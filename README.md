# DataPoly
Language: [简体中文](README.zh.md) [English](README.md)

[![CI](https://github.com/camilesing/datapoly/actions/workflows/ci.yml/badge.svg)](https://github.com/camilesing/datapoly/actions/workflows/ci.yml)
[![License: BSD-3-Clause](https://img.shields.io/badge/License-BSD_3--Clause-blue.svg)](LICENSE)

> A convenient tool that transforms SQL operations into RESTful APIs

DataPoly is an open-source SQL/DSL data access middleware: pick a data source, configure SQL or scripts, and set a
path to create RESTful APIs — no backend code required. It supports 20+ databases including mainstream Chinese
domestic ones, with MyBatis dynamic SQL, Groovy scripting, token authentication, Sentinel flow control,
Hazelcast/Redis caching, online API docs, and LLM MCP services.

## Documentation

- [Overview](docs/en/overview.md): features, supported databases, module structure
- [Build & Deployment](docs/en/build-deploy.md): requirements, build, deployment, startup and access
- [Usage](docs/en/usage.md): guided workflows (in preparation)
- [Async Data Tasks (DataTask) guide](docs/en/data-task.md): end-to-end walkthrough, sink extension authoring, worker configuration and troubleshooting
- [One-click install (docker-compose)](build-docker/install/README.md)
- [中文文档](docs/zh/overview.md)

## Contribute & Security

- Contributions welcome: see [CONTRIBUTING.md](CONTRIBUTING.md); please report bugs in issues.
- Before production deployment, read [SECURITY.md](SECURITY.md). Licensed under [BSD 3-Clause](LICENSE); third-party
  notices in [NOTICE](NOTICE).