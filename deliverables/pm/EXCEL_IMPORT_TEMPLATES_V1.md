# 六类 Excel 导入模板与校验样例

| 字段 | 内容 |
| --- | --- |
| 项目 | 九更亚马逊 ERP |
| 交付物 ID | DEL-017 |
| Owner | PM（模板口径）/ Backend（导入契约）/ QA（错误覆盖） |
| 关联 Sprint/任务 | Sprint 0 / S0-PM-02、S0-CONTRACT-01/02 |
| 版本 | v1.1 |
| 状态 | In Review（模板结构已通过初检，异步导入契约待 Frontend/QA 复核） |
| 日期 | 2026-09-12 |

## 1. 交付清单

| # | 模板 | 业务主键/唯一键 | 前置依赖 | 工作表 |
| --- | --- | --- | --- | --- |
| 1 | `01_products_v1.0.xlsx` | `product_code` | 无 | 填写说明、导入模板、正确样例、错误样例、枚举值 |
| 2 | `02_variants_v1.0.xlsx` | `sku_code`；`sku_code + attribute_code` | 产品 | SKU 与动态属性各自的模板/正确/错误样例、枚举值 |
| 3 | `03_store_listings_v1.0.xlsx` | `store_code + marketplace_id + seller_sku` | 产品、SKU、系统预置 5 店 | 填写说明、导入模板、正确样例、错误样例、枚举值 |
| 4 | `04_auxiliary_materials_v1.0.xlsx` | `material_code` | 无 | 填写说明、导入模板、正确样例、错误样例、枚举值 |
| 5 | `05_sku_auxiliary_materials_v1.0.xlsx` | `sku_code + material_code` | SKU、辅料 | 填写说明、导入模板、正确样例、错误样例、枚举值 |
| 6 | `06_initial_inventory_v1.0.xlsx` | `warehouse_code + material_code` | 辅料、系统固定单仓 | 填写说明、导入模板、正确样例、错误样例 |

文件目录：[excel_templates](excel_templates/)。可重复生成脚本：[generate_excel_templates.py](scripts/generate_excel_templates.py)。

## 2. 导入类型、模板版本与文件门禁

### 2.1 机器值

| `importType` | 模板文件 | `templateVersion` | 导入工作表 | 最大数据行 |
| --- | --- | --- | --- | ---: |
| `PRODUCTS` | `01_products_v1.0.xlsx` | `1.0` | `导入模板` | 1,000 |
| `VARIANTS` | `02_variants_v1.0.xlsx` | `1.0` | `variants_导入模板`、`attributes_导入模板` | 两表合计 1,000 |
| `STORE_LISTINGS` | `03_store_listings_v1.0.xlsx` | `1.0` | `导入模板` | 1,000 |
| `AUXILIARY_MATERIALS` | `04_auxiliary_materials_v1.0.xlsx` | `1.0` | `导入模板` | 1,000 |
| `PRODUCT_AUXILIARY_MATERIALS` | `05_sku_auxiliary_materials_v1.0.xlsx` | `1.0` | `导入模板` | 1,000 |
| `AUXILIARY_MATERIAL_OPENING_INVENTORY` | `06_initial_inventory_v1.0.xlsx` | `1.0` | `导入模板` | 1,000 |

`importType` 是 API/任务机器值；逻辑资源名分别为 `products`、`variants`、`store_listings`、`auxiliary_materials`、`product_auxiliary_materials`、`auxiliary_material_opening_inventory`。模板文件名、机器值、逻辑资源和领域实体的映射以 `PRODUCT_DATA_MODEL.md` §6 为准。

每个工作簿包含不可见的 `_meta` 工作表，固定记录 `schemaIdentifier=jiugeng.amazon.erp.import`、`importType`、`templateVersion=1.0`、`maxDataRows=1000`。上传端以请求值、`_meta` 和服务器支持矩阵三方一致为兼容条件；文件名只用于用户识别，不作为唯一判断依据。

### 2.2 上传限制

