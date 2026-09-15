# GroovyDefine Language (GDL) Engine

GDL（GroovyDefine Language）是基于 Groovy 语法的领域特定模型语言（DSL），运行在 TRE（TaoSha Runtime Engine）分布式规则与计算引擎中。
它提供流批一体的数据建模、ETL 编排、面向业务的本体（Ontology）建模、跨数据中心漂移（Drift）以及可扩展多数据源访问能力。

---

## 模块架构

工程基于 Java 17 + Groovy 4，采用标准 Maven 多模块体系构建，顶层包名统一为 `com.pl.gdl`：

```text
gdl-parent (pom.xml)
├── gdl-common        // 通用模型
├── gdl-dataframe     // DataFrame、算子、数据源 SPI、SQL 方言与执行引擎
├── gdl-runtime       // Groovy DSL 编译执行、变量与 DAG
├── gdl-ontology      // 业务本体
├── gdl-drift         // 跨节点漂移计算
└── gdl-server        // TreClient SDK 与 MCP 工具
```

多数据源核心分层：

```text
GDL DSL
  │ datasource(type, config) / hive() / postgres() / mysql() / sqlite() / h2()
  ▼
DatasourceRegistry
  ├── DatasourceProvider SPI
  ├── capability discovery
  └── ServiceLoader / runtime register
  ▼
CmdDatasource
  ├── JdbcDatasource ── PostgreSQL / MySQL / SQLite / H2
  ├── HiveDatasource
  └── LlmDatasource
  ▼
ExecutionEngine
  ├── JdbcExecutionEngine + SqlDialect
  ├── InMemoryEngine
  └── existing non-JDBC execution paths
```

---

## 多数据源支持矩阵

| 类型 | DSL / Registry | SQL 方言 | JDBC 实际查询 | 读/写能力 | 说明 |
| --- | --- | --- | --- | --- | --- |
| H2 | ✅ | ✅ | ✅ | ✅ | 本地开发、单测、嵌入式场景 |
| SQLite | ✅ | ✅ | ✅ | ✅ | 文件或 `:memory:` 数据库 |
| PostgreSQL | ✅ | ✅ | ✅ | ✅ | JDBC Driver 随模块运行时依赖提供 |
| MySQL | ✅ | ✅ | ✅ | ✅ | JDBC Driver 随模块运行时依赖提供 |
| Hive | ✅ | ✅ | 当前沿用 SQL 规划/下推能力 | ✅ | 保持原有 Hive DSL 兼容，不伪装成 JDBC |
| LLM | ✅ | N/A | N/A | 专用 | 非关系型远程能力，沿用 LLM 算子体系 |

> “多数据源”当前表示：同一 GDL 工具中可以注册、配置并独立执行不同物理数据源；**尚不宣称单条 SQL 自动完成跨 PostgreSQL/MySQL/SQLite 的联邦 Join**。跨地域/跨执行域仍由现有 Drift/Federated 层承担，后续可在 capability-aware planner 上继续扩展。

---

## 数据源使用

### 兼容原有 DSL

```groovy
def hiveDs = hive()
def pg = postgres("127.0.0.1", 5432, "app", "user", "pass")
def llmDs = llm("http://llm.internal/v1", 4)
```

### 新增便捷 DSL

```groovy
def mysqlDs = mysql("127.0.0.1", 3306, "app", "user", "pass")
def sqliteDs = sqlite("data/app.db")
def h2Ds = h2()
```

### 推荐的通用配置化方式

```groovy
def pg = datasource("POSTGRES", [
    host: "db.internal",
    port: 5432,
    database: "app",
    username: "gdl",
    password: "secret"
])

def local = datasource("SQLITE", [path: "data/local.db"])
def memory = datasource("H2", [url: "jdbc:h2:mem:demo;DB_CLOSE_DELAY=-1"])

def df = query(memory, "SELECT 42 AS answer")
returnDf(df)
```

`GdlExecutionContext` 会根据 `CmdDatasource#getDatasourceType()` 从 `DatasourceRegistry` 查找 Provider，并选择对应执行引擎。JDBC 数据源统一使用 `JdbcExecutionEngine`，SQL 生成仍由各自 `SqlDialect` 负责。

