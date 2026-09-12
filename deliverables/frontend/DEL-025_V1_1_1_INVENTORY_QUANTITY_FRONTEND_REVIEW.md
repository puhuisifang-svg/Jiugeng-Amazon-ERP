# DEL-025 v1.1.1 库存流水数量契约前端增量复核

| 字段 | 内容 |
| --- | --- |
| 交付物 ID | DEL-029 |
| 复核人 | Frontend |
| 冻结输入 | Backend commit `c364a3727c9b587a7de72d5245cb0df0301b747e` |
| 基线 | DEL-028 / DEL-025 v1.1 |
| 日期 | 2026-09-12 |
| 状态 | Completed（PJM 核验通过） |
| 总结论 | **PASS（P0=0、P1=0）** |

## 1. 复核边界

本报告只审查从精确提交 `c364a37` 隔离导出的 DEL-025 v1.1.1。范围为 `InventoryTransactionQuantityRule`、`PreviewImpact.inventoryTransaction` 条件分支，以及 `49bef4a` revalidate/wire 基线回归；不以共享可变工作树为证据，不验证真实 API、数据库约束或 IAM。

## 2. 增量结论

| 检查项 | 结论 | 机器契约与实例证据 |
| --- | --- | --- |
| Transaction type | **PASS** | 精确枚举 `OPENING/INBOUND/OUTBOUND/ADJUST_IN/ADJUST_OUT/REVERSAL`，对象要求 `transactionType` 与 `quantity`，禁止额外属性。 |
| OPENING quantity | **PASS** | `if transactionType=OPENING` 后引用 `NonNegativeDecimalString`；接受 `0`、`0.0000`、正数，拒绝负数、指数记法及 JSON number。 |
| 其他五类 quantity | **PASS** | `else` 引用 `PositiveDecimalString`；五类均只接受严格大于 0 的 canonical decimal string，拒绝 `0`、`0.0000`、负数、指数记法及 JSON number。 |
| PreviewImpact inventory 分支 | **PASS** | `inventoryTransaction` 为必填；当 `resource=inventory_transactions` 时必须为 `InventoryTransactionQuantityRule` 对象且不能为 null。 |
| PreviewImpact 非 inventory 分支 | **PASS** | 当 resource 为其余 8 类时，`inventoryTransaction` 必须为 null，不能携带交易数量对象；字段仍保持必填，前端无需区分 missing 与 null。 |
| Domain invariant 引用 | **PASS** | `x-domain-invariants.openingInventory.transactionQuantitySchema` 直接引用新增条件 schema；整批事务语义继续说明零 OPENING 创建不可变流水。 |
| v1.1 回归 | **PASS** | `paths`、revalidate 事务语义、wire conventions、状态机与 capability 文件相对 `49bef4a` 未变；仍为 11 operations、7 statuses、21 capabilities、25 errors。 |

## 3. 前端可判别数据形状

```ts
type PreviewImpact =
  | {
      resource: 'inventory_transactions'
      inventoryTransaction: InventoryTransactionQuantityRule
    }
  | {
      resource: Exclude<ImpactResource, 'inventory_transactions'>
      inventoryTransaction: null
    }
```

`transactionType` 再判别数量约束：`OPENING` 使用非负 decimal string，其余五类使用正 decimal string。TypeScript 的 string 类型本身不能表达正则，表单/运行时解析仍须按 OpenAPI pattern 校验。

## 4. 独立验证证据

使用 `git archive c364a37` 导出至临时目录，并从导出根目录执行冻结文档中的命令：

```text
$ python3 .../tests/validate_contract.py
PASS: S0-CONTRACT-01/02 machine-readable contracts are consistent
CHECKS: importTypes=6 statuses=7 phases=8 operations=11 capabilities=21 errors=25

$ npm --prefix .../tests ci --ignore-scripts
added 5 packages

$ npm --prefix .../tests test
PASS: ImportIssue Draft 2020-12 instances valid=4 invalid-rejected=6
PASS: InventoryTransaction quantity condition valid=8 invalid-rejected=6

$ npx --yes @redocly/cli@1.34.5 lint .../openapi.json --format=stylish
Woohoo! Your API description is valid.
0 error / 0 warning

Frontend independent AJV assertions:
PASS: quantity independent valid=8 invalid-rejected=14
PASS: PreviewImpact branch valid=4 invalid-rejected=4
```

前端负例额外覆盖五种非 OPENING transactionType 的 `0/0.0000`、指数记法、JSON number、未知 transactionType、inventory resource 的 null、非 inventory resource 的对象以及字段缺失。

## 5. 残留与实现边界

- **无 P0、无 P1。** Schema 已足够生成可判别 DTO；decimal 正则仍需由后续表单层/运行时 validator 保留，不能仅依赖 TypeScript `string`。
- 数量对库存余额的真实写入、唯一 OPENING 审计流水和数据库原子性仍须在 Backend 运行时测试中证明，不影响本次静态契约 PASS。
- `49bef4a` 的 revalidate/wire 结论不扩展到任何未提交工作树变化；真实接口/IAM 不提前编码。

## 6. 证据路径

- [OpenAPI](../backend/sprint-0/import-contract/openapi.json)
- [Backend validator](../backend/sprint-0/import-contract/tests/validate_contract.py)
- [库存数量 AJV 测试](../backend/sprint-0/import-contract/tests/validate_inventory_transaction_instances.mjs)
- [Backend 测试报告](../backend/sprint-0/S0_CONTRACT_01_02_TEST_REPORT.md)
- [DEL-028 v1.1 增量复核](DEL-025_V1_1_INCREMENTAL_FRONTEND_REVIEW.md)

## 7. 最终结论

**PASS，P0=0、P1=0。** 精确提交 `c364a3727c9b587a7de72d5245cb0df0301b747e` 正确区分零值合法的 OPENING 与必须严格为正的其余五类库存流水，并将该规则互斥绑定到 `PreviewImpact` 的 inventory/non-inventory 分支；DEL-025 v1.1 的 revalidate/wire 基线保持不变。
