# 项目文档总索引

## 当前接手入口（2026-11-20 更新）

先读 [README](../README.md) → [AGENTS](../AGENTS.md) → [项目接手指南](04-engineering/PROJECT_HANDOVER.md) → [当前状态报告](08-project-management/STATUS_REPORT.md)。界面任务同时读 [UI 规范](ui/UI_STANDARD.md)，跨会话使用 [交接模板](../templates/SESSION_HANDOFF_TEMPLATE.md)。

当前未提交业务迭代：[产品管理 19 项收尾记录](../deliverables/pjm/PRODUCT_CLOSEOUT_LOCAL_2026-11-20.md)。其中库存、恢复、权限依赖和列表新 API 是本地新增，尚未发布；接手时先检查该记录的验证状态。

下表保留原有文档登记和审批状态，不表示各历史文档已同步到最新代码。早期 MVP、Backlog、架构和编码规范的适用范围需结合当前实现与近期任务记录判断；差异交 PJM 确认，不静默覆盖历史批准。

| 分类 | 文档 | Owner | 状态 |
| --- | --- | --- | --- |
| 治理 | [项目章程](00-governance/PROJECT_CHARTER.md) | PJM | Draft |
| 治理 | [文档治理规范](00-governance/DOCUMENT_CONTROL.md) | PJM | Approved |
| 治理 | [决策日志](00-governance/DECISION_LOG.md) | PJM | Active |
| 治理 | [变更日志](00-governance/CHANGE_LOG.md) | PJM | Active |
| 产品 | [原始项目规划方案](01-product/PROJECT_PLAN.md) | PJM/PM | Superseded |
| 产品 | [首期范围与实施规划](01-product/PHASE1_SCOPE_PLAN.md) | PJM/PM | Superseded |
| 产品 | [精简 MVP 范围与实现方案](01-product/MVP_SCOPE_PLAN.md) | PJM/PM | Superseded |
| 产品 | [MVP 最终规划](01-product/MVP_FINAL_PLAN.md) | PJM | In Review（ERP-006 增量） |
| 产品 | [当前 MVP 活跃范围契约](01-product/MVP_ACTIVE_SCOPE_CONTRACT.md) | PJM/PM/Backend/Frontend/QA | Approved |
| 产品 | [PRD](01-product/PRD.md) | PM | In Review（ERP-006 增量） |
| 产品 | [产品与辅料编码规则](01-product/PRODUCT_CODE_STANDARD.md) | PM/Backend | Approved |
| 产品 | [产品 Backlog](01-product/BACKLOG.md) | PM/PJM | Draft |
| 产品 | [业务术语表](01-product/GLOSSARY.md) | PM | Draft |
| 设计 | [UI/UX 设计说明](02-design/UI_UX_SPEC.md) | UI | Draft |
| 架构 | [总体架构](03-architecture/ARCHITECTURE.md) | Backend/Ops | In Review（ERP-006 增量） |
| 架构 | [产品与 Amazon 多店铺数据模型](03-architecture/PRODUCT_DATA_MODEL.md) | PM/Backend | In Review（当前 MVP 已裁剪） |
| 架构 | [ADR 索引](03-architecture/adr/README.md) | Backend/Ops | Active |
| 架构 | [API 规范](03-architecture/API_GUIDELINES.md) | Backend | Draft |
| 架构 | [数据字典](03-architecture/DATA_DICTIONARY.md) | PM/Backend | Draft |
| 研发 | [项目接手指南](04-engineering/PROJECT_HANDOVER.md) | PJM / Codex | In Review |
| 设计 | [当前 UI 规范](ui/UI_STANDARD.md) | UI / Frontend | 见文内日期增补及验收范围 |
| 项管 | [会话交接模板](../templates/SESSION_HANDOFF_TEMPLATE.md) | PJM / Codex | In Review |
| 研发 | [开发规范](04-engineering/DEVELOPMENT_GUIDE.md) | Frontend/Backend | Draft |
| 研发 | [环境说明](04-engineering/ENVIRONMENTS.md) | Ops | Draft |
| 研发 | [本地开发环境审计](04-engineering/LOCAL_ENVIRONMENT_AUDIT.md) | PJM | Completed |
| 安全 | [密钥管理规范](05-security/SECRETS_MANAGEMENT.md) | Ops/PJM | Approved |
| 安全 | [密钥登记表](05-security/SECRET_REGISTRY.md) | Ops | Draft |
| 安全 | [权限矩阵](05-security/ACCESS_MATRIX.md) | PM/PJM | Approved |
| 质量 | [测试策略](06-quality/TEST_STRATEGY.md) | QA | Approved |
| 质量 | [验收标准](06-quality/ACCEPTANCE_CRITERIA.md) | QA/PM/PJM | Approved |
| 运维 | [部署运行手册](07-operations/DEPLOYMENT_RUNBOOK.md) | Ops | Draft |
| 运维 | [监控与告警](07-operations/OBSERVABILITY.md) | Ops/Backend | Draft |
| 运维 | [备份与恢复](07-operations/BACKUP_RECOVERY.md) | Ops | Draft |
| 项管 | [Sprint 管理](08-project-management/SPRINTS/README.md) | PJM | Active |
| 项管 | [风险登记册](08-project-management/RISK_REGISTER.md) | PJM | Active |
| 项管 | [项目状态报告](08-project-management/STATUS_REPORT.md) | PJM | Active |
| 交付 | [交付物总账](DELIVERABLE_REGISTER.md) | PJM | Active |

最新累计迭代发布交接：[DEL-042（2026-11-21）](../deliverables/pjm/CATALOG_RELEASE_HANDOFF_2026-11-21.md)。

## 权威性说明

- `Approved` 表示相应范围和版本曾获批准，不表示文档自动覆盖后续全部实现；当前接手信息以带日期和证据的状态报告及代码地图为入口，业务规则冲突交 PJM 复核。
- 同一主题存在多个版本时，以状态为 `Approved` 且版本号最高者为准。
- 口头或聊天结论须进入决策日志或对应文档后，才视为正式项目决定。
- 旧版本不得删除，应标记为 `Superseded` 并指向新版本。
