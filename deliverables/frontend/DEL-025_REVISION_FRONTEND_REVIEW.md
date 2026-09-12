# DEL-025 修订版前端机器契约复验

| 字段 | 内容 |
| --- | --- |
| 交付物 ID | DEL-027 |
| 复核人 | Frontend |
| 冻结输入 | Backend commit `4ebd2be1976f79a3c952f267432321d336836ba0` |
| 日期 | 2026-09-12 |
| 状态 | Completed（PJM 核验通过） |
| 总结论 | **PASS（FE-CONTRACT-01～07：7/7 PASS；P0=0、P1=0）** |

## 1. 复验边界

本报告只以精确提交 `4ebd2be` 导出的 `deliverables/backend/sprint-0/import-contract/` 为证据，不使用当前可变工作树。复验覆盖 DEL-026 提出的三项 P1，并回归 FE-CONTRACT-01～07。真实 endpoint、异步 worker、数据库事务和 IAM 实现不在本轮范围。

## 2. FE-CONTRACT-01～07 结论

| 项目 | 结论 | 证据摘要 |
| --- | --- | --- |
| FE-CONTRACT-01 上传元数据与门禁 | **PASS** | 6 个 `importType`、`templateVersion=1.0`、multipart xlsx、1 byte～10 MiB、1000 行限制均可机读；上传 202 引用 `QueuedImportBatch`，`status const QUEUED` 且 `progress.phase const WAITING`。 |
| FE-CONTRACT-02 状态、取消与 preview expiry | **PASS** | 7 状态、8 phase、终态/可取消集合、24 小时 preview TTL 与取消 operation 保持一致。 |
| FE-CONTRACT-03 Issue DTO 与错误 | **PASS** | issue 四级 severity 与 BATCH/SHEET/ROW/FIELD 定位约束可机读；issues 独立 cursor 分页；10/10 operation 均声明 `x-error-codes`，code/status/response 双向可达，且 10/10 均有统一 500 `application/problem+json`。 |
| FE-CONTRACT-04 Preview/diff/impact | **PASS** | preview 独立 cursor 分页；`before/after` 使用 `FieldMap`；值统一为 7 类 tagged `TransportFieldValue`，DECIMAL 仅接受非科学计数法 string；`BusinessKey` 为 7 个互斥结构；行级与摘要共同引用 `ImpactResource`。 |
| FE-CONTRACT-05 原子提交、重试与幂等 | **PASS** | 上传/确认/取消均要求 `Idempotency-Key`；确认/取消 operation 与冲突码可达；整批原子且状态集中无 `PARTIALLY_SUCCEEDED`。 |
| FE-CONTRACT-06 依赖 DAG | **PASS** | 6 import node、2 system node、依赖无环；StoreAccount 固定 5 店且无 `/api/v1/stores` 管理接口。 |
| FE-CONTRACT-07 capability/三角色 | **PASS** | 10/10 operation 的 `x-capability` 均存在于 21 项 capability 集；6 个 import capability 授予 ADMIN/PRODUCT_MATERIAL，SELECTION 不授予；`security: []` 明确运行时 IAM 延后。 |

## 3. DEL-026 三项 P1 关闭证据

### P1-01：上传即 QUEUED — CLOSED

- `POST /api/v1/import-batches` 的 202 response 只引用 `QueuedImportBatch`。
- `QueuedImportBatch` 与通用批次 schema 组合后，将 `status` 固定为 `QUEUED`，并将 `progress.phase` 固定为 `WAITING`；通用 schema 同时要求 `status` 和 `progress` 必填。

### P1-02：operation error reachability — CLOSED

- operation 数：10；带非空 `x-error-codes`：10/10；带 500 response：10/10；带 `IMPORT_TECHNICAL_FAILURE`：10/10。
- 每个 operation 声明的 code 都存在于根 catalog，catalog 的 HTTP status 均出现在该 operation responses。
- 每个非 2xx response 都至少有一个同 status 的声明 code；模板、请求、cursor 稳定码已覆盖。
- 错误 catalog 从 21 项增至 24 项，Backend validator 已加入上述双向可达断言。

### P1-03：canonical scalar / BusinessKey / resource — CLOSED

- `FieldMap.additionalProperties` 只引用 tagged `TransportFieldValue`。
- scalar tag 精确为 STRING、DECIMAL、INTEGER、BOOLEAN、DATE、DATETIME、NULL；每个 variant 必须含 `kind/value` 且禁止额外属性。
- DECIMAL 的 `value` 类型仅为 string，pattern 为 `^-?(0|[1-9][0-9]*)(\\.[0-9]+)?$`，不接受指数记法。
- `BusinessKey.oneOf` 精确覆盖 7 个实际键结构，并通过 `additionalProperties: false` 保持互斥。
- `PreviewImpact.resource` 与 `ImpactSummaryItem.resource` 均引用同一 `ImpactResource`。

## 4. 独立验证证据

从提交 `4ebd2be` 使用 `git archive` 导出到临时目录后执行，未读取后续工作树修改：

```text
$ python3 .../import-contract/tests/validate_contract.py
PASS: S0-CONTRACT-01/02 machine-readable contracts are consistent
CHECKS: importTypes=6 statuses=7 phases=8 operations=10 capabilities=21 errors=24

$ npx --yes @redocly/cli@1.34.5 lint .../openapi.json --format=stylish
Woohoo! Your API description is valid.
0 error / 0 warning

Frontend semantic assertions: 20/20 PASS
- queued response/schema: 5/5
- operation/error reachability: 8/8
- tagged scalar/business key/resource: 6/6
- issue scope locator constraints: 1/1
```

## 5. 残留项

- **无阻断歧义，P0=0、P1=0。** `x-error-codes` 是项目扩展，后续生成 TypeScript client 时需保留扩展或生成 operation 级 error union，不能让通用生成器静默丢弃。
- `ImportBatch` 尚未提供原始文件名、创建人展示信息和建议轮询间隔；这不影响当前机器契约消费，但若后续 UI 明确要求展示，需在真实接口 OpenAPI 中补齐或由其他接口提供。
- 本次 PASS 仅说明静态契约可实现；真实接口、IAM、路由守卫、403 和按钮权限仍按计划后移。

## 6. 证据路径

- [OpenAPI](../backend/sprint-0/import-contract/openapi.json)
- [Backend validator](../backend/sprint-0/import-contract/tests/validate_contract.py)
- [状态机](../backend/sprint-0/import-contract/import-batch-state-machine.json)
- [提交语义](../backend/sprint-0/import-contract/import-commit-semantics.json)
- [依赖 DAG](../backend/sprint-0/import-contract/import-dependency-dag.json)
- [Capability](../backend/sprint-0/import-contract/capabilities.json)
- [Backend 测试报告](../backend/sprint-0/S0_CONTRACT_01_02_TEST_REPORT.md)
- [DEL-026 初次复核](DEL-025_IMPORT_OPENAPI_FRONTEND_REVIEW.md)

## 7. 最终结论

**PASS。** 精确提交 `4ebd2be1976f79a3c952f267432321d336836ba0` 已关闭 DEL-026 的三项 P1；FE-CONTRACT-01～07 为 7/7 PASS。Frontend 可将该提交作为后续 DTO/client 实现基线，但本轮不提前实现真实接口或 IAM。

## 8. PJM 核验

- DEL-027：**PASS / Completed**。
- PJM 对同一隔离提交独立执行 20 项前端消费语义断言：20/20 PASS；并复用隔离 validator 与 Redocly 通过证据。
- FE-OPENAPI-P1-01～03 关闭；DEL-025 项目级状态仍等待 QA 最终复验。
