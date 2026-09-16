#!/bin/sh

# 本机构建统一用 JDK 8（产物即成 Java 8 字节码，与 CI 的 temurin 8 一致；本机无需安装 JDK 25）
# 候选顺序：现有 JAVA_HOME → Homebrew keg → /usr/libexec/java_home -v 8。
# 注意：java_home 在无匹配 JDK 时会回退返回唯一已装 JDK 且退出码仍为 0，
# 因此每个候选都必须用 java -version 校验主版本确为 8，防止静默落回其他版本。
# HotSpot 8 的版本串形如 "1.8.0_504"，jdkmajor 会把它归一成 8 再比较。

jdkmajor() {
    "$1/bin/java" -version 2>&1 | sed -n 's/.*version "\([0-9][0-9]*\).*/\1/p' | sed 's/^1$/8/'
}

if [ "$(uname -s)" = "Darwin" ]; then
    java_home_8=""
    if [ -x /usr/libexec/java_home ]; then
        java_home_8=$(/usr/libexec/java_home -v 8 2>/dev/null || true)
    fi
    for candidate in \
        "${JAVA_HOME:-}" \
        /opt/homebrew/opt/openjdk@8/libexec/openjdk.jdk/Contents/Home \
        /usr/local/opt/openjdk@8/libexec/openjdk.jdk/Contents/Home \
        "$java_home_8"; do
        if [ -n "$candidate" ] && [ "$(jdkmajor "$candidate" 2>/dev/null)" = "8" ]; then
            export JAVA_HOME="$candidate"
            echo "[build.sh] JAVA_HOME=$JAVA_HOME"
            break
        fi
    done
    if [ "$(jdkmajor "$JAVA_HOME" 2>/dev/null)" != "8" ]; then
        echo "[build.sh] 未找到 JDK 8，沿用当前 JAVA_HOME/PATH 构建（JAVA_HOME=${JAVA_HOME:-<未设置>}）" >&2
    fi
fi

# 先装配宿主扩展（build-extension.sh：环境变量门控，未配置且无本地目录时无操作），
# 再构建内置 UI 产物（node:23-alpine 容器），最后 mvn 打包；mvn 本身不触发前端构建。
# 传 "debug" 可构建 devtools 可用的调试版 UI（透传给 build-ui.sh），仅限本机联调。
sh "$(dirname "$0")/build-extension.sh"
sh "$(dirname "$0")/build-ui.sh" "$1"

# -DskipTests（而非 -Dmaven.test.skip=true）：跳过执行但保留测试编译，尽早暴露测试源断裂
mvn clean package -DskipTests \
    && sh "$(dirname "$0")/build-docker/sync_release_dir.sh"
