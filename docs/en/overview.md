# Introduction

Language: [简体中文](../zh/overview.md) [English](overview.md)

DataPoly (abbreviated as DP) is a fully open-source project designed to provide a simple yet powerful way to transform
SQL (or SQL-Like, i.e., DSL) operations into RESTful APIs. It supports multiple databases and allows users to create
APIs by configuring SQL (or DSL) statements without writing complex backend logic. Users only need to select a data
source, input SQL or scripts, and configure a simple path to quickly generate API endpoints.

## 1. Features

DataPoly provides the following features:

- **SQL-driven API Creation**: Generate RESTful APIs by configuring CRUD SQL statements and parameters.
- **Multi-Database Support**: Supports 20+ common databases, including several domestic Chinese databases.
- **MyBatis Syntax Support**: Supports MyBatis dynamic SQL syntax.
- **Groovy Script Support**: Supports Groovy syntax for building complex interface logic.
- **Parameter Type Support**: Supports integer, float, time, date, boolean, string, object, and more.
- **ContentType Support**: Supports application/x-www-form-urlencoded, application/json, and other request formats.
- **Authentication Support**: Provides token-based authentication to secure APIs.
- **Online API Documentation**: Supports auto-generated Swagger and Knife4j online documentation.
- **Cache Configuration Support**: Supports Hazelcast or Redis caching to improve API performance.
- **Flow Control Management**: Supports traffic control via Sentinel to prevent system overload.
- **Unified Alert Integration**: Supports integration with unified alerting systems.
- **RESTful API Forwarding**: Supports HTTP data source RESTful API forwarding via DSL.
- **Document Database Support**: Supports MongoDB, ElasticSearch, and other document databases via DSL.
- **API Version Management**: Supports API version control management.
- **Batch Import/Export**: Supports bulk import and export of APIs.
- **LLM MCP Service**: Supports creating MCP tools with simple configuration.

As a data access middleware in microservice architectures, DataPoly is suitable for the following scenarios:

- **Quickly convert SQL (or DSL) into APIs**
- **Applicable to data platforms, BI tools, low-code platforms, etc.**

## 2. Supported Databases

To date, the supported databases include:

- Oracle
- Microsoft SQL Server (2005+)
- MySQL
- MariaDB
- PostgreSQL/Greenplum
- IBM DB2
- Sybase
- DM (Dameng)
- Kingbase8
- HighGo
- Oscar
- GBase8a
- Apache Hive
- Cloudera Impala
- SQLite3
- OpenGauss
- ClickHouse
- Apache Doris
- StarRocks
- OceanBase
- TDEngine
- MongoDB
- ElasticSearch
- Http (RESTful)

## 3. Module Structure

![STRUCTURE.PNG](../images/STRUCTURE.PNG)

```
└── datapoly
    ├── datapoly-common           // Common definitions module
    ├── datapoly-mcp              // MCP protocol module
    ├── datapoly-template         // SQL content template module
    ├── datapoly-cache            // Executor cache module
    ├── datapoly-persistence      // Database persistence module
    ├── datapoly-core             // Core API implementation module
    ├── datapoly-gateway          // Gateway node
    ├── datapoly-executor         // Executor node
    ├── datapoly-manager          // Manager node
    ├── datapoly-manager-ui       // Web UI
    ├── datapoly-dist             // Packaging module
```

## 4. Planned Features

- **API Details**: Support for detailed API definitions, data sources, access analytics, and more.
- **Enhanced Syntax Hints**: Build upon existing database/table name hints with database metadata-driven intelligent
  suggestions for improved user experience.