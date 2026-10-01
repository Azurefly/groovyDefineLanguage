# Security Policy

## 报告安全漏洞

**请不要通过公开 Issue 报告安全漏洞。**

如果你发现了安全问题（如远程代码执行、SQL 注入、鉴权绕过、敏感信息泄露等），请通过以下方式私下报告：

- 给仓库维护者发邮件（见 GitHub 仓库主页的联系方式），或
- 通过 GitHub 的 [Private vulnerability reporting](https://github.com/Azurefly/groovyDefineLanguage/security/advisories/new) 功能提交。

请在报告中包含：

- 漏洞描述与影响范围
- 复现步骤（PoC）
- 建议的修复思路（如有）

我们会尽快确认并修复，在修复发布前请勿公开细节。

## 安全设计说明

- GDL 脚本默认在沙箱中编译执行（`GdlCompiler.sandboxed()`），限制 import 白名单并拦截危险调用（`System.exit`、`Runtime.exec`、`.execute()`、文件类、反射等）。
- HTTP 服务鉴权默认开启（`ServerConfig.requireToken = true`）；未配置 token 时服务进入 open 模式并打印醒目警告，仅建议本地调试使用。
- SQL 标识符与字面量拼接必须经过 `SqlSanitizer` 校验或转义。

## 支持的版本

| 版本 | 是否支持安全更新 |
| ---- | ---------------- |
| main（最新） | ✅ |
| 历史 release | 仅关键漏洞视情况修复 |
