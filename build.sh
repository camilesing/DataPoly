#!/bin/sh

# 本机构建统一用 JDK 25（产物即成 Java 25 字节码，与 CI 的 temurin 25 一致）
# 候选顺序：现有 JAVA_HOME → Homebrew keg → /usr/libexec/java_home -v 25。
# 注意：java_home 在无匹配 JDK 时会回退返回唯一已装 JDK 且退出码仍为 0，
# 因此每个候选都必须用 java -version 校验主版本确为 25，防止静默落回其他版本。
# HotSpot 8 的版本串形如 "1.8.0_504"，jdkmajor 会把它归一成 8 再比较。

jdkmajor() {
    "$1/bin/java" -version 2>&1 | sed -n 's/.*version "\([0-9][0-9]*\).*/\1/p' | sed 's/^1$/8/'
}

if [ "$(uname -s)" = "Darwin" ]; then
    java_home_want=""
    if [ -x /usr/libexec/java_home ]; then
        java_home_want=$(/usr/libexec/java_home -v 25 2>/dev/null || true)
    fi
    for candidate in \
        "${JAVA_HOME:-}" \
        /opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home \
        /usr/local/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home \
        "$java_home_want"; do
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

# 先装配宿主扩展（build-extension.sh：环境变量门控，未配置且无本地目录时无操作），
# 再构建内置 UI 产物（node:24-alpine 容器），最后 mvn 打包；mvn 本身不触发前端构建。
# 传 "debug" 可构建 devtools 可用的调试版 UI（透传给 build-ui.sh），仅限本机联调。
sh "$(dirname "$0")/build-extension.sh"
sh "$(dirname "$0")/build-ui.sh" "$1"

# -DskipTests（而非 -Dmaven.test.skip=true）：跳过执行但保留测试编译，尽早暴露测试源断裂
mvn clean package -DskipTests \
    && sh "$(dirname "$0")/build-docker/sync_release_dir.sh"
