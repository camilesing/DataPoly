// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.common.enums;

import com.cs.common.dto.*;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;

import java.sql.*;
import java.util.*;
import java.util.function.BiFunction;

@Getter
public enum ProductTypeEnum {

    /**
     * MySQL database type
     */
    MYSQL(
            ProductContext.builder()
                    .id(1)
                    .quote("`")
                    .name("mysql")
                    .driver("com.mysql.jdbc.Driver")
                    .defaultPort(3306)
                    .testSql("/* ping */ SELECT 1")
                    .urlPrefix("jdbc:mysql://")
                    .tplUrls(new String[]{"jdbc:mysql://{host}[:{port}]/[{database}][\\?{params}]"})
                    .urlSample(
                            "jdbc:mysql://172.17.2.10:3306/mysql?useUnicode=true&characterEncoding=utf-8&useSSL=false&zeroDateTimeBehavior=convertToNull&serverTimezone=Asia/Shanghai&tinyInt1isBit=false&rewriteBatchedStatements=true&useCompression=true")
                    .sqlSchemaList("SELECT `SCHEMA_NAME` FROM `information_schema`.`SCHEMATA`")
                    .adapter(database -> Pair.of(database, null))
                    .pageSql("%s LIMIT ? OFFSET ? ")
                    .pageConsumer(
                            (page, size, parameters) -> {
                                parameters.add(size);
                                parameters.add((page - 1) * size);
                            }
                    ).build()),
    /**
     * MariaDB database type
     */
    MARIADB(
            ProductContext.builder()
                    .id(2)
                    .quote("`")
                    .name("mariadb")
                    .driver("org.mariadb.jdbc.Driver")
                    .defaultPort(3306)
                    .testSql("/* ping */ SELECT 1")
                    .urlPrefix("jdbc:mariadb://")
                    .tplUrls(new String[]{"jdbc:mariadb://{host}[:{port}]/[{database}][\\?{params}]"})
                    .urlSample(
                            "jdbc:mariadb://172.17.2.10:3306/mysql?useUnicode=true&characterEncoding=utf-8&useSSL=false&zeroDateTimeBehavior=convertToNull&serverTimezone=Asia/Shanghai&tinyInt1isBit=false&rewriteBatchedStatements=true&useCompression=true")
                    .sqlSchemaList("SELECT `SCHEMA_NAME` FROM `information_schema`.`SCHEMATA`")
                    .adapter(database -> Pair.of(database, null))
                    .pageSql("%s LIMIT ? OFFSET ? ")
                    .pageConsumer(
                            (page, size, parameters) -> {
                                parameters.add(size);
                                parameters.add((page - 1) * size);
                            }
                    ).build()),
    /**
     * Oracle database type
     */
    ORACLE(
            ProductContext.builder()
                    .id(3)
                    .quote("\"")
                    .name("oracle")
                    .driver("oracle.jdbc.driver.OracleDriver")
                    .defaultPort(1521)
                    .testSql("SELECT 'Hello' from DUAL")
                    .urlPrefix("jdbc:oracle:thin:@")
                    .tplUrls(new String[]{"jdbc:oracle:thin:@{host}:{port}:{database}",
                            "jdbc:oracle:thin:@{host}:{port}/{database}",
                            "jdbc:oracle:thin:@//{host}[:{port}]/{database}"})
                    .urlSample("jdbc:oracle:thin:@172.17.2.10:1521:ORCL")
                    .sqlSchemaList("SELECT USERNAME FROM SYS.ALL_USERS")
                    .adapter(database -> Pair.of(null, database))
                    .pageSql(
                            "SELECT * FROM ( SELECT ALIAS.*, ROWNUM ROW_ID FROM ( %s ) ALIAS WHERE ROWNUM <= ? ) WHERE ROW_ID > ?")
                    .pageConsumer(
                            (page, size, parameters) -> {
                                parameters.add(page * size);
                                parameters.add((page - 1) * size);
                            }
                    ).build()),
    /**
     * Microsoft SQL Server database type (>=2005)
     */
    SQLSERVER(
            ProductContext.builder()
                    .id(4)
                    .quote("\"")
                    .name("sqlserver")
                    .driver("com.microsoft.sqlserver.jdbc.SQLServerDriver")
                    .defaultPort(1433)
                    .testSql("SELECT 1+2 as a")
                    .urlPrefix("jdbc:sqlserver://")
                    .tplUrls(new String[]{"jdbc:sqlserver://{host}[:{port}][;DatabaseName={database}][;{params}]"})
                    .urlSample("jdbc:sqlserver://172.17.2.10:1433;DatabaseName=master")
                    .sqlSchemaList("select schema_name from INFORMATION_SCHEMA.SCHEMATA")
                    .hasCatalogAndSchema(true)
                    .adapter(database -> Pair.of(null, database))
                    .pageSql(
                            "SELECT TOP <LIMIT> * FROM ( SELECT ROW_NUMBER() OVER(ORDER BY (SELECT 0)) AS ALIAS_ROW_NUM,PAGE_ALAS1.* FROM (%s) PAGE_ALAS1 ) PAGE_ALAS2 WHERE ALIAS_ROW_NUM > ? ")
                    .pageConsumer(
                            (page, size, parameters) -> {
                                parameters.add((page - 1) * size);
                            }
                    ).build()),