| 项目 | MVP 冻结值 | 错误码 |
| --- | --- | --- |
| 扩展名 | 仅 `.xlsx`；拒绝 `.xls`、`.xlsm`、`.csv`、压缩包及改后缀文件 | `IMPORT_FILE_TYPE_UNSUPPORTED` |
| MIME | `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet` | `IMPORT_MIME_UNSUPPORTED` |
| 单文件大小 | `1..10,485,760` bytes（最大 10 MiB） | `IMPORT_FILE_TOO_LARGE` / `IMPORT_FILE_EMPTY` |
| 数据行数 | 按上表统计非空数据行，不含表头、说明/样例/枚举工作表；最大 1,000 | `IMPORT_ROW_LIMIT_EXCEEDED` |
| 模板版本 | 请求 `templateVersion` 必须等于文件内版本且为服务器支持版本 | `IMPORT_TEMPLATE_VERSION_MISMATCH` |
| 工作表/表头 | 必须与对应模板精确匹配；禁止额外导入列，列顺序可由表头解析 | `IMPORT_SHEET_MISSING` / `IMPORT_HEADER_MISMATCH` |
| 宏与外部链接 | 不允许宏、外部工作簿链接或嵌入可执行对象 | `IMPORT_UNSAFE_CONTENT` |

服务端必须校验扩展名、MIME、OOXML 实际结构、模板版本和文件 SHA-256；不能只信任浏览器提供的文件名/MIME。上传成功后返回服务器计算的 `fileHash`，后续预览和确认必须绑定该哈希。

## 3. 异步批次状态与取消规则

### 3.1 状态机器值

| `status` | 含义 | 是否终态 | 是否允许取消 | 业务数据写入 |
| --- | --- | --- | --- | --- |
| `QUEUED` | 已创建批次，等待校验 Worker | 否 | 是 | 无 |
| `VALIDATING` | 解析文件并执行全量业务校验 | 否 | 是（协作式取消） | 无 |
| `VALIDATED` | 校验完成，预览可读；以 `commitAllowed` 表示能否提交 | 否 | 是 | 无 |
| `COMMITTING` | 已接受确认，执行整批原子提交 | 否 | 否 | 事务提交中 |
| `SUCCEEDED` | 整批提交成功或全部为不变记录 | 是 | 否 | 已提交 |
| `FAILED` | 不可恢复的解析/系统/提交故障；提交故障必须已整批回滚 | 是 | 否 | 无新增写入或已回滚 |
| `CANCELLED` | 用户在允许阶段取消完成 | 是 | 否 | 无 |

MVP **不定义 `PARTIALLY_SUCCEEDED`**：确认提交采用整批原子事务，不允许部分业务数据成功。业务校验错误仍进入 `VALIDATED`，并返回完整 issue/preview；只有技术故障进入 `FAILED`。

### 3.2 状态迁移

```text
QUEUED → VALIDATING → VALIDATED → COMMITTING → SUCCEEDED
   │          │           │             └────→ FAILED（整批回滚）
   └──────────┴───────────┴────→ CANCELLED
QUEUED / VALIDATING ────────────→ FAILED（技术故障）
```

- `cancel` 仅允许 `QUEUED`、`VALIDATING`、`VALIDATED`；重复取消返回当前 `CANCELLED`，不报二次错误。
- `VALIDATING` 取消采用安全点协作式终止；状态变为 `CANCELLED` 前不得产生业务写入。
- 从 `COMMITTING` 起返回 `409 IMPORT_NOT_CANCELLABLE`，避免用户误以为可回滚已开始的业务事务。
- 进度响应固定包含 `phase`、`processedRows`、`totalRows`、`percent`（0～100）、`updatedAt`；终态不得回退。
- `phase` 机器值固定为 `WAITING` / `PARSING` / `VALIDATING_ROWS` / `BUILDING_PREVIEW` / `WAITING_CONFIRMATION` / `COMMITTING_ROWS` / `FINALIZING` / `DONE`；它只描述当前阶段，不替代 `status`。
- `VALIDATED` 返回 `previewVersion` 与 `previewExpiresAt`；默认有效期 24 小时。过期后确认返回 `409 IMPORT_PREVIEW_EXPIRED`，必须重新校验。

## 4. Issue 分级与阻断规则

### 4.1 Issue DTO

每条 issue 至少包含：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `code` | string | 稳定机器码，不使用中文句子做判断。 |
| `severity` | enum | `BLOCKER` / `ERROR` / `WARNING` / `INFO`。 |
| `blocking` | boolean | `BLOCKER`、`ERROR` 固定为 `true`；其他固定为 `false`。 |
| `scope` | enum | `BATCH` / `SHEET` / `ROW` / `FIELD`。 |
| `sheet` | string/null | 工作表；批次级问题可空。 |
| `row` | integer/null | Excel 物理行号（表头为 1）；行级/字段级必填。 |
| `field` | string/null | 模板字段机器名；字段级必填。 |
| `reason` | string | 用户可读原因。 |
| `rawValue` | string/null | 脱敏后的原值；不得返回密钥或公式内容。 |
| `suggestion` | string/null | 可执行修复建议。 |

