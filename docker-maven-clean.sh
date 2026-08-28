#!/bin/sh

# maven:3.6.3-jdk-8 只有 amd64 镜像，Apple Silicon 上无法运行；
# 改用基于 Eclipse Temurin 8 的镜像（同为 JDK 8，与 CI 的 temurin 8 对齐，且支持 arm64）
docker run -it --rm \
	--name my-maven-project \
	-v ~/.m2:/opt/maven/localRepository \
	-v "$PWD":/usr/src/mymaven \
	-w /usr/src/mymaven \
  maven:3.9-eclipse-temurin-8 mvn clean

