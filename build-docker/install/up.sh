#!/bin/sh
# Use of this source code is governed by a BSD-style license

# compose 一键部署的内网友好入口：先经国内镜像加速站预取 docker-compose.yml 引用的
# 全部运行期镜像（mysql:5.7 与 camilesing/datapoly-*，镜像路径与 compose 保持原样，
# 由 ../../docker-prefetch-images.sh 拉取并 tag 规范名；幂等、已有则跳过），再执行
# docker compose up。公司内网直连 Docker Hub 超时（例如 docker_install.sh 提供的
# 旧镜像源已停服）时，用 `sh up.sh -d` 代替 `docker compose up -d`；网络可达或已配置
# 可用镜像源的环境两者等价（compose 优先命中本地已 tag 的规范名镜像，不再联网）。
# DATAPOLY_IMAGE_MIRROR 可覆盖镜像加速站；镜像清单自动取自 docker-compose.yml 的 image: 行。
set -e

cd "$(dirname "$0")"

IMAGES=$(grep -hE '^[[:space:]]*image:' docker-compose.yml | awk '{print $NF}' | grep -vE '[\$<>]')
if [ -n "$IMAGES" ]; then
  # 容错：公共镜像（如 mysql）预取失败通常意味着加速站整体不可用，compose 会给出原始错误；
  # 私有/未公开发布的镜像（如本地构建的三服务镜像）本地已有时自动跳过，缺失时交由 compose 决断。
  sh ../../docker-prefetch-images.sh $IMAGES \
    || echo '[up.sh] 部分镜像预取失败，继续执行 compose（本地已有同名镜像时不受影响）'
else
  echo '[up.sh] 未从 docker-compose.yml 解析到镜像清单，跳过预取（compose 可能使用环境变量镜像名）'
fi

docker compose up "$@"
