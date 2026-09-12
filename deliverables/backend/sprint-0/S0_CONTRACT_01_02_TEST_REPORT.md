# S0-CONTRACT-01/02 机器契约测试报告

| 字段 | 内容 |
| --- | --- |
| 日期 | 2026-09-12 |
| Owner | Backend |
| 版本 | v1.1.2 |
| 结论 | PASS（待 Frontend/QA 独立复核） |

## 覆盖结果

| 检查 | 结果 |
| --- | --- |
| OpenAPI 3.1 语义与规则校验 | PASS，Redocly CLI 1.34.5，0 error / 0 warning |
| JSON 语法 | PASS，5/5 文件可由 Python 标准库解析 |
| OpenAPI 本地 `$ref` | PASS，全部可解析且无外部引用 |
| 六类 `importType` 与 DAG | PASS，6/6 且无环 |
| 批次状态/阶段 | PASS，7 状态、8 阶段，终态无出边，无 `PARTIALLY_SUCCEEDED` |
| ImportIssue DTO | PASS，10 个字段、4 级 severity、blocking 条件已冻结 |
| API 面 | PASS，11 个 operation 覆盖模板、上传校验、查询、issues、preview、过期重验、提交、取消、结果 |
| Preview 过期重验 | PASS，固定 `POST .../{batchId}/revalidate`；旧批次保持 `VALIDATED`，新批次使用新 ID 从 `QUEUED` 开始，双向关联且每个源批次最多一个直接后继 |
| 分页/响应 envelope | PASS，列表、issues、preview 均为 `items + page(limit/nextCursor/hasMore)`；JSON 成功为 direct schema、错误为 `application/problem+json` / `ApiError` |
| Header | PASS，上传、重验、确认、取消均要求 `Idempotency-Key`；11/11 operation 的全部响应声明 `X-Request-Id` |
| 上传响应 | PASS，202 专用 schema 固定 `status=QUEUED`、`phase=WAITING` |
| 错误映射 | PASS，11/11 operation 声明 `x-error-codes`，code/status/response 双向可达并全部覆盖 500 |
| 幂等/事务/重试码 | PASS，写操作均要求 `Idempotency-Key`；冲突码与 retryable 标记完整 |
| P0 条件语义 | PASS，普通 Upsert 仅前五类；零期初生成唯一 `quantity=0` OPENING，其他流水 `>0` |
| 库存流水条件 Schema | PASS，`InventoryTransactionQuantityRule` 以 `if/then/else` 约束 OPENING 非负、其他 5 类严格为正；`PreviewImpact` 在 `inventory_transactions` 资源下强制复用该结构；AJV 接受 8 个正例、拒绝 6 个负例 |
| 期初余额/流水不变量 | PASS，同事务创建余额与唯一 OPENING；OPENING 不可编辑；同载荷重放保持单流水；异载荷 409；余额等于流水重算；余额/流水/审计纳入整批回滚范围 |
| Issue 定位 | PASS，BATCH/SHEET/ROW/FIELD 四种 scope 的 sheet/row/field nullability 与必填条件机器化 |
| Issue Draft 2020-12 实例 | PASS，AJV 8.17.1：有效 scope 4/4 接受，非法定位与 blocking 组合 12/12 拒绝 |
| Preview 标量/业务键 | PASS，decimal 使用带 `DECIMAL` 标签的非科学计数法 string；7 种实际业务键；impact resource 复用枚举 |
| DEL-017 v1.2 工作簿回归 | PASS，6/6、17 个 DataValidation、零期初正例、Listing 新增负例 4/4 |
| Capability | PASS，21 个全域机器码；6 个 import capability 的三角色映射一致 |
| StoreAccount 边界 | PASS，固定 5 店；无 `stores.write` 与店铺管理 API |

## 可复现命令

```bash
python3 deliverables/backend/sprint-0/import-contract/tests/validate_contract.py
npm --prefix deliverables/backend/sprint-0/import-contract/tests ci --ignore-scripts
npm --prefix deliverables/backend/sprint-0/import-contract/tests test
npx --yes @redocly/cli@1.34.5 lint \
  deliverables/backend/sprint-0/import-contract/openapi.json \
  --format=stylish
```

工作簿回归使用 `openpyxl` 只读核对 6 个 `_meta`、17 个 DataValidation、零期初正例及 4 类 Listing 负例；输出：

```text
PASS: workbooks=6 validations=17 zero-opening=present listing-negative-cases=4/4
```

关键输出：

```text
PASS: S0-CONTRACT-01/02 machine-readable contracts are consistent
CHECKS: importTypes=6 statuses=7 phases=8 operations=11 capabilities=21 errors=25
PASS: ImportIssue Draft 2020-12 instances valid=4 invalid-rejected=12
PASS: InventoryTransaction quantity condition valid=8 invalid-rejected=6
Woohoo! Your API description is valid.
```

## Frontend/QA 差异处理

| 差异 | 处理 |
| --- | --- |
| FE P1-01 上传 202 未固定 QUEUED | 新增 `QueuedImportBatch`，测试解析 202 `$ref` 并断言 `const=QUEUED` |
| FE P1-02 错误码未绑定 operation | 10/10 operation 添加 `x-error-codes`；补模板 404、游标/请求 400 与统一 500；测试双向校验可达性 |
| FE P1-03 decimal 双编码、业务键过宽 | 新增 `TransportFieldValue` tagged union；decimal 仅 string；业务键按实际七种行形状收窄；impact resource 统一枚举 |
| QA P1-01 ROW/SHEET 定位条件缺失 | 四种 scope 均增加 if/then 条件并加入静态负向约束断言 |
| QA 后续门禁：缺 Draft 2020-12 实例测试 | AJV 实例级夹具：4 个合法对象必须通过，12 个非法对象必须拒绝；逐项覆盖 SHEET 缺失/空值、ROW 缺失/空 sheet 与 null/越界行号、FIELD 缺失/null、BATCH 非空定位字段，以及 severity/blocking 冲突 |
| PJM OpenAPI 收口 4 项 | 新增过期重验 endpoint 与新旧批次关联；对既有分页和 diff JSON schema 增加强断言；冻结 direct/page/problem envelope 与全响应 `X-Request-Id` |
| PM P0 条件 Schema | 新增 `InventoryTransactionQuantityRule`、非负/正 decimal wire schema 与 AJV 正负实例，直接证明零 OPENING 合法且非 OPENING 零值非法 |
| PJM P0 完整关闭口径 | 提交语义新增不可编辑、唯一流水、余额重算和 rollback scope 机器字段与 validator 强断言；差异报告移除 P0-02 二选一旧建议并改为最终条件规则 |

## 非实现声明

本报告只证明 Sprint 0 静态机器契约自洽；不证明接口已部署、IAM 已实现或逐接口鉴权已生效。运行时与越权测试进入后续 Sprint。
