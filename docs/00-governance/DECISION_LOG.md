# 决策日志

| ID | 日期 | 决策主题 | 结论 | 状态 | 决策人 | 关联文档 |
| --- | --- | --- | --- | --- | --- | --- |
| DEC-001 | 2026-09-12 | 项目名称 | 项目命名为“九更亚马逊 ERP” | Approved | 老板 | [项目章程](PROJECT_CHARTER.md) |
| DEC-002 | 2026-09-12 | 项目成员模型 | PM、UI、Frontend、Backend、QA、Ops 与 PJM 统一使用 `openai/gpt-5.6-sol`；main 不属于项目成员 | Approved | 老板 | [项目 README](../../README.md) |
| DEC-003 | 2026-09-12 | 首期业务基线 | 美国站、5 个内部店铺；产品、辅料/物料、生产为第一优先级，选品与监控为第二优先级，登录/RBAC 技术先行 | Approved | 老板 | [首期规划](../01-product/PHASE1_SCOPE_PLAN.md) |
| DEC-004 | 2026-09-12 | MVP 范围收缩 | 首期只做登录、产品数据、产品对应辅料、选品和任意一个店铺上新监控；生产等能力后置，DEC-003 的首期范围被本决策替代 | Approved | 老板 | [精简 MVP](../01-product/MVP_SCOPE_PLAN.md) |
| DEC-005 | 2026-09-12 | 辅料库存范围 | 精简 MVP 增加辅料库存维护；包含基础仓库、期初、入出库、调整、余额、流水和安全库存提示，采购/生产/批次/成本后置 | Approved | 老板 | [精简 MVP](../01-product/MVP_SCOPE_PLAN.md) |
| DEC-006 | 2026-09-12 | 辅料仓库数量 | 首期只管理一个辅料仓库，不做仓库主档、多仓切换、库位或仓库调拨 | Approved | 老板 | [精简 MVP](../01-product/MVP_SCOPE_PLAN.md) |
| DEC-007 | 2026-09-12 | 登录角色 | 首期只设系统管理员、产品/辅料人员、选品人员三类角色，不设只读角色 | Approved | 老板 | [权限矩阵](../05-security/ACCESS_MATRIX.md) |
| DEC-008 | 2026-09-12 | 产品多规格 | 一个内部产品包含多个 SKU；产品结构按 Amazon 美国站 5 店铺背景设计，内部产品/SKU、Amazon Catalog ASIN、各店铺 Listing 分层 | Approved | 老板 | [产品数据模型](../03-architecture/PRODUCT_DATA_MODEL.md) |
| DEC-009 | 2026-09-12 | 5 店 Listing 数据方式 | MVP 采用人工维护和 Excel 导入管理 5 个公司店铺的 Listing 映射；SP-API 自动同步后置 | Approved | 老板 | [MVP PRD](../01-product/PRD.md) |
| DEC-010 | 2026-09-12 | 内部产品编码 | Product/SPU、内部 SKU 和辅料编码均由业务人员手动输入维护，系统不自动生成 | Approved | 老板 | [编码规则](../01-product/PRODUCT_CODE_STANDARD.md) |
| DEC-011 | 2026-09-12 | MVP 最终规划 | 老板授权 PJM 决定剩余实现细节并形成最终版；MVP v1.0 范围、路线、架构和验收基线获批，Sprint 0 仍需单独启动 | Approved | 老板 | [MVP 最终规划](../01-product/MVP_FINAL_PLAN.md) |

## 记录规则

- 架构决策使用 ADR，业务和项目决策登记在本表。
- 任何影响范围、成本、排期、安全或上线的决定必须留痕。
- 决策变更时新增记录，不直接抹除历史结论。
