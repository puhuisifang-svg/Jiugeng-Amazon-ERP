# Sprint 0 验收报告

| 字段 | 内容 |
| --- | --- |
| 状态 | Pending |
| 验收人 | 老板 |
| PJM 核验 | 进行中 |

## 验收结果

待 Sprint 0 交付完成后填写。所有结论必须附交付物路径和验证证据。

## 当前阻断门禁

- 任务边界：BE-CONTRACT-P0-01/02 与 Backend 机器契约专项属于 `S0-CONTRACT-01` 关闭门禁；`S0-INT-01` 并行等待真实业务输入与后续运行实现，二者不得混作旧文档状态回退。
- `BE-CONTRACT-P0-01/02`：PM 已按 PJM 决策完成修订；Backend、PJM、QA 在 `106a0e2` 基线回归通过，现已 Closed。该关闭仅代表静态契约一致，不代表运行时实现或真实样本验收完成。
- `QA-CONTRACT-P1-01`：`21870a8` 的 Draft 2020-12 合法 4/4、非法 6/6 实例测试已由 PJM/QA 独立复验通过，现已 Closed。
- 真实接口/IAM/事务及 DATA-001～004、真实 5 店和固定仓库编码仍未到位，运行与真实样本验收保持 Blocked。

### P0/P1 整改复核（提交 `106a0e2`）

- QA 独立静态结论：**BE-CONTRACT-P0-01/02、BE-CONTRACT-P1-01～03 的 `106a0e2` 修订回归 PASS**；PJM 据此已将五项治理问题关闭。
- 跨文档：COM-008 已限定前五类幂等 Upsert；期初库存改为一次性幂等命令，零期初生成唯一 `quantity=0` OPENING，其他库存流水严格 `>0`。
- 机器契约：最终校验脚本通过（6 importTypes、7 statuses、8 phases、10 operations、21 capabilities、24 errors），OpenAPI/commit semantics 已同步期初条件约束。
- 模板实测：6/6 xlsx 可读、17 组 DataValidation；4 类 Listing 负例、零期初正确样例及 `display_order` whole integer `>=1` 校验均存在。
- 边界：本结论不代表真实样本、数据库迁移或接口实现已通过；机器契约缺陷已关闭，运行门禁转 S0-INT-01。

### DEL-025 导入机器契约

- 最终结论：**PASS / Completed**；Frontend、PJM、QA 已基于 `106a0e2 + 4ebd2be + 21870a8` 独立复验通过。
- 独立验证：跨文件测试通过（6 importTypes、7 statuses、8 phases、10 operations、21 capabilities、24 errors）；5/5 JSON 可解析；AJV Draft 2020-12 合法 4/4、非法 6/6；Redocly CLI 1.34.5 为 0 error / 0 warning。
- P0 条件已进入机器契约：普通 Upsert 仅前五类；期初为不可覆盖的一次性命令；零期初生成唯一零额 OPENING，其他流水严格大于 0。
- 边界：DEL-025 只冻结契约，不代表真实 API、IAM、逐接口鉴权、数据库迁移或 Worker 已实现。
- 证据：[机器契约](../../../../deliverables/backend/sprint-0/import-contract/README.md) / [测试报告](../../../../deliverables/backend/sprint-0/S0_CONTRACT_01_02_TEST_REPORT.md)。

### DEL-026 DEL-025 前端机器契约复核

- 评审交付物结论：**PASS / Completed**；被评对象 DEL-025 结论为 **FAIL（0 P0 / 3 P1）**。
- P1：上传 202 未以 schema 固定 QUEUED；operation 与错误 catalog/HTTP response 未形成机器可达映射；diff 标量允许 decimal 双编码且业务键过宽。
- PJM 复核：三项均会影响客户端类型生成或错误分支穷尽，严重度 P1 合理。当前工作树已有 Backend 整改候选并通过 validator/Redocly，但无精确提交号前不视为关闭。
- 证据：[DEL-026 复核报告](../../../../deliverables/frontend/DEL-025_IMPORT_OPENAPI_FRONTEND_REVIEW.md)。

### DEL-025 修订提交 `4ebd2be` PJM 隔离复核

