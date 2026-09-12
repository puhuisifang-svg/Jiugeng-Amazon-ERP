# DEL-025 导入 OpenAPI 前端机器契约复核

| 字段 | 内容 |
| --- | --- |
| 交付物 ID | DEL-026 |
| 复核人 | Frontend |
| 输入 | DEL-025 `import-contract/`、DEL-017 v1.2、权限矩阵 v1.1 |
| 日期 | 2026-09-12 |
| 状态 | Completed（PJM 核验通过；评审结论为 FAIL） |
| 总结论 | **FAIL（FE-CONTRACT 4 PASS / 3 FAIL；P0=0、P1=3）** |

## 1. 逐项结论

| 项目 | 结论 | 已通过 | 未关闭项 |
| --- | --- | --- | --- |
| FE-CONTRACT-01 上传元数据与门禁 | **FAIL** | 6 importType、templateVersion、multipart file、1～10 MiB、xlsx/MIME vendor extensions、202 上传 operation、限制快照均存在。 | **P1-01：** `POST /import-batches` 的 202 schema 直接引用通用 `ImportBatch`，其 `status` 是全量 `BatchStatus`，机器 schema 允许返回任意 7 状态，未把“上传成功即 `QUEUED`”约束为 `const: QUEUED`。 |
| FE-CONTRACT-02 批次状态、取消与 expiry | PASS | 7 状态、8 phase、迁移/终态/可取消集合、24 小时 TTL、自 `COMMITTING` 起不可取消均一致；`IMPORT_PREVIEW_EXPIRED` 在 retry policy 明确为新建校验批次。 | 无。 |
| FE-CONTRACT-03 Issue DTO 与错误码 | **FAIL** | issue DTO 10 字段、四级 severity、blocking 条件、issues 独立游标页均正确。 | **P1-02：** 21 个 code 仅在根 `x-error-catalog` 汇总，未绑定到 operation/response；所有 operation 均无 `x-error-codes`，且无 500 response。模板下载 404 没有合适的模板错误码（catalog 唯一 404 为 `IMPORT_BATCH_NOT_FOUND`），列表 400 也无通用请求/游标错误码。前端不能从 operation 推导穷尽错误 union 与分支。 |
| FE-CONTRACT-04 Preview/diff/impact | **FAIL** | issues/preview 独立 cursor、PageMeta、rowIdentity、action、before/after、impacts、summary 和计数口径均存在。 | **P1-03：** `FieldMap`/`BusinessKey` 的任意值允许 `string | number | integer | boolean | null`；decimal 定点字段可由服务端任选 JSON number 或 string，前端无法稳定保持库存/用量/损耗率精度与格式。需固定 decimal/date/datetime 的传输编码，至少 decimal 必须为 string。另建议 `ImpactSummaryItem.resource` 复用 `PreviewImpact.resource` 枚举。 |
| FE-CONTRACT-05 原子提交、重试与幂等 | PASS | 上传/确认/取消均要求 `Idempotency-Key`；CommitRequest 绑定 file/type/version/preview；整批原子、无部分成功、期初特例与 retry policy 均可机读。 | operation 级错误绑定随 P1-02 一并补齐。 |
| FE-CONTRACT-06 依赖 DAG | PASS | 6 import nodes、2 system nodes、依赖无环；Listing 不依赖辅料；StoreAccount 预置 5 店且无管理 API。 | 无。 |
| FE-CONTRACT-07 capability/角色 | PASS | 21 capability、三角色集合与权限矩阵一致；10 operations 均有合法 `x-capability`；6 个 import capability 对 ADMIN/PRODUCT_MATERIAL 全授予、SELECTION 全不授予。 | `security: []` 与 IAM 延后声明一致；运行时鉴权不在本轮证明范围。 |

## 2. P1 关闭条件

### P1-01 上传响应固定 QUEUED

为 202 响应定义专用 schema，例如组合通用批次字段并覆盖：

