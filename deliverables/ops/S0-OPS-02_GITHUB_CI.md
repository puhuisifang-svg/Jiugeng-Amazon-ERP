# S0-OPS-02 GitHub 私有远程与 CI 基线

| 字段 | 内容 |
| --- | --- |
| 日期 | 2026-09-12 |
| Owner | Ops / PJM |
| 状态 | Blocked / In Review |

## GitHub 授权门禁

本 Agent 执行 `gh auth status` 返回“未登录任何 GitHub host”。因此未创建远程仓库、未修改 remote、未推送，也未请求或展示任何凭据。私有远程与 GitHub 保护规则仍由“GitHub 安全授权”输入门禁阻塞。

授权到位后的建议基线：

- 私有仓库，默认分支 `main`。
- 禁止强推和删除 `main`；通过 PR 合并。
- 至少要求 CI 中 Secret scan、Backend build and test、Frontend build and test、Compose configuration 四项检查。
- 最少 1 名代码所有者/维护者审批；管理员绕过受审计。
- GitHub Actions `GITHUB_TOKEN` 维持只读内容权限，额外权限逐项显式声明。

## 本地 CI 基线

`.github/workflows/ci.yml` 已覆盖：

1. Git 历史与工作树敏感信息扫描（Gitleaks）。
2. 后端 Java 21 + Maven Wrapper `verify`（脚手架提交后自动激活）。
3. 前端 Node 24 + pnpm 锁文件安装、测试和构建（脚手架提交后自动激活）。
4. Docker Compose 配置模型校验。

所有第三方/官方 Actions 均固定到 2026-09-12 从其官方 GitHub 仓库解析的完整 commit SHA，并保留对应 major 版本注释，避免 tag 漂移直接进入 CI 供应链。复核已将 `pnpm/action-setup@v4` 与 `gitleaks/gitleaks-action@v2` 从 tag object SHA 修正为 peeled commit SHA。

本地验证：CI YAML 解析通过；Compose 模型校验通过；高置信凭据模式扫描无命中；前端脚手架 `4/4` 测试通过且生产构建通过；后端 Maven Wrapper `verify` 构建成功且 `7/7` 测试通过。后端 CI 指向 Sprint 0 已登记的工程路径。

当前没有私有远程，故无法提供 GitHub Actions 在线运行 URL；这不是授权绕过项。CI 文件须在授权后首次推送，并由 PJM 核验真实运行结果后，才能将 S0-OPS-02 标记 Completed。
