# Sprint 0 验收报告

| 字段 | 内容 |
| --- | --- |
| 状态 | Pending |
| 验收人 | 老板 |
| PJM 核验 | 进行中 |

## 验收结果

待 Sprint 0 交付完成后填写。所有结论必须附交付物路径和验证证据。

## 当前阻断门禁

- `BE-CONTRACT-P0-01`：DEL-013 通用 Upsert 与期初库存不可覆盖的一次性 `OPENING` 语义冲突。
- `BE-CONTRACT-P0-02`：模板允许 `opening_quantity=0`，但字段字典要求所有库存流水 `quantity>0`，约束无法同时成立。
- PJM 决策：保留零期初；`OPENING` 流水数量允许为 0，其他库存流水必须大于 0。期初库存明确排除在普通 Upsert 之外。
- 两项经 PM 修订、Backend 技术复核和 QA 回归前，DEL-013/017 不得整体通过，S0-CONTRACT-01 保持 Blocked。

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

### S0-INT-01 / DEL-020、DEL-021 后端验收输入

- DEL-021 结论：**PASS / 已纳入动态证据**。
- PJM 实测：`./mvnw clean verify` BUILD SUCCESS；7 tests / 0 failure / 0 error / 0 skipped；Modulith 门禁、Flyway H2 MySQL 模式、Jar 启动和 `/actuator/health=UP` 通过。
- QA 复核：4 个 Surefire XML 汇总为 7/0/0/0；Jar 存在且非空；4/4 JSON Schema 经 `jq empty` 通过；Backend 目录高置信敏感扫描与 `git diff --check` 通过。
- DEL-020 结论：**STATIC PASS / 3 店连续 7 天 BLOCKED**。框架、Schema、Runbook 和框架内快照保护/去重测试通过，但无批准店铺链接和数据源审批，不能整体 Pass。
- 受控遗留：H2 MySQL 模式不替代 MySQL 8 Compose 联调；CI 专用 Secret Scanner 仍为最终门禁证据。
- 证据：[QA 后端输入记录](../../../../deliverables/qa/S0_INT_01_BACKEND_ACCEPTANCE_INPUT.md) / [Backend 自测](../../../../deliverables/backend/sprint-0/TEST_REPORT.md) / [Collector Runbook](../../../../deliverables/backend/sprint-0/collector-spike/RUNBOOK.md)。

### S0-INT-01 / DEL-013、DEL-017 字段与 Excel 结构契约

- 结论：**PASS（静态结构）/ BLOCKED（真实样本动态导入）**。
- QA 独立结果：6/6 xlsx、32 个工作表、7/7 导入表、57/57 表头、7/7 正确样例表、7/7 错误样例表、16/16 Excel 校验组均通过；生成脚本可语义复现；合成样例声明和高置信敏感扫描通过。
- Backend：有条件通过，六资源/实体/依赖/业务键及期初库存一次性幂等 `OPENING` 语义已冻结。
- Frontend：DEL-013 字段结构和 DEL-017 xlsx 结构 Pass；异步导入联调契约因 FE-CONTRACT-01～07 共 7 个 P1 暂为 Fail，未关闭前不得进入实现 Ready。
- 阻塞：DATA-001～004、真实 5 店 `store_code`、固定 `warehouse_code` 未到；`TEST-*` 不计业务验收。
- 证据：[QA 契约复核](../../../../deliverables/qa/S0_INT_01_PM_CONTRACT_REVIEW.md) / [Frontend 复核](../../../../deliverables/frontend/S0-PM-FRONTEND_CONTRACT_REVIEW.md) / [字段字典](../../../../deliverables/pm/FIELD_DICTIONARY_V1.md) / [模板规范](../../../../deliverables/pm/EXCEL_IMPORT_TEMPLATES_V1.md)。

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
- 证据：[DEL-024 关闭复核](../../../../deliverables/frontend/S0-CONTRACT-01-02_FRONTEND_CLOSURE_REVIEW.md)。
