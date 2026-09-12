# S0-INT-01：DEL-013/017 字段与 Excel 结构契约复核

| 字段 | 内容 |
| --- | --- |
| 项目 | 九更亚马逊 ERP |
| Reviewer | QA |
| 关联任务 | S0-CONTRACT-01 关闭门禁；S0-INT-01 后续动态验收 |
| 输入交付物 | DEL-013 v1.1、DEL-017 v1.2、DEL-023、DEL-024、Backend 机器契约 v1.0、PRODUCT_DATA_MODEL v1.1 受控澄清、ACCESS_MATRIX v1.1 |
| 状态 | Static Contract Defects Closed by PJM / XLSX & Machine Contract Pass / Dynamic Import Blocked |
| 日期 | 2026-09-12 |

## 1. 结论

- **六个 xlsx 的字段、表头、枚举、校验和样例结构：PASS。** v1.2 为 17 组 DataValidation，含零期初正例及四类 Listing 新增负例。
- **BE-CONTRACT-P0-01/02：Closed。** `106a0e2` 已将 Upsert 限定为前五类，并冻结零期初生成唯一 `quantity=0` OPENING、其他流水 `>0`；Backend/PJM/QA 回归通过后由 PJM 收口。
- **异步导入产品契约与 Backend 机器契约：PASS。** `4ebd2be + 21870a8` 已固化 OpenAPI/状态机/事务/DAG/capability，并以 Draft 2020-12 实例测试覆盖 Issue scope 与 severity/blocking。
- **真实脱敏样本动态导入：BLOCKED。** DATA-001～004、真实 5 店 `store_code` 和固定 `warehouse_code` 未到位；`TEST-*`/`example.invalid` 仅为合成校验夹具，不计业务通过。
- 静态契约缺陷已由 PJM 关闭，不代表 DEL-013/017 运行实现或真实样本整体 Ready。

## 2. QA 独立检查结果

| 检查项 | 预期 | 实际 | 结果 |
| --- | --- | --- | --- |
| xlsx 文件 | 6/6 | 6/6，文件名与 DEL-017/产品模型映射一致 | Pass |
| 工作簿/工作表 | 所有声明工作表存在且非空 | 32 个工作表；7 个导入表（SKU 含 variants/attributes 两表）全部存在且非空 | Pass |
| 导入表结构 | 冻结首行、筛选、表头一致 | 7/7 `freeze=A2` 且启用筛选；57/57 表头顺序与预期一致 | Pass |
| 正确/错误样例 | 每个逻辑导入表均有非空正误样例 | 正确样例表 7/7；错误样例表 7/7；错误表末列均为 `_预期错误` | Pass |
| Excel 校验 | 17 组 | 17/17：原 16 组加 `display_order>=1` 整数校验；作用区均非空 | Pass |
| 字段一致性 | 模板字段均回溯 DEL-013 | 57/57 表头字段均在 DEL-013 声明 | Pass |
| 枚举一致性 | Excel 与 DEL-013 机器值一致 | 11/11 枚举组逐值一致 | Pass |
| 引用/条件错误覆盖 | SKU、店铺、父子体、辅料、仓库等可定位 | 原覆盖保留；新增同店 FNSKU 重复、同店同 SKU 多 ACTIVE、跨店父体、父子主题不一致 4/4 | Pass |
| 生成脚本 | 可解析且可复现现有模板 | Python 语法通过；在临时目录生成 6 个工作簿，与仓库版本的工作表、单元格、注释、冻结/筛选及校验语义一致 | Pass |
| 合成样例声明 | 文档、脚本、工作簿均明确非真实数据 | 6/6 工作簿及 DEL-017/脚本均声明“人工构造、非真实、不得用于业务验收”；样例含 `TEST-*` | Pass |
| 敏感扫描 | 无高置信凭据/私钥 | DEL-013、DEL-017、生成脚本及 6 个 xlsx XML 均无高置信敏感模式命中 | Pass |

## 3. 静态契约缺陷关闭状态

| ID | 复核结论 | 可复核证据 | 状态 |
| --- | --- | --- | --- |
| BE-CONTRACT-P0-01 | 普通 Upsert 已限定为前五类；期初禁止覆盖式 Upsert | `FIELD_DICTIONARY_V1.md:33`；`PRODUCT_DATA_MODEL.md:211`；`EXCEL_IMPORT_TEMPLATES_V1.md:158-159` | Closed（Backend/PJM/QA） |
| BE-CONTRACT-P0-02 | `OPENING.quantity>=0`，其他流水 `>0`；零期初仍生成唯一不可编辑 OPENING | `FIELD_DICTIONARY_V1.md:152,165,170`；`PRODUCT_DATA_MODEL.md:211,221` | Closed（Backend/PJM/QA） |
| QA-CONTRACT-P1-01 | `ImportIssue` 的 BATCH/SHEET/ROW/FIELD 定位与 severity/blocking 已由 Draft 2020-12 合法/非法实例测试覆盖 | `21870a8`；`tests/validate_issue_instances.mjs`；fixtures 正例 4/4、负例 6/6 | Closed（Backend/PJM/QA） |

