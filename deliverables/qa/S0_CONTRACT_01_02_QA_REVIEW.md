# S0-CONTRACT-01/02：DEL-017 v1.1 QA 静态复核与测试口径

| 字段 | 内容 |
| --- | --- |
| 项目 | 九更亚马逊 ERP |
| Reviewer | QA |
| 复核快照 | `c43347a` / DEL-017 v1.1 |
| 关联交付物 | DEL-013、DEL-017、DEL-023、DEL-024 |
| 结论 | FAIL（2 P0）；DEL-017 异步导入产品语义与 xlsx 结构本身 Pass |
| 动态状态 | Backend OpenAPI/实现与真实脱敏样本 Blocked |
| 日期 | 2026-09-12 |

## 1. 静态复核结论

| 范围 | 结果 | 证据/说明 |
| --- | --- | --- |
| 6 个 `importType`、`templateVersion=1.0`、格式/MIME、1～10 MiB、1,000 行 | Pass | DEL-017 v1.1 §2；六个 `_meta` 映射一致 |
| 7 状态、迁移、取消边界、preview 24 小时过期 | Pass | DEL-017 v1.1 §3 |
| `BLOCKER/ERROR/WARNING/INFO` 与阻断规则 | Pass | DEL-017 v1.1 §4 |
| preview 计数等式、稳定排序、直接/派生 impact | Pass | DEL-017 v1.1 §5 |
| 六类整批原子回滚、上传/取消/确认幂等 | Pass | DEL-017 v1.1 §6 |
| 期初同键同依据同载荷单一 `OPENING`、异载荷拒绝 | Pass（DEL-017 局部） | DEL-017 v1.1 §6.2 |
| 导入依赖 DAG 与文件内顺序 | Pass | DEL-017 v1.1 §7 |
| 六个 import capability 与三角色 | Pass | DEL-017 v1.1 §8；ACCESS_MATRIX v1.1 |
| StoreAccount 系统预置且无 `/system/stores` | Pass | DEL-017 v1.1 §7～8 |
| DEL-013/017 跨文档总体一致性 | **Fail / 2 P0** | 通用 Upsert 与期初特例冲突；零期初与流水正数约束冲突 |

快照独立读取结果：6/6 xlsx 可打开；6/6 veryHidden `_meta` 的 `schemaIdentifier/importType/templateVersion/maxDataRows` 精确匹配；16 组 Excel DataValidation；生成脚本 `py_compile` 通过。此前敏感扫描无命中；合成 `TEST-*` 不计真实业务验收。

## 2. 必执行测试口径

### 2.1 文件、模板和容量门禁

| 用例 | 操作/边界 | 预期 |
| --- | --- | --- |
| IMP-FILE-001 | 六个合法 `importType` 分别上传匹配模板 | 创建 `QUEUED` 批次；返回服务器 `fileHash` 和限制快照 |
| IMP-FILE-002 | 未知/错配 `importType`，或请求版本与 `_meta`/服务器版本错配 | 稳定错误码；不创建可提交批次 |
| IMP-FILE-003 | `.xlsx` 合法 OOXML；分别伪造 `.xls/.xlsm/.csv/.zip`、改后缀、MIME 错配、宏/外链/嵌入对象 | 仅合法文件通过；服务端不信任浏览器文件名/MIME |
| IMP-FILE-004 | 0 bytes、1 byte、恰好 10,485,760 bytes、10,485,761 bytes | 空文件/超限分别拒绝；合法上边界不因大小被拒绝 |
| IMP-FILE-005 | 0、1、1,000、1,001 个非空数据行；VARIANTS 两导入表合计 1,000/1,001 | 1,000 通过，1,001 返回 `IMPORT_ROW_LIMIT_EXCEEDED`；说明/样例/枚举/表头不计数 |
| IMP-FILE-006 | `_meta` 缺失、可见、字段缺失/篡改；工作表/表头缺失、额外列、列重排 | `_meta` 三方不一致拒绝；列重排按表头解析，其余按冻结错误码处理 |

