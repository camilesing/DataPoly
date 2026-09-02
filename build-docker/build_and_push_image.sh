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
cd $PROJECT_ROOT_DIR && sh docker-maven-build.sh && cd -

# sync release lib/ & drivers/ into image staging dir (shared with build.sh)
sh $BUILD_DOCKER_DIR/sync_release_dir.sh

# build image
cd ${DOCKER_DATAPOLY_DIR} && tar zcvf datapoly-release.tar.gz datapoly-release/

docker build -f Dockerfile-manager -t ${IMAGE_NAMESPACE}/datapoly-manager:${DATAPOLY_VERSION} .
docker build -f Dockerfile-executor -t ${IMAGE_NAMESPACE}/datapoly-executor:${DATAPOLY_VERSION} .
docker build -f Dockerfile-gateway -t ${IMAGE_NAMESPACE}/datapoly-gateway:${DATAPOLY_VERSION} .

rm -f datapoly-release.tar.gz && rm -rf datapoly-release/lib/* && rm -rf datapoly-release/drivers/*

# clean project
cd $PROJECT_ROOT_DIR && sh docker-maven-clean.sh && cd -

# optionally push images (requires docker login first)
if [ "${PUSH_IMAGES}" = "1" ]; then
  for svc in manager executor gateway; do
    docker tag ${IMAGE_NAMESPACE}/datapoly-${svc}:${DATAPOLY_VERSION} ${IMAGE_NAMESPACE}/datapoly-${svc}:latest
    docker push ${IMAGE_NAMESPACE}/datapoly-${svc}:${DATAPOLY_VERSION}
    docker push ${IMAGE_NAMESPACE}/datapoly-${svc}:latest
  done
fi

echo 'success'