### 4.2 分级

| 级别 | 示例 | 结果 |
| --- | --- | --- |
| `BLOCKER` | 文件格式/版本不支持、工作表缺失、不安全内容、文件无法解析 | 整个批次不可预览业务 diff、不可提交。 |
| `ERROR` | 必填缺失、格式/枚举错误、批内/库内唯一冲突、引用不存在、条件/父子关系错误 | 返回可定位行；批次可查看预览，但 `commitAllowed=false`。 |
| `WARNING` | 可疑空白、将停用记录更新为启用、非阻断的格式规范化提示 | 可提交；确认页必须展示并要求用户明确确认。 |
| `INFO` | trim、大小写规范化、完全不变记录等 | 可提交；仅提示。 |

任意 `blocking=true` 时 `commitAllowed=false`；服务端必须拒绝绕过前端直接确认。issue 排序固定为 `severity → sheetOrder → row → field → code`，保证分页/刷新稳定。

## 5. Preview 新增/更新/不变与影响口径

### 5.1 行级动作

| `action` | 判断口径 | 提交影响 |
| --- | --- | --- |
| `CREATE` | 规范化业务键在数据库不存在，且批内不重复 | 新建一条直接业务记录。 |
| `UPDATE` | 业务键已存在，至少一个可导入字段与当前有效值不同 | 更新同一记录并记录审计前后值。 |
| `UNCHANGED` | 业务键已存在，规范化后所有可导入字段相同 | 不写业务表；计入不变。 |
| `null` | 该行有阻断 issue，无法可靠计算动作 | 不得提交。 |

`before` / `after` 仅返回模板白名单字段；敏感字段、SecretRef、系统内部列不进入 diff。每行 `rowIdentity` 固定包含 `sheet`、`row`、`businessKey`。预览排序按模板工作表顺序、物理行号、业务键稳定输出。

### 5.2 汇总计数

`summary` 固定包含：`inputRows`、`validRows`、`invalidRows`、`createRows`、`updateRows`、`unchangedRows`、`warningRows`。其中：

- `inputRows = validRows + invalidRows = createRows + updateRows + unchangedRows + invalidRows`；按实际导入工作表的非空物理数据行统计。
- SKU 文件的 `variants` 与 `variant_attributes` 行分别计数；不能把一个 SKU 的多条属性合并成一行。
- `warningRows` 是至少含一个 WARNING 的去重行数，可与 CREATE/UPDATE/UNCHANGED 重叠，不参与上述等式相加。
- 派生写入不增加输入行计数，另放 `impactSummary`：例如 Listing 行导致 `AmazonCatalogItem` Upsert，期初行同时影响余额与 `OPENING` 流水。

`impactSummary` 至少按 `resource + effect(CREATE/UPDATE/UNCHANGED)` 汇总数量；行详情的 `impacts[]` 标明直接/派生资源、业务键和 effect。不得把“可能受影响”计为已写入。

## 6. 确认事务、重试与幂等

### 6.1 整批原子决策

- 六类导入均在全量校验通过后才能确认；每次确认对该批次执行**一个整批业务事务**。
- 任一行在提交时因并发变化、唯一键或引用约束失败，整批回滚并进入 `FAILED`；结果标明失败行/字段，不留下部分业务数据。
- `COMMITTING` 前再次校验 `batchId + fileHash + importType + templateVersion + previewVersion`；任一不匹配拒绝提交。
- `SUCCEEDED` 的 `writtenRows = createRows + updateRows`；`UNCHANGED` 不触发业务更新，但保留批次结果与审计关联。

### 6.2 幂等与重试

