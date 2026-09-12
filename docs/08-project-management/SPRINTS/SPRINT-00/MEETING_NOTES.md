# Sprint 0 会议记录

## 2026-09-12 启动

- 老板指令：启动 Sprint 0，拆解并分配任务。
- 基线：MVP 最终规划 v1.0、PRD v1.0、总体架构 v1.0、验收标准 v1.0。
- 决定：Sprint 0 状态切换为 `In Progress`，六个专业角色并行启动。
- 原则：业务功能不在本 Sprint 正式开发；监控 Spike 不得绕过平台访问控制；真实凭据不得进入仓库、文档、日志或聊天。
- 待输入：脱敏业务样本、3 个公开店铺链接、首期用户清单、GitHub 安全授权。

## 2026-09-12 DEL-023 契约复核决定

- DEL-023 作为前端跨角色复核报告验收通过；DEL-013 字段结构、DEL-017 模板结构通过。
- DEL-017 端到端异步导入契约尚未 Ready：PM/Backend 须冻结 OpenAPI、批次状态机、结构化 issue DTO、preview diff、事务/幂等语义和依赖 DAG，再交 Frontend/QA 复核。
- Sprint 0 只冻结 capability 名称与三角色映射；会话、路由守卫、按钮 `can()` 和后端逐接口鉴权实现按计划后移。
- 5 个公司 StoreAccount 采用系统预置；MVP 不新增店铺管理页面，不触发 DEL-016 IA 变更。
- PM 已提交 DEL-017 v1.1（`c43347a`）：产品语义、7 状态、整批原子事务、依赖 DAG、capability 映射及工作簿 `_meta` 已由 PJM 静态复核通过；DEL-017 仍等待 Backend OpenAPI 与 Frontend/QA 复核，不提前完成验收。

## 2026-09-12 DEL-013/017 P0/P1 整改

- QA/Backend 发现通用 Upsert 错套期初与零期初/流水正数两项 P0；PJM 决定保留零期初，OPENING 允许数量 0，其他流水必须大于 0。
- PM 提交 `106a0e2`：DEL-013 v1.1、DEL-017 v1.2 完成 P0 修订，并补两根依赖链、4 类 Listing 负例与 `display_order>=1` Excel 校验。
- PJM 已验证 Backend 机器契约校验通过、6 个工作簿含 17 组数据校验、4 类负例与零期初样例存在。整改候选静态通过，但 P0/P1 仍须 QA 独立回归后关闭。
- Backend 已交 DEL-025 导入机器契约；PJM 独立复跑跨文件测试、JSON 解析与 Redocly 1.34.5，均通过。DEL-025 继续 In Review，等待 Frontend/QA 独立复核；真实实现后移。
- Frontend DEL-026 对 DEL-025 判定 0 P0 / 3 P1：上传 QUEUED const、operation 错误可达映射、diff canonical scalar/business key 收窄。PJM 确认问题成立；DEL-026 报告验收完成，DEL-025 退回 Backend 精确提交整改后复验。工作树候选不替代提交证据。
- Backend 已精确提交 DEL-025 修订 `4ebd2be`。PJM 从 commit object 隔离复跑 validator、JSON 解析、Redocly 与四项 P1 断言均通过；DEL-025 保持 In Review，等待 Frontend/QA 针对同一提交最终复验。
- Frontend DEL-027 已基于 `4ebd2be` 隔离复验通过，PJM 复跑 20/20 前端消费语义断言通过；FE-OPENAPI-P1-01～03 关闭。DEL-025/S0-CONTRACT-01 仍待 QA 终验。
- Backend 提交 `21870a8` 补充 ImportIssue Draft 2020-12 AJV 实例测试，未改变冻结机器语义。PJM 隔离执行 `npm ci`/`npm test`，正例 4/4、负例 6/6 通过；权威静态基线更新为 `106a0e2 + 4ebd2be + 21870a8`，仍待 QA 终验。
- QA 已基于权威静态基线完成最终独立回归：BE-CONTRACT-P0-01/02、BE-CONTRACT-P1-01～03、QA-CONTRACT-P1-01 全部 Pass/Closed，DEL-025 与 S0-CONTRACT-01/02 标记 Completed/Closed。该关闭只代表静态契约冻结；DEL-013/017 继续 In Review，真实样本及 API/DB/IAM/Worker 动态验收仍为 Blocked，Sprint 0 不据此提前验收。
- Backend 提交 `4e4aacb` 固化 DEL-021 真实 MySQL 8 联调证据：通过 configtree 无回显注入运行，Flyway 校验并应用 V1，Jar 启动且 `/actuator/health` 返回 HTTP 200 / UP。PJM 已核验提交范围、报告、定向 diff 与敏感模式；S0-BE-02 保持 Completed，MySQL 8 不再是其阻塞。S0-BE-01 仍等待 3 店合规样本与连续 7 天观察；DEL-018/BUG-S0-OPS-001 仍须按 Ops 生命周期门禁独立关闭。
- 联调环境已安全收尾：PJM 只读核验 18081 已关闭、ERP MySQL 为 `exited / exit_code=0`、ERP 命名卷仍存在，stable file-secret 目录为 0700、四个文件均为 0600；未读取或回显凭据。该结果关闭本次 Backend 联调收尾，不替代 DEL-018/BUG-S0-OPS-001 的完整生命周期与 QA 门禁。
