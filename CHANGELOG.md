# Changelog

本文件记录 GDL（GroovyDefine Language）引擎的所有重要变更。
格式参考 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.0.0/)，
版本号遵循 [语义化版本](https://semver.org/lang/zh-CN/)。

## [Unreleased]

### 新增
- 数据源：Hive JDBC 支持（`HiveJdbcDatasource implements JdbcDatasource`），`DatasourceRegistry` 的 HIVE provider 根据是否提供 `url` 自动选择 JDBC 或配置型实现；本地 HiveServer2（3.1.3）全流程实测通过（建表/插入/查询/聚合/INSERT OVERWRITE/元数据/DROP）
- LLM：`LlmClient`（OkHttp 调用 OpenAI-compatible Chat Completions API，支持 `no_proxy`）与 `LlmCallExecutor`（并发推理并回写结果列），已接入 `InMemoryEngine`；Ollama（qwen2:0.5b）本地实测通过
- 算子：数据采样 `sample(n)`/`sample(fraction)`（支持 `seed` 可复现）、`describe()` 数据探查统计、`validate(condition, message)` 数据质量检查（违规抛 `DataQualityException`）、`pivot()` 行转列透视表、`cache()`/`uncache()`/`isCached()` 显式缓存
- Demo：独立可运行的三段式演示项目（H2 ETL、跨源联邦、LLM 情感分析），`demo/run-demo.sh` 一键运行
- `distributeSort` 在单机引擎下降级为 `ORDER BY`（文档已说明语义差异）

### 优化
- `DatasourceRegistry` 的 LLM provider 支持 `timeout` 参数配置；`LlmDatasource` 构造时校验 URL 格式
- `LlmCallExecutor` 空输入保护与线程池优雅关闭（`awaitTermination`）；`LlmClient` 的 `ObjectMapper` 改为静态复用
- `InMemoryEngine` H2 类型映射补充（SMALLINT/TINYINT/TIME/CHAR/VARCHAR(n) 等）
- `SampleOperator` 的 `seed` 透传给 `RAND(seed)`

### 修复
- `InMemoryEngine.createAndPopulateH2Table` 类型映射 bug（数值列被建成 VARCHAR 导致 SUM/AVG 失败）
- `CmdDataframeImpl.index()` 保持不可变性（不再修改共享的 SortOperator）
- `sort()` 空参数抛 `IllegalArgumentException`；`toSql` 优雅处理空排序
- `PivotOperator` 列别名冲突时追加序号消解
- `SubgraphScriptGenerator` 远端源表占位改为生成明确抛异常的代码（避免静默数据错误）

### 诚实性改进（API 治理）
- `periodReactor`/`increment` 标记 `@Deprecated`（调度属于编排层，当前未实现）
- 5 个 Bdos 变量标记 `@Deprecated`（依赖已下线服务）
- `PythonScriptOperator` 标记 `@Deprecated`（尚未实现）
- 流式窗口算子（tumble/hop/cumulate）在文档中明确标注"路线图中，暂未实现"

### 安全
- `GdlCompiler` 默认启用沙箱：`SecureASTCustomizer`（import 白名单仅 `com.pl.gdl.**`、禁止脚本内定义方法）
  + 编译期 `DangerousPatternCustomizer` 拦截 `System.exit`、`Runtime.exec`、任意 `.execute()`、
  `Class.forName`、`.getClass()`、`eval`/`evaluate`、`new File` 等文件类、`GroovyShell`/`GroovyClassLoader`；
  新增 `GdlCompiler.sandboxed()`（默认）与 `GdlCompiler.trusted()`（仅受信任环境）
- HTTP 服务鉴权默认开启：`ServerConfig.requireToken` 默认为 `true`，未配置 token 时显式进入 open 模式并告警
- `SqlSanitizer` 强化：标准 SQL 双引号标识符引用（doubling 转义）、`isValidIdentifier` 白名单、`escapeLiteral`
- `InMemoryEngine` DDL 表名/列名白名单校验（防拼接注入）；`CommonIncrementVar` 字段名白名单 + 字面量转义；
  Ontology DDL 生成器标识符转义
- `TreClientImpl.startTask` 改为真异步执行，任务结果带 TTL（30 分钟）与数量上限（1000），防内存泄漏

### 修复
- `InMemoryEngine.execute` 不再吞掉所有 SQL 异常：仅"表不存在"返回空 DataFrame，其余抛 `GdlExecutionException`
- `DagGraph.addEdge` 悬空边抛 `IllegalArgumentException`；`topologicalSort` 检测到环抛 `GdlCompilationException`（不再静默回退）
- `SqlPushdownEngine` 未知算子由静默 no-op 改为抛异常；Hive 不支持 `subtractAll`/`intersectAll` 时 fail-fast
- `TreRemoteHttpClient` 字段映射修正（任务结果不再丢失）；`URLConnection` 传输加响应大小限制与 `disconnect()`
- `LogicalOperator` 全局监听器并发/生命周期语义修正
- `Row`/`DataType` 统一 `Locale.ROOT`；`RowDataFrame.addRowValue` 严格校验值数量与列数；
  `ColumnInfo.equals/hashCode` 同时比较名称与类型
- 修复 `TaskResult.status` 类型：`String` → `TaskStatus` 枚举，默认 `PENDING`
- BDOS 系列变量去除硬编码日期假数据

### 新增
- 整理开源发布物料：Apache-2.0 许可证、`CONTRIBUTING.md`、`SECURITY.md`、`CHANGELOG.md`
- 文档统一收拢至 `docs/`（GDL 语法指南、TSML 手册、远程 API 指南）
- 新增 GitHub Issue / PR 模板
- 补齐各模块公开 API 的中文 Javadoc；新增 `SqlSanitizerTest`、`TaskResultTest`、沙箱拦截测试、
  方言/SQL 生成测试、`TreClientTest` 异步与鉴权用例等，全量 85 个测试通过

### 变更
- `bin/start-server.sh` 改写：不再依赖作者本机 Maven 仓库硬编码路径，改为 Maven 构建 classpath

### 移除
- 删除个人辅助脚本 `push_to_github.sh`（与开源发布无关）

## [1.0.0-SNAPSHOT] - 2026-09-15

### 新增
- GDL 引擎六大模块：`gdl-common`、`gdl-dataframe`、`gdl-runtime`、`gdl-ontology`、`gdl-drift`、`gdl-server`
- 基于 Groovy 4 的 DSL：基础/集合/关联/输出/实时窗口/高级算子体系
- 多数据源 Provider SDK：H2、SQLite、PostgreSQL、MySQL、Hive、LLM，含连接池、元数据、健康检查与 `SecretResolver`
- 能力感知规划器（READ / SQL_READ / SQL_WRITE / PROVIDER_PUSHDOWN / FALLBACK）
- 联邦执行：按 `areaCode` + 数据源划分执行域，MEMORY / REMOTE_DRIFT 交换
- 跨地域漂移（Drift）：DAG 亲和性分析、子图切割、`driftTo`/`driftFrom` 自动注入、临时表自动清理
- 本体（Ontology）系统：`@Table`/`@Column` 注解、动态类加载热加载、DDL 生成与 CRUD
- 动态变量体系：`CurrentTimeVar`、`CommonIncrementVar`、BDOS 分区增量系列变量
- 服务化：内置 HTTP 服务、Java 远程 SDK（`TreRemoteHttpClient`）、HTTP REST API、MCP 工具（`start_task` / `get_task_result` / `get_tsml_to_dag`）
- GitHub Actions CI：`mvn -B -ntp verify`
