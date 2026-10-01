#!/usr/bin/env bash
# ==============================================================================
# GDL / TRE Engine Remote Service Startup Script
# ==============================================================================
set -e

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$DIR"

PORT=${1:-8080}
TOKEN=${2:-""}

echo "=== 正在启动 GDL 引擎远程服务 ==="
echo "工作目录: $DIR"
echo "服务端口: $PORT"

CP=$(find /Users/pan/.m2/repository -name "groovy-4.0.18.jar" -o -name "groovy-json-4.0.18.jar" -o -name "slf4j-api-2.0.13.jar" -o -name "h2-2.2.224.jar" -o -name "okhttp-4.12.0.jar" -o -name "assertj-core-3.24.2.jar" -o -name "junit-jupiter-api-5.10.2.jar" -o -name "apiguardian-api-*.jar" -o -name "byte-buddy-1.14.9.jar" 2>/dev/null | tr '\n' ':')

if [ ! -d "target/classes" ]; then
    echo "正在编译源码..."
    find gdl-*/src/main/java gdl-*/src/test/java -name "*.java" > sources.txt
    mkdir -p target/classes
    javac -cp "$CP" -d target/classes @sources.txt
    rm -f sources.txt
fi

ARGS="--port $PORT"
if [ -n "$TOKEN" ]; then
    ARGS="$ARGS --token $TOKEN"
fi

echo "服务启动参数: $ARGS"
echo "启动命令: java -cp target/classes:$CP com.pl.gdl.server.GdlServerApplication $ARGS"
exec java -cp "target/classes:$CP" com.pl.gdl.server.GdlServerApplication $ARGS
