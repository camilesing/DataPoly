# Build & Deployment

Language: [简体中文](../zh/build-deploy.md) [English](build-deploy.md)

This tool is developed in pure Java, with all dependencies from open-source projects. DataPoly uses Maven for building.

## 1. Build

- Requirements:

  **JDK**: >=1.8 (JDK 1.8 recommended)

  **Maven**: >=3.6

  **Docker** (or a local Node 14): `build.sh` / `docker-maven-build.sh` use Docker to build the built-in UI
  (`datapoly-manager-ui`, a Vue 2 + webpack 3 project that only builds on Node 14, hence the `node:14-alpine`
  container). UI assets are build artifacts and are not committed to git.

> The Maven repository is hosted overseas by default, which can be slow in China. You can switch to the Alibaba Cloud
> mirror.
>
> Tutorial: [Configure Alibaba Cloud Maven Mirror](https://www.runoob.com/maven/maven-repositories.html)

- Build commands:

**(1) On Windows:**

```
Double-click the build.cmd script to build and package
```

**(2) On Linux/macOS:**

```
git clone https://github.com/camilesing/datapoly.git datapoly
cd datapoly/
sh ./build.sh
```

**(3) With Docker:**

```
git clone https://github.com/camilesing/datapoly.git datapoly
cd datapoly/
sh ./docker-maven-build.sh
```

> The built-in UI is generated before packaging: `build.sh` and `docker-maven-build.sh` run `build-ui.sh`
> first, which builds `datapoly-manager-ui` (Node 14 inside a container) and syncs `dist/index.html` +
> `dist/static/` into `datapoly-manager/src/main/resources/`. Running `mvn package` directly produces a jar
> **without** the management UI. See [`CONTRIBUTING.md`](../../CONTRIBUTING.md) for the manual UI build flow.

## 2. Installation & Deployment

(1) After building, a packaged file named `datapoly-release-x.x.x.tar.gz` will be generated in the `datapoly/target/`
directory. Copy it to a machine with JRE installed and extract it.

(2) A docker-compose based one-click installation is available for networked Linux environments (x86; for ARM you need
to build the images yourself). See: [build-docker/install/README.md](../../build-docker/install)

(3) Bare-metal deployment:

- Step 1: Prepare a MySQL 5.7+ or PostgreSQL 11+ database

> When using MySQL, set `DB_TYPE` to `mysql` in `config.ini` and configure the `MYSQLDB_` prefixed parameters;
>
> When using PostgreSQL, set `DB_TYPE` to `postgres` in `config.ini` and configure the `PGDB_` prefixed parameters.

- Step 2: Modify the `datapoly-release-x.x.x/conf/config.ini` configuration file

```
# Host address of the manager node. If gateway and executor nodes
# are not on the same machine as manager, configure the manager IP here.
MANAGER_HOST=localhost


# Manager port
MANAGER_PORT=8090

# Executor port
EXECUTOR_PORT=8092

# Gateway port
GATEWAY_PORT=8091


# Database type: mysql or postgres
DB_TYPE=mysql

# MySQL host address
MYSQLDB_HOST=192.168.1.100
# MySQL port
MYSQLDB_PORT=3306
# MySQL database name
MYSQLDB_NAME=datapoly
# MySQL username
MYSQLDB_USERNAME=root
# MySQL password
MYSQLDB_PASSWORD=123456

# PostgreSQL host address
PGDB_HOST=192.168.1.100
# PostgreSQL port
PGDB_PORT=5432
# PostgreSQL database name
PGDB_NAME=datapoly
# PostgreSQL username
PGDB_USERNAME=postgres
# PostgreSQL password
PGDB_PASSWORD=123456


# JSON serialization timezone
JSON_TIMEZONE=Asia/Shanghai

# Whether to encrypt datasource credentials at rest
DATAPOLY_DS_ENCRYPT=false

# Externally configured gateway/manager addresses, empty by default
# DATAPOLY_MANAGER_URL=http://www.example.com:8090
# DATAPOLY_GATEWAY_URL=http://www.example.com:8091
```

> DataPoly's cache supports distributed Hazelcast or Redis. In the `application.yml` configuration file under
`conf/{manager,gateway,executor}/`, you can configure Redis caching as follows. The default is Hazelcast (note: the
> cache configuration must be consistent across manager, gateway, and executor).

```
datapoly:
  cache:
    hazelcast:
      # Whether to enable Hazelcast caching
      enabled: false
    redis:
      # Whether to enable Redis caching. When enabled, configure the Redis info below
      enabled: true
      # Not needed in sentinel mode
      host: 127.0.0.1
      # Not needed in sentinel mode
      port: 6379
      password: 123456
      database: 0
      pool:
        min-idle: 1
        max-idle: 8
        max-active: 8
        max-wait: -1
        time-between-eviction-runs: -1
      # Remove the entire sentinel node in non-sentinel mode
      sentinel:
        # Configure master in sentinel mode
        master: mymaster
        # Configure nodes in sentinel mode
        nodes: 127.0.0.1:26379,127.0.0.1:26380,127.0.0.1:26381
```

- Step 3: For multi-node deployment, distribute `datapoly-release-x.x.x` to other host nodes. For single-node
  deployment, skip this step.

- Step 4: Start the services

> On Windows, start the services in the following order by double-clicking the scripts:

Start the manager service: `bin/manager_startup.cmd`

Start the executor service: `bin/executor_startup.cmd`

Start the gateway service: `bin/gateway_startup.cmd`

> On Linux/macOS, start the services in the following order:

Start the manager service: `sh bin/datapolyctl.sh start manager`

Start the executor service: `sh bin/datapolyctl.sh start executor`

Start the gateway service: `sh bin/datapolyctl.sh start gateway`

## 3. Access the System

After startup, access the system via `http://<MANAGER_HOST>:<MANAGER_PORT>`.

Login username: ```admin```  Login password: ```123456```

> These are factory demo credentials. Change the admin password after first login, and see
> [SECURITY.md](../../SECURITY.md) before exposing the service to a network.