- 结论：**PASS / In Review（待 Frontend/QA）**。
- PJM 从 Git commit object 独立导出复跑，避免共享工作树并行影响：validator PASS（6/7/8/10/21/24）、5/5 JSON 解析 PASS、Redocly CLI 1.34.5 为 0 error / 0 warning。
- Frontend 三项 P1 已修：上传响应 `const QUEUED`；operation 错误码与 HTTP response 双向可达且含 500/模板 404/通用 400；diff 使用 tagged canonical scalar，decimal 固定字符串，BusinessKey 收窄。
- QA P1 已修：ImportIssue 的 scope 与 sheet/row/field 定位条件进入 schema 与 validator。
- `4ebd2be` 的 Frontend/QA 独立复验与 `21870a8` 实例测试均已完成，DEL-025 后续状态为 Completed。

### DEL-027 DEL-025 修订版前端复验

- 结论：**PASS / Completed**。
- Frontend 基于 `4ebd2be1976f79a3c952f267432321d336836ba0` 隔离复验：FE-CONTRACT-01～07 为 7/7 PASS、P0=0、P1=0；Backend validator 与 Redocly 均通过。
- PJM 对同一隔离快照独立执行 20 项前端消费语义断言，20/20 PASS。
- FE-OPENAPI-P1-01～03 已关闭；QA 最终复验随后通过，DEL-025 与 S0-CONTRACT-01/02 已完成静态收口。
- 证据：[DEL-027 复验报告](../../../../deliverables/frontend/DEL-025_REVISION_FRONTEND_REVIEW.md)。

### DEL-025 AJV 实例验证补强 `21870a8`

- 结论：**PASS / 纳入静态基线**；未改变 `4ebd2be` 的 OpenAPI、状态机、事务、DAG 或 capability 机器语义。
- PJM 从 commit object 隔离导出，在 Node 26.8.2 / npm 11.19.1 下执行标准 `npm ci` 与 `npm test`：AJV 8.17.1 正例 4/4 接受、非法定位/blocking 组合 6/6 拒绝。
- 当前完整静态契约基线：PM `106a0e2` + Backend `4ebd2be` + 实例测试 `21870a8`；QA 终验通过，`QA-CONTRACT-P1-01` 已 Closed。

### DEL-025 v1.1 OpenAPI 增量 `49bef4a`

- 当前结论：**PASS / Completed（随后由 v1.1.1 增量继承）**；Frontend DEL-028 与 QA 隔离复核均为 P0=0/P1=0。
- 增量范围：新增 preview 过期重验 endpoint 与新旧批次双向关联；冻结 direct JSON、`items + page`、`application/problem+json`、direct binary envelope；11/11 operation 全响应声明 `X-Request-Id`。
- PJM 从 `49bef4a` 隔离导出复跑：机器契约校验 6/7/8/11/21/25 PASS，AJV 正例 4/4、负例 6/6，Redocly 1.34.5 为 0 error / 0 warning；另行 revalidate/wire 语义断言 22/22 PASS，定向 diff 与高置信敏感模式检查通过。
- Frontend DEL-028 对同一提交隔离复验：revalidate、双向关联、幂等唯一后继、分页/preview DTO、三类 envelope 与 11/11 operation 全响应 `X-Request-Id` 均通过，语义断言 28/28 PASS；DEL-028 已 Completed。
- 边界：真实 endpoint、持久化事务、IAM 与逐接口鉴权仍属后续实现；S0-CONTRACT-01/02 的 v1.0 静态关闭状态不回退，本项按受控增量独立验收。
- 证据：[机器契约](../../../../deliverables/backend/sprint-0/import-contract/README.md) / [测试报告](../../../../deliverables/backend/sprint-0/S0_CONTRACT_01_02_TEST_REPORT.md) / [DEL-028 前端增量复核](../../../../deliverables/frontend/DEL-025_V1_1_INCREMENTAL_FRONTEND_REVIEW.md) / [QA v1.1 增量复核](../../../../deliverables/qa/S0_CONTRACT_DEL025_V1_1_INCREMENTAL_REVIEW.md)。

### DEL-025 v1.1.1 库存流水条件 Schema 增量 `c364a37`

