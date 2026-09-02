# 编译打包与部署

语言: [简体中文](build-deploy.md) [English](../en/build-deploy.md)

本工具纯Java语言开发，依赖全部来自于开源项目。DataPoly项目采用Maven构建。

## 1、编译打包

- 环境要求:

  **JDK**:>=1.8 （建议用JDK 1.8）

  **maven**:>=3.6

  **Docker**（或本机 Node 14）：`build.sh` / `docker-maven-build.sh` 会用 Docker 构建内置管理端 UI
  （`datapoly-manager-ui`，Vue 2 + webpack 3 工程，仅 Node 14 可构建，故经 `node:14-alpine` 容器构建）。
  UI 产物不随仓库提交，打包前由脚本自动生成。

> Maven 仓库默认在国外， 国内使用难免很慢，可以更换为阿里云的仓库。
>
> 参考教程： [配置阿里云的仓库教程](https://www.runoob.com/maven/maven-repositories.html)

- 编译命令:

**(1) windows下：**

```
 双击build.cmd脚本文件即可编译打包
```

**(2) Linux/MacOS下：**

```
git clone https://github.com/camilesing/datapoly.git datapoly
cd datapoly/
sh ./build.sh
```

**(3) Docker下:**

```
git clone https://github.com/camilesing/datapoly.git datapoly
cd datapoly/
sh ./docker-maven-build.sh
```

> 打包前会先生成内置 UI：`build.sh` 与 `docker-maven-build.sh` 先执行 `build-ui.sh`，用容器内的 Node 14
> 构建 `datapoly-manager-ui` 并把 `dist/index.html` + `dist/static/` 同步进
> `datapoly-manager/src/main/resources/`（UI 产物不入库）。直接执行 `mvn package` 打出的 jar **不含管理界面**。
> 手工构建 UI 的流程见 [CONTRIBUTING.md](../../CONTRIBUTING.md)。

## 2、安装部署

(1) 当编译打包完成后，会在datapoly/target/目录下生成datapoly-release-x.x.x.tar.gz的打包文件，将文件拷贝到已安装JRE的部署机器上解压即可。

(2) 基于docker-compose提供linux联网环境下的一键安装，x86环境可直接使用（arm下需自行制作docker镜像）。

文档详见: [build-docker/install/README.md](../../build-docker/install)

(3) 物理机方式部署

- 步骤1：准备好一个MySQL5.7+或PostgreSQL11+的数据库

> 当使用MySQL数据库时，config.ini里的DB_TYPE配置mysql,并需要配置 MYSQLDB_ 前缀的参数;
>
> 当使用PostgreSQL数据库时，config.ini里的DB_TYPE配置postgres,并需要配置 PGDB_ 前缀的参数

- 步骤2：修改datapoly-release-x.x.x/conf/config.ini配置文件

```
# manager节点的host地址，如果gateway与executor节点
# 与manager不在同一台机器时需要配置manager节点的IP地址
MANAGER_HOST=localhost


# manager的端口号
MANAGER_PORT=8090

# executor的端口号
EXECUTOR_PORT=8092

# gateway的端口号
GATEWAY_PORT=8091


# 数据库类型:mysql或postgres
DB_TYPE=mysql

# mysql的host地址
MYSQLDB_HOST=192.168.1.100
# mysql的端口号
MYSQLDB_PORT=3306
# mysql的库名
MYSQLDB_NAME=datapoly
# mysql的账号
MYSQLDB_USERNAME=root
# mysql的密码
MYSQLDB_PASSWORD=123456

# pgsql的host地址
PGDB_HOST=192.168.1.100
# pgsql的端口号
PGDB_PORT=5432
# pgsql的库名
PGDB_NAME=datapoly
# pgsql的账号
PGDB_USERNAME=postgres
# pgsql的密码
PGDB_PASSWORD=123456


# JSON序列化时区设置
JSON_TIMEZONE=Asia/Shanghai

# 数据源账号密码是否加密存储
DATAPOLY_DS_ENCRYPT=false

# 外部配置化网关/管理地址，默认为空
# DATAPOLY_MANAGER_URL=http://www.example.com:8090
# DATAPOLY_GATEWAY_URL=http://www.example.com:8091
```

> datapoly的缓存支持使用分布式的hazelcast或者redis，在conf/{manager,gateway,executor}/目录下的application.yml配
> 置文件中，可通过如下调整配置实用redis缓存，默认为使用hazelcast缓存(注意:manager/gateway/executor三者的缓存配置要一致)。

```
datapoly:
  cache:
    hazelcast:
      # 是否开启使用hazelcast缓存
      enabled: false
    redis:
      # 是否开启使用redis缓存，开启时需要配置下面对应的redis信息
      enabled: true
      # 哨兵模式下不用配置host
      host: 127.0.0.1
      # 哨兵模式下不用配置port
      port: 6379
      password: 123456
      database: 0
      pool:
        min-idle: 1
        max-idle: 8
        max-active: 8
        max-wait: -1
        time-between-eviction-runs: -1
      # 非哨兵模式下删除掉sentinel整个节点
      sentinel:
        # 哨兵模式下需要配置master
        master: mymaster
        # 哨兵模式下需要配置nodes
        nodes: 127.0.0.1:26379,127.0.0.1:26380,127.0.0.1:26381
```

- 步骤3：如果为多主机节点部署，需要将datapoly-release-x.x.x分发到其他主机节点上；如果为单机（单节点）部署可直接忽略本步骤。

- 步骤4：启动服务

> windows下，需按照如下顺序双击脚本启动对应的服务

启动manager服务：bin/manager_startup.cmd

启动executor服务：bin/executor_startup.cmd

启动gateway服务：bin/gateway_startup.cmd

> linux/macos下，需按照如下顺序执行脚本启动对应的服务

启动manager服务：sh bin/datapolyctl.sh start manager

启动executor服务：sh bin/datapolyctl.sh start executor

启动gateway服务：sh bin/datapolyctl.sh start gateway

## 3、系统访问

启动完成后,通过http://<MANAGER_HOST>:<MANAGER_PORT> 地址即可访问。

登陆账号：```admin```  登陆密码：```123456```

> 以上为出厂演示口令，首次登录后请立即修改admin密码；对外部署前请务必阅读 [SECURITY.md](../../SECURITY.md)。