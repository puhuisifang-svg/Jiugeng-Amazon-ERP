# S0-INT-01：S0-CONTRACT-01/02 Backend 机器契约 QA 复核

> DEL-025 v1.1 的精确提交 `49bef4a` 增量终验见 [S0_CONTRACT_DEL025_V1_1_INCREMENTAL_REVIEW.md](S0_CONTRACT_DEL025_V1_1_INCREMENTAL_REVIEW.md)：静态 Pass，P0=0 / P1=0；动态门禁不变。
>
> DEL-025 v1.1.1 的精确提交 `c364a37` 数量条件 Schema 终验见 [S0_CONTRACT_DEL025_V1_1_1_INCREMENTAL_REVIEW.md](S0_CONTRACT_DEL025_V1_1_1_INCREMENTAL_REVIEW.md)：静态 Pass，P0=0 / P1=0；IMP-010/014～018 继续 Blocked。
>
> DEL-025 v1.1.2 的精确提交 `6a0989a` OPENING 语义终验见 [S0_CONTRACT_DEL025_V1_1_2_INCREMENTAL_REVIEW.md](S0_CONTRACT_DEL025_V1_1_2_INCREMENTAL_REVIEW.md)：静态 Pass，P0=0 / P1=0；运行门禁不变。
>
> `ef991ea` 的当前 4+12 实例语义无回归，仅作为已关闭 QA-CONTRACT-P1-01 的补充增强证据。QA 独立复验发现新的 Backend 测试完整性缺陷 QA-CONTRACT-P1-02，详见 [QA_CONTRACT_P1_02_EF991EA_REVIEW.md](QA_CONTRACT_P1_02_EF991EA_REVIEW.md)：两个 blocking 冲突夹具可被等量非法夹具替换后保持绿灯。原 QA-CONTRACT-P1-01 与 S0-CONTRACT 保持 Closed，DEL-025 保持 Completed。DEL-031 可作为 `ef991ea` 相对 `6a0989a` 无回归的补充历史证据，但不是 P1-02 关闭依据。
>
> QA-CONTRACT-P1-02 修复提交 `f8a6b45` 的定向复验见 [QA_CONTRACT_P1_02_F8A6B45_RETEST.md](QA_CONTRACT_P1_02_F8A6B45_RETEST.md)：12/12 删除及 12/12 替换/语义漂移变异全部被门禁检测，Retest Pass / Closed；动态门禁不变。

| 字段 | 内容 |
| --- | --- |
| 项目 | 九更亚马逊 ERP |
| Reviewer | QA |
| 仓库根目录 | `/Users/thinkperfect/.openclaw/workspace-jiugeng_pjm/jiugeng-amazon-erp` |
| 复核时 HEAD | `4e4aacb`（包含 `21870a8`、`4ebd2be`、`106a0e2`） |
| 关联任务 | S0-CONTRACT-01/02 关闭门禁；S0-INT-01 后续运行验收 |
| 基线 | PM 修订 `106a0e2`；Backend 机器契约 `4ebd2be`；实例测试修订 `21870a8`；DEL-017 v1.2 |
| 范围 | OpenAPI、状态机、提交语义、依赖 DAG、capability、契约测试 |
| 结论 | PASS（静态机器契约）；运行实现与真实样本继续 Blocked |
| 当前静态待办 | **无**；QA-CONTRACT-P1-02 已 Closed，其余静态契约缺陷保持 Closed，DEL-025 保持 Completed |
| 动态状态 | IMP-010/014～018、Runtime API / DB / IAM / Worker / 真实样本继续 Blocked |
| 日期 | 2026-09-12 |

## 0. 环境基线快照

```text
$ pwd
/Users/thinkperfect/.openclaw/workspace-jiugeng_pjm/jiugeng-amazon-erp
$ git rev-parse --short HEAD
4e4aacb
$ git log -3 --oneline
4e4aacb docs: record MySQL 8 integration evidence
21870a8 test: validate import issue schema instances
4ebd2be docs: freeze Sprint 0 import machine contract
```

`git status --short` 为 dirty：共享工作树包含 Ops、Frontend、QA 文档以及 6 个 PM xlsx 的并发修改/未跟踪交付。本轮对 xlsx 仅执行读取，未保存、修改或重生成；复核结论依据当前工作树实物，并用上述提交链标识已提交契约证据。

## 1. QA 独立执行结果