- 当前结论：**PASS / Completed（v1.1.1 历史版本）**；Frontend DEL-029 与 QA 均基于 `c364a37` 隔离复核为 P0=0/P1=0。当前交付物已升至 v1.1.2，其状态单独判定。
- 增量范围：`InventoryTransactionQuantityRule` 以条件 Schema 固定 OPENING `>=0`、其余五类流水 `>0`；`PreviewImpact.resource=inventory_transactions` 时强制复用该结构，其他资源必须为 `null`。
- PJM 从 `c364a37` 隔离导出复跑：机器契约 6/7/8/11/21/25、ImportIssue AJV 4+6、库存流水 AJV 8+6、Redocly 0/0 全部通过；数量 Schema/PreviewImpact 专项断言 13/13 PASS，定向 diff 与高置信敏感模式检查通过。
- Frontend DEL-029 对同一提交隔离复验：六类 quantity 分支、inventory/non-inventory PreviewImpact 互斥条件与 `49bef4a` revalidate/wire 回归均通过；Backend AJV 全通过，Frontend 独立 AJV 为 quantity 8+14、PreviewImpact 4+4；DEL-029 已 Completed。
- 产品语义仍以 `106a0e2` / DEL-017 v1.2 为准；真实 IMP-010/014～018、API、数据库事务与 IAM 继续 Blocked。
- 证据：[DEL-029 前端增量复核](../../../../deliverables/frontend/DEL-025_V1_1_1_INVENTORY_QUANTITY_FRONTEND_REVIEW.md) / [QA v1.1.1 增量复核](../../../../deliverables/qa/S0_CONTRACT_DEL025_V1_1_1_INCREMENTAL_REVIEW.md)。

### DEL-025 v1.1.2 OPENING 一致性增量 `6a0989a`

- 当前结论：**PASS / Completed**；Backend、Frontend、PJM 与 QA 均基于精确提交 `6a0989a` 完成隔离复核，P0=0/P1=0。v1.1.1 的 DEL-029 只绑定 `c364a37`，不自动覆盖本提交。
- 增量范围：移除 P0-02 的活跃二选一建议并固定唯一口径；机器化 OPENING 不可编辑、余额/流水同事务、每业务键唯一 OPENING、余额等于流水重算，以及余额/OPENING/审计整批回滚范围。
- PJM 从 `6a0989a` 隔离导出复跑：validator 6/7/8/11/21/25、ImportIssue AJV 4+6、库存流水 AJV 8+6、Redocly 0/0 全部通过；OPENING 不变量断言 15/15 PASS，七类负向变异 7/7 被 validator 拒绝，定向 diff 与高置信敏感模式检查通过。
- Frontend DEL-030 对同一提交隔离复验：新增期初 immutable、同事务唯一流水、余额重算、rollback scope 字段完整，v1.1.1 DTO/wire/revalidate/capability 基线无回归，22/22 专项与回归断言 PASS；DEL-030 已 Completed。
- QA 对同一提交隔离终验：validator 6/7/8/11/21/25、ImportIssue AJV 4+6、InventoryTransaction AJV 8+6、Redocly 0/0 全部 PASS；七类负向变异 7/7 被拒绝。
- 静态差异报告结论为 PASS / 运行 Deferred；IMP-010/014～018、领域迁移、真实 DTO/controller/worker、数据库约束及 IAM 继续 Blocked。
- 证据：[DEL-030 前端增量复核](../../../../deliverables/frontend/DEL-025_V1_1_2_OPENING_SEMANTICS_FRONTEND_REVIEW.md) / [QA v1.1.2 增量复核](../../../../deliverables/qa/S0_CONTRACT_DEL025_V1_1_2_INCREMENTAL_REVIEW.md)。

## S0-INT-01 后续动态验收输入

| 输入 | 版本/状态 | 静态证据 | 动态验收状态 |
| --- | --- | --- | --- |
| DEL-016：S0-UI-01 桌面端低保真信息架构与核心流程 | v0.1 / Completed | `deliverables/ui/S0-UI-01_LOW_FIDELITY_IA_AND_FLOWS.md`、`S0-UI-01_ACCEPTANCE_EVIDENCE.md`；PJM 验收提交 `240355c` | Pending，等待真实脱敏样本和可运行实现 |

DEL-016 已纳入 S0-INT-01，后续只验证低保真契约落地与运行时可用性：

1. **字段长度与词表**：使用真实脱敏样本核对表单、列表、详情和导入状态中的长文本、边界长度、受控词表、截断/换行及错误提示，不以合成样例代替业务结论。
2. **5 店显示**：验证 SKU × 5 店信息可区分，Seller SKU/ASIN/FNSKU/履约/状态不串店或互相覆盖，父体/子体/独立 Listing 的展示关系可识别。
3. **三角色可用性**：系统管理员、产品/辅料人员、选品人员分别完成其允许任务；菜单/按钮/受限状态与权限矩阵一致，直接 URL/API 越权另按 QA 权限专项验证。

