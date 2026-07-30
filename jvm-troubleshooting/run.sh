#!/usr/bin/env bash
# JVM 实战排查练习 - 启动脚本（Linux 云服务器友好）
#
# 推荐用法（云服务器只有 JRE 也行）：
#   本地:   mvn -pl jvm-troubleshooting -am clean package -DskipTests
#           把 target/jvm-troubleshooting.jar 和本脚本一起上传到服务器
#   服务器: bash run.sh            # 有 jar 就直接跑，不强制装 maven
#
# 其它模式:
#   bash run.sh build      # 服务器上装了 maven 时，先编译再跑
#   bash run.sh skip-build # 显式跳过编译
#
# 用小堆 + GC 日志 + OOM 自动 dump 启动，便于练习排查。
set -e

MODULE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APP_JAR="$MODULE_DIR/target/jvm-troubleshooting.jar"
DUMP_DIR="$MODULE_DIR/heapdump"
GC_LOG="$MODULE_DIR/gc.log"

mkdir -p "$DUMP_DIR"

MODE="${1:-auto}"

# 决定是否需要编译：build 显式编译；auto 在没有 jar 时才编译
NEED_BUILD=0
if [ "$MODE" = "build" ]; then
  NEED_BUILD=1
elif [ "$MODE" = "skip-build" ]; then
  NEED_BUILD=0
elif [ ! -f "$APP_JAR" ]; then
  echo "==> 未找到 $APP_JAR"
  if command -v mvn >/dev/null 2>&1; then
    echo "==> 检测到 maven，自动编译"
    NEED_BUILD=1
  else
    echo "错误：找不到 jar 且服务器未安装 maven。" >&2
    echo "请在本地执行: mvn -pl jvm-troubleshooting -am clean package -DskipTests" >&2
    echo "然后把 target/jvm-troubleshooting.jar 上传到服务器后重试。" >&2
    exit 1
  fi
fi

if [ "$NEED_BUILD" = "1" ]; then
  echo "==> 编译打包"
  PROJECT_ROOT="$(cd "$MODULE_DIR/.." && pwd)"
  cd "$PROJECT_ROOT"
  mvn -pl jvm-troubleshooting -am clean package -DskipTests -q
fi

if [ ! -f "$APP_JAR" ]; then
  echo "找不到 $APP_JAR。" >&2
  exit 1
fi

# 解析 java 可执行文件：优先 JAVA_HOME，其次 PATH
if [ -n "$JAVA_HOME" ] && [ -x "$JAVA_HOME/bin/java" ]; then
  JAVA_BIN="$JAVA_HOME/bin/java"
elif command -v java >/dev/null 2>&1; then
  JAVA_BIN="java"
else
  echo "错误：找不到 java。" >&2
  echo "请确认已安装 JDK/JRE，然后用下面任一方式：" >&2
  echo "  1) export JAVA_HOME=/你的/jdk路径  （例如 /usr/lib/jvm/java-21-openjdk）" >&2
  echo "  2) 把 java 加入 PATH：export PATH=\$JAVA_HOME/bin:\$PATH" >&2
  echo "查找已安装的 JDK：ls /usr/lib/jvm/  或  which java" >&2
  exit 1
fi

# 练习用 JVM 参数：小堆、G1、GC 日志、OOM 自动 dump
JVM_OPTS="-Xms256m -Xmx256m \
  -XX:+UseG1GC \
  -Xlog:gc*:file=$GC_LOG:time,uptime,level,tags:filecount=5,filesize=10m \
  -XX:+HeapDumpOnOutOfMemoryError \
  -XX:HeapDumpPath=$DUMP_DIR \
  -XX:ErrorFile=$MODULE_DIR/hs_err_%p.log"

echo "==> 以练习参数启动"
echo "    java: $JAVA_BIN"
echo "    堆: -Xms256m -Xmx256m (G1)"
echo "    GC 日志: $GC_LOG"
echo "    OOM dump 目录: $DUMP_DIR"
echo "    服务端口: 8000"
echo "    触发示例: curl http://localhost:8000/jvm/cpu/start?threads=4"
echo

exec "$JAVA_BIN" $JVM_OPTS -jar "$APP_JAR"
