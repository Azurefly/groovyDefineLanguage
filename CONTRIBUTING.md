# Contributing to GDL

感谢你对 GDL（GroovyDefine Language）引擎的关注！本文档说明如何参与贡献。

## 行为准则

请以尊重、友善的态度参与讨论与协作。我们欢迎不同背景的贡献者。

## 如何贡献

### 报告 Bug

- 先搜索 [Issues](https://github.com/Azurefly/groovyDefineLanguage/issues) 确认问题尚未被报告。
- 使用 Bug 报告模板，提供：复现步骤、期望行为、实际行为、环境信息（JDK 版本、操作系统、GDL 版本）。

### 提出新功能

- 使用 Feature Request 模板，说明：解决的问题、使用场景、建议的 API 形态。
- 大的架构改动建议先开 Issue 讨论，达成共识后再编码。

### 提交代码

1. Fork 本仓库并创建特性分支（`git checkout -b feat/xxx` 或 `fix/xxx`）。
2. 遵循现有代码风格：Java 17，中文 Javadoc 说明公开 API 的关键语义。
3. 新增功能请补充单元测试；修复 Bug 请补充回归测试。
4. 本地执行 `mvn -B -ntp verify` 确保全量测试通过。
5. 提交信息使用中文，简明描述改动内容与原因。
6. 发起 Pull Request，关联相关 Issue，说明改动内容与验证方式。

## 代码规范

- 公开 API 必须有中文 Javadoc（一句话职责 + 关键语义）。
- 异常优先使用 `com.pl.gdl.common.exception` 下的 `GdlException` 体系，避免裸 `RuntimeException`。
- SQL 拼接必须经过 `SqlSanitizer` 校验或转义，禁止裸拼接用户输入。
- Groovy 脚本执行默认走 `GdlCompiler.sandboxed()` 沙箱；`trusted()` 仅限受信任环境。

## 许可证

贡献的代码将以 [Apache License 2.0](LICENSE) 发布。提交 PR 即表示你同意该条款。
