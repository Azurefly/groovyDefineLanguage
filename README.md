# GroovyDefine Language (GDL) Engine

GDL（GroovyDefine Language）是基于 Groovy 语法的领域特定模型语言（DSL），运行在 TRE（TaoSha Runtime Engine）分布式规则与计算引擎中。
它提供流批一体的数据建模、ETL 编排、面向业务的本体（Ontology）建模、跨数据中心漂移（Drift）以及可扩展多数据源访问能力。

---

## 模块架构

工程基于 Java 17 + Groovy 4，采用标准 Maven 多模块体系构建：

```text
gdl-parent (pom.xml)
├── gdl-common        // 通用模型
├── gdl-dataframe     // DataFrame、算子、Provider SDK、SQL 方言与 JDBC 执行
├── gdl-runtime       // Groovy DSL、能力规划、联邦规划与执行、变量与 DAG
├── gdl-ontology      // 业务本体
├── gdl-drift         // 跨区域 Exchange、漂移计算
└── gdl-server        // TreClient SDK 与 MCP 工具
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
| H2 | ✅ | ✅ | ✅ | ✅ | Pushdown | ✅ 实测 | 本地开发、测试、嵌入式 |
| SQLite | ✅ | ✅ | ✅ | ✅ | Pushdown | ✅ 实测 | 文件或 `:memory:` |
| PostgreSQL | ✅ | ✅ | ✅ | ✅ | Pushdown | 架构支持 | 当前 CI 未启动真实 PostgreSQL Server |
| MySQL | ✅ | ✅ | ✅ | ✅ | Pushdown | 架构支持 | 当前 CI 未启动真实 MySQL Server |
| Hive | ✅ | 现有路径 | — | 待专用实现 | Fallback | 待扩展 | 保持既有 Hive 兼容 |
| LLM | ✅ | N/A | — | 待专用实现 | 专用路径 | N/A | 非关系型远程能力 |

当前已经存在**真实跨物理数据源执行路径**：H2 与 SQLite 分别执行 SQL 下推，将结果通过内存 Exchange 物化，再进行 Hash Join。它不是“把任意跨库 SQL 原样发出去”，也不宣称已经支持任意 DataFrame 算子图的自动联邦化。

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

## Capability-aware Planner

当前执行意图：

- `READ`：要求 `READ`
- `SQL_READ`：要求 `READ + SQL`
- `SQL_WRITE`：要求 `WRITE + SQL`

Planner 只依据 Provider capability 决策，不按数据库类型写死分支。结果通过 `GdlExecutionContext#getExecutionPlans()` 可诊断：

```java
ExecutionPlan plan = result.getContext().getExecutionPlans().get(0);
System.out.println(plan.mode());
System.out.println(plan.datasourceType());
System.out.println(plan.reason());
```

模式：

- `PROVIDER_PUSHDOWN`：Provider 提供匹配能力的专用执行引擎。
- `FALLBACK`：能力满足，但仍沿用既有 Runtime 执行路径，例如当前 Hive。

Runtime 创建 `from/query/insert` DAG 节点时会同时写入 `datasourceType`、`datasourceName`、`areaCode`、执行模式和执行意图，为后续联邦切图提供物理执行信息。

---

## 联邦 DAG 与 Exchange

`FederatedDagPlanner` 以三个维度确定执行域：

```text
areaCode + datasourceType + datasourceName
```

只有“执行域相同且在 DAG 中连续连接”的节点会合并为一个 `FederatedFragment`。跨 Fragment 的边形成 `ExchangeBoundary`：

- 同一 `areaCode`、不同 Provider：`MEMORY`
- 不同 `areaCode`：`REMOTE_DRIFT`
- `INTERMEDIATE_TABLE` 已保留为后续大结果集物化策略

这样不会把同一个 Provider 在 DAG 中不连续的两段错误合并，也不会把跨地域计算误当成本地跨库 Join。

`DriftAwareFederationPlanner` 已把 `REMOTE_DRIFT` Exchange 接到现有 `IntermediateTableManager`：跨区域边会获得 `tre_temp_*` 中间表，用于后续远端调度和传输。

---

## 第一条真实跨数据源 Join

当前提供显式 `federatedJoin(...)` DSL。两个数据源的 SQL 各自在自己的 Provider 中执行，结果通过内存 Exchange 后做 Hash Join：

```groovy
def h2ds = datasource("H2", [
    url: "jdbc:h2:mem:federated_demo;DB_CLOSE_DELAY=-1"
])

def sqliteDs = datasource("SQLITE", [path: ":memory:"])

def rows = federatedJoin(
    h2ds,
    "SELECT 1 AS id, 'alice' AS name UNION ALL SELECT 2 AS id, 'bob' AS name",
    "id",
    "person",
    sqliteDs,
    "SELECT 1 AS id, 'risk' AS department",
    "id",
    "dept",
    "INNER"
)

return rows
```

当前支持：

