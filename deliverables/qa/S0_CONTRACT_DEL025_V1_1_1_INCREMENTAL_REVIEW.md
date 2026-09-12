# DEL-025 v1.1.1 增量 QA 复核

| 字段 | 内容 |
| --- | --- |
| Reviewer | QA |
| 精确提交 | `c364a37` |
| 方法 | 从 Git commit object 导出隔离快照；不使用当前 HEAD/共享工作树文件 |
| 范围 | 库存流水 quantity 的 OpenAPI Draft 2020-12 条件 Schema 与 PreviewImpact 集成 |
| 结论 | **PASS（P0=0 / P1=0）** |
| 边界 | 仅静态机器契约；IMP-010/014～018、Runtime API / DB / IAM / Worker / 真实样本继续 Blocked |
| 日期 | 2026-09-12 |

## 1. 自动化结果

| 检查 | 结果 |
| --- | --- |
| `npm ci --ignore-scripts && npm test` | Pass；ImportIssue 正例 4/4、负例 6/6；InventoryTransaction 正例 8/8、负例 6/6 |
| `python3 validate_contract.py` | Pass；6 importTypes / 7 statuses / 8 phases / 11 operations / 21 capabilities / 25 errors |
| Redocly CLI 1.34.5 | Pass；0 error / 0 warning |
| QA 扩展 quantity 实例 | Pass；合法 8/8、非法 11/11 被拒绝 |
| QA PreviewImpact 条件实例 | Pass；合法 2/2、非法 3/3 被拒绝 |

## 2. 条件 Schema 结论

| 规则 | 结果 | 证据 |
| --- | --- | --- |
| OPENING 非负 | Pass | `transactionType=OPENING` 使用 `NonNegativeDecimalString`；`0`、`0.0000` 与正数通过，负数/科学计数法/JSON number 拒绝 |
| 非 OPENING 严格正数 | Pass | INBOUND/OUTBOUND/ADJUST_IN/ADJUST_OUT/REVERSAL 使用 `PositiveDecimalString`；`0`、`0.0000`、负数、JSON number 拒绝 |
| 类型穷尽 | Pass | 六种 transactionType 枚举固定；未知类型拒绝 |
| 规范化小数编码 | Pass | quantity 仅接受非科学计数法 decimal string；拒绝 `1e2`、`1e-2`、`+1`、前导零 `01` |
| PreviewImpact 库存分支 | Pass | `resource=inventory_transactions` 时 `inventoryTransaction` 必须是有效数量对象；null 与非 OPENING 零数量拒绝 |
| PreviewImpact 非库存分支 | Pass | 其他 resource 的 `inventoryTransaction` 必须为 null；携带库存流水对象拒绝 |
| 复用一致性 | Pass | `x-domain-invariants.openingInventory.transactionQuantitySchema` 与 PreviewImpact 均引用同一 `InventoryTransactionQuantityRule` |

## 3. 最终结论

精确提交 `c364a37` 的 DEL-025 v1.1.1 静态 Schema **Pass，P0=0，P1=0**。这只证明 OpenAPI 条件与 AJV 实例门禁正确，不证明运行服务已创建唯一不可变 OPENING、拒绝零值非 OPENING、保持余额/流水事务一致或完成失败回滚。

IMP-010/014～018 必须继续在真实 API 与数据库实现上执行；在此之前不得将静态结果外推为动态 Pass。
