# DEL-025 v1.1 增量 QA 复核

| 字段 | 内容 |
| --- | --- |
| Reviewer | QA |
| 精确提交 | `49bef4a` |
| 方法 | 从 Git commit object 导出隔离快照；不使用当前工作树文件 |
| 范围 | preview 过期重验、新旧 batch、分页/游标 envelope、preview DTO、endpoint/header/response envelope |
| 结论 | **PASS（P0=0 / P1=0）** |
| 边界 | 仅静态机器契约；Runtime API / IAM / Worker / 真实样本继续 Blocked |
| 日期 | 2026-09-12 |

## 1. 自动化结果

| 检查 | 结果 |
| --- | --- |
| `npm ci --ignore-scripts && npm test` | Pass；ImportIssue Draft 2020-12 正例 4/4、负例 6/6 |
| `python3 validate_contract.py` | Pass；6 importTypes / 7 statuses / 8 phases / 11 operations / 21 capabilities / 25 errors |
| Redocly CLI 1.34.5 | Pass；0 error / 0 warning |
| 负向变异 | Pass；6/6 均被独立 QA 断言拒绝 |

## 2. 增量契约逐项结论

| 验收项 | QA 结论 | 核验证据 |
| --- | --- | --- |
| 过期重验 endpoint | Pass | `POST /api/v1/import-batches/{batchId}/revalidate`；`imports.validate`；要求 `Idempotency-Key`；202 返回 `RevalidationReceipt` |
| 新旧 batch 语义 | Pass | 旧 batch 保持 `VALIDATED` 且过期后不可提交；新 ID 后继从 `QUEUED` 开始；复用不可变暂存文件；无业务写入；源 batch 最多一个直接后继；重放返回既有后继 |
| issues/preview 游标 envelope | Pass | `ImportIssuePage`、`ImportPreviewPage` 与批次列表均固定 `items + page`；`PageMeta` 必含 `limit/nextCursor/hasMore` |
| businessKey | Pass | `PreviewRow.rowIdentity -> RowIdentity.businessKey -> BusinessKey`，业务键使用七种实际行形状 |
| before/after | Pass | 均为 `FieldMap | null`；字段值复用 tagged canonical `TransportFieldValue` |
| impacts | Pass | `PreviewRow.impacts[] -> PreviewImpact`；resource 复用 `ImpactResource`，effect/derived 明确 |
| endpoint 集合 | Pass | 11/11 operation 完整：模板、上传、列表、批次、issues、preview、revalidate、commit、cancel、result、result-file |
| Header | Pass | 写操作的幂等键与长度约束一致；11/11 operation 的全部响应均声明 `X-Request-Id` |
| 成功响应 envelope | Pass | JSON 使用 direct schema；分页使用 `items + page`；XLSX 使用 direct binary |
| 错误响应 envelope | Pass | 统一 `application/problem+json` / `ApiError`；operation error code、HTTP status 与 response 双向可达，均覆盖 500 |
| 状态机/DAG/capability | Pass | v1.1 未破坏既有 7 状态、8 阶段、6 类无环 DAG 与 21 capability 映射 |

## 3. 负向变异控制

以下六种内存变异均被 QA 独立检查拒绝：

1. 把重验后的源 batch 从 `VALIDATED` 回退为 `QUEUED`；
2. 删除 issues page 的 `page`；
3. 删除 preview row 的 `impacts`；
4. 删除任一成功响应的 `X-Request-Id`；
5. 把错误媒体类型改为 `text/plain`；
6. 删除 revalidate 的 `Idempotency-Key`。

结果：**negative controls 6/6 Pass**。

## 4. 最终结论

DEL-025 v1.1 在精确提交 `49bef4a` 上静态契约 **Pass，P0=0，P1=0**。该结果不代表 endpoint 已运行、IAM 已执行授权、Worker/事务已实现或真实 `store_code`/`warehouse_code` 样本已导入；上述动态门禁继续 Blocked。
