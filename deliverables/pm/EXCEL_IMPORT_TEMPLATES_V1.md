# 六类 Excel 导入模板与校验样例

| 字段 | 内容 |
| --- | --- |
| 项目 | 九更亚马逊 ERP |
| 交付物 ID | DEL-017 |
| Owner | PM（模板口径）/ Backend（导入契约）/ QA（错误覆盖） |
| 关联 Sprint/任务 | Sprint 0 / S0-PM-02 |
| 版本 | v1.0 |
| 状态 | In Review（模板已生成，待真实脱敏样本与 Backend/Frontend/QA 复核） |
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

## 2. 导入顺序

```text
01 产品 ─→ 02 SKU ─┬→ 03 五店 Listing
                   └→ 05 SKU—辅料关系
04 辅料 ───────────┬→ 05 SKU—辅料关系
                   └→ 06 期初库存
```

同一个文件/批次内，Listing 父体行必须先于子体行；SKU 文件内先处理 `variants`，再处理 `variant_attributes`。

## 3. 样例数据声明

- `TEST-*`、`example.invalid` 等均为人工构造的**合成校验夹具**，用于说明通过/失败规则。
- 合成样例不是公司真实产品、店铺、库存或用户数据，不得作为业务验收证据或容量结论。
- 真实验收仍依赖 20～50 条业务提供的脱敏样本，当前状态见 `BUSINESS_INPUT_CHECKLIST_V1.md`。

## 4. 已覆盖的错误类型

| 模板 | 必填 | 唯一 | 枚举/格式 | 引用/条件 |
| --- | --- | --- | --- | --- |
| 产品 | 缺产品编码 | 大小写不敏感重复编码 | 非法编码、非法状态 | - |
| SKU | 缺条件字段 | 重复 SKU/属性编码/顺序 | 条码、重量、单位、状态 | 产品/SKU 不存在；重量与单位联动 |
| Listing | 子体缺字段 | 同店 Seller SKU 规则 | ASIN、角色、履约、状态 | 店铺/Product/SKU/父体引用；父子/履约/FNSKU 条件 |
| 辅料 | 缺辅料编码 | 大小写不敏感重复编码 | 类型、单位、状态、图片 URL | - |
| SKU—辅料 | - | 重复关系 | 用量、单位、损耗率、布尔值 | SKU/辅料引用；单位一致 |
| 期初库存 | 缺日期/依据号 | 重复单仓辅料/依据号 | 非负数量、未来日期 | 固定仓库与辅料引用 |

## 5. Excel 交互约束

- 所有导入表冻结首行并启用筛选；表头带字段注释（关键字段）。
- 枚举列配置下拉校验；数值列配置基本范围校验。
- Excel 内校验只用于提前提示，服务端必须重新执行完整校验及数据库约束，不得信任客户端文件。
- `_预期错误` 仅存在于错误样例工作表，不属于导入字段。

## 6. 验证过程与结果

生成命令：

```bash
python3 deliverables/pm/scripts/generate_excel_templates.py
```

自动检查包括：

1. 六个 `.xlsx` 文件均可被 `openpyxl` 重新打开；
2. 每个文件包含规定工作表且工作表非空；
3. 模板列、正确样例、错误样例与字段字典一致；
4. 无真实凭据、Seller ID、客户/供应商隐私数据。

## 7. 已知限制与风险

1. 真实 5 店 `store_code`、固定 `warehouse_code` 未提供，测试占位值不得导入业务环境。
2. Excel 不能替代服务端引用校验、跨行组合唯一和事务/幂等校验。
3. 未使用真实脱敏样本执行端到端导入，Sprint 0 模板验收仍受 DATA-001～004 阻塞。

## 8. 评审记录

| 评审人 | 结论 | 日期 | 意见 |
| --- | --- | --- | --- |
| PM 自检 | 通过 | 2026-09-12 | 六类模板、正确/错误样例和生成脚本完整。 |
| Backend/Frontend/QA/PJM | 待评审 | - | 待确认导入契约、页面提示和测试覆盖。 |