    /**
     * PostgreSQL database type
     */
    POSTGRESQL(
            ProductContext.builder()
                    .id(5)
                    .quote("\"")
                    .name("postgresql")
                    .driver("org.postgresql.Driver")
                    .defaultPort(5432)
                    .testSql("SELECT 1")
                    .urlPrefix("jdbc:postgresql://")
                    .tplUrls(new String[]{"jdbc:postgresql://{host}[:{port}]/[{database}][\\?{params}]"})
                    .urlSample("jdbc:postgresql://172.17.2.10:5432/test")
                    .sqlSchemaList("SELECT schema_name FROM information_schema.schemata ")
                    .adapter(database -> Pair.of(null, database))
                    .pageSql("%s LIMIT ? OFFSET ? ")
                    .pageConsumer(
                            (page, size, parameters) -> {
                                parameters.add(size);
                                parameters.add((page - 1) * size);
                            }
                    ).build()),

    /**
     * DB2 database type
     */
    DB2(
            ProductContext.builder()
                    .id(6)
                    .quote("\"")
                    .name("db2")
                    .driver("com.ibm.db2.jcc.DB2Driver")
                    .defaultPort(50000)
                    .testSql("SELECT 1 FROM SYSIBM.SYSDUMMY1")
                    .urlPrefix("jdbc:db2://")
                    .tplUrls(new String[]{"jdbc:db2://{host}:{port}/{database}[:{params}]"})
                    .urlSample(
                            "jdbc:db2://172.17.2.10:50000/testdb:driverType=4;fullyMaterializeLobData=true;fullyMaterializeInputStreams=true;progressiveStreaming=2;progresssiveLocators=2;")
                    .sqlSchemaList("SELECT SCHEMANAME FROM SYSCAT.SCHEMATA ")
                    .adapter(database -> Pair.of(null, database))
                    .pageSql(
                            "SELECT * FROM (SELECT TMP_PAGE.*,ROWNUMBER() OVER() AS ROW_ID FROM ( %s ) AS TMP_PAGE) ALIAS WHERE ROW_ID BETWEEN ? AND ?")
                    .pageConsumer(
                            (page, size, parameters) -> {
                                parameters.add((page - 1) * size);
                                parameters.add(page * size);
                            }
                    ).build()),

    /**
     * Dameng (DM) database type (Chinese domestic)
     */
    DM(
            ProductContext.builder()
                    .id(7)
                    .quote("\"")
                    .name("dm")
                    .driver("dm.jdbc.driver.DmDriver")
                    .defaultPort(5236)
                    .testSql("SELECT 'Hello' from DUAL")
                    .urlPrefix("jdbc:dm://")
                    .tplUrls(new String[]{"jdbc:dm://{host}:{port}[/{database}][\\?{params}]"})
                    .urlSample("jdbc:dm://172.17.2.10:5236")
                    .sqlSchemaList("SELECT DISTINCT object_name FROM ALL_OBJECTS WHERE OBJECT_TYPE = 'SCH'")
                    .adapter(database -> Pair.of(null, database))
                    .pageSql("%s LIMIT ? OFFSET ? ")
                    .pageConsumer(
                            (page, size, parameters) -> {
                                parameters.add(size);
                                parameters.add((page - 1) * size);
                            }
                    ).build()),

