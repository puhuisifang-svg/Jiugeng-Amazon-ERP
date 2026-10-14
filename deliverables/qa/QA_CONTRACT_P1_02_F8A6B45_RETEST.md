# QA-CONTRACT-P1-02 `f8a6b45` 定向复验

| 字段 | 内容 |
| --- | --- |
| Reviewer | QA |
| 精确提交 | `f8a6b45` |
| 方法 | 从 Git commit object 导出隔离快照；不使用共享工作树文件 |
| 结论 | **PASS（P0=0 / P1=0），建议关闭 QA-CONTRACT-P1-02** |
| 日期 | 2026-09-12 |

## 1. 标准门禁

| 检查 | 结果 |
| --- | --- |
| ImportIssue AJV | Pass：合法 4/4 接受、非法 12/12 拒绝；required scenarios 12/12 |
| 内建防退化变异 | Pass：删除 12/12、相邻场景替换 12/12 均被检测 |
| InventoryTransaction AJV | Pass：合法 8/8 接受、非法 6/6 拒绝 |
| 跨文件 validator | Pass：6 importTypes / 7 statuses / 8 phases / 11 operations / 21 capabilities / 25 errors |
| Redocly CLI 1.34.5 | Pass：0 error / 0 warning |
| 精确提交 diff | Pass：`git diff --check f8a6b45^ f8a6b45` exit 0 |
| 高置信敏感扫描 | Pass：目标 Backend 跟踪文件 0 个命中文件 |

## 2. 十二个目标场景

- BATCH：非空定位字段；
- SHEET：`sheet` 缺失、空字符串、null；
- ROW：`sheet` 缺失、空字符串，`row=1`、`row=null`；
- FIELD：`field` 缺失、null；
- blocking：`ERROR + blocking=false`、`WARNING + blocking=true`。

脚本为全部 12 个场景固定文件名和语义谓词，不再仅依赖非法实例总数。

## 3. QA 独立外部变异

QA 在隔离快照的实际 fixtures 目录逐个执行：

1. 移走每个目标文件后启动实例门禁：12/12 均非零退出；
2. 保留目标文件名、用相邻目标场景内容覆盖后启动实例门禁：12/12 均非零退出；
3. 恢复全部夹具后复跑基线：4 个合法、12 个非法、12 个 required、12 个删除变异、12 个替换变异全部 Pass；库存 8+6 同时 Pass。

```text
QA_EXTERNAL_DELETION_DETECTED=12/12
QA_EXTERNAL_REPLACEMENT_DETECTED=12/12
QA_UNDETECTED_MUTATIONS=0
```

相邻场景替换保持文件名不变，直接验证语义漂移能够被门禁识别。

## 4. 治理与实现边界

- 原 QA-CONTRACT-P1-01 与 S0-CONTRACT 保持 Closed；DEL-025 v1.1.2 保持 Completed。
- 本轮只修复 QA-CONTRACT-P1-02 的静态测试完整性，不关联 DEL-031/Frontend。
- OpenAPI、提交语义、状态机、DAG、capability 五个冻结机器文件相对 `6a0989a` 无差异。
- IMP-010/014～018、Runtime API/DB/IAM/Worker 与真实样本继续 Blocked；静态复验不外推为动态 Pass。

结论：**QA-CONTRACT-P1-02 Retest Pass，P0=0 / P1=0，建议 PJM 关闭。**
