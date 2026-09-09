#!/bin/sh

set -e

DATAPOLY_VERSION=1.9.0
# Docker image namespace, override as needed, e.g. IMAGE_NAMESPACE=yourname sh build_and_push_image.sh
IMAGE_NAMESPACE=${IMAGE_NAMESPACE:-camilesing}
# Set PUSH_IMAGES=1 to also tag and push the images after building
PUSH_IMAGES=${PUSH_IMAGES:-0}
BUILD_DOCKER_DIR="$( cd "$( dirname "$0"  )" && pwd  )"
PROJECT_ROOT_DIR=$( dirname "$BUILD_DOCKER_DIR")
DOCKER_DATAPOLY_DIR=$BUILD_DOCKER_DIR/datapoly

# build project
# 注意：不要写成 `cd X && sh Y && cd -` 单行链——macOS 自带 sh(bash 3.2)
# 的 set -e 不会因 && 链中间命令失败而中止，Maven 失败会被静默吞掉继续出镜像。
# 传 "debug" 可把 devtools 可用的调试版 UI 打进镜像（透传 docker-maven-build.sh → build-ui.sh），仅本机联调。
cd "$PROJECT_ROOT_DIR"
sh docker-maven-build.sh "$1"
cd "$BUILD_DOCKER_DIR"

# sync release lib/, drivers/ & conf/ into image staging dir (shared with build.sh; bin/ 容器启动器入库维护，不同步)
sh $BUILD_DOCKER_DIR/sync_release_dir.sh

# build image
cd "$DOCKER_DATAPOLY_DIR"
tar zcvf datapoly-release.tar.gz datapoly-release/

docker build -f Dockerfile-manager -t ${IMAGE_NAMESPACE}/datapoly-manager:${DATAPOLY_VERSION} .
docker build -f Dockerfile-executor -t ${IMAGE_NAMESPACE}/datapoly-executor:${DATAPOLY_VERSION} .
docker build -f Dockerfile-gateway -t ${IMAGE_NAMESPACE}/datapoly-gateway:${DATAPOLY_VERSION} .

# install/docker-compose.yml 消费的是 :latest；本地构建后立即打标，
# 否则 `docker compose up -d` 不会重建容器、继续跑旧镜像里的旧 jar
for svc in manager executor gateway; do
  docker tag ${IMAGE_NAMESPACE}/datapoly-${svc}:${DATAPOLY_VERSION} ${IMAGE_NAMESPACE}/datapoly-${svc}:latest
done

# 清理同步进暂存目录的构建产物（隐藏占位文件保留）
rm -f datapoly-release.tar.gz
for sub in lib drivers conf; do
    find "datapoly-release/$sub" -mindepth 1 -maxdepth 1 ! -name '.*' -exec rm -rf {} +
done

# clean project
cd "$PROJECT_ROOT_DIR"
sh docker-maven-clean.sh
cd "$BUILD_DOCKER_DIR"

# optionally push images (requires docker login first)
if [ "${PUSH_IMAGES}" = "1" ]; then
  for svc in manager executor gateway; do
    docker tag ${IMAGE_NAMESPACE}/datapoly-${svc}:${DATAPOLY_VERSION} ${IMAGE_NAMESPACE}/datapoly-${svc}:latest
    docker push ${IMAGE_NAMESPACE}/datapoly-${svc}:${DATAPOLY_VERSION}
    docker push ${IMAGE_NAMESPACE}/datapoly-${svc}:latest
  done
fi

echo 'success'