验收边界：不重复要求 UI 扩展高保真、移动端、复杂数据权限或其他 MVP 外范围。若运行实现偏离已核验低保真契约，缺陷归属按实际层次分派 UI/Frontend/Backend，不以新增高保真设计作为通过前提。

## 分项核验记录

### S0-INT-01 / DEL-018、DEL-019 Ops P1 验收输入

- 当前结论：**BUG-S0-OPS-001 Dynamic Pass / Closed；BUG-S0-OPS-002 Local Static Pass / GitHub Online Blocked；Ops P0=0**。
- `BUG-S0-OPS-001`：**Closed**。QA 已完成 stable gitignored secret 初始化幂等、权限/保留、restart、stop/start、down/up、受控轮换、三类持久化探针及最终安全收尾复验；PJM 复核通过。
- `BUG-S0-OPS-002`：**Local Static Pass / Online Blocked**。5/5 远程 Action SHA 与官方 major tag 的 direct/peeled commit 精确匹配；GitHub 在线 run、job 结论与失败门禁仍待安全授权。
- Ops 的 Compose 生命周期门禁已通过；DEL-018 仍因 Docker Hub TLS 外部门禁保持 In Review。CI 整体仍不能 Pass，直至可追溯 GitHub Actions 在线证据到位。
- 证据：[QA Ops 验收输入](../../../../deliverables/qa/S0_INT_01_OPS_ACCEPTANCE_INPUT.md) / [Compose 交付](../../../../deliverables/ops/S0-OPS-01_DOCKER_COMPOSE.md) / [CI 交付](../../../../deliverables/ops/S0-OPS-02_GITHUB_CI.md)。

### S0-INT-01 / DEL-020、DEL-021 后端验收输入

- DEL-021 结论：**PASS / 已纳入动态证据**。
- PJM 实测：`./mvnw clean verify` BUILD SUCCESS；7 tests / 0 failure / 0 error / 0 skipped；Modulith 门禁、Flyway H2 MySQL 模式通过。真实 MySQL 8 集成随后完成：V1 baseline 应用成功，`0.0.1-SNAPSHOT` Jar 使用 configtree 无回显注入启动，`/actuator/health` 返回 HTTP 200 / `UP`（提交 `4e4aacb`）。
- QA 复核：4 个 Surefire XML 汇总为 7/0/0/0；Jar 存在且非空；4/4 JSON Schema 经 `jq empty` 通过；Backend 目录高置信敏感扫描与 `git diff --check` 通过。
- DEL-020 结论：**STATIC PASS / 3 店连续 7 天 BLOCKED**。框架、Schema、Runbook 和框架内快照保护/去重测试通过，但无批准店铺链接和数据源审批，不能整体 Pass。
- 受控遗留：真实 MySQL 8 Compose 联调已完成；CI 专用 Secret Scanner 仍为最终门禁证据。
- 证据：[QA 后端输入记录](../../../../deliverables/qa/S0_INT_01_BACKEND_ACCEPTANCE_INPUT.md) / [Backend 自测](../../../../deliverables/backend/sprint-0/TEST_REPORT.md) / [Collector Runbook](../../../../deliverables/backend/sprint-0/collector-spike/RUNBOOK.md)。

### S0-INT-01 / DEL-013、DEL-017 字段与 Excel 结构契约

- 结论：**PASS（静态结构）/ BLOCKED（真实样本动态导入）**。
- QA 独立结果：6/6 xlsx、32 个工作表、7/7 导入表、57/57 表头、7/7 正确样例表、7/7 错误样例表、17/17 Excel 校验组均通过；生成脚本可语义复现；合成样例声明和高置信敏感扫描通过。
- Backend：有条件通过，六资源/实体/依赖/业务键及期初库存一次性幂等 `OPENING` 语义已冻结。
- Frontend：DEL-013 字段结构和 DEL-017 xlsx 结构 Pass；FE-CONTRACT-01～07 与后续 OpenAPI P1 已通过 DEL-024/026/027 关闭。
- 阻塞：DATA-001～004、真实 5 店 `store_code`、固定 `warehouse_code` 未到；`TEST-*` 不计业务验收。
- 证据：[QA 契约复核](../../../../deliverables/qa/S0_INT_01_PM_CONTRACT_REVIEW.md) / [Frontend 复核](../../../../deliverables/frontend/S0-PM-FRONTEND_CONTRACT_REVIEW.md) / [字段字典](../../../../deliverables/pm/FIELD_DICTIONARY_V1.md) / [模板规范](../../../../deliverables/pm/EXCEL_IMPORT_TEMPLATES_V1.md)。

