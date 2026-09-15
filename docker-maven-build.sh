#!/bin/sh

# maven:3.6.3-jdk-8 只有 amd64 镜像，Apple Silicon 上无法运行；
# 改用基于 Eclipse Temurin 8 的镜像（同为 JDK 8，与 CI 的 temurin 8 对齐，且支持 arm64）

set -e

# 先装配宿主扩展（build-extension.sh：宿主机同步 git 仓库 + JDK 25 构建后端 + 投放 lib-extra，
# 无配置时无操作；容器内 temurin 8 只负责宿主自身的 clean package）；maven 镜像不含 node，不单独跑 npm。
# 传 "debug" 可构建 devtools 可用的调试版 UI（透传给 build-ui.sh），仅限本机联调。
sh "$(dirname "$0")/build-extension.sh"
sh "$(dirname "$0")/build-ui.sh" "$1"

docker run -it --rm \
	--name my-maven-project \
	-v ~/.m2:/opt/maven/localRepository \
	-v "$PWD":/usr/src/mymaven \
	-w /usr/src/mymaven \
	--entrypoint /usr/share/maven/bin/mvn \
  maven:3.9-eclipse-temurin-8 \
  -s /usr/src/mymaven/docker-maven-settings.xml clean package

