# DEL-025 v1.1 OpenAPI 前端增量复核

| 字段 | 内容 |
| --- | --- |
| 交付物 ID | DEL-028 |
| 复核人 | Frontend |
| 冻结输入 | Backend commit `49bef4a3569599180f3cee6a7a1e8dd13ad8d3ca` |
| 基线 | DEL-027 / DEL-025 v1.0 |
| 日期 | 2026-09-12 |
| 状态 | Completed（PJM 核验通过） |
| 总结论 | **PASS（P0=0、P1=0）** |

## 1. 复核边界

本报告只审查从精确提交 `49bef4a` 隔离导出的 DEL-025 v1.1，不使用当前共享工作树。范围为 revalidate 契约、分页与 DTO、成功/错误/二进制 envelope、全响应 request ID，以及 DEL-017 v1.2 核心语义回归；真实 endpoint、持久化事务和 IAM 实现不在本轮范围。

## 2. 增量结论

| 检查项 | 结论 | 前端消费证据 |
| --- | --- | --- |
| Revalidate endpoint | **PASS** | 新增 `POST /api/v1/import-batches/{batchId}/revalidate`，使用既有 `imports.validate` capability，要求 `Idempotency-Key`，202 返回 `RevalidationReceipt`。 |
| 旧批次不回退 | **PASS** | receipt 将 `sourceStatus` 固定为 `VALIDATED`、`sourcePreviewExpired` 固定为 `true`；事务语义将 `sourceStatusAfterRequest` 固定为 `VALIDATED`、过期后 `commitAllowed=false`。 |
| 新 QUEUED 批次 | **PASS** | `newBatch` 引用 `QueuedImportBatch`，其 status/phase 分别固定为 `QUEUED/WAITING`；事务语义要求新 `batchId`、复用不可变 staged file 且无业务写入。 |
| 双向关联 | **PASS** | `ImportBatch` 必填且可空的 `revalidationOfBatchId`、`supersededByBatchId` 均为 UUID；事务语义明确 successor/source link field，receipt 明确新批次指回 source。 |
| 幂等唯一后继 | **PASS** | 每个 source 最多一个直接 successor；同请求重放返回已有 successor；revalidate 纳入必须幂等的写操作集合，409 稳定码可达。 |
| Issues/preview 分页 | **PASS** | 两个 endpoint 各自接受 cursor；`ImportIssuePage`、`ImportPreviewPage` 均使用 `items + page`，`PageMeta` 必含 `limit/nextCursor/hasMore`。 |
| Preview DTO | **PASS** | `FieldMap` 值继续使用 tagged `TransportFieldValue`；`before/after` 引用 `FieldMap`；`impacts` 引用 `PreviewImpact`；`BusinessKey` 保持 7 个封闭结构。 |
| Wire envelope | **PASS** | JSON 成功响应为 direct schema；错误统一为 `application/problem+json` + `ApiError`；两个 XLSX 成功响应均为 direct binary。 |
| X-Request-Id | **PASS** | 11/11 operation 的全部 response（成功与错误）均声明 `X-Request-Id`；公共 header schema 为非空 opaque string。 |
| 产品语义回归 | **PASS** | 状态仍为精确 7 项且无 `PARTIALLY_SUCCEEDED`；整批 `ATOMIC_BATCH`、禁止部分成功；capability 集仍为 21 项。 |

## 3. Revalidate 前端状态模型

```text
source: VALIDATED + preview expired + commitAllowed=false
  POST /revalidate + Idempotency-Key
    -> receipt.sourceBatchId = source.batchId
    -> receipt.sourceStatus = VALIDATED
    -> receipt.newBatch = QUEUED / WAITING / distinct batchId

source.supersededByBatchId  -> successor.batchId
successor.revalidationOfBatchId -> source.batchId
```

相同幂等请求返回同一 successor；同一 source 不产生第二个直接 successor。successor 未来 preview 过期时可作为下一链节的 source。

## 4. 独立验证证据

使用 `git archive 49bef4a` 导出到临时目录后执行：

```text
$ python3 .../import-contract/tests/validate_contract.py
PASS: S0-CONTRACT-01/02 machine-readable contracts are consistent
CHECKS: importTypes=6 statuses=7 phases=8 operations=11 capabilities=21 errors=25

$ npx --yes @redocly/cli@1.34.5 lint .../openapi.json --format=stylish
Woohoo! Your API description is valid.
0 error / 0 warning

Frontend incremental semantic assertions: 28/28 PASS
- operation / X-Request-Id: PASS
- revalidate state, linkage, errors and idempotency: PASS
- pagination and preview DTO: PASS
- direct JSON / problem+json / binary envelopes: PASS
- 7 statuses / atomic batch / unchanged capabilities: PASS
```

## 5. 残留与实现边界

- **无 P0、无 P1。** OpenAPI/JSON Schema 无法以标准关键字表达两个对象 UUID 的运行时相等关系；当前通过明确字段、receipt 描述及 `import-commit-semantics.json` 的 link field 约束形成静态契约。双向值一致性与唯一索引仍须在真实 Backend 实现测试中证明，不影响前端 DTO 可实现性。
- `x-wire-conventions`、`x-error-codes`、revalidation 事务语义均为项目扩展；后续生成 TypeScript client 时需保留或显式转换，不能由通用生成器静默丢弃。
- IAM/session、403、路由守卫与按钮权限继续后移，本轮不提前编码。

## 6. 证据路径

- [OpenAPI](../backend/sprint-0/import-contract/openapi.json)
- [提交语义](../backend/sprint-0/import-contract/import-commit-semantics.json)
- [Backend validator](../backend/sprint-0/import-contract/tests/validate_contract.py)
- [Backend 测试报告](../backend/sprint-0/S0_CONTRACT_01_02_TEST_REPORT.md)
- [DEL-027 v1.0 复验](DEL-025_REVISION_FRONTEND_REVIEW.md)

## 7. 最终结论

**PASS，P0=0、P1=0。** 精确提交 `49bef4a3569599180f3cee6a7a1e8dd13ad8d3ca` 已从前端消费角度完成 DEL-025 v1.1 的 endpoint、JSON shape、envelope、revalidate 与 request correlation 收口，可作为后续 DTO/client 的冻结基线；运行时实现验证仍按后续 Sprint 执行。
