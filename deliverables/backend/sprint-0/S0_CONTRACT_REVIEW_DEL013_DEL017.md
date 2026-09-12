# Sprint 0 DEL-013 / DEL-017 技术契约复核与差异关闭报告

| 字段 | 内容 |
| --- | --- |
| 复核角色 | Backend |
| 日期 | 2026-09-12 |
| 对象 | DEL-013 字段字典、DEL-017 v1.2 六类 Excel、产品数据模型 v1.1、DEL-025 OpenAPI/机器契约 |
| 结论 | **PASS（静态差异已关闭）；真实 API/数据库行为 Deferred** |
| 样本依赖 | 无；本结论仅针对结构契约 |
| 关闭基线 | PM `106a0e2`；Backend `4ebd2be + 49bef4a + c364a37`；本报告最终口径增量 |

## 1. 结论矩阵

| 核验项 | 文档/模板 | 后端实现映射 | 结论 |
| --- | --- | --- | --- |
| 字段类型与长度 | DEL-013 已定义；六个 Excel 导入表头与字典字段一致 | 尚无六类业务 DTO/Bean Validation | **PASS（结构）/ Deferred（实现）** |
| 大小写不敏感唯一键 | 产品、SKU、辅料及同店 Seller SKU 口径明确 | V1 仅脚手架基线，无领域索引/归一化列 | **PASS（结构）/ Deferred（实现）** |
| 跨表引用与依赖顺序 | 数据模型 v1.1、DEL-013 v1.2 与 DEL-017 一致 | 机器 DAG 无环；领域外键 Deferred | **PASS（静态）/ Deferred（实现）** |
| 父子 Listing | 条件必填、同店父体、角色/履约约束明确 | 尚无 DTO/数据库 Check/服务校验 | **PASS（结构）/ Deferred（实现）** |
| 期初 OPENING 事务/幂等 | DEL-013/017 v1.2 与数据模型一致 | OpenAPI 条件 Schema、提交语义与测试已冻结；运行服务 Deferred | **PASS（静态）/ Deferred（实现）** |
| 错误四要素 | 字段字典与数据模型一致 | `ImportIssue` DTO 与 BATCH/SHEET/ROW/FIELD 条件 Schema、AJV 正负实例齐全 | **PASS（静态）** |

## 2. P0 差异

### P0-01：通用 Upsert 与期初不可覆盖语义冲突 — CLOSED

- DEL-013 `COM-008` 写成所有导入都按唯一键 Upsert，唯一键冲突可更新同一记录。
- 数据模型 v1.1 要求期初库存是一次性幂等记账：同键同载荷重试返回既有结果；同键不同载荷拒绝；禁止覆盖 `OPENING`。
- 若按 COM-008 实现，会允许修改已落账期初并破坏不可编辑流水、余额重算和审计语义。

**最终口径与关闭证据：** COM-008 已限定前五类执行幂等 Upsert；`auxiliary_material_opening_inventory` 明确为 `CREATE_ONCE_IDEMPOTENT_COMMAND` 且 `ordinaryUpsertForbidden=true`。同键同依据同载荷返回既有结果并保持一条 OPENING；同键异载荷返回 `409 OPENING_INVENTORY_CONFLICT`；OPENING 不可编辑，纠错只追加调整/冲销流水。

**证据：**

- `deliverables/pm/FIELD_DICTIONARY_V1.md:33`
- `docs/03-architecture/PRODUCT_DATA_MODEL.md:181`
- `docs/03-architecture/PRODUCT_DATA_MODEL.md:211`

### P0-02：零期初与流水数量约束冲突 — CLOSED

- DEL-013 允许 `opening_quantity >= 0`。
- 同文件要求 `InventoryTransaction.quantity > 0`。
- 数据模型要求确认期初时创建 `InventoryTransaction(OPENING)`，因此数量为 0 时无法同时满足两项约束。

**PJM 最终口径（替代原二选一建议）：** 保留 `opening_quantity >= 0`。`transaction_type=OPENING` 时 `InventoryTransaction.quantity >= 0`；INBOUND、OUTBOUND、ADJUST_IN、ADJUST_OUT、REVERSAL 必须 `> 0`。零期初仍与余额初始化在同一事务生成唯一、不可编辑的 `quantity=0` OPENING 审计流水。

**机器关闭证据：** OpenAPI `InventoryTransactionQuantityRule` 使用 Draft 2020-12 `if/then/else` 表达条件数量；`PreviewImpact(resource=inventory_transactions)` 强制复用该 DTO。AJV 接受 8 个合法实例并拒绝 6 个非法实例，明确拒绝非 OPENING 的 `0` / `0.0000`、负数、JSON number 和未知类型。

**证据：**

- `deliverables/pm/FIELD_DICTIONARY_V1.md:152`
- `deliverables/pm/FIELD_DICTIONARY_V1.md:165`
- `docs/03-architecture/PRODUCT_DATA_MODEL.md:194`
- `deliverables/pm/scripts/generate_excel_templates.py:338`

## 3. P1 差异与实现入口条件

### P1-01：DEL-013 依赖图错误表达辅料依赖 SKU — CLOSED

`Product → SKU/Variant → Auxiliary Material` 会被实现为辅料主档依赖 SKU；实际辅料主档独立，正确图应为 Product 与 AuxiliaryMaterial 两条根链：

```text
Product → Variant ─┬→ StoreListing
                   └┐
AuxiliaryMaterial ──┴→ ProductAuxiliaryMaterial
AuxiliaryMaterial + Warehouse → Opening Inventory
```

DEL-013 v1.2 与 `import-dependency-dag.json` 已按双根无环 DAG 修订并通过自动化检查。

