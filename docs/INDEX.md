# 项目文档总索引

## 本轮发布交接（2026-11-25）

[ERP-104修复与官网需求归档 / DEL-055](../deliverables/pjm/ERP-104_RELEASE_HANDOFF_2026-11-25.md)：795项前端测试、6个相关浏览器场景通过；用户授权PJM提交main并部署。官网仅需求，页面和8081入口另待开发。

## 新需求评审（2026-11-25）

[九更静态官网与 ERP 域名分流 PRD / DEL-054](01-product/JIUGENG_CORPORATE_WEBSITE_PRD_v0.1_DRAFT.md)：v0.6，默认HTTP IP入口给官网，临时ERP建议用IP:8081（完整前端与API，TCP8081对所有外网放行、保留登录权限）；先验证ERP入口再切换，未部署；首期用于备案的静态企业展示，Logo/一般文案自主设计；根域/www及HTTP公网IP进入官网，erp子域进入现有ERP。IP默认80无需手填端口，ERP内部服务不动；仅需求草案，开放时机按备案类型与接入商要求。

## 当前接手入口（2026-11-24 更新）

先读 [README](../README.md) → [AGENTS](../AGENTS.md) → [项目接手指南](04-engineering/PROJECT_HANDOVER.md) → [当前状态报告](08-project-management/STATUS_REPORT.md)。界面任务同时读 [UI 规范](ui/UI_STANDARD.md)，跨会话使用 [交接模板](../templates/SESSION_HANDOFF_TEMPLATE.md)。

当前发布证据：[DEL-052 PDF 连续工作区与 HS 历史引用](../deliverables/pjm/PDF_HS_RELEASE_HANDOFF_2026-11-24.md)。PR #24 已受保护合并，应用精确 SHA 的 Production 实际部署与入口版本已核验；详见[状态报告](08-project-management/STATUS_REPORT.md)。归档文档 PR 的最终部署由 PJM 同会话核实，Codex 不等待。以下为开发阶段记录，不代表当前未提交状态。

同迭代新增：[客户商品 HS 历史资料核对带入 / DEL-051](../deliverables/pjm/CUSTOMER_HS_HISTORY_LOCAL_2026-11-24.md)，本地已实现，完整门禁待交接。

当前本地追加：[PDF 连续页面工作区](../deliverables/pjm/PDF_CONTINUOUS_WORKSPACE_LOCAL_2026-11-24.md)；2026-11-24，默认提取合并、全部页连续勾选、连续编辑及缩放，未提交，统一验证待交接。

当前发布交接：[ERP-103 多模块体验修复 / DEL-053](../deliverables/pjm/ERP-103_CUSTOMS_FILES_LOCAL_2026-11-24.md)。用户已授权交项目经理提交 main/部署；本地统一验证完成，全部未提交，发送交接后由 PJM 独占接管。Codex 不跟踪线上发布。

当前授权发布交接：[DEL-049 图片性能与 PDF](../deliverables/pjm/IMAGE_PDF_RELEASE_HANDOFF_2026-11-23.md)；统一验证完成，正式移交后由 PJM 独占写入、提交 main 和部署，Codex 不跟踪上线。下述是开发阶段记录。

开发阶段累计迭代：[ERP-101 图片性能 1–7 项 / DEL-047](../deliverables/pjm/ERP-101_IMAGE_PERFORMANCE_LOCAL_2026-11-23.md)。显示尺寸不变，外链转受控存储并更新；本地已同步 V107，完整验证与发布授权状态已由 DEL-049 更新。

同迭代追加：[PDF 编辑选定九项收尾 / DEL-048](../deliverables/pjm/PDF_CLOSEOUT_LOCAL_2026-11-23.md)，本地未提交；保留四页签源文件命名，最终验证与未测边界见 DEL-049。

上一轮累计发布：[DEL-046 交接](../deliverables/pjm/OPERATIONS_RELEASE_HANDOFF_2026-11-23.md)及 [PJM 发布结果](../deliverables/pjm/OPERATIONS_RELEASE_RESULT_2026-11-23.md)。辅料 / 供应商 / 采购原开发记录（DEL-043–045）保留技术背景，其未提交状态不再代表当前工作区。

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

上一轮产品/样品发布交接：[DEL-042（2026-11-21）](../deliverables/pjm/CATALOG_RELEASE_HANDOFF_2026-11-21.md)。

## 权威性说明

- `Approved` 表示相应范围和版本曾获批准，不表示文档自动覆盖后续全部实现；当前接手信息以带日期和证据的状态报告及代码地图为入口，业务规则冲突交 PJM 复核。
- 同一主题存在多个版本时，以状态为 `Approved` 且版本号最高者为准。
- 口头或聊天结论须进入决策日志或对应文档后，才视为正式项目决定。
- 旧版本不得删除，应标记为 `Superseded` 并指向新版本。
