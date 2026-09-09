#!/bin/sh

# maven:3.6.3-jdk-8 只有 amd64 镜像，Apple Silicon 上无法运行；
# 改用基于 Eclipse Temurin 8 的镜像（同为 JDK 8，与 CI 的 temurin 8 对齐，且支持 arm64）

set -e

# 先构建内置 UI（node:14-alpine 容器，见 build-ui.sh）；maven 镜像不含 node，不单独跑 npm。
# 传 "debug" 可构建 devtools 可用的调试版 UI（透传给 build-ui.sh），仅限本机联调。
sh "$(dirname "$0")/build-ui.sh" "$1"

docker run -it --rm \
	--name my-maven-project \
	-v ~/.m2:/opt/maven/localRepository \
	-v "$PWD":/usr/src/mymaven \
	-w /usr/src/mymaven \
	--entrypoint /usr/share/maven/bin/mvn \
  maven:3.9-eclipse-temurin-8 \
  -s /usr/src/mymaven/docker-maven-settings.xml clean package

