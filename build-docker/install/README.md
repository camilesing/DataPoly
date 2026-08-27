# DataPoly 基于 docker-compose 的一键部署

**要求**: 已安装 Docker（含 docker compose 插件）的 Linux 系统。

> 部署前请先阅读仓库根目录的 [SECURITY.md](../../SECURITY.md)：默认口令仅为演示用途，
> 生产环境必须通过环境变量覆盖（见下文"安全相关变量"）。

## 一、准备镜像

compose 默认使用 Docker Hub 上的 `camilesing/datapoly-{manager,executor,gateway}` 镜像。
如镜像不可用或你需要自行构建，可在项目根目录执行：

```
sh build.sh                          # 生成 datapoly-dist/target/datapoly-release-x.x.x.tar.gz
sh build-docker/build_and_push_image.sh   # 或按脚本内的 REGISTRY/命名空间自行调整后构建
```

## 二、一键启动

```
cd build-docker/install
docker compose up -d
```

compose 会启动 5 个容器（专用 bridge 网络 `datapoly-net`，仅 gateway 对外发布 8091 端口）：

| 容器 | 说明 | 地址 |
|---|---|---|
| datapoly_mysqldb | 元数据库 MySQL 5.7 | 172.28.0.10（仅网络内） |
| datapoly_manager | 管理节点 | 172.28.0.20（仅网络内） |
| datapoly_executor | 执行节点 | 172.28.0.30（仅网络内） |
| datapoly_gateway | 网关节点（唯一入口） | 172.28.0.40，宿主机 8091 |

启动完成后访问 `http://<主机IP>:8091`（经 gateway 代理进入管理页面），
登录账号 `admin`，默认口令 `123456`（出厂演示口令，请立即修改）。

## 三、安全相关变量

| 变量 | 默认 | 说明 |
|---|---|---|
| `MYSQL_ROOT_PASSWORD` / `MYSQL_PASSWORD` | 123456 | 元数据库口令（演示默认，必须覆盖） |
| `DATAPOLY_ADMIN_PASSWORD` | 空 | 覆盖种子 admin 口令 |
| `DATAPOLY_GATEWAY_TOKEN` | UNSET | gateway→executor 共享密钥，设置后 executor 侧同值配置 |

## 四、常用操作命令

```
docker compose up -d        # 创建并启动
docker compose down         # 销毁（注意：/data/mysql 数据卷会保留）
docker compose stop         # 停止
docker compose start        # 启动
docker compose restart      # 重启
docker compose logs -f <服务名>   # 查看日志
```