| 检查 | 结果 | 证据 |
| --- | --- | --- |
| JSON 语法 | Pass | 5/5 JSON 经 `jq empty` |
| OpenAPI 3.1 | Pass | Redocly CLI 1.34.5：0 error / 0 warning |
| Backend 契约测试 | Pass | `importTypes=6 statuses=7 phases=8 operations=11 capabilities=21 errors=25` |
| ImportIssue 实例测试 | Retest Pass | `f8a6b45` 合法 4/4、非法 12/12；required 12/12、删除变异 12/12、替换/语义漂移 12/12；QA 外部变异 24/24 被检测 |
| 本地 `$ref` | Pass | 无外部引用，测试脚本递归解析全部 `$ref` |
| P0 修订 | Pass（静态） | 前五类才允许 Upsert；期初为一次性命令；零期初生成唯一 `quantity=0` OPENING；其他流水 `>0` |
| DEL-017 v1.2 工作簿 | Pass | 6/6 `_meta`、17 个 DataValidation、零期初正例、Listing 四类负例 4/4 |
| 字段字典依赖图 | Pass | Product 与 AuxiliaryMaterial 为独立根；Listing、关系、期初依赖与机器 DAG 一致 |
| BE-CONTRACT-P1-01～03 | Pass | 依赖图、四类 Listing 负例、`display_order>=1` 校验均已回归 |
| 负向控制 | Pass | 删除 importType、恢复期初 Upsert、给 SELECTION 导入能力、增加 stores API 四种变异均被测试阻断 |
| 高置信敏感扫描 | Pass | 机器契约目录 0 个命中文件 |
| Git 差异格式 | Pass | `git diff --check` |

可复现命令：

```bash
python3 deliverables/backend/sprint-0/import-contract/tests/validate_contract.py
(cd deliverables/backend/sprint-0/import-contract/tests && npm ci && npm test)
for f in deliverables/backend/sprint-0/import-contract/*.json; do jq empty "$f"; done
npx --yes @redocly/cli@1.34.5 lint deliverables/backend/sprint-0/import-contract/openapi.json --format=stylish
```

## 2. FE-CONTRACT-01～07 映射

| 项目 | 机器载体 | QA 结论 |
| --- | --- | --- |
| FE-CONTRACT-01 文件/版本/限制 | `openapi.json` 上传 schema、`LimitSnapshot` | Pass |
| FE-CONTRACT-02 状态/取消/expiry | `import-batch-state-machine.json`、OpenAPI batch/commit/cancel | Pass |
| FE-CONTRACT-03 Issue DTO | `openapi.json#/components/schemas/ImportIssue` | Retest Pass；当前 4+12 实例与 severity/blocking 防退化门禁完整，QA-CONTRACT-P1-02 已 Closed |
| FE-CONTRACT-04 Preview/diff/impact | Preview/summary/impact/page schemas | Pass（静态形状） |
| FE-CONTRACT-05 原子/幂等/重试 | `import-commit-semantics.json`、commit API、错误目录 | Pass（静态语义） |
| FE-CONTRACT-06 DAG | `import-dependency-dag.json` | Pass |
| FE-CONTRACT-07 capability/角色 | `capabilities.json`、每 operation `x-capability` | Pass |

## 3. P1 关闭复核

### QA-CONTRACT-P1-01：Issue scope 实例级 Schema 回归已补齐

DEL-017 要求：批次级 issue 的 `sheet` 可空；SHEET/ROW/FIELD 级必须有 `sheet`；ROW/FIELD 级必须有 `row`；FIELD 级必须有 `field`。

提交 `4ebd2be` 已补齐 BATCH/SHEET/ROW/FIELD 的条件 Schema；提交 `21870a8` 新增 AJV 8 / Draft 2020-12 实例测试、锁定依赖与 10 个 fixtures。

```json
{
  "code": "ROW_REFERENCE_MISSING",
  "severity": "ERROR",
  "blocking": true,
  "scope": "ROW",
  "sheet": null,
  "row": null,
  "field": null,
  "reason": "引用不存在",
  "rawValue": null,
  "suggestion": null
}
```

**QA 独立复验：** `npm ci && npm test` 执行通过，覆盖：

- 四个合法正例：BATCH 三定位值为空；SHEET 有 sheet；ROW 有 sheet+row；FIELD 有 sheet+row+field；
- 非法负例：SHEET 缺/空 sheet，ROW 缺/空 sheet 或 row<2，FIELD 缺/空任一定位值，BATCH 携带非空定位值；
- severity/blocking 的四级合法组合与反向非法组合；
- 测试实际调用 AJV Draft 2020-12 validator，并断言正例通过、负例失败。

