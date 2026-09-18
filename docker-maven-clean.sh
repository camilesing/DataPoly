#!/bin/sh

# 构建基线为 JDK 25（LTS，与 CI 的 temurin 25 对齐），镜像支持 arm64
set -e

docker run -it --rm \
	--name my-maven-project \
	-v ~/.m2:/opt/maven/localRepository \
	-v "$PWD":/usr/src/mymaven \
	-w /usr/src/mymaven \
	--entrypoint /usr/share/maven/bin/mvn \
  maven:3.9-eclipse-temurin-25 \
  -s /usr/src/mymaven/docker-maven-settings.xml clean

