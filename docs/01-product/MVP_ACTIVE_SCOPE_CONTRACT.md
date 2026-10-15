# 当前 MVP 活跃范围契约

| 字段 | 内容 |
| --- | --- |
| 任务 | ERP-006 |
| 版本 | v1.0 |
| 状态 | Approved |
| 日期 | 2026-10-15 |
| Owner | PJM / PM / Backend / Frontend / QA |
| 决策依据 | DEC-012、MVP_FINAL_PLAN v1.1、PRD v1.1 |
| 机器可读副本 | [mvp-active-scope-contract.json](mvp-active-scope-contract.json) |

## 1. 目的与优先级

本文件是 DEC-012 后当前 MVP 实施、测试和验收的范围覆盖层。出现范围冲突时，按以下顺序解释：

1. DEC-012 及其后批准的范围变更；
2. 本活跃范围契约；
3. MVP 最终规划和 PRD；
4. Sprint 0 已关闭的历史字段、模板和机器契约。

Sprint 0 的六类导入契约及其评审记录继续作为审计证据保留，不直接作为当前实现清单。任何重新引入店铺 Listing 映射的工作必须新增范围决策，不得仅恢复历史文件中的定义。

## 2. 当前 MVP 领域范围

| 领域 | 当前实现目标 | 明确后置 |
| --- | --- | --- |
| IAM / Audit | 用户、角色、会话、锁定/重置/改密、动作级鉴权、审计事件 | SSO、多租户、组织树、复杂数据权限 |
| Product | Product、ProductVariant、VariantAttribute、编码与状态 | AmazonCatalogItem、StoreAccount、StoreListing 及五店映射 |
| Material | AuxiliaryMaterial、ProductAuxiliaryMaterial 单层关系 | 多层 BOM、替代料、成本、采购与生产 |
| Inventory | 单一预置 Warehouse、余额、OPENING/INBOUND/OUTBOUND/ADJUSTMENT 流水 | 多仓、库位、批次、效期、调拨与成本 |
| Selection | Candidate、评估、状态、负责人、标签、评审记录、证据链接 | AI 决策、销量预测、评论分析 |
| Watch | 一个指定公开店铺、采集运行、快照、基线、新品事件、人工复核 | 公司店铺账号管理、SP-API Listing 同步、实时监控、多站点 |
| Import / Export | 五类当前 MVP 导入、结果文件、候选 CSV 导出 | Store Listing 导入、任意格式识别、跨模块报表中心 |

`WatchStore` 表示公开上新监控目标，不是公司 `StoreAccount`，不保存 Seller ID、账号凭据或授权关系。`NewListingEvent` 中的 Listing 仅表示公开店铺中新发现的商品事件，不构成 StoreListing 映射功能。

## 3. 当前导入类型

| 顺序 | importType | 逻辑资源 | 主要实体 | 依赖 |
| --- | --- | --- | --- | --- |
| 1 | `PRODUCTS` | `products` | Product | 无 |
| 2 | `VARIANTS` | `variants` | ProductVariant、VariantAttribute | PRODUCTS |
| 3 | `AUXILIARY_MATERIALS` | `auxiliary_materials` | AuxiliaryMaterial | 无 |
| 4 | `PRODUCT_AUXILIARY_MATERIALS` | `product_auxiliary_materials` | ProductAuxiliaryMaterial | VARIANTS、AUXILIARY_MATERIALS |
| 5 | `AUXILIARY_MATERIAL_OPENING_INVENTORY` | `auxiliary_material_opening_inventory` | InventoryBalance、InventoryTransaction(OPENING) | AUXILIARY_MATERIALS、预置 Warehouse |

五类导入统一采用“上传 → 全量校验 → 预览 → 确认提交 → 结果下载”。前四类按业务键幂等写入；期初库存是不可覆盖的一次性幂等记账，余额、OPENING 流水和审计必须整批同事务提交或回滚。

`STORE_LISTINGS` 和 `03_store_listings_v1.0.xlsx` 仅保留为历史契约/模板证据，不进入当前 UI、API、数据库迁移、Worker、测试通过率或试点验收。

## 4. API 资源边界

当前正式 API 只实现以下资源族，统一位于 `/api/v1`：

- `/auth`、`/users`、`/audit-events`
- `/products`
- `/materials`
- `/inventory`
- `/import-batches`
- `/selection/candidates`
- `/watch/stores`、`/watch/events`
- `/dashboard`、`/dashboard-maintenance`、`/dashboard-images`

当前 MVP 不提供 `/store-accounts`、`/store-listings` 或等价的公司店铺与 Listing 映射接口。监控目标只通过 `watch.*` 能力访问。

## 5. 动作级权限

当前 capability 共 22 个：

- `system.users.manage`
- `products.read`、`products.write`
- `materials.read`、`materials.write`
- `inventory.read`、`inventory.write`
- `selection.read`、`selection.write`
- `watch.read`、`watch.write`
- `imports.read`、`imports.template.download`、`imports.validate`、`imports.commit`、`imports.cancel`、`imports.result.download`
- `audit.read.all`、`audit.read.self`
- `dashboard.read`、`dashboard.shipments.manage`、`dashboard.pending-products.manage`

`listings.read`、`listings.write` 和 `stores.write` 不属于当前 capability 集合。角色映射以 [权限矩阵](../05-security/ACCESS_MATRIX.md) 为准，前端可见性不能替代后端逐接口鉴权。

## 6. 当前业务输入门禁

- `DATA-001`：20～50 条脱敏 Product/SKU 样本。
- `DATA-003`：辅料主档及 SKU—辅料关系样本。
- `DATA-004`：固定仓库编码及单仓期初库存样本。
- `WATCH-001`：3 个公开 Amazon 美国站样本店铺链接，用于数据源 Stage Gate；正式 MVP 只配置一个获批监控目标。
- `IAM-001`：首期用户、角色及试点人员清单。

`DATA-002` 五店 Listing 样本已由 DEC-012 从当前 MVP 门禁移除，状态为 `N/A-Approved`；未来恢复 Listing 范围时重新启用。

## 7. 历史契约处置

以下材料不删除、不改写其已关闭结论，但必须标记为历史基线：

- DEL-017 六类 Excel 模板与异步导入契约；
- DEL-025 Sprint 0 六类导入机器契约及其 Frontend/QA 复核；
- PRODUCT_DATA_MODEL v1.1 中 StoreAccount、AmazonCatalogItem、StoreListing 和六类导入章节；
- DATA-002 五店 Listing 脱敏样本输入项。

后续实现任务引用历史契约中的通用状态机、错误、幂等和 OPENING 语义时，必须同时应用本文件的五类范围过滤，禁止据此创建 Listing 功能。

## 8. ERP-006 退出条件

- 当前范围的人类可读与机器可读契约一致；
- PRD、架构、权限、测试策略和业务输入门禁均指向本契约；
- 历史六类契约清楚标记为审计证据，不再被误认为当前实现目标；
- 当前 MVP 中不存在 Listing capability、Listing API、Listing 导入或 DATA-002 退出门禁；
- Markdown 链接、JSON 解析、范围一致性检查和 `git diff --check` 通过。