    /**
     * Kingbase database type (Chinese domestic)
     */
    KINGBASE(
            ProductContext.builder()
                    .id(8)
                    .quote("\"")
                    .name("kingbase")
                    .driver("com.kingbase8.Driver")
                    .defaultPort(54321)
                    .testSql("SELECT 1")
                    .urlPrefix("jdbc:kingbase8://")
                    .tplUrls(new String[]{"jdbc:kingbase8://{host}[:{port}]/[{database}][\\?{params}]"})
                    .urlSample("jdbc:kingbase8://172.17.2.10:54321/test")
                    .sqlSchemaList("SELECT schema_name FROM information_schema.schemata ")
                    .adapter(database -> Pair.of(null, database))
                    .pageSql("%s LIMIT ? OFFSET ? ")
                    .pageConsumer(
                            (page, size, parameters) -> {
                                parameters.add(size);
                                parameters.add((page - 1) * size);
                            }
                    ).build()),

    /**
     * Oscar database type (Chinese domestic)
     */
    OSCAR(
            ProductContext.builder()
                    .id(9)
                    .quote("\"")
                    .name("oscar")
                    .driver("com.oscar.Driver")
                    .defaultPort(2003)
                    .testSql("SELECT 1")
                    .urlPrefix("jdbc:oscar://")
                    .tplUrls(new String[]{"jdbc:oscar://{host}[:{port}]/[{database}][\\?{params}]"})
                    .urlSample("jdbc:oscar://172.17.2.10:2003/OSCRDB")
                    .sqlSchemaList("SELECT schema_name FROM information_schema.schemata ")
                    .adapter(database -> Pair.of(null, database))
                    .pageSql("%s LIMIT ? OFFSET ? ")
                    .pageConsumer(
                            (page, size, parameters) -> {
                                parameters.add(size);
                                parameters.add((page - 1) * size);
                            }
                    ).build()),

    /**
     * GBase8A database type (Chinese domestic)
     */
    GBASE8A(
            ProductContext.builder()
                    .id(10)
                    .quote("`")
                    .name("gbase8a")
                    .driver("com.gbase.jdbc.Driver")
                    .defaultPort(5258)
                    .testSql("/* ping */ SELECT 1")
                    .urlPrefix("jdbc:gbase://")
                    .tplUrls(new String[]{"jdbc:gbase://{host}[:{port}]/[{database}][\\?{params}]"})
                    .urlSample("jdbc:gbase://172.17.2.10:5258/test")
                    .sqlSchemaList("SELECT schema_name FROM information_schema.schemata ")
                    .adapter(database -> Pair.of(database, null))
                    .pageSql("%s LIMIT ? OFFSET ? ")
                    .pageConsumer(
                            (page, size, parameters) -> {
                                parameters.add(size);
                                parameters.add((page - 1) * size);
                            }
                    ).build()),

    /**
     * Sybase database type
     */
    SYBASE(
            ProductContext.builder()
                    .id(11)
                    .quote("\"")
                    .name("sybase")
                    .driver("com.sybase.jdbc4.jdbc.SybDriver")
                    .defaultPort(5000)
                    .testSql("SELECT 1+2 as a")
                    .urlPrefix("jdbc:sybase:Tds:")
                    .tplUrls(new String[]{"jdbc:sybase:Tds:{host}[:{port}][/{database}][\\?{params}]"})
                    .urlSample("jdbc:sybase:Tds:172.17.2.10:5000/test?charset=cp936")
                    .sqlSchemaList(null)
                    .adapter(database -> Pair.of(null, database))
                    .hasCatalogAndSchema(true)
                    .pageSql("select * , row_num=identity(10) into #delTmpTb from ( \n"
                            + " %s  \n"
                            + ") t  select * from #delTmpTb where row_num > ? and row_num<= ? order by row_num ASC \n"
                            + "DROP TABLE #delTmpTb ")
                    .pageConsumer(
                            (page, size, parameters) -> {
                                parameters.add((page - 1) * size);
                                parameters.add(page * size);
                            }
                    )
                    .resultSetFunc((hasResult, statement) -> {
                        try {
                            while (hasResult || statement.getUpdateCount() != -1) {
                                if (hasResult) {
                                    return true;
                                }
                                hasResult = statement.getMoreResults();
                            }
                            return false;
                        } catch (SQLException e) {
                            throw new RuntimeException(e);
                        }
                    })
                    .build()),