---

## 可扩展 Provider SPI

新增数据源不需要修改 `GdlScriptBase` 或核心 Registry。最小实现：

```java
public final class CustomProvider implements DatasourceProvider {
    @Override
    public String getType() {
        return "CUSTOM";
    }

    @Override
    public CmdDatasource create(Map<String, Object> config) {
        return new CustomDatasource(config);
    }

    @Override
    public Set<DatasourceCapability> getCapabilities() {
        return EnumSet.of(DatasourceCapability.READ, DatasourceCapability.SQL);
    }
}
```

运行时注册：

```java
DatasourceRegistry registry = new DatasourceRegistry();
registry.register(new CustomProvider());
```

也可以通过标准 Java `ServiceLoader` 发布插件，在插件 JAR 中增加：

```text
META-INF/services/com.pl.gdl.dataframe.datasource.DatasourceProvider
```

文件内容填写 Provider 的全限定类名。`DatasourceRegistry` 初始化时会自动发现。

如果是 JDBC 数据源，实现 `JdbcDatasource` 并让 Provider 返回对应 `SqlDialect`，即可复用统一 `JdbcExecutionEngine`，避免为每一种数据库重复实现查询执行器。

---

## 能力发现

Provider 可通过 `DatasourceCapability` 声明：

- `READ` / `WRITE`
- `SQL` / `JDBC`
- `TRANSACTION`
- `PARTITIONED_WRITE`
- `REMOTE_EXECUTION`
- `LLM`

调用方可以用 `DatasourceRegistry.capabilities(type)` 做能力判断，避免依赖 `if (type == ...)` 的硬编码分支。

---

## 算子与现有能力

- **基础算子**：`from`, `where`, `select`, `mapping`, `withColumn`, `group`, `sort`, `index`, `limit`, `distinct`, `distributeSort`, `groupSortFirst`, `alias`, `nodeId`, `depend`
- **集合算子**：`union`, `unionAll`, `subtract`, `subtractAll`, `intersect`, `intersectAll`
- **关联算子**：`join`, `leftJoin`, `rightJoin`, `fullJoin`, `exists`, `notExists`
- **输出算子**：`to`, `overwriteTo`, `fields`, `ttl`, `overwrite`, `partition`, `upsert`, `view`
- **流计算窗口**：`tumbleWindow`, `hopWindow`, `cumulateWindow`
- **调度信号量**：`periodReactor`, `taskReactor`, `increment`
- **高级算子**：`http`、`llmCall`、`groovy`、`python`、`CustomProcess`
- **动态变量**：`CurrentTimeVar`、`CommonIncrementVar`、BDOS 分区类变量
- **Ontology**：注解模型、动态类加载、DDL 与 CRUD
- **Drift**：areaCode 亲和性、子图切割、中间表与跨节点执行编排
- **Server / MCP**：TreClient、`start_task`、`get_task_result`、`get_tsml_to_dag`

---

## 工程边界与下一步扩展点

当前架构已经把“增加数据源”从修改 DSL 核心，降低为实现 Provider / Datasource / Dialect。后续适合继续扩展：

1. capability-aware planner：根据能力自动决定 SQL 下推、内存执行或 Drift。
2. 跨物理数据源联邦 Join：将不同 Provider 的子图拆分执行并通过中间表/Arrow 等交换。
3. 连接池与连接生命周期：把 HikariCP 纳入 Provider/Engine 生命周期管理。
4. 凭据管理：从明文配置升级到 Secret Provider / 环境变量 / KMS。
5. 元数据发现：库、表、字段、主键、分区及健康检查统一 SPI。
6. 更多 Provider：Oracle、SQL Server、KingbaseES、GaussDB、Doris、ClickHouse、REST/Object Storage/Kafka 等。

---

## 测试与构建

```bash
# 与 CI 一致：编译、单元测试、集成验证和 Maven 生命周期校验
mvn -B -ntp verify
```

GitHub Actions 会对 `main`、PR 以及 `chatgpt/**` 分支运行同一验证命令。