结论：**Pass；QA-CONTRACT-P1-01 已由 PJM 关闭。**

### QA-CONTRACT-P1-02：`ef991ea` 防退化完整性

QA 同哈希复验确认当前 4 个合法实例全部接受、12 个非法实例全部拒绝，但 `requiredLocatorCoverage` 只固定 10 个 locator 文件，未固定 ERROR/WARNING blocking 冲突夹具。隔离负向变异移除两个 blocking 夹具并用其他非法夹具补足总数后，测试仍为绿色。

因此 QA-CONTRACT-P1-02 在 `ef991ea` 时点作为独立测试完整性 P1 建立。它不回退已关闭的原始 Schema 缺陷或 S0-CONTRACT，DEL-025 继续 Completed。DEL-031 仅保留为 `ef991ea` 相对 `6a0989a` 无回归的补充历史证据，不作为 P1-02 关闭依据。

修复提交 `f8a6b45` 已固定全部 12 个目标文件名及语义谓词。QA 从该 Git object 导出隔离快照，确认当前实例、跨文件 validator、InventoryTransaction 与 Redocly 全部通过，并额外执行磁盘级删除 12/12、相邻场景替换/语义漂移 12/12，全部被门禁检测，0 个漏检。结论：**Retest Pass，QA-CONTRACT-P1-02 已 Closed。**

## 4. 静态回归项与保留门禁

- `BE-CONTRACT-P0-01/02`：在 `106a0e2` 的 DEL-013、DEL-017 v1.2、产品模型受控澄清中已对齐，Backend/PJM/QA 回归通过，PJM 已标记 Closed；该关闭不替代运行时回归。
- Backend 机器契约由 `4ebd2be` 固化，实例测试由 `21870a8` 固化；QA-CONTRACT-P1-01 已具备关闭证据。
- OpenAPI 明确 `security=[]` 且 IAM implementation deferred；因此 capability 只能判“机器映射 Pass”，不能判运行时授权 Pass。
- API、Worker、事务、分页稳定性、并发 cancel/confirm、幂等重放与三角色越权尚无运行实现，继续 Blocked。
- DATA-001～004、真实 5 店 `store_code`、固定 `warehouse_code` 未到位，真实样本导入继续 Blocked；`TEST-*` 不计业务验收。

## 5. P0 动态回归门禁

| 用例 | 核验点 | 状态 |
| --- | --- | --- |
| IMP-014 | 首次期初原子创建余额/期初记录及恰好一条不可编辑 OPENING，审计/source/批次/载荷摘要完整 | Blocked：待运行实现 |
| IMP-015 | 同键同依据同载荷串行/并发重放（含不同请求 key）只返回既有结果，不增加余额或流水 | Blocked：待运行实现 |
| IMP-016 | 同键异依据或异载荷稳定拒绝，既有余额/流水/审计 hash 不变 | Blocked：待运行实现 |
| IMP-010 | 零期初成功并生成唯一 `quantity=0` OPENING；非 OPENING 零数量拒绝 | Blocked：待运行实现 |
| IMP-017 | OPENING 禁止更新/删除；纠错仅追加正数量调整/冲销且保留审计链 | Blocked：待运行实现 |
| IMP-018 | 余额与流水重算一致；首/中/末行提交故障使整批余额、流水、派生记录与审计全回滚 | Blocked：待运行实现 |
| IMP-DAG/IMP-011/012 | 两根依赖链、Listing 四类负例；`display_order` 空值、null、0、负数、非整数、越界及同 SKU 重复顺序 | Static Pass；动态服务端校验待实现 |

DEL-013/017 的静态结构与机器契约 Pass；运行行为与真实样本仍未 Pass，不得用静态结果替代动态验收。

## 6. Ops 复验准备状态

`BUG-S0-OPS-001/002` 已映射至 [S0_INT_01_OPS_ACCEPTANCE_INPUT.md](S0_INT_01_OPS_ACCEPTANCE_INPUT.md)：

- stable gitignored file-secret 生命周期及 restart、stop/start、down/up、轮换重建持久化；
- 所有远程 Action 与官方 tag 的 peeled commit SHA 逐项比对，并要求真实 CI run/失败门禁证据。

Ops 候选尚未完成 QA 动态复验，不提前关闭。
