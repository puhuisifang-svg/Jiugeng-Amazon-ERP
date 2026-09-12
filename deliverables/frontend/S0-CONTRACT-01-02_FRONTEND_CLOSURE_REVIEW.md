# S0-CONTRACT-01/02 前端关闭复核

| 字段 | 内容 |
| --- | --- |
| 交付物 ID | DEL-024 |
| 复核人 | Frontend |
| 输入版本 | DEL-017 v1.1、权限矩阵 v1.1 |
| 复核提交 | `c43347a18675261663a037c5207887c2bc4c4dfe` |
| 日期 | 2026-09-12 |
| 状态 | Completed（PJM 核验通过） |
| 总结论 | **PASS（FE-CONTRACT-01～07 全部通过；P0=0）** |

## 1. 逐项结论

| 项目 | 结论 | 复核证据 | 残留歧义 |
| --- | --- | --- | --- |
| FE-CONTRACT-01 上传元数据与门禁 | PASS | 6 个 `importType`、`templateVersion=1.0`、`.xlsx`、准确 MIME、1～10,485,760 bytes、最多 1,000 非空数据行、版本/表头/不安全内容错误码均已冻结；文件名不作为唯一判断依据。 | 无产品语义歧义；浏览器只做快速提示，服务端仍须校验真实 OOXML/MIME/hash。 |
| FE-CONTRACT-02 批次状态、取消与 expiry | PASS | 7 状态、终态、取消阶段、409、8 phase、进度字段、终态不回退、24 小时 preview expiry 均已冻结；明确无 `PARTIALLY_SUCCEEDED`。 | `IMPORT_PREVIEW_EXPIRED` 后是复用原批次重验还是创建新批次，需 Backend OpenAPI 固定调用方式。 |
| FE-CONTRACT-03 Issue DTO | PASS | `code/severity/blocking/scope/sheet/row/field/reason/rawValue/suggestion` 类型与 nullable 语义齐全；四级 severity、阻断规则、稳定排序已冻结。 | 分页 envelope/游标的具体 JSON 结构待 OpenAPI；不影响 issue 行模型。 |
| FE-CONTRACT-04 Preview/diff/impact | PASS | `CREATE/UPDATE/UNCHANGED/null`、`rowIdentity`、before/after 白名单、稳定排序、7 项 summary、计数等式、SKU 双表计数、`impactSummary` 与 `impacts[]` 均明确。 | `businessKey`、before/after、impacts 的最终 JSON 对象形状与字段类型待 OpenAPI schema 固定。 |
| FE-CONTRACT-05 原子提交、重试与幂等 | PASS | 六类均整批原子；失败整批回滚；`writtenRows` 等式；上传/取消/确认幂等作用域与冲突码；期初库存特例均明确。 | 无产品语义歧义；HTTP endpoint/header/response envelope 待 Backend OpenAPI。 |
| FE-CONTRACT-06 依赖 DAG | PASS | Listing 仅依赖 Product+SKU+预置 StoreAccount；关系依赖 SKU+辅料；期初依赖辅料+唯一仓库；Product 与辅料可并行；同文件内顺序清楚。 | 无。 |
| FE-CONTRACT-07 capability/角色 | PASS | 权限矩阵 v1.1 冻结全域 capability；DEL-017 固定 6 个 import capability，ADMIN/PRODUCT_MATERIAL 全有、SELECTION 全无；StoreAccount 预置且无 `/system/stores`。 | 真实 IAM、前端 `can()` 和守卫按计划后移，不属于本轮实现验收。 |

## 2. `_meta` 工作簿实测

| 模板 | `importType` | state | version | max rows | 结论 |
| --- | --- | --- | --- | ---: | --- |
| `01_products_v1.0.xlsx` | `PRODUCTS` | `veryHidden` | `1.0` | 1000 | PASS |
| `02_variants_v1.0.xlsx` | `VARIANTS` | `veryHidden` | `1.0` | 1000 | PASS |
| `03_store_listings_v1.0.xlsx` | `STORE_LISTINGS` | `veryHidden` | `1.0` | 1000 | PASS |
| `04_auxiliary_materials_v1.0.xlsx` | `AUXILIARY_MATERIALS` | `veryHidden` | `1.0` | 1000 | PASS |
| `05_sku_auxiliary_materials_v1.0.xlsx` | `PRODUCT_AUXILIARY_MATERIALS` | `veryHidden` | `1.0` | 1000 | PASS |
| `06_initial_inventory_v1.0.xlsx` | `AUXILIARY_MATERIAL_OPENING_INVENTORY` | `veryHidden` | `1.0` | 1000 | PASS |

六个 `_meta` 的 `schemaIdentifier` 均精确为 `jiugeng.amazon.erp.import`；文件均可由 `openpyxl` 打开，大小均远低于 10 MiB，原有 16 组 Excel 数据校验仍存在。

## 3. 前端边界结论

- 当前 `/imports`、`/imports/:batchId` 的路由级角色边界与 v1.1 一致：仅 `ADMIN`、`PRODUCT_MATERIAL`；`SELECTION` 无入口。
- 当前无 `/system/stores`，与“5 店系统预置”决策一致。
- FE-CONTRACT-01～07 已具备建立 TypeScript DTO、状态机和 capability 常量的稳定产品语义；但本轮只做复核，不提前实现。
- 联调实现应等待 Backend OpenAPI，避免猜测 endpoint、response envelope、pagination 和上述三个残留 JSON/重验细节。

## 4. 证据路径

- [DEL-017 v1.1 异步导入契约](../pm/EXCEL_IMPORT_TEMPLATES_V1.md)
- [权限矩阵 v1.1](../../docs/05-security/ACCESS_MATRIX.md)
- [六个 Excel 模板](../pm/excel_templates/)
- [生成与自检脚本](../pm/scripts/generate_excel_templates.py)
- [DEL-023 原始差异清单](S0-PM-FRONTEND_CONTRACT_REVIEW.md)
- [现有前端路由](../../frontend/src/router/index.ts)

## 5. 验证摘要

```text
reviewed commit: c43347a18675261663a037c5207887c2bc4c4dfe
xlsx open: 6/6 PASS
veryHidden _meta: 6/6 PASS
schemaIdentifier/importType/templateVersion/maxDataRows: 6/6 exact match
Excel data validations retained: 16
FE-CONTRACT-01..07: 7 PASS / 0 FAIL
P0: 0
git diff --check: PASS
```

## 6. 最终结论

**PASS。** DEL-017 v1.1 与权限矩阵 v1.1 已在产品契约层关闭 DEL-023 的 FE-CONTRACT-01～07。残留项是 Backend OpenAPI 的实现级细化，不阻塞 Sprint 0 契约冻结，但在前端进入联调编码前必须明确。

## 7. PJM 核验

- DEL-024 复核报告：**PASS / Completed**。
- PJM 独立验证：6/6 工作簿可读、6/6 `_meta` 为 veryHidden 且四项机器值一致、16 组数据校验保留；7 状态和 6 个导入 capability 均可检索，`git diff --check` 通过。
- Frontend 侧 FE-CONTRACT-01～07 关闭；项目级 S0-CONTRACT-01 仍须 Backend OpenAPI 与 QA 复核，S0-CONTRACT-02 仍须 QA 复核。
