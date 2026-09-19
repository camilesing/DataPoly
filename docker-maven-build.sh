#!/bin/sh

# 构建基线为 JDK 25（LTS，与 CI 的 temurin 25 对齐），镜像支持 arm64

set -e

# 先装配宿主扩展（build-extension.sh：按本地 datapoly-extension/ 目录构建 + 投放 lib-extra，
# 目录不存在时无操作，脚本不做 git 拉取；容器内 temurin 25 只负责宿主自身的 clean package）；maven 镜像不含 node，不单独跑 npm。
# 传 "debug" 可构建 devtools 可用的调试版 UI（透传给 build-ui.sh），仅限本机联调。
sh "$(dirname "$0")/build-extension.sh"
sh "$(dirname "$0")/build-ui.sh" "$1"

docker run -it --rm \
	--name my-maven-project \
	-v ~/.m2:/opt/maven/localRepository \
	-v "$PWD":/usr/src/mymaven \
	-w /usr/src/mymaven \
	--entrypoint /usr/share/maven/bin/mvn \
  maven:3.9-eclipse-temurin-25 \
  -s /usr/src/mymaven/docker-maven-settings.xml clean package