**证据：**

- `deliverables/pm/FIELD_DICTIONARY_V1.md:221`
- `deliverables/pm/EXCEL_IMPORT_TEMPLATES_V1.md:28`
- `docs/03-architecture/PRODUCT_DATA_MODEL.md:198`

### P1-02：Listing 唯一性负向样例覆盖不完整 — CLOSED

字段字典定义同店非空 FNSKU 唯一、同一 SKU 同店最多一个 ACTIVE Listing、父子同店且主题一致；现有错误样例覆盖角色必填、父体禁填、FBA/FBM 条件，但没有明确覆盖：

- 同店重复 FNSKU；
- 同店同 SKU 两条 ACTIVE Listing；
- 子体引用其他店父体；
- 父子 `variation_theme` 不一致。

建议补充合成错误行，避免只在文字中声明。

DEL-017 v1.2 已补齐上述 4 类合成负例，Backend/QA 工作簿回归 4/4 PASS。

**证据：**

- `deliverables/pm/FIELD_DICTIONARY_V1.md:107`
- `deliverables/pm/FIELD_DICTIONARY_V1.md:108`
- `deliverables/pm/FIELD_DICTIONARY_V1.md:109`
- `deliverables/pm/FIELD_DICTIONARY_V1.md:113`
- `deliverables/pm/scripts/generate_excel_templates.py:248`

### P1-03：Excel 数值范围提示与声明不完全一致 — CLOSED

DEL-017 声明数值列配置基本范围校验，但 `attributes_导入模板.display_order` 没有 Excel DataValidation。服务端仍必须校验，因此不影响权威规则，但应补充前端提示以符合模板声明。

DEL-017 v1.2 已增加 `display_order >= 1` DataValidation；六工作簿合计 17 个 DataValidation 回归 PASS。

**证据：**

- `deliverables/pm/EXCEL_IMPORT_TEMPLATES_V1.md:57`
- `deliverables/pm/FIELD_DICTIONARY_V1.md:79`
- `deliverables/pm/scripts/generate_excel_templates.py:183`

### P1-04：机器 DTO 已冻结；领域运行实现 Deferred

DEL-025 已提供 OpenAPI 3.1、`ImportIssue`、preview/impact DTO、库存流水条件 DTO、状态机、提交语义、DAG 与 capability 机器契约。当前 Flyway V1 仍只有脚手架 `schema_baseline`，没有 Product、Variant、Listing、AuxiliaryMaterial、库存余额/流水表或业务应用服务，因此不能声称约束已被真实 API/数据库自动化验证。

这符合 S0-BE-02“不实现正式业务功能”的范围，但应作为 Sprint 1 开发入口条件：

- MySQL 大小写不敏感唯一策略必须显式固定（归一化列/函数索引或明确 collation），不能依赖环境默认值；
- 领域外键、唯一索引、非负/正数 Check 与库存事务必须落迁移；
- DTO 使用定点数类型并约束 scale/precision、长度、枚举、条件必填；
- 行错误契约至少包含 `sheet`、`rowNumber`、`field`、`reason`，嵌入通用错误响应 `details`；
- 导入确认使用幂等请求号并持久化载荷摘要，支持同键同载荷重放和同键异载荷冲突检测。

**证据：**

- `deliverables/backend/sprint-0/jiugeng-erp-backend/src/main/resources/db/migration/V1__baseline.sql:1`
- `docs/03-architecture/API_GUIDELINES.md:17`
- `deliverables/backend/sprint-0/jiugeng-erp-backend/src/main/java/`（仅模块边界和 Collector Spike，无六类导入 DTO）

## 4. 已通过项目

- 六个 `.xlsx` 文件均存在且可由 `openpyxl` 只读打开。
- 六类导入表头与 DEL-013 字段集合一致，模板未增加私有业务字段。
- 产品/SKU/辅料编码长度、正则及大小写不敏感规则一致。
- Listing 的 Product/SKU/StoreAccount 引用、父体先于子体、父子角色与履约条件一致。
- SKU—辅料的复合唯一键和单位必须等于辅料基础单位一致。
- 期初库存依赖启用辅料和系统固定单仓，业务唯一键与辅助依据号防重口径一致。
- DEL-013 与数据模型均要求逐行错误四要素：工作表、行号、字段、原因。

## 5. 验证证据

执行了只读工作簿结构检查，结果：

```text
WORKBOOKS=6
IMPORT_SHEETS=7
HEADER_CONTRACT=PASS
```

实际模板路径：`deliverables/pm/excel_templates/`。

后端覆盖检查：

```text
DOMAIN_MIGRATIONS=0
OPENAPI_OPERATIONS=11
IMPORT_TYPES=6
BATCH_STATUSES=7
ROW_ERROR_DTO=ImportIssue
INVENTORY_QUANTITY_DTO=InventoryTransactionQuantityRule
AJV_IMPORT_ISSUE=valid:4 invalid-rejected:6
AJV_INVENTORY_QUANTITY=valid:8 invalid-rejected:6
```

## 6. Gate 建议

- **DEL-013/017：PASS（静态）**；P0-01/P0-02 与 P1-01～03 已按 `106a0e2` 及机器契约关闭。
- **DEL-025：PASS（Backend 自测）**；OpenAPI/JSON Schema/DTO/事务语义具备机器校验证据，增量仍按流程交 Frontend/QA 复核。
- **运行实现：Deferred/Blocked**；领域迁移、真实 DTO/controller/worker、并发幂等、不可编辑约束、余额重算与故障回滚必须在后续 Sprint 以数据库前后快照和动态用例验证。
- 真实样本只影响数据适配/UAT，不影响上述结构契约整改。
