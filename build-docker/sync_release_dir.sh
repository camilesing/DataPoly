#!/bin/sh

# 将 target/datapoly-release-*.tar.gz 中的 lib/、drivers/ 与 conf/ 同步到
# build-docker/datapoly/datapoly-release/ 镜像暂存目录。
# build.sh（本地打包）与 build_and_push_image.sh（docker 打包）共用本脚本，
# 保证两条路径的拷贝语义一致。
# conf/ 必须同步：启动脚本把 conf/<module> 放在 classpath 最前，jar 内 yaml 会被其遮蔽，
# 曾因暂存目录配置副本不刷新导致新配置项（如 datapoly.data-task）打不进镜像。
# bin/ 不同步：暂存目录的 datapolyctl.sh 是入库维护的容器专用启动器（单模块参数、
# 不读 config.ini、带容器 JVM 参数与一次自动重试），与发行包中面向裸机的两段式启动器
# （start|stop|status <module>，强制从 config.ini export 配置）刻意不同，勿混用。
# 同步采用"先清空非 . 开头条目再拷贝"，避免旧文件残留进镜像；.gitkeep 等入库占位文件保留。

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
    # 拷贝同样只取非隐藏条目，macOS 同步产生的 ._* AppleDouble 文件不会混进镜像
    find "$src" -mindepth 1 -maxdepth 1 ! -name '.*' -exec cp -R {} "$dst" \;
}

sync_into "$RELEASE_DIR/lib" "$STAGING_DIR/lib"
sync_into "$RELEASE_DIR/drivers" "$STAGING_DIR/drivers"
sync_into "$RELEASE_DIR/conf" "$STAGING_DIR/conf"

echo "[sync_release_dir] 已同步 lib/、drivers/ 与 conf/ → $STAGING_DIR"