    /**
     * Hive database type
     */
    HIVE(
            ProductContext.builder()
                    .id(12)
                    .quote("`")
                    .name("hive")
                    .driver("org.apache.hive.jdbc.HiveDriver")
                    .defaultPort(10000)
                    .testSql("SELECT 1")
                    .urlPrefix("jdbc:hive2://")
                    .tplUrls(new String[]{"jdbc:hive2://{host}[:{port}]/[{database}][;{params}]"})
                    .urlSample("jdbc:hive2://172.17.2.12:10000/default")
                    .sqlSchemaList("SHOW DATABASES")
                    .adapter(database -> Pair.of(null, database))
                    .pageSql("%s LIMIT ? OFFSET ? ")
                    .pageConsumer(
                            (page, size, parameters) -> {
                                parameters.add(size);
                                parameters.add((page - 1) * size);
                            }
                    )
                    .executeBeforeQuery(
                            connection -> {
                                try (Statement statement = connection.createStatement()) {
                                    statement.execute("set hive.resultset.use.unique.column.names=false");
                                } catch (SQLException e) {
                                    throw new RuntimeException(e);
                                }
                            }
                    ).build()),

    /**
     * Impala database type
     */
    IMPALA(
            ProductContext.builder()
                    .id(13)
                    .quote("`")
                    .name("impala")
                    .driver("com.cloudera.impala.jdbc.Driver")
                    .defaultPort(10000)
                    .testSql("SELECT 1")
                    .urlPrefix("jdbc:impala://")
                    .tplUrls(new String[]{"jdbc:impala://{host}[:{port}]/[{database}][;{params}]"})
                    .urlSample("jdbc:impala://172.17.2.12:10000/default")
                    .sqlSchemaList("SHOW DATABASES")
                    .adapter(database -> Pair.of(null, database))
                    .pageSql("%s LIMIT ? OFFSET ? ")
                    .pageConsumer(
                            (page, size, parameters) -> {
                                parameters.add(size);
                                parameters.add((page - 1) * size);
                            }
                    )
                    .executeBeforeQuery(
                            connection -> {
                                try (Statement statement = connection.createStatement()) {
                                    statement.execute("set hive.resultset.use.unique.column.names=false");
                                } catch (SQLException e) {
                                    throw new RuntimeException(e);
                                }
                            }
                    ).build()),

    /**
     * Inceptor database type
     */
    INCEPTOR(
            ProductContext.builder()
                    .id(14)
                    .quote("`")
                    .name("inceptor")
                    .driver("org.apache.hive.jdbc.HiveDriver")
                    .defaultPort(10000)
                    .testSql(null)
                    .urlPrefix("jdbc:hive2://")
                    .tplUrls(new String[]{"jdbc:hive2://{host}[:{port}]/[{database}][;{params}]"})
                    .urlSample("jdbc:hive2://172.17.2.12:10000/default;escape=false")
                    .sqlSchemaList("SHOW DATABASES")
                    .adapter(database -> Pair.of(null, database))
                    .pageSql("%s LIMIT ? OFFSET ? ")
                    .pageConsumer(
                            (page, size, parameters) -> {
                                parameters.add(size);
                                parameters.add((page - 1) * size);
                            }
                    )
                    .executeBeforeQuery(
                            connection -> {
                                try (Statement statement = connection.createStatement()) {
                                    statement.execute("set hive.resultset.use.unique.column.names=false");
                                } catch (SQLException e) {
                                    throw new RuntimeException(e);
                                }
                            }
                    ).build()),

