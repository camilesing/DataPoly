#!/usr/bin/env bash

USAGE="Usage: datapolyctl.sh (start|stop|status) <manager|executor|gateway> "
# if no args specified, show usage
if [ $# -le 1 ]; then
  echo $USAGE
  exit 1
fi

operator=$1
shift
module=$1
shift

echo "Begin $operator $module......"

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

# 定义配置读取函数（兼容 macOS / Linux）
get_config_value() {
    local key=$1
    local file=$2
    grep "^${key}=" "$file" | cut -d'=' -f2- | tr -d '\r'
}

# 读取配置项
export APP_DRIVERS_PATH=$APP_HOME/drivers
export MANAGER_HOST=$(get_config_value "MANAGER_HOST" "${APP_CONF_PATH}/config.ini")
export MANAGER_PORT=$(get_config_value "MANAGER_PORT" "${APP_CONF_PATH}/config.ini")
export EXECUTOR_PORT=$(get_config_value "EXECUTOR_PORT" "${APP_CONF_PATH}/config.ini")
export GATEWAY_PORT=$(get_config_value "GATEWAY_PORT" "${APP_CONF_PATH}/config.ini")
export DB_TYPE=$(get_config_value "DB_TYPE" "${APP_CONF_PATH}/config.ini")
export MYSQLDB_HOST=$(get_config_value "MYSQLDB_HOST" "${APP_CONF_PATH}/config.ini")
export MYSQLDB_PORT=$(get_config_value "MYSQLDB_PORT" "${APP_CONF_PATH}/config.ini")
export MYSQLDB_NAME=$(get_config_value "MYSQLDB_NAME" "${APP_CONF_PATH}/config.ini")
export MYSQLDB_USERNAME=$(get_config_value "MYSQLDB_USERNAME" "${APP_CONF_PATH}/config.ini")
export MYSQLDB_PASSWORD=$(get_config_value "MYSQLDB_PASSWORD" "${APP_CONF_PATH}/config.ini")
export PGDB_HOST=$(get_config_value "PGDB_HOST" "${APP_CONF_PATH}/config.ini")
export PGDB_PORT=$(get_config_value "PGDB_PORT" "${APP_CONF_PATH}/config.ini")
export PGDB_NAME=$(get_config_value "PGDB_NAME" "${APP_CONF_PATH}/config.ini")
export PGDB_USERNAME=$(get_config_value "PGDB_USERNAME" "${APP_CONF_PATH}/config.ini")
export PGDB_PASSWORD=$(get_config_value "PGDB_PASSWORD" "${APP_CONF_PATH}/config.ini")
export JSON_TIMEZONE=$(get_config_value "JSON_TIMEZONE" "${APP_CONF_PATH}/config.ini")
export DATAPOLY_MANAGER_URL=$(get_config_value "DATAPOLY_MANAGER_URL" "${APP_CONF_PATH}/config.ini")
export DATAPOLY_GATEWAY_URL=$(get_config_value "DATAPOLY_GATEWAY_URL" "${APP_CONF_PATH}/config.ini")

# JVM参数可以在这里设置
JVMFLAGS="-server -Xms1024m -Xmx1024m -Xmn1024m -XX:+DisableExplicitGC -Djava.awt.headless=true -Dfile.encoding=UTF-8 "

if [ "$JAVA_HOME" != "" ]; then
  JAVA="$JAVA_HOME/bin/java"
else
  JAVA=java
fi

# 配置classpath和启动类
CLASSPATH=$APP_CONF_PATH
APP_MAIN_CLASS='com.cs.manager.ManagerApplication'
if [ "$module" == "manager" ]; then
  CLASSPATH="$APP_CONF_PATH/manager:$APP_LIB_COMMON_PATH/*:$APP_HOME/lib/webmvc/*:$APP_HOME/lib/manager/*"
  APP_MAIN_CLASS='com.cs.manager.ManagerApplication'
elif [ "$module" == "executor" ]; then
  CLASSPATH="$APP_CONF_PATH/executor:$APP_LIB_COMMON_PATH/*:$APP_HOME/lib/webmvc/*:$APP_HOME/lib/executor/*"
  APP_MAIN_CLASS='com.cs.executor.ExecutorApplication'
elif [ "$module" == "gateway" ]; then
  CLASSPATH="$APP_CONF_PATH/gateway:$APP_LIB_COMMON_PATH/*:$APP_HOME/lib/webflux/*:$APP_HOME/lib/gateway/*"
  APP_MAIN_CLASS='com.cs.gateway.GatewayApplication'
else
  echo "Error: No module named '$module' was found."
  exit 1
fi

# 执行命令
case $operator in
  (start)
    [ -d "${APP_HOME}/run" ] || mkdir -p "${APP_HOME}/run"
    cd ${APP_HOME}
    echo -n `date +'%Y-%m-%d %H:%M:%S'`            >>${APP_RUN_LOG}
    echo "---- Start service [${module}] process. ">>${APP_RUN_LOG}
    res=`ps aux|grep java|grep $APP_HOME|grep $APP_MAIN_CLASS |grep -v grep|awk '{print $2}'`
    if [ -n "$res"  ]; then
       echo "$res program  [${module}] is already running"
       exit 1
    fi
    nohup $JAVA -cp $CLASSPATH $JVMFLAGS $APP_MAIN_CLASS >>${APP_RUN_LOG} 2>&1 &
    echo $! > ${APP_PID_FILE}
    ;;

  (stop)
    PID_LIST=`ps -ef | grep java | grep "$APP_HOME" | grep "$APP_MAIN_CLASS" | awk '{print $2}'`
    if [ -z "$PID_LIST" ]; then
      echo "ERROR: The module server does not started!"
      exit 1
    fi
    printf "Stopping the module server ..."
    for PID in $PID_LIST ; do
      kill $PID > /dev/null 2>&1
    done

    COUNT=0
    while [ $COUNT -lt 1 ]; do
        printf "."
        sleep 1
        COUNT=1
        for PID in $PID_LIST ; do
          PID_EXIST=`ps -f -p $PID | grep java`
          if [ -n "$PID_EXIST" ]; then
              COUNT=0
              break
          fi
        done
    done
    echo "Stop OK, and PID: $PID_LIST"
    ;;

  (status)
    serverCount=`ps -ef |grep "$APP_MAIN_CLASS" | grep -v "grep" | wc -l`
    state="STOP"
    #  font color - red
    state="[ \033[1;31m $state \033[0m ]"
    if [[ $serverCount -gt 0 ]];then
      state="RUNNING"
      # font color - green
      state="[ \033[1;32m $state \033[0m ]"
    fi
    printf "$module  $state\n"
    ;;

  (*)
    echo $USAGE
    exit 1
    ;;

esac

echo "Finish $operator $module !"