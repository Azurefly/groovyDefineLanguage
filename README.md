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
├── gdl-runtime       // Groovy DSL、能力规划、变量与 DAG
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
  ├── DatasourceProvider SDK
  ├── provider descriptor / config validation
  ├── capability discovery
  ├── health / metadata discovery
  ├── SecretResolver
  └── ServiceLoader / runtime register
  ▼
Capability-aware Planner
  ├── READ
  ├── SQL_READ
  └── SQL_WRITE
  │
  ├── provider pushdown
  └── compatible fallback execution path
  ▼
CmdDatasource / ExecutionEngine
  ├── JdbcDatasource ── PostgreSQL / MySQL / SQLite / H2
  ├── HiveDatasource
  ├── LlmDatasource
  ├── JdbcExecutionEngine + SqlDialect + HikariCP
  └── InMemory / existing non-JDBC execution paths
```

---

## 多数据源支持矩阵

| 类型 | DSL / Registry | SQL 方言 | JDBC 实际查询 | 连接池 | 元数据/健康 | Planner | 读/写能力 | 说明 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| H2 | ✅ | ✅ | ✅ | ✅ | ✅ | Provider Pushdown | ✅ | 本地开发、单测、嵌入式场景 |
| SQLite | ✅ | ✅ | ✅ | ✅ | ✅ | Provider Pushdown | ✅ | 文件或 `:memory:` 数据库 |
| PostgreSQL | ✅ | ✅ | ✅ | ✅ | ✅ | Provider Pushdown | ✅ | JDBC Driver 随模块运行时依赖提供 |
| MySQL | ✅ | ✅ | ✅ | ✅ | ✅ | Provider Pushdown | ✅ | JDBC Driver 随模块运行时依赖提供 |
| Hive | ✅ | ✅ | 当前沿用 SQL 规划/下推能力 | — | 待专用实现 | Compatible Fallback | ✅ | 保持原有 Hive DSL 兼容，不伪装成 JDBC |
| LLM | ✅ | N/A | N/A | — | 待专用实现 | 专用路径 | 专用 | 非关系型远程能力，沿用 LLM 算子体系 |

> “多数据源”当前表示：同一 GDL 工具中可以注册、配置并独立执行不同物理数据源；**尚不宣称单条 SQL 自动完成跨 PostgreSQL/MySQL/SQLite 的联邦 Join**。能力规划已经完成，但跨源 Join 仍需下一阶段的子图切分、数据交换和合并执行层。

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

`GdlExecutionContext` 不再直接按数据源类型选择引擎，而是把 `from/query/insert` 转换为执行意图交给 `DatasourceExecutionPlanner`。Planner 根据 Provider 声明的能力决定使用 Provider Pushdown 还是兼容的 fallback 路径。

---

## Capability-aware Planner

当前执行意图：

- `READ`：至少要求 `READ`
- `SQL_READ`：要求 `READ + SQL`
- `SQL_WRITE`：要求 `WRITE + SQL`

例如，一个只声明 `READ + SQL` 的 Provider 可以执行查询，但在进入实际执行前就会拒绝 `insert`，不会等数据库报错后才发现能力不匹配。

Planner 的结果通过 `GdlExecutionContext#getExecutionPlans()` 可观察：

```java
ExecutionPlan plan = result.getContext().getExecutionPlans().get(0);
System.out.println(plan.mode());
System.out.println(plan.datasourceType());
System.out.println(plan.reason());
```

当前模式：

- `PROVIDER_PUSHDOWN`：Provider 提供匹配能力的执行引擎，例如 H2 / SQLite / PostgreSQL / MySQL JDBC。
- `FALLBACK`：Provider 能力满足，但执行仍委托现有 Runtime 路径，例如当前 Hive。

这层规划是后续跨源联邦执行的前置基础：下一阶段可以继续加入 `DRIFT`、`FEDERATED_EXCHANGE` 等策略，而不需要修改 DSL 语义。

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

## Provider SDK

新增数据源不需要修改 `GdlScriptBase` 或核心 Registry。Provider 现在拥有稳定的描述、校验、能力和执行契约：

```java
public final class CustomProvider implements DatasourceProvider {
    @Override
    public String getType() {
        return "CUSTOM";
    }

    @Override
    public String getVersion() {
        return "1.0";
    }

    @Override
    public void validateConfig(Map<String, Object> config) {
        if (!config.containsKey("endpoint")) {
            throw new DatasourceValidationException("endpoint is required");
        }
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

Registry 会在 `create()` 前先调用 `validateConfig()`。Provider 的稳定描述可以用于插件管理界面、兼容性检查和诊断：

```java
DatasourceProviderDescriptor one = registry.describe("CUSTOM");
List<DatasourceProviderDescriptor> all = registry.describeAll();
```

Descriptor 包含：

- Provider 类型
- Provider 契约版本
- 能力集合
- SQL 方言名（如有）

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

调用方既可以用 `DatasourceRegistry.capabilities(type)` 查询能力，也可以直接使用 `DatasourceProviderDescriptor`。Planner 本身只依据能力，不再依赖 `if (type == ...)` 的硬编码分支。

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

当前架构已经完成 Provider SDK 基础契约和 capability-aware planner。继续演进时优先顺序：

1. 跨物理数据源联邦 Join：识别多 Provider DAG，按数据源切分子图并执行。
2. 交换层：定义中间结果交换格式，优先评估 Arrow / 流式 RowDataFrame / 临时表。
3. Drift Planner 融合：把 `areaCode` 与 Provider capability 同时纳入执行位置决策。
4. Provider 插件工程模板：GaussDB、KingbaseES、Vastbase/海量、Doris、ClickHouse、Oracle。
5. 元数据增强：主键、索引、分区、统计信息和 schema 过滤。
6. 插件兼容性：Provider SDK 版本协商、插件清单、自动兼容测试套件。
7. 连接池治理：指标、泄漏检测、优雅关闭和运行时重载。

---

## 测试与构建

```bash
# 与 CI 一致：编译、单元测试、集成验证和 Maven 生命周期校验
mvn -B -ntp verify
```

GitHub Actions 会对 `main`、PR 以及 `chatgpt/**` 分支运行同一验证命令。
