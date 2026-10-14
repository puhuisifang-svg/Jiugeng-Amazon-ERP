# DEL-025 `ef991ea` ImportIssue 测试覆盖增量复核

| 字段 | 内容 |
| --- | --- |
| 交付物 ID | DEL-031 |
| 复核人 | Frontend |
| 冻结输入 | Backend commit `ef991ea10f3d7d6114e0a75dcac0b3a16d56e13e` |
| 继承基线 | DEL-030 / `6a0989ab224c3a56c4527fc4f2cac080fd4f0818` |
| 日期 | 2026-09-12 |
| 状态 | Superseded（PJM 最新范围校正：QA-only 测试增强） |
| 总结论 | **不登记 Frontend Pass/Fail 或 P0/P1；仅保留 v1.1.2 机器语义无回归的技术观察** |

> **范围撤回声明：** PJM 最新权威指令明确 `ef991ea` 为 QA-only 测试增强，Frontend 无需参与其关闭。DEL-031 已撤出验收并标记 Superseded，不等待 Backend/QA 后续修订，也不登记为 Frontend FAIL/P1。DEL-025 v1.1.2 的有效 Frontend Completed 基线仍为 DEL-030 / `6a0989a`。

## 1. 复核边界

本文仅保留对精确提交 `ef991ea` 的既有技术观察：QA-CONTRACT-P1-01 已 Closed；当前测试完整性缺陷为 **QA-CONTRACT-P1-02**，归属 QA-only。Frontend 观察到相对 `6a0989a` 的 v1.1.2 OpenAPI、revalidate/envelope、库存数量条件和 OPENING 不变量无回归，但该观察不参与验收。不使用共享可变工作树，不验证真实 API、DB 或 IAM。

## 2. ImportIssue 实例门禁

| 类别 | 结论 | 覆盖 |
| --- | --- | --- |
| 合法实例 | **PASS 4/4** | BATCH、SHEET、ROW、FIELD 各 1 个，全部被 AJV 接受。 |
| BATCH 非法定位 | **PASS 1/1** | BATCH 携带非空 sheet 被拒绝。 |
| SHEET 非法定位 | **PASS 3/3** | sheet 缺失、空字符串、null 均被拒绝。 |
| ROW 非法定位 | **PASS 4/4** | sheet 缺失、sheet 空字符串、row null、row=1 均被拒绝。 |
| FIELD 非法定位 | **PASS 2/2** | field 缺失、field null 均被拒绝。 |
| Severity/blocking | **PASS 2/2** | ERROR+blocking=false 与 WARNING+blocking=true 均被拒绝。 |

合计：当前夹具的合法 4/4 接受，非法 12/12 拒绝，结果有效。脚本显式列出并保护了 10 个 locator coverage 文件，但只用 `invalidRejected === 12` 保护非法总数，**没有显式要求两个 severity/blocking 冲突夹具存在**。

### QA-only 技术观察：QA-CONTRACT-P1-02

`import-issue-invalid-error-blocking.json` 与 `import-issue-invalid-warning-blocking.json` 可被两个新的 locator 负例等量替换；此时 10 个必需 locator 文件仍存在、非法总数仍为 12，测试继续绿灯，但 severity/blocking 负向覆盖已经丢失。因此“10 个 locator 文件 + 总数可防误删”的结论不成立。

QA-CONTRACT-P1-02 的处理建议是：测试脚本显式断言两个 blocking 夹具（或等价的命名场景清单）存在，并加入删除/等量替换这些场景时测试必须失败的负向变异证据。该缺陷归属 QA-only；DEL-031 不跟踪或关闭它，也不回退 `6a0989a` 的契约 Completed 状态。

## 3. v1.1.2 无回归证据

`git diff 6a0989a..ef991ea` 对以下冻结机器文件均为空：

- `openapi.json`：6 importTypes、7 statuses、8 phases、11 operations、21 capabilities 引用、25 errors 不变。
- `import-commit-semantics.json`：OPENING immutable、同事务唯一流水、余额重算、rollback scope 与 revalidate 语义不变。
- `import-batch-state-machine.json`：状态/phase 与迁移不变。
- `import-dependency-dag.json`、`capabilities.json`：依赖与角色能力不变。
- `tests/validate_contract.py`、`tests/validate_inventory_transaction_instances.mjs`：结构 validator 与库存数量条件测试不变。

因此，本次提交未改变 DTO schemas、wire conventions、direct/page/problem/binary envelope、`X-Request-Id`、revalidate、库存 quantity/PreviewImpact 或 OPENING 业务不变量。

## 4. 独立验证证据

使用 `git archive ef991ea` 导出到临时目录，并从导出根目录执行：

```text
$ python3 .../tests/validate_contract.py
PASS: S0-CONTRACT-01/02 machine-readable contracts are consistent
CHECKS: importTypes=6 statuses=7 phases=8 operations=11 capabilities=21 errors=25

$ npm --prefix .../tests ci --ignore-scripts
added 5 packages

$ npm --prefix .../tests test
PASS: ImportIssue Draft 2020-12 instances valid=4 invalid-rejected=12
PASS: InventoryTransaction quantity condition valid=8 invalid-rejected=6

$ npx --yes @redocly/cli@1.34.5 lint .../openapi.json --format=stylish
Woohoo! Your API description is valid.
0 error / 0 warning

Frontend fixture coverage assertions:
PASS: exact fixture counts valid=4 invalid=12
PASS: invalid locator coverage BATCH=1 SHEET=3 ROW=4 FIELD=2 plus blocking=2
```

## 5. 时序与实现边界

- **DEL-025 v1.1.2 契约基线继续绑定 `6a0989a`，状态 Completed。** `ef991ea` 不是最终静态门禁或关闭候选，只是仍待修订的测试增强。
- DEL-031 仅保留 `ef991ea` 相对 `6a0989a` 的 OpenAPI、DTO、wire、revalidate、库存 quantity/PreviewImpact 与 OPENING 不变量无回归观察；**不能关闭 QA-CONTRACT-P1-02**。
- DEL-031 为 **Superseded**，不登记 Frontend Pass/Fail 或 P0/P1，不参与验收，也不等待 Backend/QA 后续关闭。
- 静态契约 PASS 不代表真实 API、DB、IAM、异步 worker 或真实样本流程已运行；相关实现门禁继续保持 Blocked。

## 6. 证据路径

- [OpenAPI](../backend/sprint-0/import-contract/openapi.json)
- [ImportIssue AJV 测试](../backend/sprint-0/import-contract/tests/validate_issue_instances.mjs)
- [ImportIssue fixtures](../backend/sprint-0/import-contract/tests/fixtures/)
- [Backend validator](../backend/sprint-0/import-contract/tests/validate_contract.py)
- [Backend 测试报告](../backend/sprint-0/S0_CONTRACT_01_02_TEST_REPORT.md)
- [DEL-030 v1.1.2 期初语义复核](DEL-025_V1_1_2_OPENING_SEMANTICS_FRONTEND_REVIEW.md)

## 7. Superseded 结论

DEL-031 **Superseded，不参与验收**。技术观察显示 `ef991ea10f3d7d6114e0a75dcac0b3a16d56e13e` 当前夹具达到 ImportIssue 合法 4/4、非法 12/12，且相对 `6a0989a` 的 OpenAPI/DTO/wire/OPENING 语义无回归；同时观察到两个 severity/blocking 场景未被必需清单保护。该测试完整性问题为 QA-CONTRACT-P1-02，由 QA-only 处理，不形成 Frontend FAIL/P1。

DEL-025 v1.1.2 继续以 DEL-030 / `6a0989a` 为 Frontend Completed 基线。
