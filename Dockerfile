# Use of this source code is governed by a BSD-style license

# ============================================================================
# 单 Dockerfile 全自动构建(多阶段):UI(node:23) → Maven 打包(temurin 8) → 运行时(temurin 8 JRE)
# 产物与 build.sh / docker-maven-build.sh / build_and_push_image.sh 一致,两条链路并行可用。
#
# 用法(在仓库根目录):
#   docker build -t datapoly:1.9.0 .
#   docker build -t datapoly:1.9.0 --build-arg MAVEN_ARGS=-Dmaven.test.skip=true .  # CI 跳过测试
#
# 产出为通用镜像(含发行包完整 conf/lib/drivers),K8s 中三服务共用同一镜像,以 args 区分:
#   manager  → args: ["manager"]  (CMD 默认值)
#   executor → args: ["executor"]
#   gateway  → args: ["gateway"]
# 运行时依赖(DB 连接、MANAGER_HOST/MANAGER_PORT、admin 口令、网关 token 等)仍由部署环境
# 经环境变量注入,镜像内不打包任何凭据(见 SECURITY.md)。
#
# 依赖 BuildKit(RUN --mount=type=cache,Docker ≥ 23 / compose v2 默认开启);首次构建经
# npmmirror / 阿里云镜像下载 npm 与 Maven 依赖,之后由层缓存与 cache mount 加速。
# ============================================================================

# ---- Stage 1: 构建内置管理端 UI(Vue2 + webpack3,与 build-ui.sh 同为 node:23) ----
FROM node:23-alpine AS ui
WORKDIR /opt/app
# 拷贝整个仓库(与 build-ui.sh 挂载仓库根一致):webpack 的 @extension 别名会探测
# 仓库根下 datapoly-extension/front/src,缺省回退 stub;仅生产构建,debug 变体仍走 build-ui.sh
COPY . /opt/app
WORKDIR /opt/app/datapoly-manager-ui
RUN npm config set registry https://registry.npmmirror.com \
 && npm install --no-audit --no-fund --no-package-lock --legacy-peer-deps \
 && npm run build

# ---- Stage 2: Maven 打包(与 docker-maven-build.sh 同为 temurin 8,支持 arm64) ----
FROM maven:3.9-eclipse-temurin-8 AS build
# CI 可传 MAVEN_ARGS=-Dmaven.test.skip=true 加速;默认与 docker-maven-build.sh 一致(跑测试)
ARG MAVEN_ARGS=""
WORKDIR /src
COPY . /src
# UI 产物注入 manager resources(.dockerignore 已排除本地旧产物,等效 build-ui.sh 的先清后拷)
COPY --from=ui /opt/app/datapoly-manager-ui/dist/static /src/datapoly-manager/src/main/resources/static
COPY --from=ui /opt/app/datapoly-manager-ui/dist/index.html /src/datapoly-manager/src/main/resources/index.html
# 复用 docker-maven-settings.xml:localRepository=/opt/maven/localRepository 恰为 cache mount
# 落点,central 走阿里云镜像;cache 跨构建保留依赖,免重复下载
RUN --mount=type=cache,target=/opt/maven/localRepository \
    mvn -s /src/docker-maven-settings.xml clean package ${MAVEN_ARGS} \
 && mkdir -p /release \
 && tar -xzf /src/target/datapoly-release-*.tar.gz -C /release \
 && mv /release/datapoly-release-* /release/datapoly-release

# ---- Stage 3: 运行时镜像(与 build-docker/datapoly/Dockerfile-* 同基线) ----
FROM eclipse-temurin:8-jre-jammy
ENV TZ=Asia/Shanghai
# 非 root 运行(K8s 安全上下文友好);用户需在 COPY --chown 前存在
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone \
 && useradd -r -u 1001 -d /datapoly-release datapoly

# --chown 在拷贝层内直接赋权,避免 chown -R 把整个发行目录复制成第二个层
COPY --chown=datapoly:datapoly --from=build /release/datapoly-release /datapoly-release
# bin 用容器专用单模块启动器(与 sync_release_dir.sh 同语义:发行包内为裸机两段式启动器,不用)
COPY --chown=datapoly:datapoly build-docker/datapoly/datapoly-release/bin/datapolyctl.sh /datapoly-release/bin/datapolyctl.sh

# run/ 放 pid 与启动日志(logs/ 随发行 tar 已由 --chown 归属 datapoly);run/ 新建后归 datapoly,否则非 root 无法写入
RUN chmod u+x /datapoly-release/bin/datapolyctl.sh \
 && mkdir -p /datapoly-release/run \
 && chown datapoly:datapoly /datapoly-release/run

WORKDIR /datapoly-release
USER datapoly

EXPOSE 8090 8091 8092

ENTRYPOINT ["/datapoly-release/bin/datapolyctl.sh"]
CMD ["manager"]