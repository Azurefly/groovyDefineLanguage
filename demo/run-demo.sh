#!/usr/bin/env bash
# GDL Demo 一键运行脚本
# 用法: ./run-demo.sh [1|2|3]  -- 1=H2 ETL, 2=跨源联邦查询, 3=LLM 情感分析（需 Ollama）
set -e
cd "$(dirname "$0")"

DEMO="${1:-1}"
MAIN_CLASS=""
case "$DEMO" in
  1) MAIN_CLASS="com.pl.gdl.demo.H2EtlDemo" ;;
  2) MAIN_CLASS="com.pl.gdl.demo.FederatedDemo" ;;
  3) MAIN_CLASS="com.pl.gdl.demo.LlmDemo" ;;
  *) echo "用法: $0 [1|2|3]"; echo "  1 - H2 ETL 全流程"; echo "  2 - 跨源 left join"; echo "  3 - LLM 情感分析（需 Ollama）"; exit 1 ;;
esac

JAR=$(ls target/gdl-demo-*-shaded.jar 2>/dev/null | head -1 || true)
if [ -z "$JAR" ]; then
  echo "未找到 shade 包，正在打包..."
  mvn -q -o package -DskipTests
  JAR=$(ls target/gdl-demo-*-shaded.jar 2>/dev/null | head -1 || true)
fi
if [ -z "$JAR" ]; then
  echo "打包失败，请检查 Maven 输出"
  exit 1
fi

echo "运行 $MAIN_CLASS ..."
java -cp "$JAR" "$MAIN_CLASS"
