# GroovyDefine Language (GDL) Engine

GDL（GroovyDefine Language）是基于 Groovy 语法的领域特定模型语言（DSL），运行在 TRE（TaoSha Runtime Engine）分布式规则与计算引擎中。
它提供流批一体的数据建模、ETL 编排、面向业务的本体（Ontology）建模以及跨数据中心的漂移（Drift）分布式计算能力。

---

## 模块架构

工程基于 Java 17 + Groovy 4，采用标准 Maven 多模块体系构建，顶层包名统一为 `com.pl.gdl`：

```
gdl-parent (pom.xml)
├── gdl-common        // 通用模型（RowDataFrame、ColumnInfo、DataType、TaskResult等）
├── gdl-dataframe     // 数据集与算子（CmdDataframe、CmdDatasource、基础/集合/关联/输出/流窗口/HTTP/LLM算子及SQL下推）
├── gdl-runtime       // 运行时编译与执行（GroovyShell、GdlScriptBase、动态变量生成器体系、DAG图生成与序列化）
├── gdl-ontology      // 业务本体系统（@Table、@Column 注解、Ontology基类、动态ClassLoader热加载、DDL生成与CRUD）
├── gdl-drift         // 跨节点漂移计算（areaCode 亲和性分析、DAG边界切割、driftTo/driftFrom 自动注入与中间表管理）
└── gdl-server        // 服务接入层（TreClient Java SDK、MCP 流式协议工具接口：start_task、get_task_result、get_tsml_to_dag）
```

---

## 核心功能与特性

### 1. 支持数据源
- `hive(["conf"])`: Hive 3.1.x / 华为 Hive 数据源（别名 `hivehw()`）
- `postgres(host, port, db, user, pass)`: PostgreSQL 数据源
- `llm([url, concurrent])`: 大模型服务调用数据源
- 统一支持 `.areaCode("节点编码")` 标记地域归属

### 2. 算子流水线体系
- **基础算子**：`from`, `where`, `select`, `mapping`, `withColumn`, `group`, `sort`, `index`, `limit`, `distinct`, `distributeSort`, `groupSortFirst`, `alias`, `nodeId`, `depend`
- **集合算子**：`union`, `unionAll`, `subtract`, `subtractAll`, `intersect`, `intersectAll`
- **关联算子**：`join`, `leftJoin`, `rightJoin`, `fullJoin`, `exists`, `notExists`
- **输出算子**：`to`, `overwriteTo`, `fields`, `ttl`, `overwrite`, `partition`, `upsert`, `view`
- **流计算窗口**：`tumbleWindow`, `hopWindow`, `cumulateWindow`
- **调度信号量**：`periodReactor`, `taskReactor`, `increment`
- **高级算子**：`http`（支持 GET/POST/分页/认证/`REF{}`动态取值）、`llmCall`、`groovy` 自定义闭包、`python` 脚本、`CustomProcess` 自定义扩展

### 3. 动态变量生成器
通过 `variable("生成器名称", 参数Map)` 动态求值：
- `CurrentTimeVar`: 支持 `DATE8`, `DATE10`, `DATE14`, `DATE_8`, `DATE_14`, `SEC` 等时间变量
- `CommonIncrementVar`: 增量高水位线抽取条件
- `BdosPartitionIncrementVar`: 分区增量范围抽取
- `BdosPartitionModifyIncrementVar`: 分区最后修改时间增量
- `BdosReadLastNPartitionVar`: 最新 N 个分区增量
- `BdosReadLastOnePartitionVar`: 最新单一分区
- `BdosReadLastPartitionVar`: 读取最新分区区间

### 4. 业务本体模型（Ontology）
- 业务类继承 `Ontology`，使用 `@Table` 与 `@Column` 注解定义模型元数据与物理表映射
- 支持通过 `OntologyRegistry` 进行动态字节码编译与热加载，规避 Metaspace 内存泄露
- 支持 `save`, `delete`, `update`, `addColumns`, `dropTable` 物理表操作
- 支持链式查询 `where`, `select`, `mapping`, `sort`, `limit`, `distinct`，及跨地市漂移查询与多本体数据传递

### 5. 跨节点漂移计算（Drift）
- 依据数据源的 `areaCode`，自动分析全图亲和性
- 遇到跨地域边时，自动将 DAG 图切割为本地执行子图与远端执行子图
- 自动生成 `driftTo().attach()` 发送与 `driftFrom()` 接收逻辑
- 传输使用 `tre_temp_{uuid}_{timestamp}` 临时表，任务执行完毕后保证自动清理

### 6. TreClient SDK 与 MCP 协议支持
- **Java SDK**：`TreClient` 提供本体注册管理、全量/指定本体信息查询、任务下发与异步结果拉取
- **MCP 服务**：支持 Model Context Protocol，暴露 `start_task`、`get_task_result`、`get_tsml_to_dag` 工具方法，方便 AI Agent / 大模型客户端直接调度与可视化

---

## 快速使用示例

### 典型 GDL 脚本
```groovy
// 1. 定义数据源与动态变量
def hiveDs = hive()
def timeVar = variable("CurrentTimeVar", [timeVarName: "today", timeFormat: "yyyy-MM-dd"])

// 2. 提取与链式算子计算
def df = from(hiveDs, "dw.t_order")
    .alias("o")
    .where("o.create_date = '${timeVar.today}' and o.amount > 100")
    .select("o.order_id, o.user_id, o.amount")
    .sort("o.amount desc")
    .limit(100)

// 3. 结果入库与返回
df.to(hiveDs, "dw.t_order_top100").overwrite()
returnDf(df)
```

---

## 测试与构建

```bash
# 编译并运行全量模块单元测试
mvn clean test
```
