# DEL-025 v1.1.2 期初库存语义前端增量复核

| 字段 | 内容 |
| --- | --- |
| 交付物 ID | DEL-030 |
| 复核人 | Frontend |
| 冻结输入 | Backend commit `6a0989ab224c3a56c4527fc4f2cac080fd4f0818` |
| 基线 | DEL-029 / DEL-025 v1.1.1 |
| 日期 | 2026-09-12 |
| 状态 | Completed（PJM 核验通过） |
| 总结论 | **PASS（P0=0、P1=0）** |

## 1. 复核边界

本报告只审查从精确提交 `6a0989a` 隔离导出的 DEL-025 v1.1.2，并与 `c364a37` 做结构比较。范围仅为 OPENING 期初库存语义机器字段、相应 validator 与既有契约回归；不使用共享可变工作树，不验证真实数据库、API 或 IAM。

## 2. 期初语义结论

| 检查项 | 结论 | 机器字段证据 |
| --- | --- | --- |
| OPENING immutable | **PASS** | `openingTransactionImmutable=true`；修正模式固定为 `APPEND_ADJUSTMENT_OR_REVERSAL`，不修改既有 OPENING。 |
| 余额与流水同事务 | **PASS** | 每输入行原子写集合精确为 `AuxiliaryMaterialInventory` 与 `InventoryTransaction(OPENING)`；`sameTransaction=true`，OpenAPI domain invariant 同步为 `balanceAndOpeningSameTransaction=true`。 |
| 唯一 OPENING | **PASS** | 每业务键 `openingTransactionsPerBusinessKey=1`；业务键继续为 `warehouse_code + material_code`。 |
| 幂等重放 | **PASS** | 相同证据与载荷重放固定为 `RETURN_EXISTING_RESULT_WITH_ONE_OPENING`，不新增第二条 OPENING；不同载荷仍映射 `OPENING_INVENTORY_CONFLICT`。 |
| 余额来源与重算 | **PASS** | `balanceDeltaSource=opening_quantity`；提交后不变量固定为 `BALANCE_EQUALS_LEDGER_RECOMPUTATION`。 |
| Rollback scope | **PASS** | 整批失败回滚集合精确包含 `AuxiliaryMaterialInventory`、`InventoryTransaction(OPENING)` 与 `audit`。 |
| OpenAPI 投影 | **PASS** | OpenAPI 版本为 1.1.2；`x-domain-invariants.openingInventory` 同步声明 immutable、同事务与每业务键唯一 OPENING，数量条件 schema 继续引用 `InventoryTransactionQuantityRule`。 |

## 3. v1.1.1 基线回归

与 `c364a37` 结构化比较：

- OpenAPI `paths`：相同；11 operations 不变。
- OpenAPI `components.schemas`：全部相同；DTO 与 PreviewImpact 分支不变。
- `x-wire-conventions`、`x-error-catalog`：相同。
- revalidate、idempotency、retry policy：相同。
- `capabilities.json`：相同，21 capabilities 不变。
- 状态机：相同，7 statuses 不变。
- 依赖 DAG：相同。

## 4. 独立验证证据

使用 `git archive 6a0989a` 导出至临时目录，并从导出根目录执行：

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

Frontend opening-semantics and regression assertions: 22/22 PASS
```

22 项断言同时验证 9 项新增期初语义和 13 项未回归条件，包括 DTO、wire、error、revalidate、idempotency、状态机、DAG 与 capability 的结构相等。

## 5. 残留与实现边界

- **无 P0、无 P1。** 静态机器契约已完整表达前端展示/提示所需语义。
- `sameTransaction`、唯一索引、余额重算与 rollback scope 是 Backend 行为不变量，仍需在真实持久化实现的集成测试中证明；本次 PASS 不代表运行时已实现。
- DEL-028、DEL-029 继续分别只绑定 `49bef4a`、`c364a37`；本报告只绑定 `6a0989a`。真实 API/IAM 不提前编码。

## 6. 证据路径

- [OpenAPI](../backend/sprint-0/import-contract/openapi.json)
- [提交语义](../backend/sprint-0/import-contract/import-commit-semantics.json)
- [Backend validator](../backend/sprint-0/import-contract/tests/validate_contract.py)
- [Backend 测试报告](../backend/sprint-0/S0_CONTRACT_01_02_TEST_REPORT.md)
- [DEL-029 v1.1.1 增量复核](DEL-025_V1_1_1_INVENTORY_QUANTITY_FRONTEND_REVIEW.md)

## 7. 最终结论

**PASS，P0=0、P1=0。** 精确提交 `6a0989ab224c3a56c4527fc4f2cac080fd4f0818` 已将 OPENING 不可变、余额与唯一 OPENING 同事务、幂等单流水、余额按流水重算及完整 rollback scope 固化为机器字段；DEL-025 v1.1.1 的 DTO/wire/revalidate/capability 基线未回归。
