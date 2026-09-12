# S0-INT-01：DEL-013/017 字段与 Excel 结构契约复核

| 字段 | 内容 |
| --- | --- |
| 项目 | 九更亚马逊 ERP |
| Reviewer | QA |
| 关联任务 | S0-INT-01 |
| 输入交付物 | DEL-013 v1.0、DEL-017 v1.0、DEL-023 v0.1、PRODUCT_DATA_MODEL v1.1 |
| 状态 | Static Pass / Dynamic Import Blocked |
| 日期 | 2026-09-12 |

## 1. 结论

- **DEL-013/017 字段与 Excel 结构契约：PASS。**
- **异步导入端到端联调契约：FAIL / P1 待收口。** Frontend 已登记 FE-CONTRACT-01～07；不推翻字段、模板和 xlsx 结构 Pass，但阻塞四步导入器进入实现 Ready。
- **真实脱敏样本动态导入：BLOCKED。** DATA-001～004、真实 5 店 `store_code` 和固定 `warehouse_code` 未到位；`TEST-*`/`example.invalid` 仅为合成校验夹具，不计业务通过。
- P0：0；本轮未新增结构缺陷。

## 2. QA 独立检查结果

| 检查项 | 预期 | 实际 | 结果 |
| --- | --- | --- | --- |
| xlsx 文件 | 6/6 | 6/6，文件名与 DEL-017/产品模型映射一致 | Pass |
| 工作簿/工作表 | 所有声明工作表存在且非空 | 32 个工作表；7 个导入表（SKU 含 variants/attributes 两表）全部存在且非空 | Pass |
| 导入表结构 | 冻结首行、筛选、表头一致 | 7/7 `freeze=A2` 且启用筛选；57/57 表头顺序与预期一致 | Pass |
| 正确/错误样例 | 每个逻辑导入表均有非空正误样例 | 正确样例表 7/7；错误样例表 7/7；错误表末列均为 `_预期错误` | Pass |
| Excel 校验 | 16 组 | 16/16：枚举下拉 11 组、十进制范围 5 组；作用区均非空 | Pass |
| 字段一致性 | 模板字段均回溯 DEL-013 | 57/57 表头字段均在 DEL-013 声明 | Pass |
| 枚举一致性 | Excel 与 DEL-013 机器值一致 | 11/11 枚举组逐值一致 | Pass |
| 引用/条件错误覆盖 | SKU、店铺、父子体、辅料、仓库等可定位 | 13/13 关键引用/条件断言命中错误样例 | Pass |
| 生成脚本 | 可解析且可复现现有模板 | Python 语法通过；在临时目录生成 6 个工作簿，与仓库版本的工作表、单元格、注释、冻结/筛选及校验语义一致 | Pass |
| 合成样例声明 | 文档、脚本、工作簿均明确非真实数据 | 6/6 工作簿及 DEL-017/脚本均声明“人工构造、非真实、不得用于业务验收”；样例含 `TEST-*` | Pass |
| 敏感扫描 | 无高置信凭据/私钥 | DEL-013、DEL-017、生成脚本及 6 个 xlsx XML 均无高置信敏感模式命中 | Pass |

## 3. 16 组 Excel 校验明细

| 文件 | 校验组 |
| --- | --- |
| `01_products_v1.0.xlsx` | `status` 枚举（1） |
| `02_variants_v1.0.xlsx` | `package_weight_unit`、`status` 枚举；`package_weight > 0`（3） |
| `03_store_listings_v1.0.xlsx` | `listing_role`、`fulfillment_channel`、`listing_status` 枚举（3） |
| `04_auxiliary_materials_v1.0.xlsx` | `material_type`、`base_unit`、`status` 枚举（3） |
| `05_sku_auxiliary_materials_v1.0.xlsx` | `unit`、`enabled` 枚举；`standard_quantity > 0`；`loss_rate 0..1`（4） |
| `06_initial_inventory_v1.0.xlsx` | `opening_quantity >= 0`；`safety_stock >= 0`（2） |

合计：**16/16**。Excel 校验只用于录入前提示；必填、引用、跨行唯一、条件必填、事务和幂等仍须由服务端重新校验。

## 4. Backend / Frontend 反馈汇总

### Backend

- 证据：`docs/03-architecture/PRODUCT_DATA_MODEL.md` v1.1 §6、§10；提交 `d0ba6b8`、`fb2fdce`、`92f9337`。
- 结论：**有条件通过。** 六类资源、实体映射、依赖 DAG、业务键和命名已冻结。
- 关键约束：前五类为幂等 Upsert；期初库存为不可覆盖的一次性幂等命令，首次产生 `OPENING`，同键/依据/载荷重试复用既有结果，同键不同载荷拒绝。
- 阻塞：真实样本、固定仓库编码和 5 店编码未到前，不得标记业务样本验收完成。

### Frontend

- 证据：`deliverables/frontend/S0-PM-FRONTEND_CONTRACT_REVIEW.md`（DEL-023 v0.1）。
- 字段结构：**PASS**；Excel 模板结构：**PASS**；三角色路由可见性：**PASS**。
- 异步导入联调契约：**FAIL，7 个 P1**：
  1. FE-CONTRACT-01：导入类型/模板版本/MIME/大小/行数机器字段未冻结；
  2. FE-CONTRACT-02：异步批次状态机、进度和取消条件未冻结；
  3. FE-CONTRACT-03：逐行错误尚缺正式 API DTO/code/severity/blocking 等机器字段；
  4. FE-CONTRACT-04：预览新增/更新/不变、before/after、影响和分页未冻结；
  5. FE-CONTRACT-05：部分失败的提交事务粒度与计数/重试语义未决；
  6. FE-CONTRACT-06：DEL-017 依赖 DAG 与 UI 线性展示需统一为 DAG；
  7. FE-CONTRACT-07：按钮级 capability/permission codes 未冻结。

上述 7 项属于 S0-INT-01 后续 API/IAM 联调契约，不是 xlsx 结构错误；未关闭前不得把异步导入器标记为 Ready。

## 5. 证据与复核方式

- 字段字典：`deliverables/pm/FIELD_DICTIONARY_V1.md`
- 模板规范：`deliverables/pm/EXCEL_IMPORT_TEMPLATES_V1.md`
- 工作簿：`deliverables/pm/excel_templates/`
- 生成脚本：`deliverables/pm/scripts/generate_excel_templates.py`
- Backend 语义：`docs/03-architecture/PRODUCT_DATA_MODEL.md` v1.1
- Frontend 反馈：`deliverables/frontend/S0-PM-FRONTEND_CONTRACT_REVIEW.md`
- QA 执行：openpyxl 只读结构/表头/样例/校验检查；临时目录生成语义对比；高置信敏感模式扫描；`git diff --check`。

## 6. 下一步与门禁

1. PJM/PM/Backend/Frontend 关闭 FE-CONTRACT-01～07 后，QA 复核异步导入机器契约。
2. DATA-001～004、真实 5 店 `store_code` 和固定 `warehouse_code` 到位后，执行真实脱敏样本动态导入、行数对账、错误文件和幂等/期初流水验证。
3. 在动态证据完成前，DEL-013/017 只可声明“结构契约 Pass”，不得声明业务样本导入 Pass。
