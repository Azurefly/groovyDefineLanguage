# GDL Demo — 真实可运行演示项目

本目录是一个**独立 Maven 项目**，通过依赖引入 GDL 引擎（`com.pl.gdl:gdl-dataframe` 等），
演示 GDL 的核心能力。所有 Demo 均可在本地真实运行，无需外部数据库。

## 前置条件

1. JDK 17+
2. 先把 GDL 安装到本地 Maven 仓库（在项目根目录执行）：
   ```bash
   cd .. && mvn -o -B -ntp install -DskipTests -pl gdl-common,gdl-dataframe,gdl-runtime -am
   ```

## 运行 Demo

**推荐：一键运行脚本**（自动打包 shade jar，无需手动处理 classpath）：

```bash
cd demo
./run-demo.sh 1   # Demo 1：H2 内存 ETL 全流程（过滤→投影→聚合→排序→TopN）
./run-demo.sh 2   # Demo 2：跨源联邦查询（用户表 left join 订单表）
./run-demo.sh 3   # Demo 3：LLM 大模型调用（需要先启动 Ollama）
                  #   ollama serve &
                  #   ollama pull qwen2:0.5b
                  # 可通过环境变量覆盖：LLM_URL、LLM_MODEL
```

<details>
<summary>手动运行（备选）</summary>

```bash
cd demo
mvn -o compile

# Demo 1：H2 内存 ETL 全流程（过滤→投影→聚合→排序→TopN）
mvn -o exec:java -Dexec.mainClass=com.pl.gdl.demo.H2EtlDemo

# Demo 2：跨源联邦查询（用户表 left join 订单表）
mvn -o exec:java -Dexec.mainClass=com.pl.gdl.demo.FederatedDemo

# Demo 3：LLM 大模型调用（需要先启动 Ollama）
#   ollama serve &
#   ollama pull qwen2:0.5b
mvn -o exec:java -Dexec.mainClass=com.pl.gdl.demo.LlmDemo
# 可通过环境变量覆盖：LLM_URL、LLM_MODEL
```

> 注：`exec:java` 需要 `exec-maven-plugin`，首次运行时会自动下载。
> 如需离线运行，可改用 `mvn -o package` 打包后用 `java -cp` 直接运行。
</details>

## Demo 说明

| Demo | 内容 | 依赖 |
|------|------|------|
| H2EtlDemo | 内存表注册、where/select/group/sort/limit 全链路 | 无 |
| FederatedDemo | 双表 leftJoin 联邦查询 | 无 |
| LlmDemo | llmCall 情感分析，结果写回新列 | Ollama 服务 |

## 预期输出（Demo 1）

```
=== GDL Demo 1: H2 内存 ETL 全流程 ===
已注册订单表 t_orders，共 6 行

--- 用户消费 Top 榜 ---
user_id    total_amount    order_cnt
U02        380.0           2
U01        170.75          2
U03        150.75          1

Demo 1 执行成功！
```
