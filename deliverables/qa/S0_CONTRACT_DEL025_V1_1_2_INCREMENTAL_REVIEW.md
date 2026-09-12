# DEL-025 v1.1.2 增量 QA 复核

| 字段 | 内容 |
| --- | --- |
| Reviewer | QA |
| 精确提交 | `6a0989a` |
| 执行时仓库 HEAD | `347e29a`（冻结输入仍为 `6a0989a`） |
| 方法 | 从 Git commit object 导出隔离快照；不使用当前 HEAD/共享工作树文件 |
| 范围 | OPENING 幂等、冲突、不可变、余额/流水一致性与整批回滚的机器语义 |
| 结论 | **PASS（P0=0 / P1=0）** |
| 边界 | 仅静态机器契约；IMP-010/014～018、Runtime API / DB / IAM / Worker / 真实样本继续 Blocked |
| 日期 | 2026-09-12 |

## 1. 标准门禁

| 检查 | 结果 |
| --- | --- |
| `npm ci --ignore-scripts && npm test` | Pass；ImportIssue 正例 4/4、负例 6/6；InventoryTransaction 正例 8/8、负例 6/6 |
| `python3 validate_contract.py` | Pass；6 importTypes / 7 statuses / 8 phases / 11 operations / 21 capabilities / 25 errors |
| Redocly CLI 1.34.5 | Pass；0 error / 0 warning |
| QA 负向变异 | Pass；7/7 均被独立断言拒绝 |

## 2. 六项 P0 静态证据

| 验收项 | 结果 | 机器证据 |
| --- | --- | --- |
| 同载荷重放 | Pass | `sameKeyEvidenceAndPayloadReplay=RETURN_EXISTING_RESULT_WITH_ONE_OPENING`，每业务键 OPENING 数为 1 |
| 异载荷冲突 | Pass | `sameKeyDifferentPayload=OPENING_INVENTORY_CONFLICT`；错误目录固定 HTTP 409、不可自动重试 |
| 零 OPENING | Pass | `openingQuantity.minimum=0`、非 exclusive、zero 仍生成流水；条件 Schema/AJV 接受 `0` 与 `0.0000` |
| OPENING 不可编辑 | Pass | `openingTransactionImmutable=true`；纠错仅 `APPEND_ADJUSTMENT_OR_REVERSAL` |
| 余额/流水一致 | Pass | 余额与唯一 OPENING 同事务创建；`postCommitInvariant=BALANCE_EQUALS_LEDGER_RECOMPUTATION`；增量源为 `opening_quantity` |
| 整批失败回滚 | Pass | rollback scope 明确包含 `AuxiliaryMaterialInventory`、`InventoryTransaction(OPENING)` 与 `audit` |

OpenAPI 的 `x-domain-invariants.openingInventory` 同步投影普通 Upsert 禁止、零 OPENING、不可变、同事务和每业务键唯一流水；`import-commit-semantics.json` 承载完整幂等、重算与回滚语义。

## 3. QA 负向变异

以下七种内存变异全部被拒绝：

1. 同载荷重放改为新增另一条 OPENING；
2. 异载荷冲突从 HTTP 409 改为 200；
3. 把零 OPENING 改为非法且不生成流水；
4. 把 OPENING 改为可编辑；
5. 余额与流水取消同事务；
6. 余额不再按流水重算；
7. rollback scope 删除 audit。

结果：**negative controls 7/7 Pass**。

## 4. 最终结论

精确提交 `6a0989a` 的 DEL-025 v1.1.2 静态契约 **PASS，P0=0/P1=0**，具备增量静态收口条件。

该结果不证明真实 API/数据库已经实现唯一 OPENING、并发幂等、409 冲突、不可编辑、余额重算或故障回滚。IMP-010/014～018 必须继续在运行实现上执行，在此之前不得外推为动态 Pass。
