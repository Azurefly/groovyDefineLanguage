#!/usr/bin/env bash
# =============================================================================
# GDL Engine HTTP 服务启动脚本
# =============================================================================
# 用法: ./bin/start-server.sh [port] [token]
#   port  - 监听端口，默认 8080
#   token - 鉴权 token；不传则服务以 open 模式启动（仅建议本地调试）
# =============================================================================
set -euo pipefail

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$DIR"

PORT="${1:-8080}"
TOKEN="${2:-}"

command -v java >/dev/null 2>&1 || { echo "错误: 未找到 java，请先安装 JDK 17+" >&2; exit 1; }
command -v mvn >/dev/null 2>&1 || { echo "错误: 未找到 mvn，请先安装 Maven 3.8+" >&2; exit 1; }

echo "=== 正在构建 GDL 引擎 ==="
mvn -B -ntp -q -DskipTests package

echo "=== 正在组装运行时 classpath ==="
CP_FILE="$(mktemp)"
mvn -B -ntp -q -pl gdl-server -am dependency:build-classpath -Dmdep.outputFile="$CP_FILE" -Dmdep.includeScope=runtime
CP="$(cat "$CP_FILE")"
rm -f "$CP_FILE"
for m in gdl-common gdl-dataframe gdl-runtime gdl-ontology gdl-drift gdl-server; do
  CP="$DIR/$m/target/classes:$CP"
done

echo "=== 正在启动 GDL 引擎 HTTP 服务 ==="
echo "监听端口: $PORT"
if [ -z "$TOKEN" ]; then
  echo "警告: 未设置 token，服务将以 open 模式启动，仅建议本地调试使用！"
  exec java -cp "$CP" com.pl.gdl.server.GdlServerApplication --port "$PORT"
else
  exec java -cp "$CP" com.pl.gdl.server.GdlServerApplication --port "$PORT" --token "$TOKEN"
fi
