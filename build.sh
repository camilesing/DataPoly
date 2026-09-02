#!/bin/sh

# 本机构建统一用 JDK 25（工具链升 25；产物仍为 Java 8 字节码，CI 仍用 temurin 8 直跑 mvn）
# 候选顺序：现有 JAVA_HOME → Homebrew keg → /usr/libexec/java_home -v 25。
# 注意：java_home 在无匹配 JDK 时会回退返回唯一已装 JDK 且退出码仍为 0，
# 因此每个候选都必须用 java -version 校验主版本确为 25，防止静默落回 JDK 8/26。

jdkmajor() {
    "$1/bin/java" -version 2>&1 | sed -n 's/.*version "\([0-9][0-9]*\).*/\1/p'
}

if [ "$(uname -s)" = "Darwin" ]; then
    java_home_25=""
    if [ -x /usr/libexec/java_home ]; then
        java_home_25=$(/usr/libexec/java_home -v 25 2>/dev/null || true)
    fi
    for candidate in \
        "${JAVA_HOME:-}" \
        /opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home \
        /usr/local/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home \
        "$java_home_25"; do
        if [ -n "$candidate" ] && [ "$(jdkmajor "$candidate" 2>/dev/null)" = "25" ]; then
            export JAVA_HOME="$candidate"
            echo "[build.sh] JAVA_HOME=$JAVA_HOME"
            break
        fi
    done
    if [ "$(jdkmajor "$JAVA_HOME" 2>/dev/null)" != "25" ]; then
        echo "[build.sh] 未找到 JDK 25，沿用当前 JAVA_HOME/PATH 构建（JAVA_HOME=${JAVA_HOME:-<未设置>}）" >&2
    fi
fi

# 先构建内置 UI 产物（node:14-alpine 容器），再打包；mvn 本身不触发前端构建
sh "$(dirname "$0")/build-ui.sh"

mvn clean package -Dmaven.test.skip=true \
    && sh "$(dirname "$0")/build-docker/sync_release_dir.sh"