- 上传、取消、确认等写操作均要求 `Idempotency-Key`（1～128 位不透明字符串）；幂等作用域为“当前用户 + 操作 + 业务对象”。
- 相同上传幂等键与相同文件哈希返回原批次；相同键不同哈希返回 `409 IDEMPOTENCY_KEY_REUSED`。
- 相同 `batchId + confirm Idempotency-Key + previewVersion` 重试返回第一次确认结果；不得重复创建、更新或记账。
- 已成功批次使用新确认键再次提交，返回 `409 IMPORT_ALREADY_COMMITTED`。
- 前五类按 `PRODUCT_DATA_MODEL.md` 中业务键执行幂等 Upsert；同一批内业务键重复是 `ERROR`，不以最后一行覆盖。
- 期初库存不是覆盖式 Upsert：首次确认在同一整批事务内为每个有效输入行创建余额/期初记录和一条不可编辑的 `InventoryTransaction(OPENING)`；相同业务键、依据和载荷的重试返回既有结果且仅有一条 `OPENING`；同键不同载荷返回 `409 OPENING_INVENTORY_CONFLICT`，纠错只能追加调整/冲销流水。

## 7. 依赖 DAG

```text
01 PRODUCTS ─────────→ 02 VARIANTS ────────┬→ 03 STORE_LISTINGS
系统预置 5 个 StoreAccount ────────────────┘

02 VARIANTS ───────────────────────────────┬→ 05 PRODUCT_AUXILIARY_MATERIALS
04 AUXILIARY_MATERIALS ────────────────────┘

04 AUXILIARY_MATERIALS ────────────────────┬→ 06 AUXILIARY_MATERIAL_OPENING_INVENTORY
系统唯一启用 Warehouse ────────────────────┘
```

- `PRODUCTS` 与 `AUXILIARY_MATERIALS` 可并行；按文件号 `01 → 02 → 03 → 04 → 05 → 06` 串行也合法。
- Listing 只依赖 Product、SKU、系统预置 StoreAccount，不依赖辅料；不得用线性 UI 流程额外阻断。
- SKU—辅料关系同时依赖启用 SKU 与启用辅料，且 `unit = base_unit`。
- 期初库存依赖启用辅料与系统唯一启用仓库，是库存链路首次/最后落账步骤。
- SKU 文件内先处理 `variants` 再处理 `variant_attributes`；Listing 同批父体必须先解析，子体必须同店引用父体。
- StoreAccount 由系统受控预置 5 店，MVP 不提供新增/编辑页面，不新增 `/system/stores`。

## 8. Capability 名称与三角色映射

导入中心使用下列动作级 capability；前端 `can()` 与后端接口鉴权必须使用相同机器值：

| Capability | 系统管理员 `ADMIN` | 产品/辅料人员 `PRODUCT_MATERIAL` | 选品人员 `SELECTION` |
| --- | --- | --- | --- |
| `imports.read` | 是 | 是 | 否 |
| `imports.template.download` | 是 | 是 | 否 |
| `imports.validate` | 是 | 是 | 否 |
| `imports.commit` | 是 | 是 | 否 |
| `imports.cancel` | 是 | 是 | 否 |
| `imports.result.download` | 是 | 是 | 否 |

- 角色只用于初始化 capability 集合；按钮、路由和 API 一律判断 capability，不硬编码中文角色名。
- `SELECTION` 对产品/Listing/辅料只有查看能力，不获得六类业务导入能力；选品/监控未来若有独立导入，应定义新的 `importType` 和 capability，不复用本契约。
- `StoreAccount` 预置不暴露应用内 capability；真实用户、会话、Token 与 IAM 执行在后续实现，本轮只冻结名称和映射。
- 全量领域 capability 与审计范围见 `docs/05-security/ACCESS_MATRIX.md` v1.1。

## 9. 最小请求/响应语义

### 9.1 创建校验批次

请求语义：`importType + templateVersion + file + Idempotency-Key`。成功返回 `batchId`、`status=QUEUED`、`fileHash`、限制快照和创建时间。

### 9.2 查询批次/预览

返回 `batchId`、`importType`、`templateVersion`、`status`、进度字段、`commitAllowed`、issue 分级汇总、preview summary、`previewVersion`、`previewExpiresAt`。issues 与 rows 分页必须分别使用稳定游标或稳定页码排序，刷新不得重复/漏行。

### 9.3 确认与取消

- 确认请求：`batchId + previewVersion + fileHash + Idempotency-Key + warningsAccepted`。存在 WARNING 且 `warningsAccepted=false` 时返回 `409 IMPORT_WARNINGS_NOT_ACCEPTED`。
- 取消请求：`batchId + Idempotency-Key`。响应返回最终/当前状态；不允许阶段返回 `409 IMPORT_NOT_CANCELLABLE`。
- HTTP 错误只表达请求级结果；业务 issue 始终使用稳定 `code/severity/blocking`，不得要求前端解析中文 `reason`。

