# DataPoly
Language: [简体中文](README.zh.md) [English](README.md)

[![CI](https://github.com/camilesing/datapoly/actions/workflows/ci.yml/badge.svg)](https://github.com/camilesing/datapoly/actions/workflows/ci.yml)
[![License: BSD-3-Clause](https://img.shields.io/badge/License-BSD_3--Clause-blue.svg)](LICENSE)

> A convenient tool that transforms SQL operations into RESTful APIs

DataPoly is an open-source SQL/DSL data access middleware: pick a data source, configure SQL or scripts, and set a
path to create RESTful APIs — no backend code required. It supports 20+ databases including mainstream Chinese
domestic ones, with MyBatis dynamic SQL, Groovy scripting, token authentication, Sentinel flow control,
Hazelcast/Redis caching, online API docs, and LLM MCP services.

## Building DataPoly from Source

Prerequisites for building DataPoly:

* Unix-like environment (we use Linux, Mac OS X; Windows users can run `build.cmd` directly)
* Git
* Maven (we require version 3.6 or above)
* Java (version 25, LTS)
* Docker (only used to build the management UI — no local Node.js required)

### Basic Build Instructions

First, clone the repository:

```
git clone https://github.com/camilesing/datapoly.git
cd datapoly
```

Then, choose one of the following commands based on your environment:

**For Linux / Mac OS X (Standard Release)**

```
sh ./build.sh
# if you want debug something
sh ./build.sh debug
```


**For a Containerized Maven Build (Docker)**

```
sh ./docker-maven-build.sh
```

**For Windows**

```
build.cmd
```

The build process may take several minutes, and the first build downloads all Maven dependencies.
When it completes, the release package is generated at `target/datapoly-release-<version>.tar.gz`.

### Notes

* Make sure your `JAVA_HOME` environment variable points to a JDK 25 installation — the build produces Java 25 bytecode
* `build.sh` (and `docker-maven-build.sh`) first assemble optional local extensions (`build-extension.sh`, a no-op unless the `datapoly-extension/` directory exists) and build the management UI inside a `node:24-alpine` container (`build-ui.sh`), then run `mvn clean package -DskipTests`
* A bare `mvn package` does not build the UI — the resulting jar contains no management UI
* Passing `debug` to `build.sh` / `docker-maven-build.sh` produces a devtools-enabled debug UI build, for local development only
* To run the full test suite (the same command CI uses): `mvn test -pl datapoly-test -am` — all tests are centralized in the `datapoly-test` module

## Developing DataPoly

We recommend IntelliJ IDEA for developing the DataPoly codebase.

Minimal requirements for an IDE are:
* Support for Java 25 and Lombok annotation processing
* Support for Maven

### IntelliJ IDEA

The IntelliJ IDE supports Maven out of the box; make sure the Lombok plugin is enabled and annotation processing is turned on.

* IntelliJ download: [https://www.jetbrains.com/idea/](https://www.jetbrains.com/idea/)

Check out our [CONTRIBUTING.md](CONTRIBUTING.md) guide and the [Build & Deployment](docs/en/build-deploy.md) documentation for detailed instructions.

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