### 2.2 状态、取消与 preview 过期

| 用例 | 操作/边界 | 预期 |
| --- | --- | --- |
| IMP-STATE-001 | 正常跑完 `QUEUED→VALIDATING→VALIDATED→COMMITTING→SUCCEEDED` | 状态只前进；phase/进度字段合法；终态不回退 |
| IMP-STATE-002 | 解析/系统故障及提交期约束故障 | 前者进入 `FAILED` 且无写入；后者整批回滚后 `FAILED` |
| IMP-STATE-003 | 在 `QUEUED/VALIDATING/VALIDATED` 取消并重复取消 | 可取消；VALIDATING 在安全点结束且零业务写入；重复取消幂等返回 `CANCELLED` |
| IMP-STATE-004 | 在 `COMMITTING/SUCCEEDED/FAILED/CANCELLED` 取消 | 返回 `409 IMPORT_NOT_CANCELLABLE`，状态及数据不变 |
| IMP-STATE-005 | 并发发起 cancel 与 confirm | 只有一个权威状态获胜；不得出现成功写入后仍为 `CANCELLED` |
| IMP-STATE-006 | 确认发生在 expiry 前一瞬、恰好边界、边界后；携带旧 `previewVersion` | 过期按服务端时钟返回 `409 IMPORT_PREVIEW_EXPIRED`；旧 preview 不可提交，重新校验后使用新版本 |

### 2.3 Issue 阻断、预览和影响

| 用例 | 操作/边界 | 预期 |
| --- | --- | --- |
| IMP-ISSUE-001 | 分别构造 BLOCKER、ERROR、WARNING、INFO 及混合组合 | BLOCKER/ERROR 的 `blocking=true` 且不可提交；WARNING/INFO 非阻断；任一 blocking 使 `commitAllowed=false` |
| IMP-ISSUE-002 | WARNING 下以 `warningsAccepted=false/true` 确认 | false 返回 `409 IMPORT_WARNINGS_NOT_ACCEPTED`；true 才可提交 |
| IMP-ISSUE-003 | 绕过前端直接调用确认 API | 后端独立执行阻断判断，不接受前端布尔值伪造 |
| IMP-ISSUE-004 | issue 跨页刷新，含批次/表/行/字段级及敏感原值 | 按 `severity→sheetOrder→row→field→code` 稳定；不重不漏；`rawValue` 脱敏且不回公式/密钥 |
| IMP-PREV-001 | CREATE/UPDATE/UNCHANGED/invalid/WARNING 混合；SKU 两工作表多行 | 两条计数等式成立；invalid action=null；warningRows 去重且不参与等式；两表逐物理行计数 |
| IMP-PREV-002 | Listing 派生 Catalog、期初派生余额和 OPENING | 派生写入仅进入 `impactSummary/impacts[]`，不抬高输入行数；直接/派生资源、键和 effect 可核对 |
| IMP-PREV-003 | before/after 含允许字段与尝试注入敏感/系统字段 | 仅返回模板白名单；SecretRef、系统列及敏感字段不出现 |

### 2.4 原子性、重试与期初库存

| 用例 | 操作/边界 | 预期 |
| --- | --- | --- |
| IMP-TXN-001 | 六类分别在首/中/末行注入提交期唯一键、引用或并发冲突 | 整批回滚；业务表、派生表、余额/流水均无部分写入 |
| IMP-IDEM-001 | 相同上传 key+hash 重试；相同 key+不同 hash | 前者返回原批次；后者 `409 IDEMPOTENCY_KEY_REUSED` |
| IMP-IDEM-002 | 相同 batch+confirm key+previewVersion 并发/串行重试 | 返回第一次结果；不重复新增、更新或记账 |
| IMP-IDEM-003 | 成功批次换确认 key 再提交；篡改 fileHash/importType/templateVersion/previewVersion | 前者 `409 IMPORT_ALREADY_COMMITTED`；任一快照不匹配均拒绝 |
| IMP-OPEN-001 | 同仓库+辅料+依据+载荷，使用相同/不同请求 key 重试 | 复用既有结果，余额/期初记录唯一且仅一条不可编辑 `OPENING` |
| IMP-OPEN-002 | 同业务键但依据或载荷不同 | `409 OPENING_INVENTORY_CONFLICT`；既有余额和流水不变 |
| IMP-OPEN-003 | `opening_quantity=0` | v1.1 跨文档规则冲突，修订冻结前预期不可执行；修订后需验证零值策略与审计/流水一致 |