### S0-INT-01 / Backend 机器契约

- QA 独立执行：5/5 JSON、Redocly OpenAPI 0 error/0 warning、6 importType、7 状态、8 阶段、10 operations、21 capabilities、24 errors 均通过；AJV Draft 2020-12 合法 4/4、非法 6/6。
- 四个负向控制（缺 importType、期初退回 Upsert、SELECTION 权限提升、增加 StoreAccount API）均被契约测试阻断。
- `BE-CONTRACT-P0-01/02` 与 `BE-CONTRACT-P1-01～03` 静态回归 Pass。
- 当前结论：**PASS / Completed**。`ImportIssue` 的四类 scope 条件与实例级正反例已由 `21870a8` 补齐并经 PJM/QA 复验，`QA-CONTRACT-P1-01` 已关闭。
- Backend 机器契约由 `4ebd2be` 固化，实例测试由 `21870a8` 固化；运行实现继续转 S0-INT-01。
- 证据：[QA 机器契约复核](../../../../deliverables/qa/S0_INT_01_MACHINE_CONTRACT_REVIEW.md) / [Backend 契约测试报告](../../../../deliverables/backend/sprint-0/S0_CONTRACT_01_02_TEST_REPORT.md) / [机器契约目录](../../../../deliverables/backend/sprint-0/import-contract/)。

### S0-FE-01 / DEL-022 前端脚手架与管理端壳

- 结论：**PASS / Completed**。
- 范围：Vue 3 + TypeScript + Vite + pnpm 工程、管理端壳、DEL-016 路由/菜单增量对齐、环境配置与质量脚本。
- PJM 独立验证：冻结锁文件安装、peer check、lint、4 项测试、typecheck、build 均通过；production preview `/home` 返回 HTTP 200；敏感信息模式扫描无发现。
- 受控遗留：当前仅固化角色元数据；真实会话、路由守卫、403 跳转、按钮级权限和 API 联调不属于 Sprint 0，进入后续 Sprint。
- 证据：[DEL-022 报告](../../../../deliverables/frontend/S0-FE-01_SCAFFOLD_AND_ADMIN_SHELL_REPORT.md) / [前端工程](../../../../frontend/)。

### DEL-023 PM 字段与六类导入模板前端契约复核

- 结论：**PASS / Completed（复核报告）**。
- 实证：6 个工作簿、7 个导入工作表均可读取，表头与 DEL-013 对照 7/7 PASS；字段结构、模板结构、错误四要素和页面级三角色可见性通过。
- 发现：0 个 P0、7 个 P1。DEL-017 因缺少机器可读的异步导入状态/DTO/事务/依赖契约，继续保持 In Review，不得标记前端 Ready。
- 收口要求：FE-CONTRACT-01～06 由 PM/Backend 在 Sprint 0 冻结；FE-CONTRACT-07 在 Sprint 0 冻结 capability 名称/角色映射，实现后移。
- 范围决定：StoreAccount 由系统预置 5 店，MVP 不增加店铺管理页面。
- 证据：[DEL-023 复核报告](../../../../deliverables/frontend/S0-PM-FRONTEND_CONTRACT_REVIEW.md)。

### DEL-024 S0-CONTRACT-01/02 前端关闭复核

- 结论：**PASS / Completed（Frontend 复核）**。
- PJM 独立验证：6/6 工作簿、6/6 veryHidden `_meta`、四项机器元数据与 16 组 Excel 数据校验均符合 DEL-017 v1.1；7 状态、取消/expiry、整批原子、依赖 DAG 与 capability 映射证据成立。
- Frontend 侧 FE-CONTRACT-01～07 已关闭；DEL-017 项目级验收仍等待 Backend OpenAPI 与 QA 复核。
- QA 对 `c43347a` 的静态结论：DEL-017 v1.1 产品语义及 xlsx 结构 Pass；DEL-013/017 跨文档总体因两项期初库存 P0 仍 Fail；API/并发/原子/IAM 与真实样本用例 Blocked。
- 证据：[DEL-024 关闭复核](../../../../deliverables/frontend/S0-CONTRACT-01-02_FRONTEND_CLOSURE_REVIEW.md) / [QA 静态复核与测试口径](../../../../deliverables/qa/S0_CONTRACT_01_02_QA_REVIEW.md)。