    /**
     * Sqlite v3 database type
     */
    // Reference: https://blog.csdn.net/wank1259162/article/details/104946744
    SQLITE3(
            ProductContext.builder()
                    .id(15)
                    .quote("\"")
                    .name("sqlite3")
                    .driver("org.sqlite.JDBC")
                    .defaultPort(0)
                    .testSql("SELECT 1")
                    .urlPrefix("jdbc:sqlite:")
                    .tplUrls(new String[]{"jdbc:sqlite:{file}", "jdbc:sqlite::resource:{file}"})
                    .urlSample("jdbc:sqlite:/tmp/test.db")
                    .sqlSchemaList(null)
                    .retSchemaList(Collections.singletonList("sqlite_master"))
                    .adapter(database -> Pair.of(null, database))
                    .pageSql("%s LIMIT ? OFFSET ? ")
                    .pageConsumer(
                            (page, size, parameters) -> {
                                parameters.add(size);
                                parameters.add((page - 1) * size);
                            }
                    ).build()),

    /**
     * OpenGauss database type
     */
    OPENGAUSS(
            ProductContext.builder()
                    .id(16)
                    .quote("\"")
                    .name("opengauss")
                    .driver("org.opengauss.Driver")
                    .defaultPort(15432)
                    .testSql("SELECT 1")
                    .urlPrefix("jdbc:opengauss://")
                    .tplUrls(new String[]{"jdbc:opengauss://{host}[:{port}]/[{database}][\\?{params}]"})
                    .urlSample("jdbc:opengauss://172.17.2.10:5432/test")
                    .sqlSchemaList("SELECT schema_name FROM information_schema.schemata ")
                    .adapter(database -> Pair.of(null, database))
                    .pageSql("%s LIMIT ? OFFSET ? ")
                    .pageConsumer(
                            (page, size, parameters) -> {
                                parameters.add(size);
                                parameters.add((page - 1) * size);
                            }
                    ).build()),

    /**
     * ClickHouse database type
     */
    CLICKHOUSE(
            ProductContext.builder()
                    .id(17)
                    .quote("`")
                    .name("clickhouse")
                    .driver("com.clickhouse.jdbc.ClickHouseDriver")
                    .defaultPort(8123)
                    .testSql("SELECT 1")
                    .urlPrefix("jdbc:clickhouse://")
                    .tplUrls(new String[]{"jdbc:clickhouse://{host}[:{port}]/[{database}][\\?{params}]"})
                    .urlSample("jdbc:clickhouse://172.17.2.10:8123/default")
                    .sqlSchemaList("SELECT schema_name FROM information_schema.schemata ")
                    .adapter(database -> Pair.of(null, database))
                    .pageSql("%s LIMIT ? OFFSET ? ")
                    .pageConsumer(
                            (page, size, parameters) -> {
                                parameters.add(size);
                                parameters.add((page - 1) * size);
                            }
                    ).build()),

    /**
     * Doris database type
     */
    DORIS(
            ProductContext.builder()
                    .id(18)
                    .quote("`")
                    .name("doris")
                    .driver("com.mysql.jdbc.Driver")
                    .defaultPort(3306)
                    .testSql("/* ping */ SELECT 1")
                    .urlPrefix("jdbc:mysql://")
                    .tplUrls(new String[]{"jdbc:mysql://{host}[:{port}]/[{database}][\\?{params}]"})
                    .urlSample(
                            "jdbc:mysql://172.17.2.10:3306/test?useUnicode=true&characterEncoding=utf-8&useSSL=false&zeroDateTimeBehavior=convertToNull&serverTimezone=Asia/Shanghai&tinyInt1isBit=false&rewriteBatchedStatements=true&useCompression=true")
                    .sqlSchemaList("SELECT `SCHEMA_NAME` FROM `information_schema`.`SCHEMATA`")
                    .adapter(database -> Pair.of(database, null))
                    .pageSql("%s LIMIT ? OFFSET ? ")
                    .pageConsumer(
                            (page, size, parameters) -> {
                                parameters.add(size);
                                parameters.add((page - 1) * size);
                            }
                    ).build()),

