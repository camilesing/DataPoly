#!/bin/sh

# build-extension.sh — 宿主扩展装配：按需同步扩展 git 仓库 → 宿主机构建后端模块 → 投放 lib-extra/
# 被 build.sh 与 docker-maven-build.sh 在 build-ui.sh 之前调用，无配置时静默跳过（纯开源构建零影响）。
#
# 环境变量（真实值一律外部注入、不入库，见 AGENTS.md；CI 在 pipeline 注入，本地可用 dev-local/env.sh）：
#   DATAPOLY_EXTENSION_GIT_URL      扩展仓库地址（git clone 可用形式）；未设置且无本地目录时为无操作
#   DATAPOLY_EXTENSION_GIT_REF      拉取的 ref（分支/标签），默认 master
#   DATAPOLY_EXTENSION_FORCE_SYNC=1 目录已存在时强制 fetch+reset 到 REF（丢弃本地未提交改动，慎用）
#
# 后端模块构建沿用 dev-local/dev.sh 原约定：宿主机 JDK 8 优先、无 8 时允许 8 以上（编译目标钉在 1.8）、扩展模块不进根 reactor、
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
GIT_URL="${DATAPOLY_EXTENSION_GIT_URL:-}"
GIT_REF="${DATAPOLY_EXTENSION_GIT_REF:-master}"
FORCE_SYNC="${DATAPOLY_EXTENSION_FORCE_SYNC:-0}"

# ---------- ① git 同步（环境变量门控；克隆失败即构建终止，fail-closed） ----------
if [ -n "$GIT_URL" ]; then
    if [ ! -e "$EXT_DIR" ]; then
        echo "[ext] 浅克隆扩展仓库 ${GIT_URL}（ref=${GIT_REF}）"
        git clone --depth 1 --branch "$GIT_REF" "$GIT_URL" "$EXT_DIR"
    elif [ "$FORCE_SYNC" = "1" ]; then
        if [ -d "$EXT_DIR/.git" ]; then
            echo "[ext] 强制同步扩展仓库到 ref=${GIT_REF}（覆盖本地未提交改动）"
            git -C "$EXT_DIR" fetch --depth 1 origin "$GIT_REF"
            git -C "$EXT_DIR" reset --hard FETCH_HEAD
        else
            echo "[ext] datapoly-extension 存在但不是 git 仓库，无法强制同步，按现有目录构建" >&2
        fi
    else
        echo "[ext] datapoly-extension 已存在，按本地工作区构建（强制刷新: DATAPOLY_EXTENSION_FORCE_SYNC=1）"
    fi
else
    echo "[ext] DATAPOLY_EXTENSION_GIT_URL 未设置，跳过扩展仓库同步"
fi

# ---------- ② 发现扩展后端模块（排除 front 联调的 .host 稀疏快照） ----------
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

# ---------- ③ 宿主机 JDK：优先 JDK 8（与 CI 的 temurin 8 对齐）；扩展模块编译目标为 1.8
#     （根 pom maven.compiler.source/target=1.8），8 以上 JDK 构建同样产出 Java 8 字节码，
#     故不再硬性断言 8——仅当完全无可用 JDK 时才中止 ----------
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
if [ "$(jdkmajor "$JAVA_HOME" 2>/dev/null)" != "8" ]; then
    echo "[ext] 当前为 JDK $(jdkmajor "$JAVA_HOME" 2>/dev/null)（非 8）：编译目标 1.8，产物仍为 Java 8 字节码（与容器内 temurin 8 一致）"
fi

HOST_VERSION=$(grep -m1 '<version>' "$ROOT/pom.xml" | sed -E 's/.*<version>([^<]+)<\/version>.*/\1/')

# 版本耦合校验：扩展模块 parent 钉版须与宿主根 pom 一致（只告警，不越权改扩展仓库）
while IFS= read -r pom; do
    PARENT_VERSION=$(sed -n '/<parent>/,/<\/parent>/p' "$pom" | sed -n 's/.*<version>\([^<]*\)<\/version>.*/\1/p' | head -n 1)
    if [ -n "$PARENT_VERSION" ] && [ "$PARENT_VERSION" != "$HOST_VERSION" ]; then
        echo "[ext] 警告: $pom 的 parent 版本 $PARENT_VERSION 与宿主根 pom $HOST_VERSION 不一致，构建可能失败（需在扩展仓库同步升级 parent 钉版）" >&2
    fi
done < "$POM_LIST"

# ---------- ④ 本地仓库缺核心链时安装（扩展 pom 依赖 datapoly-core，parent 经 relativePath 解析根 pom） ----------
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

# ---------- ⑤ 逐个构建扩展模块 ----------
while IFS= read -r pom; do
    [ -f "$pom" ] || continue
    d=$(dirname "$pom")
    echo "[ext] 构建扩展模块 ${d#"$ROOT"/}"
    mvn -B -ntp -f "$pom" clean package "$TEST_FLAG"
done < "$POM_LIST"

# ---------- ⑥ 投放扩展产物进 lib-extra/：跳过 datapoly-* 开源模块系列 jar（防扩展 target 里经
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