## 10. 样例数据声明

- `TEST-*`、`example.invalid` 等均为人工构造的**合成校验夹具**，用于说明通过/失败规则。
- 合成样例不是公司真实产品、店铺、库存或用户数据，不得作为业务验收证据或容量结论。
- 真实验收仍依赖 20～50 条业务提供的脱敏样本，当前状态见 `BUSINESS_INPUT_CHECKLIST_V1.md`。

## 11. 已覆盖的错误类型

| 模板 | 必填 | 唯一 | 枚举/格式 | 引用/条件 |
| --- | --- | --- | --- | --- |
| 产品 | 缺产品编码 | 大小写不敏感重复编码 | 非法编码、非法状态 | - |
| SKU | 缺条件字段 | 重复 SKU/属性编码/顺序 | 条码、重量、单位、状态 | 产品/SKU 不存在；重量与单位联动 |
| Listing | 子体缺字段 | 同店 Seller SKU 规则 | ASIN、角色、履约、状态 | 店铺/Product/SKU/父体引用；父子/履约/FNSKU 条件 |
| 辅料 | 缺辅料编码 | 大小写不敏感重复编码 | 类型、单位、状态、图片 URL | - |
| SKU—辅料 | - | 重复关系 | 用量、单位、损耗率、布尔值 | SKU/辅料引用；单位一致 |
| 期初库存 | 缺日期/依据号 | 重复单仓辅料/依据号 | 非负数量、未来日期 | 固定仓库与辅料引用 |

## 12. Excel 交互约束

- 所有导入表冻结首行并启用筛选；表头带字段注释（关键字段）。
- 枚举列配置下拉校验；数值列配置基本范围校验。
- Excel 内校验只用于提前提示，服务端必须重新执行完整校验及数据库约束，不得信任客户端文件。
- `_预期错误` 仅存在于错误样例工作表，不属于导入字段。

## 13. 验证过程与结果

生成命令：

```bash
python3 deliverables/pm/scripts/generate_excel_templates.py
```

自动检查包括：

1. 六个 `.xlsx` 文件均可被 `openpyxl` 重新打开；
2. 每个文件包含规定工作表且工作表非空；
3. 模板列、正确样例、错误样例与字段字典一致；
4. 无真实凭据、Seller ID、客户/供应商隐私数据。
5. 每个工作簿含 veryHidden `_meta`，其 `importType/templateVersion/maxDataRows` 与 v1.1 契约一致。

S0-CONTRACT-01/02 静态自检还包括：六个 `importType`、七个批次状态、四级 issue、preview 计数等式、整批事务/期初特例、DAG 和六个 import capability 均已定义。

## 14. 已知限制与风险

1. 真实 5 店 `store_code`、固定 `warehouse_code` 未提供，测试占位值不得导入业务环境。
2. Excel 不能替代服务端引用校验、跨行组合唯一和事务/幂等校验。
3. 未使用真实脱敏样本执行端到端导入，Sprint 0 模板验收仍受 DATA-001～004 阻塞。
4. 本文冻结产品/业务与前后端联调语义，不代表真实 IAM、异步 Worker、对象存储、数据库事务或 API 已实现。

## 15. 评审记录

| 评审人 | 结论 | 日期 | 意见 |
| --- | --- | --- | --- |
| PM 自检 | 通过 | 2026-09-12 | 六类模板、正确/错误样例和生成脚本完整。 |
| PJM 初检 | 通过 | 2026-09-12 | DEL-013/015/017 齐全；6/6 xlsx、16 组数据校验、生成脚本与安全扫描通过。 |
| Frontend DEL-023 | 有条件通过 | 2026-09-12 | 模板结构通过；FE-CONTRACT-01～07 已由 v1.1 产品契约收口，待复核。 |
| Backend/Frontend/QA | 待复核 | - | 待确认异步状态、事务、capability 和测试覆盖。 |

## 16. 变更记录

| 版本 | 日期 | 变更 | 任务 |
| --- | --- | --- | --- |
| v1.0 | 2026-09-12 | 六类 Excel 模板、正确/错误样例与生成验证。 | S0-PM-02 |
| v1.1 | 2026-09-12 | 冻结异步导入类型/版本/限制、状态与取消、issue、preview、整批事务/幂等、依赖 DAG、capability；确认 StoreAccount 系统预置且无 `/system/stores`。 | S0-CONTRACT-01/02 |
