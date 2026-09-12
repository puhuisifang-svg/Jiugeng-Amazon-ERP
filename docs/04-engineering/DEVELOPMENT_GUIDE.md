# 开发规范

| 字段 | 内容 |
| --- | --- |
| Owner | Frontend / Backend |
| 状态 | Draft |

## 研发流程

1. 需求达到 `Ready`，具备验收标准和设计依赖。
2. 创建分支并关联需求 ID。
3. 完成开发、自测、静态检查和必要的迁移脚本。
4. 发起代码评审，至少一名对应领域成员批准。
5. CI 通过后合并；部署测试环境交由 QA 验证。

## 工具链基线

- 后端使用 Java 21、Maven，并在后端脚手架中提交 Maven Wrapper。
- 前端统一使用 pnpm；禁止同时提交 npm 或 Yarn 锁文件。
- 项目级 npm Registry 由根目录 `.npmrc` 固定，用户全局 Registry 配置不作为项目基线。
- 前端脚手架创建后，在根 `package.json` 固定 `packageManager`、`engines`，并提交 `pnpm-lock.yaml`。

## 提交与分支

- 分支：`feature/ERP-123-short-title`、`fix/ERP-123-short-title`
- 提交：`feat(order): ERP-123 add order search`
- 禁止直接推送保护分支。
- 数据库变更必须版本化、可前向执行，并提供失败处置说明。

## 完成定义

- 代码与配置已提交并通过评审
- 自动化检查和测试通过
- API、数据字典和运行文档已同步
- 无真实密钥进入代码、配置、日志或测试数据
- 可部署、可监控、可回滚或有明确前向修复方案
- 验收证据已登记
