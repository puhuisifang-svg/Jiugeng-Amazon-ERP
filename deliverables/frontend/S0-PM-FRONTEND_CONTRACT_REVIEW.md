# Sprint 0 PM 字段与六类导入模板前端契约复核

| 字段 | 内容 |
| --- | --- |
| 交付物 ID | DEL-023 |
| 复核人 | Frontend |
| 输入 | DEL-013 v1.0、DEL-017 v1.0、DEL-016 v0.1、权限矩阵 v1.0、现有前端壳 |
| 日期 | 2026-09-12 |
| 状态 | Completed（PJM 核验通过） |
| 总结论 | **FAIL（0 个 P0；7 个 P1 契约差异）** |

## 1. 结论摘要

| 复核项 | 结论 | 说明 |
| --- | --- | --- |
| 字段名、类型、必填、枚举、条件必填 | PASS | DEL-013 七域表述完整；六类导入字段均可映射为前端 DTO/表单规则。 |
| 六个工作簿结构及表头 | PASS | 7 个实际导入工作表（SKU 含 variants/attributes 两表）与字段字典逐项一致。 |
| 上传 → 校验 → 预览 → 提交/结果 | FAIL | 产品/UI 流程完整，但缺少可供前后端联调的机器状态、请求/响应与状态迁移契约。 |
| 错误四要素 | PASS | `工作表 + 行号 + 字段 + 原因` 已明确，且 UI 增加原值/修复建议；API schema 尚需结构化。 |
| 三角色路由可见性 | PASS | `/imports` 仅 ADMIN、PRODUCT_MATERIAL；SELECTION 无入口；库存/用户/审计路由与矩阵一致。 |
| 动作级权限 | FAIL | 当前只有路由角色集合，不能表达同页“管理/操作/查看”；属已知后续 IAM 联调边界。 |
| 与现有前端模型/路由差异 | PASS（有待办） | 路由已覆盖；Sprint 0 尚无业务 DTO，待契约冻结后新增，当前不应提前猜测 API。 |

总体判定为 FAIL 的原因不是字段或 Excel 模板不可实现，而是**异步导入联调契约尚不足以让前端无歧义实现四步导入器**。所有问题均为 P1，未发现需立即单报的 P0。

## 2. 字段与类型可实现性

### 2.1 推荐 TypeScript 映射

| 字典类型 | 前端表示 | 注意事项 |
| --- | --- | --- |
| `string(n)` / `enum` | `string` / 字符串字面量 union | 枚举保存机器值，展示文案单独映射。 |
| `decimal(p,s)` | **十进制字符串** | 禁止直接用 JS `number` 承载库存/用量等定点值，避免精度和格式损失。 |
| `integer` | `number` | 仍需执行非负/范围/整数校验。 |
| `boolean` | JSON `boolean` | Excel 的 `TRUE/FALSE` 仅是交换值，不应进入业务 DTO。 |
| `date` / `datetime` | ISO 8601 字符串 | `date` 保持 `YYYY-MM-DD`；`datetime` 需 API 明确时区/offset。 |
| `id` | 不透明字符串 | 禁止前端假设数值、自增或可排序。 |
| `SecretRef` | 不进入普通前端业务模型 | UI/API 均不得返回密钥值。 |

### 2.2 条件必填可落地性

以下组合规则均已明确，可直接转成表单 schema 与跨字段校验：

- SKU：`package_weight` 与 `package_weight_unit` 联动；动态属性三字段成组。
- Listing：ASIN 与状态、角色与 SKU/父体/主题、FBA/FBM 与 FNSKU 联动。
- 候选：ASIN/标准化 URL 至少一个；价格/币种联动；WATCH_EVENT 来源引用联动；淘汰原因联动。
- 库存流水：调整/冲销原因必填。

### 2.3 六类模板表头核验

| 文件 | 导入工作表 | 字段数 | 结论 |
| --- | --- | ---: | --- |
| `01_products_v1.0.xlsx` | 导入模板 | 8 | PASS |
| `02_variants_v1.0.xlsx` | variants_导入模板 | 11 | PASS |
| `02_variants_v1.0.xlsx` | attributes_导入模板 | 4 | PASS |
| `03_store_listings_v1.0.xlsx` | 导入模板 | 12 | PASS |
| `04_auxiliary_materials_v1.0.xlsx` | 导入模板 | 8 | PASS |
| `05_sku_auxiliary_materials_v1.0.xlsx` | 导入模板 | 7 | PASS |
| `06_initial_inventory_v1.0.xlsx` | 导入模板 | 7 | PASS |

工作簿可由 `openpyxl` 打开；模板表均冻结首行、启用筛选，枚举/数值列存在相应 Excel 数据校验。Excel 校验只作为提前提示，最终仍由服务端复核，边界正确。

## 3. P1 差异清单

