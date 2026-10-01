#!/bin/sh
# Use of this source code is governed by a BSD-style license

# compose 一键部署入口：直接执行 docker compose up。
# （历史上这里曾先经 docker-prefetch-images.sh 预取镜像再 up，但该脚本从未入库，
#   调用一直靠 || 兜底静默跳过；如需内网镜像加速，请在宿主侧自行预取或配置
#   registry mirror，compose 会优先命中本地已有镜像。）
set -e

cd "$(dirname "$0")"

docker compose up "$@"
