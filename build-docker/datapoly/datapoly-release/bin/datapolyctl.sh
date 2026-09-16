#!/bin/sh

module=$1

APP_HOME="${BASH_SOURCE-$0}"
APP_HOME="$(dirname "${APP_HOME}")"
APP_HOME="$(cd "${APP_HOME}"; pwd)"
APP_HOME="$(cd "$(dirname "${APP_HOME}")"; pwd)"
APP_BIN_PATH=$APP_HOME/bin
APP_CONF_PATH=$APP_HOME/conf
APP_LIB_COMMON_PATH=$APP_HOME/lib/common
APP_LIB_EXECUTOR_PATH=$APP_HOME/lib/executor
APP_LIB_GATEWAY_PATH=$APP_HOME/lib/gateway
APP_LIB_MANAGER_PATH=$APP_HOME/lib/manager
APP_PID_FILE="${APP_HOME}/run/${module}.pid"
APP_RUN_LOG="${APP_HOME}/run/run_${module}.log"

echo "Begin start $module......"
echo "Base Directory:${APP_HOME}"

export APP_DRIVERS_PATH=$APP_HOME/drivers

# JVM参数可以在这里设置
# 堆 4G、年轻代/老年代 1:3：长驻对象（Hazelcast token/API 响应缓存、Eureka、Spring 框架
# 对象）占堆内大头，老年代空间优先；年轻代 1G 足以容纳数据任务流式批次与 ≤200 行的
# 调试/预览结果集（线上观测：16 分钟仅 7 次 minor GC，老年代却 30 秒内 5→25 次 major GC）。
# -XX:+PerfDisableSharedMem: the JDK perfdata file lands in java.io.tmpdir; when the host
# bind-mounts /tmp (macOS Docker Desktop virtiofs), zeroing that 32KB mmap SIGBUSes the JVM
# at startup. Disabling shared-mem perfdata removes the mmap entirely.
JVMFLAGS="-server -Xms4096m -Xmx4096m -Xmn1024m -XX:+DisableExplicitGC -XX:+PerfDisableSharedMem -Djava.awt.headless=true -Dfile.encoding=UTF-8 "

if [ "$JAVA_HOME" != "" ]; then
  JAVA="$JAVA_HOME/bin/java"
else
  JAVA=java
fi

# 配置classpath和启动类
CLASSPATH=$APP_CONF_PATH
APP_MAIN_CLASS='com.cs.manager.ManagerApplication'
if [ "$module" = "manager" ]; then
  CLASSPATH="$APP_CONF_PATH/manager:$APP_LIB_COMMON_PATH/*:$APP_HOME/lib/webmvc/*:$APP_HOME/lib/manager/*"
  APP_MAIN_CLASS='com.cs.manager.ManagerApplication'
elif [ "$module" = "executor" ]; then
  CLASSPATH="$APP_CONF_PATH/executor:$APP_LIB_COMMON_PATH/*:$APP_HOME/lib/webmvc/*:$APP_HOME/lib/executor/*"
  APP_MAIN_CLASS='com.cs.executor.ExecutorApplication'
elif [ "$module" = "gateway" ]; then
  CLASSPATH="$APP_CONF_PATH/gateway:$APP_LIB_COMMON_PATH/*:$APP_HOME/lib/webflux/*:$APP_HOME/lib/gateway/*"
  APP_MAIN_CLASS='com.cs.gateway.GatewayApplication'
else
  echo "Error: No module named '$module' was found."
  exit 1
fi

# 执行命令：保留一次自动重试（aarch64 首启 SIGBUS 等竞态可自愈），
# 但结尾必须以 JVM 的真实退出码退出——勿再加会吞掉失败状态的收尾 echo
[ -d "${APP_HOME}/run" ] || mkdir -p "${APP_HOME}/run"
echo "cd ${APP_HOME} && $JAVA -cp $CLASSPATH $JVMFLAGS $APP_MAIN_CLASS"
runModule() {
  cd "${APP_HOME}" && "$JAVA" -cp "$CLASSPATH" $JVMFLAGS "$APP_MAIN_CLASS"
}
runModule || {
  # one automatic retry: startup races (e.g. aarch64 first-boot SIGBUS) leave the
  # container exited-0 otherwise; a single relaunch is enough to self-heal
  echo "JVM exited abnormally (code $?), retrying once..."
  sleep 2
  runModule
}

exit $?