### 2.5 DAG、StoreAccount 与权限

| 用例 | 操作/边界 | 预期 |
| --- | --- | --- |
| IMP-DAG-001 | PRODUCTS 与 AUXILIARY_MATERIALS 并行；分别缺 Product/SKU/辅料/Warehouse/StoreAccount 前置 | 两根链可并行；缺依赖逐行失败；UI 不强制不必要的 01→06 线性阻断 |
| IMP-DAG-002 | SKU 两表乱序；Listing 子体先于父体、跨店父体；关系单位不等于基础单位 | 按冻结的文件内顺序/引用/单位规则定位错误，不产生部分数据 |
| IMP-AUTH-001 | ADMIN、PRODUCT_MATERIAL、SELECTION 对六个 import capability 逐一检查菜单、按钮、直接 URL、六类 API | 前两角色允许，SELECTION 全部拒绝；前端隐藏不替代 API 403 |
| IMP-AUTH-002 | 篡改客户端角色名/capability，直接调用 validate/commit/cancel/result | 后端按同名 capability 独立鉴权；无授权写入和数据泄露 |
| IMP-STORE-001 | 检查路由/API并以非预置、停用及跨店 `store_code` 导入 | 不存在 `/system/stores` 新增/编辑能力；仅允许引用系统预置的 5 个启用店铺 |

## 3. Fail 原因与用例缺口

### 3.1 P0（阻断 v1.1 总体通过）

1. **COM-008 冲突**：DEL-013 把所有导入写为 Upsert，而 DEL-017/产品模型把期初库存定义为不可覆盖的一次性命令。
2. **零期初冲突**：DEL-013 允许 `opening_quantity>=0`，同时要求所有库存流水 `quantity>0`；DEL-017/产品模型又要求每条期初创建 `OPENING`。

PJM 已给出目标决策：期初排除普通 Upsert；保留零期初，`OPENING.quantity` 可为 0，其他流水必须 `>0`。该决定必须在 PM 文档/模板完成修订并经 Backend/QA 复核后，P0 才能关闭。当前工作树中的 v1.2 候选不属于 `c43347a` 快照，不能提前计为通过。

### 3.2 尚缺动态载体/可执行证据

- Backend OpenAPI、Issue/Preview DTO、分页 envelope、状态机/Worker、事务和 IAM 尚未交付，以上 API/并发/原子/越权用例当前均 Blocked。
- preview 过期后的“复用原批次重验还是创建新批次”、并发 cancel/confirm 的线性化响应、issues/rows 游标/页码 envelope 与 `businessKey/impacts` JSON 形状仍需 OpenAPI 固定。
- 真实 5 店、固定仓库编码及 DATA-001～004 未到位；合成 xlsx 只能证明模板和校验结构，不能替代真实业务导入。
- v1.1 的 16 组 Excel 校验不含 `attributes_导入模板.display_order>=1`，Listing 错误样例缺同店重复 FNSKU、同店同 SKU 多 ACTIVE、跨店父体、父子主题不一致；这些需在后续模板版本复核。

## 4. 最终判定

- **DEL-017 v1.1 异步导入产品语义：PASS（静态）**。
- **六个 xlsx 结构与 `_meta`：PASS（静态）**。
- **DEL-013/017 总体契约：FAIL（2 P0）**。
- **实现 Ready：BLOCKED**，等待 Backend OpenAPI/实现及 IAM。
- **真实样本导入：BLOCKED**，不得用 `TEST-*` 替代。