```json
{
  "status": { "type": "string", "const": "QUEUED" }
}
```

校验脚本应断言上传 202 响应的最终解析 schema 将 `status` 限定为 `QUEUED`，不能只检查 operation 描述文本。

### P1-02 operation 级错误契约

- 每个 operation 使用 `x-error-codes` 或等价机器结构列出允许 code，并断言 code 存在于根 catalog、HTTP status 与 response 一致。
- 为所有可能的技术故障补 500 `application/problem+json` 响应。
- 删除不可达 response，或补齐模板不存在、无效 cursor/请求等稳定错误码。
- 校验脚本需检查“每个非 2xx response 至少有一个可达 code；每个声明 code 的 status 已出现在 operation responses”。

### P1-03 diff 标量编码

- 定义规范化传输标量：decimal 使用十进制 string；date/datetime 使用 ISO string；integer/boolean 保持 JSON 原生类型。
- FieldMap 可保留动态 key，但值应通过 `oneOf`/discriminator 或资源字段 schema 保证编码唯一；BusinessKey 应按六类实际业务键收窄。
- `ImpactSummaryItem.resource` 应与 `PreviewImpact.resource` 使用同一枚举 schema，避免摘要出现行级不支持的资源。

## 3. 非阻断实现前置

- `ImportBatch` 尚无原始文件名、创建人展示信息和建议轮询间隔；若导入中心列表按 DEL-016 展示这些信息，Backend 实现 OpenAPI 时需补充或由其他用户摘要 API 提供。
- 本次只验证静态机器契约；真实 endpoint、异步 worker、数据库原子性、IAM、`can()` 与 403 行为仍需后续运行时测试。

## 4. 独立验证证据

```text
$ python3 .../tests/validate_contract.py
PASS: S0-CONTRACT-01/02 machine-readable contracts are consistent
CHECKS: importTypes=6 statuses=7 phases=8 operations=10 capabilities=21 errors=21

$ npx --yes @redocly/cli@1.34.5 lint .../openapi.json --format=stylish
Woohoo! Your API description is valid.
0 error / 0 warning

Frontend semantic checks:
- upload 202 status const QUEUED: FAIL (generic BatchStatus)
- operation x-error-codes: 0/10
- operation 500 responses: 0/10
- FieldMap decimal canonical encoding: FAIL (number|string both allowed)
- x-capability valid: 10/10
```

Redocly PASS 与 Backend 自检结果成立；上述 FAIL 是其现有静态检查未覆盖的前端消费语义，不是 OpenAPI 语法错误。

## 5. 证据路径

- [OpenAPI](../backend/sprint-0/import-contract/openapi.json)
- [状态机](../backend/sprint-0/import-contract/import-batch-state-machine.json)
- [提交语义](../backend/sprint-0/import-contract/import-commit-semantics.json)
- [依赖 DAG](../backend/sprint-0/import-contract/import-dependency-dag.json)
- [Capability](../backend/sprint-0/import-contract/capabilities.json)
- [Backend 测试报告](../backend/sprint-0/S0_CONTRACT_01_02_TEST_REPORT.md)
- [产品契约关闭复核 DEL-024](S0-CONTRACT-01-02_FRONTEND_CLOSURE_REVIEW.md)

## 6. 最终结论

**FAIL，P0=0。** DEL-025 已覆盖绝大多数 FE-CONTRACT 产品语义，但仍需关闭 P1-01～03 后，Frontend 才能把 OpenAPI 作为无猜测的 DTO/客户端生成基线。本轮不接入接口、不实现 IAM。

## 7. PJM 核验

- DEL-026 作为独立评审报告：**PASS / Completed**；其对所审 DEL-025 快照的 3 个 P1 判定成立。
- 当前共享工作树已出现 Backend 整改候选，但尚无稳定提交号；不得以可变工作树替代关闭证据。
- DEL-025 继续 In Review，须由 Backend 精确提交修订后再交 Frontend/QA 复验。
