#!/bin/sh

# 将 target/datapoly-release-*.tar.gz 中的 lib/ 与 drivers/ 同步到
# build-docker/datapoly/datapoly-release/ 镜像暂存目录。
# build.sh（本地打包）与 build_and_push_image.sh（docker 打包）共用本脚本，
# 保证两条路径的拷贝语义一致。
# 同步采用"先清空非 . 开头条目再拷贝"，避免旧 jar 残留进镜像；.gitkeep 等入库占位文件保留。

set -e

BUILD_DOCKER_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT_DIR=$(dirname "$BUILD_DOCKER_DIR")
STAGING_DIR="$BUILD_DOCKER_DIR/datapoly/datapoly-release"

TARBALL=$(ls -t "$PROJECT_ROOT_DIR"/target/datapoly-release-*.tar.gz 2>/dev/null | head -n 1)
if [ -z "$TARBALL" ]; then
    echo "[sync_release_dir] 未找到 target/datapoly-release-*.tar.gz，请先执行 sh build.sh 打包" >&2
    exit 1
fi

TMP_DIR=$(mktemp -d)
trap 'rm -rf "$TMP_DIR"' EXIT INT TERM

tar xzf "$TARBALL" -C "$TMP_DIR"
RELEASE_DIR=$(find "$TMP_DIR" -mindepth 1 -maxdepth 1 -type d | head -n 1)
if [ -z "$RELEASE_DIR" ]; then
    echo "[sync_release_dir] tar 包内未找到发行目录: $TARBALL" >&2
    exit 1
fi

sync_into() {
    src="$1"
    dst="$2"
    mkdir -p "$dst"
    # 只清理非隐藏条目，.gitkeep 等入库占位文件保留
    find "$dst" -mindepth 1 -maxdepth 1 ! -name '.*' -exec rm -rf {} +
    cp -R "$src/." "$dst/"
}

sync_into "$RELEASE_DIR/lib" "$STAGING_DIR/lib"
sync_into "$RELEASE_DIR/drivers" "$STAGING_DIR/drivers"

echo "[sync_release_dir] 已同步 lib/ 与 drivers/ → $STAGING_DIR"