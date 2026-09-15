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
  ├── health / metadata discovery
  ├── SecretResolver
  └── ServiceLoader / runtime register
  ▼
CmdDatasource
  ├── JdbcDatasource ── PostgreSQL / MySQL / SQLite / H2
  ├── HiveDatasource
  └── LlmDatasource
  ▼
ExecutionEngine
  ├── JdbcExecutionEngine + SqlDialect + HikariCP
  ├── InMemoryEngine
  └── existing non-JDBC execution paths
```

---

## 多数据源支持矩阵

| 类型 | DSL / Registry | SQL 方言 | JDBC 实际查询 | 连接池 | 元数据/健康 | 读/写能力 | 说明 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| H2 | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | 本地开发、单测、嵌入式场景 |
| SQLite | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | 文件或 `:memory:` 数据库 |
| PostgreSQL | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | JDBC Driver 随模块运行时依赖提供 |
| MySQL | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | JDBC Driver 随模块运行时依赖提供 |
| Hive | ✅ | ✅ | 当前沿用 SQL 规划/下推能力 | — | 待专用实现 | ✅ | 保持原有 Hive DSL 兼容，不伪装成 JDBC |
| LLM | ✅ | N/A | N/A | — | 待专用实现 | 专用 | 非关系型远程能力，沿用 LLM 算子体系 |

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
    passwordRef: "env:GDL_DB_PASSWORD"
])

def local = datasource("SQLITE", [path: "data/local.db"])
def memory = datasource("H2", [url: "jdbc:h2:mem:demo;DB_CLOSE_DELAY=-1"])

def df = query(memory, "SELECT 42 AS answer")
returnDf(df)
```

`GdlExecutionContext` 会根据 `CmdDatasource#getDatasourceType()` 从 `DatasourceRegistry` 查找 Provider，并选择对应执行引擎。JDBC 数据源统一使用 `JdbcExecutionEngine`，SQL 生成仍由各自 `SqlDialect` 负责。

---

## 连接池与生命周期

JDBC 执行统一通过 `JdbcConnectionManager` 使用 HikariCP 连接池。同一组 `url + username + password + driver` 共用一个池，避免每次查询直接通过 `DriverManager` 新建物理连接。

默认参数可通过 JVM System Property 调整：

```text
gdl.jdbc.pool.maximumPoolSize=10
gdl.jdbc.pool.connectionTimeoutMs=5000
gdl.jdbc.pool.validationTimeoutMs=3000
gdl.jdbc.pool.idleTimeoutMs=60000
```

SQLite `:memory:` 会自动限制为单连接池，避免多个物理连接各自看到不同的内存数据库。

---

## 凭据管理

不建议把数据库密码直接写进 DSL。内置 `SecretResolver` 支持：

```text
passwordRef: "env:GDL_DB_PASSWORD"
passwordRef: "sys:gdl.db.password"
```

Java Runtime 也可以注入自定义 Secret Provider：

```java
DatasourceRegistry.getDefault().setSecretResolver(reference -> vaultClient.read(reference));
```

这样可以接 Vault、KMS、本地密钥服务或企业内部密码平台，而无需修改数据源 Provider。

---

## 健康检查与元数据发现

JDBC 数据源支持统一健康检查：

```java
DatasourceHealth health = DatasourceRegistry.getDefault().health(datasource);
```

结果包含：

- 是否健康
- 建连/取连接耗时
- 数据库产品名
- 数据库版本
- 错误信息

统一元数据发现：

```java
JdbcMetadataService.Snapshot metadata = DatasourceRegistry.getDefault().inspect(datasource);
```

当前返回数据库产品信息、表/视图以及字段的 JDBC 类型、类型名、长度、是否可空、字段顺序。后续可在相同服务上继续扩展主键、索引、分区和统计信息。

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

如果是 JDBC 数据源，实现 `JdbcDatasource` 并让 Provider 返回对应 `SqlDialect`，即可复用统一 `JdbcExecutionEngine`、连接池、健康检查和元数据发现能力，避免为每一种数据库重复实现基础设施。

---

## 能力发现

Provider 可通过 `DatasourceCapability` 声明：

- `READ` / `WRITE`
- `SQL` / `JDBC`
- `TRANSACTION`
- `METADATA` / `HEALTH_CHECK`
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

当前架构已经把“增加 JDBC 数据源”降低为实现 Provider / Datasource / Dialect，并可自动复用连接池、事务执行、凭据解析、健康检查和元数据发现。下一阶段适合继续扩展：

1. capability-aware planner：根据能力自动决定 SQL 下推、内存执行或 Drift。
2. 跨物理数据源联邦 Join：将不同 Provider 的子图拆分执行并通过中间表/Arrow 等交换。
3. 元数据增强：主键、索引、分区、统计信息和 schema 过滤。
4. Provider SDK 独立模块：固定插件兼容契约、版本协商和测试套件。
5. 更多 Provider：Oracle、SQL Server、KingbaseES、GaussDB、Doris、ClickHouse、REST/Object Storage/Kafka 等。
6. 连接池治理：按数据源实例命名、指标、泄漏检测、优雅关闭和运行时重载。

---

## 测试与构建

```bash
# 与 CI 一致：编译、单元测试、集成验证和 Maven 生命周期校验
mvn -B -ntp verify
```

GitHub Actions 会对 `main`、PR 以及 `chatgpt/**` 分支运行同一验证命令。
