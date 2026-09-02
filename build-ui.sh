#!/bin/sh

# 构建内置管理端 UI 并同步进 manager resources（构建产物不入库，见 .gitignore 与 AGENTS.md）。
# 前端为 Vue2 + webpack3，仅 Node 14 可构建，故经 node:14-alpine 容器构建，本机无需 Node。
# build.sh 与 docker-maven-build.sh 会在 mvn 打包前调用本脚本；纯 mvn package 的 jar 不含 UI。
set -e

cd "$(dirname "$0")"

# 挂载整个仓库根目录：webpack 的 @extension 别名会探测 ../datapoly-extension-ui，
# 只挂 datapoly-manager-ui 会让探测在容器内落空、扩展页面（导出中心）被静默回退成 stub。
# -u + HOME 保证产物/node_modules 属主仍是宿主用户。
docker run --rm \
  -u "$(id -u):$(id -g)" \
  -e HOME=/tmp \
  -v "$PWD":/opt/app \
  -w /opt/app/datapoly-manager-ui \
  node:14-alpine \
  sh -c "npm config set registry https://registry.npmmirror.com && \
         npm install --no-audit --no-fund --no-package-lock --legacy-peer-deps && \
         npm run build"

# 先清后拷，避免旧 hash 产物残留；仅在容器构建成功后执行（set -e 保证）。
rm -rf datapoly-manager/src/main/resources/static
rm -f  datapoly-manager/src/main/resources/index.html
cp -R datapoly-manager-ui/dist/static datapoly-manager/src/main/resources/static
cp    datapoly-manager-ui/dist/index.html datapoly-manager/src/main/resources/index.html

echo '[build-ui.sh] built-in UI synced into datapoly-manager/src/main/resources'