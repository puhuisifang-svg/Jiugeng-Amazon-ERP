# 九更亚马逊 ERP

面向公司内部员工、自研自用的 Amazon ERP 管理系统。

## 当前状态

- 阶段：Sprint 0 执行
- 项目规划：MVP 最终规划 v1.0（Approved）
- Sprint：Sprint 0 / In Progress
- 项目经理：Jiugeng_PJM
- 成员：PM、UI、Frontend、Backend、QA、Ops
- Agent 模型：项目成员统一使用 `openai/gpt-5.6-sol`；`main` 不属于本项目

## 文档入口

- [项目文档总索引](docs/INDEX.md)
- [项目章程](docs/00-governance/PROJECT_CHARTER.md)
- [文档治理规范](docs/00-governance/DOCUMENT_CONTROL.md)
- [项目规划方案](docs/01-product/PROJECT_PLAN.md)
- [交付物总账](docs/DELIVERABLE_REGISTER.md)
- [密钥管理规范](docs/05-security/SECRETS_MANAGEMENT.md)
- [密钥登记表](docs/05-security/SECRET_REGISTRY.md)
- [Sprint 管理](docs/08-project-management/SPRINTS/README.md)

## 目录结构

```text
jiugeng-amazon-erp/
├── docs/                    # 受控项目文档
│   ├── 00-governance/       # 章程、决策、变更、文档治理
│   ├── 01-product/          # 规划、PRD、Backlog、业务术语
│   ├── 02-design/           # UI/UX、原型和设计规范
│   ├── 03-architecture/     # 架构、ADR、API、数据字典
│   ├── 04-engineering/      # 研发、环境、分支、发布规范
│   ├── 05-security/         # 安全、权限、密钥登记和轮换
│   ├── 06-quality/          # 测试策略、测试报告、验收
│   ├── 07-operations/       # 部署、监控、备份与故障处置
│   └── 08-project-management/ # Sprint、风险、状态、会议纪要
├── deliverables/            # 各角色正式输出成果
├── templates/               # 统一文档模板
├── secrets/                 # 只保留安全说明，禁止保存真实密钥
└── README.md
```

## 基本规则

1. 任何功能开发必须能追溯到已确认的需求或变更单。
2. 所有正式成果必须登记到 `docs/DELIVERABLE_REGISTER.md`。
3. 文档状态统一使用 `Draft`、`In Review`、`Approved`、`Superseded`。
4. Token、Key、密码、证书私钥和刷新令牌不得写入仓库、Markdown、Issue、日志或聊天。
5. Sprint 开始前由 PJM 提交计划，经老板确认后分派；完成后经核验和老板验收再进入下一 Sprint。
