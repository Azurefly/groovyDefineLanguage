# GDL 概述

GDL（GroovyDefine Language）是一门基于 Groovy 语法的模型语言，用于数据建模、ETL 编排和数据处理逻辑定义。

---

## 核心概念

| 概念 | 说明 |
|---|---|
| **TRE** | GDL 运行引擎，负责脚本解析与执行 |
| **CmdDataframe** | 数据集对象，所有算子的输入输出类型 |
| **CmdDatasource** | 数据源对象 |
| **本体（Ontology）** | 业务领域模型，封装了业务属性和操作方法 |
| **漂移** | 跨节点计算，将算子图切割为本地和远端执行子图 |

---

## 整体语法结构（BNF 简述）

```
GDL = { 变量声明 | 数据源定义 | 数据集初始化 | 算子链 | 控制结构 | 返回算子 | 注释 }
```

**典型脚本骨架：**

```groovy
// 1. 定义数据源
def hiveDs = hive()

// 2. 定义数据集
def df = from(hiveDs, "table_name").alias("a")

// 3. 算子链处理
def result = df.where("age > 18").select("name, age").sort("age desc")

// 4. 输出
result.to(hiveDs, "result_table")

// 5. 可选：返回给调用方
returnDf(result)
```

---

## 文件列表导读

| 文件 | 内容 |
|---|---|
| `01_GDL概述.md` | 语言简介与整体结构 |
| `02_数据源定义.md` | hive/postgres/llm 等数据源 |
| `03_数据集与基础算子.md` | from/where/select/mapping/group/sort/limit/distinct 等 |
| `04_集合与关联算子.md` | union/subtract/intersect/join/leftJoin 等 |
| `05_输出与SQL算子.md` | to/query/insert/returnDf/groovy/python 等 |
| `06_实时算子.md` | tumbleWindow/hopWindow/cumulateWindow/periodReactor |
| `07_控制流与变量.md` | if/else/for/variable/动态变量/系统变量 |
| `08_本体语法定义.md` | Ontology 基类、业务本体定义、CURD 操作 |
| `09_漂移算子.md` | areaCode/driftTo/driftFrom 跨节点计算 |
| `10_高级算子.md` | http/llmCall/groovy/python 等 |
