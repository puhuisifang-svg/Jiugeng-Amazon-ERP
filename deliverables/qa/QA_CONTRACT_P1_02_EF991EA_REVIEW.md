# QA-CONTRACT-P1-02 `ef991ea` 防退化完整性复验

| 字段 | 内容 |
| --- | --- |
| Reviewer | QA |
| 精确基线 | `ef991ea`（复验时当前 HEAD） |
| 范围 | ImportIssue Draft 2020-12 实例门禁、跨文件 validator、OpenAPI lint、差异与高置信敏感扫描 |
| `ef991ea` 时点结论 | **FAIL：QA-CONTRACT-P1-02，P0=0 / P1=1** |
| 当前治理状态 | **Closed**：`f8a6b45` 修复经 Backend/PJM/QA 同哈希隔离复验通过 |
| 治理边界 | 原 QA-CONTRACT-P1-01、S0-CONTRACT 保持 Closed；DEL-025 保持 Completed |
| 日期 | 2026-09-12 |

## 1. 通过项

- `npm ci --ignore-scripts && npm test`：exit 0；ImportIssue 合法 4/4 接受、非法 12/12 拒绝；InventoryTransaction 合法 8/8 接受、非法 6/6 拒绝。
- 逐夹具语义断言：SHEET 缺失/空/null，ROW 缺失/空 sheet、row=1/null，FIELD 缺失/null，BATCH 非空定位字段，以及 ERROR/WARNING blocking 冲突，12/12 当前夹具内容均符合目标。
- `python3 .../validate_contract.py`：exit 0；6 importTypes / 7 statuses / 8 phases / 11 operations / 21 capabilities / 25 errors。
- Redocly CLI 1.34.5：exit 0；0 error / 0 warning。
- `git diff --check ef991ea^ ef991ea` 与当前工作树 `git diff --check`：exit 0。
- Backend 契约范围高置信私钥、AWS、GitHub、Slack、JWT 模式：0 个命中文件。

## 2. QA-CONTRACT-P1-02：blocking 冲突夹具未被防退化门禁固定

`tests/validate_issue_instances.mjs` 的 `requiredLocatorCoverage` 仅固定 10 个 locator 夹具名，未包含：

- `import-issue-invalid-error-blocking.json`
- `import-issue-invalid-warning-blocking.json`

计数断言只要求 `invalidRejected === 12`。QA 从 `ef991ea` Git object 导出临时隔离快照，移除上述两个 blocking 冲突夹具，再复制两个既有 locator 非法夹具并以其他非法文件名补足总数；随后直接执行 `node validate_issue_instances.mjs`，仍输出：

```text
PASS: ImportIssue Draft 2020-12 instances valid=4 invalid-rejected=12
```

因此当前实例集合的功能结果正确，但“关键夹具名防止覆盖退化”的门禁不完整，未来可能丢失 ERROR/WARNING blocking 冲突覆盖而 CI 仍为绿色。

## 3. 建议整改与复验口径

1. 将两个 blocking 冲突夹具纳入必需夹具门禁；
2. 对 12 个目标场景增加语义断言，避免仅保留文件名但改成其他非法原因仍通过；
3. 增加负向变异测试：删除或替换任一目标场景时测试必须失败。

`ef991ea` 的当前新增 locator 负例与既有 locator/blocking 冲突均被 Schema 正确拒绝，无语义回归；该事实仅作为原 QA-CONTRACT-P1-01 的补充增强证据，不改变其 Closed 状态。

上述防退化问题在 `ef991ea` 时点成立，后由 `f8a6b45` 修复并经 Backend/PJM/QA 同哈希隔离复验通过，现已 Closed。原 QA-CONTRACT-P1-01 与 S0-CONTRACT 保持 Closed，DEL-025 保持 Completed。DEL-031 仅可作为 `ef991ea` 相对 `6a0989a` 无回归的补充历史证据，不是 P1-02 关闭依据；本缺陷只针对 Backend 静态测试完整性，也不外推 Runtime API/DB/IAM/Worker 或真实样本。