    /**
     * StarRocks database type
     */
    STARROCKS(
            ProductContext.builder()
                    .id(19)
                    .quote("`")
                    .name("starrocks")
                    .driver("com.mysql.jdbc.Driver")
                    .defaultPort(3306)
                    .testSql("/* ping */ SELECT 1")
                    .urlPrefix("jdbc:mysql://")
                    .tplUrls(new String[]{"jdbc:mysql://{host}[:{port}]/[{database}][\\?{params}]"})
                    .urlSample(
                            "jdbc:mysql://172.17.2.10:3306/test?useUnicode=true&characterEncoding=utf-8&useSSL=false&zeroDateTimeBehavior=convertToNull&serverTimezone=Asia/Shanghai&tinyInt1isBit=false&rewriteBatchedStatements=true&useCompression=true")
                    .sqlSchemaList("SELECT `SCHEMA_NAME` FROM `information_schema`.`SCHEMATA`")
                    .adapter(database -> Pair.of(database, null))
                    .pageSql("%s LIMIT ? OFFSET ? ")
                    .pageConsumer(
                            (page, size, parameters) -> {
                                parameters.add(size);
                                parameters.add((page - 1) * size);
                            }
                    ).build()),

    /**
     * OceanBase database type (MySQL dialect)
     */
    OCEANBASE(
            ProductContext.builder()
                    .id(20)
                    .quote("")
                    .name("oceanbase")
                    .driver("com.oceanbase.jdbc.Driver")
                    .defaultPort(2881)
                    .multiDialect(true)
                    .testSql("show global variables where variable_name = 'ob_compatibility_mode'")
                    .urlPrefix("jdbc:oceanbase://")
                    .tplUrls(new String[]{"jdbc:oceanbase://{host}[:{port}]/[{database}][\\?{params}]"})
                    .urlSample("jdbc:oceanbase://127.0.0.1:2881/test")
                    .sqlSchemaList(null)
                    .adapter(null)
                    .pageSql("%s LIMIT ? OFFSET ? ")
                    .pageConsumer(
                            (page, size, parameters) -> {
                                parameters.add(size);
                                parameters.add((page - 1) * size);
                            }
                    ).build()),

    /**
     * TDEngine database type
     */
    TDENGINE(
            ProductContext.builder()
                    .id(21)
                    .quote("`")
                    .name("TDengine")
                    .driver("com.taosdata.jdbc.rs.RestfulDriver")
                    .defaultPort(6041)
                    .testSql("/* ping */ SELECT 1")
                    .urlPrefix("jdbc:TAOS-RS://")
                    .tplUrls(new String[]{"jdbc:TAOS-RS://{host}[:{port}]/[{database}][\\?{params}]"})
                    .urlSample("jdbc:TAOS-RS://172.17.2.10:6041/test")
                    .sqlSchemaList("SELECT name FROM `information_schema`.`ins_databases`")
                    .noViewTables(true)
                    .adapter(database -> Pair.of(database, null))
                    .pageSql("%s LIMIT ? OFFSET ? ")
                    .pageConsumer(
                            (page, size, parameters) -> {
                                parameters.add(size);
                                parameters.add((page - 1) * size);
                            }
                    ).build()),

    /**
     * MongoDB database type
     */
    MONGODB(
            ProductContext.builder()
                    .id(22)
                    .quote("")
                    .name("MongoDB")
                    .driver("com.gitee.jdbc.mongodb.JdbcDriver")
                    .defaultPort(27017)
                    .testSql("use admin;")
                    .urlPrefix("jdbc:mongodb://")
                    .tplUrls(new String[]{"jdbc:mongodb://{host}[:{port}]/[{database}][\\?{params}]"})
                    .urlSample("jdbc:mongodb://172.17.2.12:27017/admin?authSource=admin&authMechanism=SCRAM-SHA-1&expand=false")
                    .sqlSchemaList(null)
                    .noViewTables(true)
                    .adapter(database -> Pair.of(database, null)
                    ).build()),

