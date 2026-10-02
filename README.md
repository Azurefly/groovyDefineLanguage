# GroovyDefine Language (GDL) Engine

[![CI](https://github.com/Azurefly/groovyDefineLanguage/actions/workflows/ci.yml/badge.svg)](https://github.com/Azurefly/groovyDefineLanguage/actions/workflows/ci.yml)
[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-17-orange.svg)](https://openjdk.org/projects/jdk/17/)
[![Groovy](https://img.shields.io/badge/Groovy-4.0-green.svg)](https://groovy-lang.org/)

GDL（GroovyDefine Language）是基于 Groovy 语法的领域特定模型语言（DSL），用于分布式规则与计算引擎。
它提供流批一体的数据建模、ETL 编排、面向业务的本体（Ontology）建模、跨数据中心漂移（Drift）以及可扩展多数据源访问能力。

---

## 模块架构

工程基于 Java 17 + Groovy 4，采用标准 Maven 多模块体系构建，顶层包名统一为 `com.pl.gdl`：

```text
gdl-parent (pom.xml)
├── gdl-common        // 通用模型（RowDataFrame、ColumnInfo、DataType、TaskResult等）
├── gdl-dataframe     // DataFrame、算子、Provider SDK、SQL 方言与 JDBC 执行
├── gdl-runtime       // Groovy DSL、能力规划、联邦规划与执行、动态变量体系与 DAG 生成
├── gdl-ontology      // 业务本体系统（@Table/@Column 注解、Ontology基类、动态ClassLoader热加载、DDL生成与CRUD）
├── gdl-drift         // 跨区域 Exchange、漂移计算（areaCode 亲和性分析、DAG边界切割、driftTo/driftFrom 自动注入与中间表管理）
└── gdl-server        // 服务接入层（TreClient Java SDK、HTTP REST 服务、MCP 流式协议工具接口）
```

多数据源执行链：

```text
GDL DSL
  │ datasource(type, config)
  ▼
DatasourceRegistry / Provider SDK
  ├── descriptor / config validation
  ├── capability discovery
  ├── SecretResolver
  ├── health / metadata
  └── ServiceLoader plugins
  ▼
Capability-aware Planner
  ├── READ / SQL_READ / SQL_WRITE
  ├── PROVIDER_PUSHDOWN
  └── FALLBACK
  ▼
Federated DAG Planner
  ├── execution domain = areaCode + datasourceType + datasourceName
  ├── same-domain contiguous fragment
  ├── same-area cross-provider => MEMORY exchange
  └── cross-area => REMOTE_DRIFT exchange
  ▼
Execution
  ├── JdbcExecutionEngine + SqlDialect + HikariCP
  ├── existing fallback paths
  ├── FederatedJoinExecutor
  └── Drift intermediate table transport
```

---

## 多数据源支持矩阵

| 类型 | Registry / DSL | JDBC 实际查询 | 连接池 | 元数据/健康 | Planner | 联邦源查询 | 说明 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| H2 | ✅ | ✅ 实测 | ✅ 实测 | ✅ 实测 | Pushdown | ✅ 实测 | 本地开发、测试、嵌入式 |
| SQLite | ✅ | ✅ 实测 | ✅ 实测 | ✅ 实测 | Pushdown | ✅ 实测 | 文件或 `:memory:` |
| PostgreSQL | ✅ | ✅ 实测 | ✅ 实测 | ✅ 实测 | Pushdown | 架构支持 | 真实 PG 14 Server 验证通过（建表/查询/健康检查/元数据） |
| MySQL | ✅ | 待真实 Server | 待真实 Server | 优雅降级已测 | Pushdown | 架构支持 | Provider/URL/方言已测；嵌入式测试在 CI 执行 |
| Hive | ✅ | 现有路径 | — | 待专用实现 | Fallback | 待扩展 | 保持既有 Hive 兼容 |
| LLM | ✅ | N/A | — | 待专用实现 | 专用路径 | N/A | 非关系型远程能力 |

> 实测覆盖：
> - `DatasourceMatrixTest`：H2/SQLite 真实建表、插入、查询、连接池、健康检查、元数据全链路验证；PG/MySQL Provider 构造、JDBC URL、方言 SQL 生成、无服务时健康检查优雅降级。
> - `RealPostgresTest`：使用开源 embedded-postgres 启动真实 PostgreSQL Server，验证建表/插入/查询、连接池、健康检查、元数据、PG 特有语法（`ON CONFLICT`、`ILIKE`）。已用真实 PG 14.10 手动验证通过。
> - `RealMysqlTest`：使用开源 wix-embedded-mysql 启动真实 MySQL Server，验证建表/插入/查询、连接池、健康检查、MySQL 特有语法（反引号、`LIMIT OFFSET`）。在 CI 环境执行。

当前已经存在**真实跨物理数据源执行路径**：H2 与 SQLite 分别执行 SQL 下推，将结果通过内存 Exchange 物化，再进行 Hash Join。它不是“把任意跨库 SQL 原样发出去”，也不宣称已经支持任意 DataFrame 算子图的自动联邦化。

---

## 核心功能与特性

### 1. 算子流水线体系
- **基础算子**：`from`, `where`, `select`, `mapping`, `withColumn`, `group`, `sort`, `index`, `limit`, `distinct`, `distributeSort`, `groupSortFirst`, `alias`, `nodeId`, `depend`
- **集合算子**：`union`, `unionAll`, `subtract`, `subtractAll`, `intersect`, `intersectAll`
- **关联算子**：`join`, `leftJoin`, `rightJoin`, `fullJoin`, `exists`, `notExists`
- **输出算子**：`to`, `overwriteTo`, `fields`, `ttl`, `overwrite`, `partition`, `upsert`, `view`
- **流计算窗口**：`tumbleWindow`, `hopWindow`, `cumulateWindow`
- **调度信号量**：`periodReactor`, `taskReactor`, `increment`
- **高级算子**：`http`（支持 GET/POST/分页/认证/`REF{}`动态取值）、`llmCall`、`groovy` 自定义闭包、`python` 脚本、`CustomProcess` 自定义扩展
  - 安全说明：`groovy` 脚本默认在沙箱中编译执行（import 白名单、禁止 `System.exit`/`Runtime.exec`/`.execute()`/文件类等危险调用），`GdlCompiler.trusted()` 仅限受信任环境使用

### 2. 动态变量生成器
通过 `variable("生成器名称", 参数Map)` 动态求值：
- `CurrentTimeVar`: 支持 `DATE8`, `DATE10`, `DATE14`, `DATE_8`, `DATE_14`, `SEC` 等时间变量
- `CommonIncrementVar`: 增量高水位线抽取条件
- `BdosPartitionIncrementVar`: 分区增量范围抽取
- `BdosPartitionModifyIncrementVar`: 分区最后修改时间增量
- `BdosReadLastNPartitionVar`: 最新 N 个分区增量
- `BdosReadLastOnePartitionVar`: 最新单一分区
- `BdosReadLastPartitionVar`: 读取最新分区区间

### 3. 业务本体模型（Ontology）
- 业务类继承 `Ontology`，使用 `@Table` 与 `@Column` 注解定义模型元数据与物理表映射
- 支持通过 `OntologyRegistry` 进行动态字节码编译与热加载，规避 Metaspace 内存泄露
- 支持 `save`, `delete`, `update`, `addColumns`, `dropTable` 物理表操作
- 支持链式查询 `where`, `select`, `mapping`, `sort`, `limit`, `distinct`，及跨地市漂移查询与多本体数据传递

### 4. 跨节点漂移计算（Drift）
- 依据数据源的 `areaCode`，自动分析全图亲和性
- 遇到跨地域边时，自动将 DAG 图切割为本地执行子图与远端执行子图
- 自动生成 `driftTo().attach()` 发送与 `driftFrom()` 接收逻辑
- 传输使用 `tre_temp_{uuid}_{timestamp}` 临时表，任务执行完毕后保证自动清理

### 5. 第三方远程调用与服务化支持
- **独立 HTTP 服务**：内置高可用 HTTP 服务，通过 `./bin/start-server.sh <port> [token]` 启动
  - 安全默认：鉴权默认开启。启动时传入 `token` 即启用鉴权（请求头 `tre-token`）；未传 `token` 则进入 open 模式并打印醒目警告，仅建议本地调试使用
- **Java 远程 SDK**：提供 `TreRemoteHttpClient` 实现透明 RPC 远程调用
- **HTTP RESTful API**：支持各类第三方系统（Python, Go, Node.js 等）通过 HTTP 接口触发任务计算、查询结果与注册本体
- **MCP 协议支持**：支持 Model Context Protocol，暴露 `start_task`、`get_task_result` 工具方法，方便 AI Agent / 大模型客户端（CherryStudio、Claude、Dify 等）直接调度
- 详见文档：[docs/REMOTE_API_GUIDE.md](docs/REMOTE_API_GUIDE.md)

---

## 数据源使用

### 通用配置

```groovy
def pg = datasource("POSTGRES", [
    host: "db.internal",
    port: 5432,
    database: "app",
    username: "gdl",
    passwordRef: "env:GDL_DB_PASSWORD"
])

def local = datasource("SQLITE", [path: "data/local.db"])
def memory = datasource("H2", [url: "jdbc:h2:mem:demo;DB_CLOSE_DELAY=-1"])

def df = query(memory, "SELECT 42 AS answer")
returnDf(df)
```

原有 `hive()`、`postgres()`、`mysql()`、`sqlite()`、`h2()`、`llm()` helper 保持可用。

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
# 启动 GDL 远程服务
./bin/start-server.sh 8080

# 运行全量模块单元测试与验证
mvn -B -ntp verify
```