P0 治理关闭只代表静态契约一致，不等于动态实现通过；期初幂等、零值流水、原子回滚仍须在真实数据库与 API 实现中执行。

## 4. 17 组 Excel 校验明细

| 文件 | 校验组 |
| --- | --- |
| `01_products_v1.0.xlsx` | `status` 枚举（1） |
| `02_variants_v1.0.xlsx` | `package_weight_unit`、`status` 枚举；`package_weight > 0`；`display_order>=1` 整数（4） |
| `03_store_listings_v1.0.xlsx` | `listing_role`、`fulfillment_channel`、`listing_status` 枚举（3） |
| `04_auxiliary_materials_v1.0.xlsx` | `material_type`、`base_unit`、`status` 枚举（3） |
| `05_sku_auxiliary_materials_v1.0.xlsx` | `unit`、`enabled` 枚举；`standard_quantity > 0`；`loss_rate 0..1`（4） |
| `06_initial_inventory_v1.0.xlsx` | `opening_quantity >= 0`；`safety_stock >= 0`（2） |

合计：**17/17**。Excel 校验只用于录入前提示；必填、引用、跨行唯一、条件必填、事务和幂等仍须由服务端重新校验。

## 5. Backend / Frontend 反馈汇总

### Backend

- 证据：`deliverables/backend/sprint-0/import-contract/`、`S0_CONTRACT_01_02_TEST_REPORT.md`、`docs/03-architecture/PRODUCT_DATA_MODEL.md` v1.1 受控澄清。
- 结论：**PASS。** QA 独立执行 5/5 JSON、Redocly 0/0、主契约测试、四个负向控制及 Draft 2020-12 实例正反例均通过。
- 实现边界：本轮是机器可读静态契约，不代表 API、Worker、数据库事务或 IAM 已实现。

### Frontend

- 证据：`deliverables/frontend/S0-CONTRACT-01-02_FRONTEND_CLOSURE_REVIEW.md`（DEL-024，复核提交 `c43347a`）。
- 结论：**PASS。** DEL-017 v1.1 与权限矩阵 v1.1 已在产品契约层关闭 FE-CONTRACT-01～07；6/6 `_meta`、导入类型、模板版本、状态机、issue、preview、事务、DAG 和 capability 均通过。
- 联调入口：Backend OpenAPI 静态契约已冻结；API、Worker、数据库事务与 IAM 尚未实现，当前不得把实现层导入器标记为 Ready。

## 6. 证据与复核方式

- 字段字典：`deliverables/pm/FIELD_DICTIONARY_V1.md`
- 模板规范：`deliverables/pm/EXCEL_IMPORT_TEMPLATES_V1.md`
- 工作簿：`deliverables/pm/excel_templates/`
- 生成脚本：`deliverables/pm/scripts/generate_excel_templates.py`
- Backend 语义：`docs/03-architecture/PRODUCT_DATA_MODEL.md` v1.1
- Frontend 反馈：`deliverables/frontend/S0-PM-FRONTEND_CONTRACT_REVIEW.md`
- Frontend 关闭复核：`deliverables/frontend/S0-CONTRACT-01-02_FRONTEND_CLOSURE_REVIEW.md`
- Backend 契约复核：`deliverables/backend/sprint-0/S0_CONTRACT_REVIEW_DEL013_DEL017.md`
- Backend 机器契约：`deliverables/backend/sprint-0/import-contract/`
- QA 机器契约复核：`deliverables/qa/S0_INT_01_MACHINE_CONTRACT_REVIEW.md`
- QA 执行：openpyxl 只读结构/表头/样例/校验检查；临时目录生成语义对比；高置信敏感模式扫描；`git diff --check`。

## 7. 下一步与门禁

1. 静态机器契约已由 `4ebd2be + 21870a8` 固化并完成 PJM/QA 收口；后续不再回退已关闭缺陷。
2. 运行实现到位后执行 IMP-010/014～018，以及 DTO、分页、错误、并发、事务、幂等和三角色越权用例。
3. DATA-001～004、真实 5 店 `store_code` 和固定 `warehouse_code` 到位后，执行真实脱敏样本动态导入、行数对账、错误文件和幂等/期初流水验证。
4. 动态证据完成前，只可声明“PM 业务契约、xlsx 结构与机器契约 Pass”；不得声明运行实现、真实样本或 DEL-013/017 整体 Ready。