    /**
     * ElasticSearch database type
     */
    ELASTICSEARCH(
            ProductContext.builder()
                    .id(23)
                    .quote("\"")
                    .name("ElasticSearch")
                    .driver("com.gitee.esdriver.driver.EsDriver")
                    .defaultPort(9200)
                    .testSql("GET /")
                    .urlPrefix("jdbc:esdriver:")
                    .tplUrls(new String[]{"jdbc:esdriver://{host}:{port}[\\?{params}]"})
                    .urlSample("jdbc:esdriver://172.17.2.10:9200")
                    .sqlSchemaList(null)
                    .noViewTables(true)
                    .retSchemaList(Collections.singletonList("elasticsearch"))
                    .adapter(database -> Pair.of(null, database)
                    ).build()),

    /**
     * RESTful HTTP type
     */
    HTTP(
            ProductContext.builder()
                    .id(99)
                    .quote(null)
                    .name("Http")
                    .driver("com.gitee.restfuldriver.driver.RestfulDriver")
                    .defaultPort(80)
                    .testSql("GET /")
                    .urlPrefix("jdbc:restful:")
                    .tplUrls(new String[]{"jdbc:restful://{host}:{port}[\\?{params}]"})
                    .urlSample("jdbc:restful://http://172.17.2.10:8080")
                    .sqlSchemaList(null)
                    .noViewTables(true)
                    .retSchemaList(Collections.singletonList("http"))
                    .adapter(database -> Pair.of(null, database)
                    ).build()),
    ;

    private ProductContext context;

    ProductTypeEnum(ProductContext context) {
        this.context = context;
    }

    public int getId() {
        return this.context.getId();
    }

    public String getName() {
        return this.context.getName();
    }

    public String getDriver() {
        return this.context.getDriver();
    }

    public String getUrlPrefix() {
        return this.context.getUrlPrefix();
    }

    public String[] getUrl() {
        return this.context.getTplUrls();
    }

    public boolean isMultiDialect() {
        return this.context.isMultiDialect();
    }

    public String getTestSql() {
        return this.context.getTestSql();
    }

    public String getSample() {
        return this.context.getUrlSample();
    }

    public String getSql() {
        return this.context.getTestSql();
    }

    public ThreeConsumer getPageConsumer() {
        return this.context.getPageConsumer();
    }

    public boolean hasDatabaseName() {
        return !Arrays.asList(DM, SQLITE3, MYSQL, MARIADB, GBASE8A, MONGODB, ELASTICSEARCH, HTTP).contains(this);
    }

    public boolean isNoViewTables() {
        return this.context.isNoViewTables();
    }

    public boolean hasFilePath() {
        return this == SQLITE3;
    }

    public boolean hasAddress() {
        return this != SQLITE3;
    }

    public boolean offTransactional() {
        return this == SYBASE || this == HIVE || this == INCEPTOR || this == IMPALA;
    }

//  public String quoteName(String name) {
//    return String.format("%s%s%s", context.getQuote(), name, context.getQuote());
//  }
//
//  public String quoteSchemaTableName(String schema, String table) {
//    String quote = context.getQuote();
//    return String.format("%s%s%s.%s%s%s", quote, schema, quote, quote, table, quote);
//  }

    public String getPageSql(String sql, int page, int size) {
        if (StringUtils.isBlank(context.getPageSql())) {
            return sql;
        }
        String pageSql = String.format(context.getPageSql(), sql);
        return pageSql.replace("<LIMIT>", String.valueOf(size));
    }

    public BiFunction<Boolean, Statement, Boolean> getResultSetFunc() {
        return context.getResultSetFunc();
    }

    /**
     * Whether a database type with the given string name exists
     *
     * @param name string name
     * @return boolean
     */
    public static boolean exists(String name) {
        return Arrays.stream(values()).anyMatch(item -> item.name().equalsIgnoreCase(name));
    }

    /**
     * Convert a string name to the enum value
     *
     * @param name string name
     * @return ProductTypeEnum
     */
    public static ProductTypeEnum of(String name) {
        if (!StringUtils.isEmpty(name)) {
            for (ProductTypeEnum type : ProductTypeEnum.values()) {
                if (type.getContext().getName().equalsIgnoreCase(name)) {
                    return type;
                }
            }
        }

        throw new IllegalArgumentException("cannot find enum name: " + name);
    }

}