#!/bin/sh

module=$1

APP_HOME="${BASH_SOURCE-$0}"
APP_HOME="$(dirname "${APP_HOME}")"
APP_HOME="$(cd "${APP_HOME}"; pwd)"
APP_HOME="$(cd "$(dirname ${APP_HOME})"; pwd)"
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
# -XX:+PerfDisableSharedMem: the JDK perfdata file lands in java.io.tmpdir; when the host
# bind-mounts /tmp (macOS Docker Desktop virtiofs), zeroing that 32KB mmap SIGBUSes the JVM
# at startup. Disabling shared-mem perfdata removes the mmap entirely.
JVMFLAGS="-server -Xms1024m -Xmx1024m -Xmn1024m -XX:+DisableExplicitGC -XX:+PerfDisableSharedMem -Djava.awt.headless=true -Dfile.encoding=UTF-8 "

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

# 执行命令
[ -d "${APP_HOME}/run" ] || mkdir -p "${APP_HOME}/run"
echo "cd ${APP_HOME} && $JAVA -cp $CLASSPATH $JVMFLAGS $APP_MAIN_CLASS"
runModule() {
  cd ${APP_HOME} && $JAVA -cp $CLASSPATH $JVMFLAGS $APP_MAIN_CLASS
}
runModule || {
  # one automatic retry: startup races (e.g. aarch64 first-boot SIGBUS) leave the
  # container exited-0 otherwise; a single relaunch is enough to self-heal
  echo "JVM exited abnormally (code $?), retrying once..."
  sleep 2
  runModule
}

echo "Finish start $module !"
