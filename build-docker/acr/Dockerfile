# Use of this source code is governed by a BSD-style license.
#
# ACR（阿里云容器镜像服务）云上构建专用多阶段 Dockerfile。
# 云构建环境只有一步 docker build：因此把整条生产链收进构建阶段——
#   阶段1 node:14-alpine 烘焙内置管理端 UI（等价 build-ui.sh 的 npm 步骤）
#   阶段2 maven:3.9-eclipse-temurin-8 全量打包（等价 docker-maven-build.sh）
#   阶段3 eclipse-temurin:8-jre-jammy 运行时镜像（复用发行版 tar 布局）
# 与仓库常规 Dockerfile（build-docker/datapoly/Dockerfile-*）的区别：
#   常规文件依赖预先产出的 datapoly-release.tar.gz（由 build_and_push_image.sh 在
#   docker 外先 mvn/npm 再打包），云构建无法执行该前置步骤，故本文件自包含。
#
# ACR 构建规则配置（每个服务一条规则）：
#   构建上下文（目录）：仓库根（默认 /）
#   Dockerfile 路径：build-docker/acr/Dockerfile
#   构建参数（ARG）：MODULE = manager | executor | gateway
# 注意：云构建产物为 amd64 单架构；正式多架构发布请仍走 GitHub Actions 的
# docker-publish 流水线（linux/amd64 + linux/arm64）。
#
# 本地等价的单命令验证（需 dockerd，构建约 10-20 分钟）：
#   docker build -f build-docker/acr/Dockerfile --build-arg MODULE=manager -t datapoly-manager:local .

# ---- 阶段 1：烘焙管理端 UI（npm 直跑，无需 docker-in-docker）----
FROM node:14-alpine AS ui
WORKDIR /work
COPY datapoly-manager-ui ./datapoly-manager-ui
# 公开仓库无异地扩展目录，@extension 探测落空回退 stub，与开源构建语义一致；
# 需要扩展 UI 的部署方可将自己的 src 拷进 datapoly-extension/front/src 再构建
WORKDIR /work/datapoly-manager-ui
RUN npm config set registry https://registry.npmmirror.com \
 && npm install --no-audit --no-fund --no-package-lock --legacy-peer-deps \
 && npm run build

# ---- 阶段 2：宿主全量打包（等价 docker-maven-build.sh，镜像自带 maven/docker 外挂）----
FROM maven:3.9-eclipse-temurin-8 AS builder
WORKDIR /repo
COPY . .
# UI 产物同步进 manager resources（等价 build-ui.sh 的 cp 步骤）
COPY --from=ui /work/datapoly-manager-ui/dist/static datapoly-manager/src/main/resources/static
COPY --from=ui /work/datapoly-manager-ui/dist/index.html datapoly-manager/src/main/resources/index.html
# 复用仓库内的容器 maven settings（localRepository 指向镜像默认路径 + 阿里云 central 镜像）
RUN mvn -B -ntp -s docker-maven-settings.xml clean package
RUN find target -maxdepth 1 -name 'datapoly-release-*.tar.gz' -exec cp {} /tmp/release.tar.gz \;

# ---- 阶段 3：运行时镜像（布局对齐 build-docker/datapoly 基础镜像）----
FROM eclipse-temurin:8-jre-jammy
ARG MODULE=manager
ENV START_MODULE=$MODULE \
    TZ=Asia/Shanghai
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

WORKDIR /datapoly-release
COPY --from=builder /tmp/release.tar.gz /tmp/release.tar.gz
RUN mkdir -p /tmp/release \
 && tar xzf /tmp/release.tar.gz -C /tmp/release \
 && DIR="$(find /tmp/release -mindepth 1 -maxdepth 1 -type d | head -n 1)" \
 && cp -a "$DIR"/. /datapoly-release/ \
 && rm -rf /tmp/release /tmp/release.tar.gz \
 && chmod u+x /datapoly-release/bin/datapolyctl.sh

EXPOSE 8090 8091 8092
CMD ["sh", "-c", "exec /datapoly-release/bin/datapolyctl.sh ${START_MODULE}"]