- `INNER`
- `LEFT`
- 多匹配键值（Hash bucket 中保留多行）
- 字段名前缀，避免两侧同名字段冲突，例如 `person.id` / `dept.id`
- 大小写不敏感的 Join Key 查找
- 执行结果保留两个 source execution plan 与 Exchange trace

当前明确边界：

- 两个数据源必须位于同一 `areaCode` 才能由本地 `FederatedJoinExecutor` 执行。
- 跨 `areaCode` 会明确拒绝本地执行并要求 `REMOTE_DRIFT`，不会伪装成功。
- 目前是显式 `federatedJoin`，还不是任意 `df1.join(df2)` 自动改写成联邦执行。
- 当前 Exchange 为内存物化；大数据量流式/Arrow/临时表 Exchange 是下一阶段工作。

---

## 连接池与生命周期

JDBC 执行统一通过 `JdbcConnectionManager` 使用 HikariCP。默认参数可通过 JVM System Property 调整：

```text
gdl.jdbc.pool.maximumPoolSize=10
gdl.jdbc.pool.connectionTimeoutMs=5000
gdl.jdbc.pool.validationTimeoutMs=3000
gdl.jdbc.pool.idleTimeoutMs=60000
```

SQLite `:memory:` 自动限制为单连接池，避免多个物理连接看到不同内存数据库。

---

## 凭据管理

内置 `SecretResolver` 支持：

```text
passwordRef: "env:GDL_DB_PASSWORD"
passwordRef: "sys:gdl.db.password"
```

Runtime 可以注入企业密钥平台：

```java
DatasourceRegistry.getDefault().setSecretResolver(reference -> vaultClient.read(reference));
```

Provider SDK 新增 context-aware 创建入口：

```java
create(config, secretResolver)
```

因此外部插件也可以使用 Registry 已配置的 SecretResolver，而不必自行读取环境变量或把密码明文写进插件配置。旧 Provider 只实现 `create(config)` 仍然兼容。

---

## 健康检查与元数据发现

```java
DatasourceHealth health = registry.health(datasource);
JdbcMetadataService.Snapshot metadata = registry.inspect(datasource);
```

当前覆盖数据库产品/版本、表/视图、字段类型、长度、nullable、字段顺序。H2 与 SQLite 在 CI 中使用真实 JDBC 连接验证。

---

## Provider SDK

Provider 拥有稳定的描述、校验、能力和执行契约：

```java
public final class CustomProvider implements DatasourceProvider {
    @Override public String getType() { return "CUSTOM"; }

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

插件可通过 `ServiceLoader` 发布：

```text
META-INF/services/com.pl.gdl.dataframe.datasource.DatasourceProvider
```

### PostgreSQL-compatible 产品模板

新增：

- `PostgresCompatibleDatasource`
- `AbstractPostgresCompatibleProvider`

用于 GaussDB、KingbaseES、Vastbase/海量等 PostgreSQL-compatible 产品的插件骨架。核心工程**不内置或猜测具体厂商 JDBC Driver**；插件应根据实际数据库版本和厂商驱动包配置 subprotocol、driver class、默认端口和产品差异。

示意：

```java
public final class VendorProvider extends AbstractPostgresCompatibleProvider {
    @Override public String getType() { return "VENDOR_DB"; }
    @Override protected String jdbcSubprotocol() { return "vendor"; }
    @Override protected String driverClassName() { return "com.vendor.jdbc.Driver"; }
    @Override protected int defaultPort() { return 5432; }
}
```

它会自动继承：

- PostgreSQL SQL dialect 基线
- READ / WRITE / SQL / JDBC / TRANSACTION
- METADATA / HEALTH_CHECK
- HikariCP
- Registry SecretResolver
- capability-aware planner

真正的厂商插件仍应针对方言差异、驱动特性和版本兼容性增加专用测试。

---

## 工程边界与下一步

当前主线已经形成：

```text
Provider SDK
→ Capability Planner
→ Execution Domain
→ DAG Fragment
→ Exchange Boundary
→ 本地 H2/SQLite Federated Hash Join
→ 跨 areaCode Drift Exchange
```

下一阶段优先项：

1. 把普通 `df1.join(df2)` 的 LogicalOperator 树自动识别成联邦 Join，而不是要求显式调用 `federatedJoin`。
2. Exchange SPI：Memory / Streaming / Intermediate Table，并增加大小阈值和 backpressure。
3. 支持 N 个 Provider/Fragment 的拓扑执行，不局限于二源 Join。
4. 把 Drift Remote Exchange 接入真实远端执行协调器，而不仅生成中间表计划。
5. PostgreSQL + MySQL GitHub Actions 真实服务集成测试。
6. 独立 Provider artifact：GaussDB、KingbaseES、Vastbase/海量、Doris、ClickHouse、Oracle。
7. 元数据增强：PK、索引、分区、统计信息，供代价规划使用。

---

## 测试与构建

```bash
mvn -B -ntp verify
```

GitHub Actions 会对 `main`、PR 以及 `chatgpt/**` 分支运行同一验证命令。