| ID | 差异 | 前端影响 | 建议关闭条件 |
| --- | --- | --- | --- |
| FE-CONTRACT-01 | 未冻结导入类型、模板版本、文件 MIME/扩展名、大小与行数上限的机器字段。 | 上传页无法可靠校验，也无法判断模板兼容。 | OpenAPI 定义 `importType`、`templateVersion`、允许格式、字节/行数上限及不兼容错误码。 |
| FE-CONTRACT-02 | 未冻结异步批次状态机器值、迁移、进度字段及何时可取消。 | `/imports/:batchId` 只能使用中文原型，无法实现轮询/恢复/取消。 | 定义 `QUEUED/VALIDATING/VALIDATED/COMMITTING/SUCCEEDED/PARTIALLY_SUCCEEDED/FAILED/CANCELLED` 或批准等价状态机。 |
| FE-CONTRACT-03 | 错误四要素仅为文档句子，未形成 API schema；UI 的 warning、blocking、原值、修复建议无机器定义。 | 无法稳定做筛选、定位控件、阻断预览或兼容错误码。 | 定义 issue DTO，至少包含 sheet、row、field、reason，并补 code、severity、blocking、rawValue、suggestion。 |
| FE-CONTRACT-04 | 预览的新增/更新/不变、前后值、影响记录和分页结构未定义。 | 无法实现 diff 表、Listing 五店汇总或关系展开。 | 定义 preview summary、row identity、action、before/after、impact、分页与排序稳定性。 |
| FE-CONTRACT-05 | `部分失败` 的提交事务粒度未决；DEL-016 已明确列为 Backend/PM 待决。 | 前端无法准确说明已写入范围、重试语义与幂等结果。 | 决定整批原子或逐行/分块提交，并定义成功/失败/跳过计数、重试与幂等语义。 |
| FE-CONTRACT-06 | 导入依赖顺序表述不一致：DEL-017 允许 Listing 在 Product/SKU 后与辅料链并行；DEL-016 线性展示为 Product→SKU→辅料→关系→Listing。 | 导入中心可能错误阻止合法的 Listing 导入。 | 以依赖 DAG 冻结：Listing 仅依赖 Product/SKU/预置店铺；关系依赖 SKU+辅料；期初依赖辅料+单仓。 |
| FE-CONTRACT-07 | 现有 `meta.roles` 只能表达页面可进入，不能表达 ADMIN 管理、产品岗位操作/查看、选品岗位操作/查看。 | 同一路由上的新增、编辑、导入、停用、状态流转按钮无法由当前模型裁剪。 | IAM 契约提供 capability/permission codes，前端建立按钮级 `can()`；后端逐接口强制鉴权。 |

## 4. 路由与三角色复核

| 路由 | ADMIN | PRODUCT_MATERIAL | SELECTION | 结论 |
| --- | --- | --- | --- | --- |
| `/products`、`/listing-mappings`、`/materials` | 可见 | 可见 | 可见（只读待 capability） | PASS |
| `/inventory/balances`、`/inventory/ledger` | 可见 | 可见 | 不可见 | PASS |
| `/imports`、`/imports/:batchId` | 可见 | 可见 | 不可见 | PASS |
| `/selection/candidates`、`/watch/*` | 可见 | 可见（只读待 capability） | 可见 | PASS |
| `/system/users` | 可见 | 不可见 | 不可见 | PASS |
| `/system/audit` | 可见 | 可见（本人范围待 API） | 可见（本人范围待 API） | PASS |

额外差异：DEL-013 定义了 5 店 `StoreAccount` 系统配置字段，但 Completed 的 DEL-016 与当前前端路由均无 `/system/stores`。该配置可由后台初始化，不影响六类导入；若 MVP 需要管理员在 UI 维护公司店铺，必须由 PJM/PM 走受控 IA 变更后再增加路由，前端不自行扩展。

## 5. 证据路径

- 字段、类型、条件与错误四要素：[DEL-013 字段字典](../pm/FIELD_DICTIONARY_V1.md)
- 六类模板、顺序与生成验证：[DEL-017 模板说明](../pm/EXCEL_IMPORT_TEMPLATES_V1.md)
- 实际工作簿：[Excel templates](../pm/excel_templates/)
- 四步导入器、状态与部分失败待决：[DEL-016 UI IA](../ui/S0-UI-01_LOW_FIDELITY_IA_AND_FLOWS.md)
- 三角色基线：[权限矩阵](../../docs/05-security/ACCESS_MATRIX.md)
- 现有前端角色类型：[router meta](../../frontend/src/router/meta.d.ts)
- 现有前端路由：[router](../../frontend/src/router/index.ts)

## 6. 验证证据

```text
实际工作簿读取：6 files / 7 import sheets
表头逐项对照 DEL-013：7 PASS / 0 FAIL
工作簿结构：均可打开，导入表 freeze=A2，auto_filter 已启用
P0 差异：0
P1 差异：7
git diff --check：PASS
```

## 7. 复核结论

- **DEL-013 字段结构：PASS（前端可实现）。**
- **DEL-017 Excel 模板结构：PASS（前端可下载、上传和解释）。**
- **端到端异步导入联调契约：FAIL（需关闭 FE-CONTRACT-01～07 后再进入 Ready）。**
- **P0：0；无需立即单报。**

## 8. PJM 核验

- DEL-023 复核报告：**PASS / Completed**；差异均有基线证据，严重度 P1 合理。
- DEL-013：字段结构前端复核 PASS，但仍受真实脱敏样本与跨角色总体验收约束。
- DEL-017：模板结构 PASS；端到端导入契约尚未 Ready，FE-CONTRACT-01～06 必须在 Sprint 0 冻结。
- FE-CONTRACT-07：能力码名称/映射在 Sprint 0 定义；真实会话、前端 `can()`、路由守卫及后端逐接口鉴权按既定计划后移。
- StoreAccount：MVP 采用受控系统预置 5 店，不新增 `/system/stores` 页面；若未来需要 UI 管理，须走范围/IA 变更。
