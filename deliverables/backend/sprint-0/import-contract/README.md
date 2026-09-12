# S0-CONTRACT-01/02 机器可读契约

| 字段 | 内容 |
| --- | --- |
| Owner | Backend |
| 版本 | v1.1 |
| 状态 | In Review（Backend 自检通过，待 Frontend/QA 复核增量） |
| 业务基线 | DEL-017 v1.2、PRODUCT_DATA_MODEL v1.1 受控澄清、ACCESS_MATRIX v1.1 |
| 范围 | Sprint 0 契约冻结；不包含真实接口、IAM 会话或逐接口鉴权实现 |

## 文件

- `openapi.json`：OpenAPI 3.1；上传并入队校验、批次查询、issues、preview、过期重验、确认、取消、结果及结果文件。
- `import-batch-state-machine.json`：七状态、八阶段、取消、终态与 24 小时预览有效期。
- `import-commit-semantics.json`：整批事务、幂等作用域、重试及期初 `OPENING >= 0` 条件规则。
- `import-dependency-dag.json`：六类导入及预置 StoreAccount/唯一 Warehouse 的依赖 DAG。
- `capabilities.json`：动作级 capability 机器码与 `ADMIN`、`PRODUCT_MATERIAL`、`SELECTION` 初始化映射。
- `tests/validate_contract.py`：零第三方依赖的结构、引用和跨文件一致性校验。

## 冻结决定

1. `POST /api/v1/import-batches` 完成文件上传、服务端安全校验并创建异步全量校验批次；成功状态为 `QUEUED`，不新增 `DRAFT`。
2. issues 与 preview rows 分别游标分页，稳定顺序由契约固定；`before`/`after` 仅允许模板白名单字段。
   diff 标量使用带 `kind` 的唯一传输编码（decimal 为非科学计数法 string），业务键按实际行类型收窄。
3. 六类确认均为整批原子事务；MVP 无 `PARTIALLY_SUCCEEDED`。期初库存仍是不可覆盖的一次性幂等 `OPENING` 记账。
   `opening_quantity=0` 合法且仍生成唯一、不可编辑的 `quantity=0` OPENING；其他库存流水数量必须 `>0`。
4. StoreAccount 是系统预置 5 店，只作为 Listing 引用依赖；不提供店铺管理 API/UI，也不定义 `stores.write`。
5. OpenAPI 的 `x-capability` 是后续实现的强制鉴权目标。本轮不声明可运行的认证机制，避免暗示 IAM 已完成。
6. 每个 operation 以 `x-error-codes` 声明穷尽错误 union；错误码 HTTP 状态必须能在该 operation response 中双向对应。
7. Preview 过期后调用 `POST /api/v1/import-batches/{batchId}/revalidate`：旧批次保持 `VALIDATED` 且不可提交，新建唯一 `QUEUED` 后继批次并复用不可变暂存文件；调用要求 `Idempotency-Key`，不发生业务写入。
8. JSON 成功响应直接使用 operation 声明的 schema；列表、issues、preview 统一为 `items + page(limit/nextCursor/hasMore)`；错误统一为 `application/problem+json` / `ApiError`；全部响应携带 `X-Request-Id`。

## 校验

```bash
python3 deliverables/backend/sprint-0/import-contract/tests/validate_contract.py
npm --prefix deliverables/backend/sprint-0/import-contract/tests ci --ignore-scripts
npm --prefix deliverables/backend/sprint-0/import-contract/tests test
```

预期：跨文件一致性 PASS；ImportIssue Draft 2020-12 实例 `valid=4 invalid-rejected=6`。

## PJM 独立核验

2026-09-12，PJM 独立复跑：

- 跨文件校验：PASS（6 importTypes、7 statuses、8 phases、10 operations、21 capabilities、24 errors）。
- 5 个 JSON 文件均可由 Python 标准库解析。
- Redocly CLI 1.34.5：OpenAPI valid，0 error / 0 warning。
- P0 条件：前五类 Upsert 与期初一次性命令分流；零期初生成唯一 `quantity=0` OPENING；其他库存流水 `>0`。
- 高置信敏感信息模式扫描与 `git diff --check`：PASS。

PJM、Frontend 与 QA 已基于 `106a0e2 + 4ebd2be + 21870a8` 完成 v1.0 独立静态复核。本次 v1.1 仅收口重验 endpoint、批次关联、响应头与 envelope 机器约束，仍需 Frontend/QA 对该增量复核。真实 API、IAM、数据库迁移、异步 Worker 与真实样本动态验收仍属于后续门禁。
