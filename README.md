# 九更亚马逊 ERP

面向公司内部员工的 Amazon ERP。当前仓库已包含产品、客户订单、采购库存、发货、财务、报关、工厂、样品开发和选品监控等业务模块，以及账号权限、审计和生产发布流程。`sprint-0` 是后端保留的目录名，不代表当前仍只有早期演示。

## 新会话 / 新模型接手

按以下顺序阅读，通常无需先遍历全部历史文档：

1. [AGENTS.md](AGENTS.md)：共享工作区、写入权、分支与发布规则。
2. [项目接手指南](docs/04-engineering/PROJECT_HANDOVER.md)：业务地图、代码定位、配置、验证与排查路径。
3. [当前状态报告](docs/08-project-management/STATUS_REPORT.md)：核实日期、代码与线上版本、已知限制及未完成工作。
4. 对应任务计划和代码；涉及界面时完整阅读 [UI 规范](docs/ui/UI_STANDARD.md)，注意后面的日期增补。

先运行 `git branch --show-current`、`git status --short`、`git rev-parse HEAD`。聊天记忆和历史交接不代表当前工作区仍干净，也不代表已获得新的写入任务。

## 工程入口

| 位置 | 内容 |
| --- | --- |
| [frontend](frontend/) | Vue 3 / TypeScript / Vite；页面、共享组件、API 客户端、Vitest 与 Playwright |
| [后端工程](deliverables/backend/sprint-0/jiugeng-erp-backend/) | Java 21 / Spring Boot / Spring Modulith；JDBC、MySQL、Flyway |
| [docs](docs/INDEX.md) | 治理、业务规则、架构、UI、状态及历史记录 |
| [任务记录](deliverables/pjm/) | ERP 任务计划、实施交接及部分历史验收记录 |
| [生产运行说明](infra/production/README.md) | Ubuntu / systemd / Caddy / MySQL 部署设计及运维边界 |
| [发布工具](scripts/production/) | 打包、版本校验、备份和受限部署脚本 |
| [.github/workflows](.github/workflows/) | CI 与 Production 发布工作流 |

## 日常验证

按 2026-11-20 用户约定，以交接项目经理为一个小迭代的结束节点。期间累计本地修改与待验证项，不逐个小功能运行完整测试或回归；交接前对累计改动统一执行受影响的门禁和验收。遇到实际错误或关键行为不确定时才做必要局部检查；有效且未受后续改动影响的结果不重复执行。

- 前端：在 `frontend/` 执行 `pnpm quality`；浏览器冒烟使用 `pnpm exec playwright test`，需要已构建前端和浏览器依赖。
- 后端：在后端工程执行 `./mvnw clean verify`。
- 导入契约变更：按 [AGENTS.md](AGENTS.md) 执行 npm 与 Python 契约门禁。
- 所有改动：`git diff --check`。文档维护检查链接、路径与事实来源；纯文档修改无需重启服务。
- 本地持久服务已获用户长期授权（2026-11-21），可按开发需要自主同步/重启，无需逐次确认；沿用现有 tmux 与 MySQL 数据，迁移前备份。线上发布仍等待用户指令，详见 [AGENTS.md](AGENTS.md#local-services)。

具体工具版本以 `frontend/package.json`、锁文件、后端 `pom.xml` 和 CI 为准。测试通过、合入 main、部署成功、业务验收是不同状态，分别提供证据。

## 持续维护

稳定的代码地图维护在接手指南；版本、任务进展和发布证据只在状态报告维护，避免复制到多个入口。换模型、结束未完成任务或交接前，按 [交接模板](templates/SESSION_HANDOFF_TEMPLATE.md) 保存可恢复的上下文。

早期 Sprint 0、MVP、架构和 Backlog 文档保留历史价值，但不是完整的当前功能清单。出现冲突时记录差异，由 PJM 确认业务基线；不据旧文档直接删除现有功能。完整入口见 [文档总索引](docs/INDEX.md)。
