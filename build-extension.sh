#!/bin/sh

# build-extension.sh — 宿主扩展装配：按本地 datapoly-extension/ 目录构建后端模块 → 投放 lib-extra/
# 被 build.sh 与 docker-maven-build.sh 在 build-ui.sh 之前调用，目录不存在时静默跳过（纯开源构建零影响）。
# 扩展仓库是独立 git 仓库，由使用者自行克隆/更新到根目录 datapoly-extension/，本脚本不做任何 git 操作。
#
# 后端模块构建：宿主机 JDK 25 优先（与 CI 的 temurin 25 对齐）、无 25 时允许 25 以上（编译目标钉在 25，
# 低于 25 无法编译，构建中止）、扩展模块不进根 reactor、
# 依赖经 <relativePath> 解析根 pom、产物投放 lib-extra/ 后由 package.xml 打进 lib/common。
# 参数：--skip-tests 用 -DskipTests 替代默认 -Dmaven.test.skip=true（仅作用于扩展侧构建）。

set -eu

ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT"

TEST_FLAG="-Dmaven.test.skip=true"
for arg in "$@"; do
    case "$arg" in
        --skip-tests) TEST_FLAG="-DskipTests" ;;
        *) echo "[ext] 未知参数: ${arg}（仅支持 --skip-tests）" >&2; exit 2 ;;
    esac
done

EXT_DIR="$ROOT/datapoly-extension"

# ---------- ① 发现扩展后端模块（排除 front 联调的 .host 稀疏快照） ----------
if [ ! -d "$EXT_DIR" ]; then
    echo "[ext] datapoly-extension 不存在，跳过扩展装配（纯开源构建不受影响）"
    exit 0
fi

POM_LIST="${TMPDIR:-/tmp}/dpt-ext-pom.$$"
trap 'rm -f "$POM_LIST"' EXIT
find "$EXT_DIR" -name pom.xml -type f ! -path '*/.host/*' | sort > "$POM_LIST"

if [ "$(wc -l < "$POM_LIST")" -eq 0 ]; then
    echo "[ext] datapoly-extension 下未发现扩展模块（无 pom.xml），跳过扩展装配"
    exit 0
fi

# ---------- ② 宿主机 JDK：优先 JDK 25（与 CI 的 temurin 25 对齐）；扩展模块编译目标为 25
#     （根 pom maven.compiler.release=25），低于 25 无法编译、25 以上亦可 ----------
jdkmajor() {
    "$1/bin/java" -version 2>&1 | sed -n 's/.*version "\([0-9][0-9]*\).*/\1/p' | sed 's/^1$/8/'
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
        if [ -n "$candidate" ] && [ -n "$(jdkmajor "$candidate" 2>/dev/null)" ]; then
            export JAVA_HOME="$candidate"
            break
        fi
    done
fi
if [ -z "${JAVA_HOME:-}" ] || [ -z "$(jdkmajor "$JAVA_HOME" 2>/dev/null)" ]; then
    # 非 macOS 或上述候选均无效：从 PATH 上的 java 反推 JAVA_HOME（如 yum/dnf 装的 OpenJDK）
    java_bin=$(command -v java 2>/dev/null || true)
    if [ -n "$java_bin" ]; then
        java_bin=$(readlink -f "$java_bin" 2>/dev/null || printf '%s' "$java_bin")
        export JAVA_HOME=$(dirname "$(dirname "$java_bin")")
    fi
fi
if [ -z "${JAVA_HOME:-}" ] || [ -z "$(jdkmajor "$JAVA_HOME" 2>/dev/null)" ]; then
    echo "[ext] 未找到可用 JDK，无法构建扩展模块" >&2
    exit 1
fi
echo "[ext] JAVA_HOME=$JAVA_HOME"
if [ "$(jdkmajor "$JAVA_HOME" 2>/dev/null)" -lt 25 ]; then
    echo "[ext] 当前为 JDK $(jdkmajor "$JAVA_HOME" 2>/dev/null)（低于 25）：扩展模块编译目标为 25，无法编译，请切换 JDK 25 或以上" >&2
    exit 1
fi

HOST_VERSION=$(grep -m1 '<version>' "$ROOT/pom.xml" | sed -E 's/.*<version>([^<]+)<\/version>.*/\1/')

# 版本耦合校验：扩展模块 parent 钉版须与宿主根 pom 一致（只告警，不越权改扩展仓库）
while IFS= read -r pom; do
    PARENT_VERSION=$(sed -n '/<parent>/,/<\/parent>/p' "$pom" | sed -n 's/.*<version>\([^<]*\)<\/version>.*/\1/p' | head -n 1)
    if [ -n "$PARENT_VERSION" ] && [ "$PARENT_VERSION" != "$HOST_VERSION" ]; then
        echo "[ext] 警告: $pom 的 parent 版本 $PARENT_VERSION 与宿主根 pom $HOST_VERSION 不一致，构建可能失败（需在扩展仓库同步升级 parent 钉版）" >&2
    fi
done < "$POM_LIST"

# ---------- ③ 本地仓库缺核心链时安装（扩展 pom 依赖 datapoly-core，parent 经 relativePath 解析根 pom） ----------
LOCAL_REPO="$HOME/.m2/repository"
NEED_INSTALL=0
for a in datapoly-common datapoly-template datapoly-persistence datapoly-cache datapoly-core; do
    if [ ! -f "$LOCAL_REPO/com/cs/$a/$HOST_VERSION/$a-$HOST_VERSION.jar" ]; then
        NEED_INSTALL=1
        break
    fi
done
if [ "$NEED_INSTALL" = 1 ]; then
    echo "[ext] 本地仓库缺少核心链 ${HOST_VERSION}，先安装…"
    mvn -B -ntp install -N
    mvn -B -ntp install -pl datapoly-common,datapoly-template,datapoly-persistence,datapoly-cache,datapoly-core -am "$TEST_FLAG"
fi

# ---------- ④ 逐个构建扩展模块 ----------
while IFS= read -r pom; do
    [ -f "$pom" ] || continue
    d=$(dirname "$pom")
    echo "[ext] 构建扩展模块 ${d#"$ROOT"/}"
    mvn -B -ntp -f "$pom" clean package "$TEST_FLAG"
done < "$POM_LIST"

# ---------- ⑤ 投放扩展产物进 lib-extra/：跳过 datapoly-* 开源模块系列 jar（防扩展 target 里经
#     installed 版本解析的核心链覆盖本次构建），扩展自身 jar 与第三方依赖一律复制 ----------
rm -f "$ROOT"/lib-extra/*.jar
while IFS= read -r pom; do
    d=$(dirname "$pom")
    [ -d "$d/target" ] || continue
    for jar in "$d"/target/*.jar; do
        [ -e "$jar" ] || continue
        name=$(basename "$jar")
        case "$name" in
            datapoly-extension-*)
                cp "$jar" "$ROOT/lib-extra/"; echo "[ext] 投放: $name" ;;
            datapoly-*)
                echo "[ext] 跳过（开源模块系列）: $name" ;;
            *)
                cp "$jar" "$ROOT/lib-extra/"; echo "[ext] 投放: $name" ;;
        esac
    done
done < "$POM_LIST"

echo "[ext] 扩展装配完成，lib-extra/ 现有 $(ls "$ROOT"/lib-extra/*.jar 2>/dev/null | wc -l | tr -d ' ') 个 jar"