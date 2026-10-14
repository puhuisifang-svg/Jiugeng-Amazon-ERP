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
- Backend 后续发现实际 MySQL profile 仍引用旧密码环境变量，提交 `3f2b876` 改为显式 `configtree` + `mysql_app_password`，并增加两项配置测试；PJM 独立复跑 9/9 tests、diff 与敏感扫描通过。经 PJM 新开的最小联调窗口，Backend 再次完成真实 MySQL 8 Flyway 校验及 health HTTP 200/UP，并以 `1f174e4` 固化动态证据；应用及 ERP MySQL 随后安全停止，卷/secrets 保留，chewy healthy。本次不重复 secrets 生命周期、不轮换、不执行 `down -v`、不触碰 chewy。
- QA 已基于 `3f2b876 + 1f174e4` 完成定向终验：Maven 9/9、配置语义、动态证据与停机终态全部 PASS，P0=0/P1=0。S0-BE-02/DEL-021 继续 Completed；BUG-S0-OPS-001 不重开；DEL-020 的 3 店连续 7 天 Spike 与 GitHub/CI 外部门禁不受影响。
- 联调终态解释统一为：Backend 18081 无监听、原进程不存在，ERP MySQL `status=exited / exit=0 / running=false / oom=false / error为空`；停止态残留的 `Health.Status=unhealthy` 不作为当前服务健康判断，不得误报为运行故障。
- QA 对 `ef991ea` 的 ImportIssue 4+12 测试增强发现 1 个有效 P1：门禁仅固定 10 个 locator 夹具和非法总数，未固定两个 severity/blocking 冲突场景，等量替换后仍可通过。PJM 登记为新的 `QA-CONTRACT-P1-02`，不回退已关闭的 QA-CONTRACT-P1-01、DEL-025 或 S0-CONTRACT；仅退回 Backend 补齐 12 场景语义与防替换门禁，Frontend 无需复核。
- Backend 以 `f8a6b45` 修复 QA-CONTRACT-P1-02：12 个非法场景均有文件名与语义守卫，并执行 12/12 删除、12/12 替换变异。PJM 独立复跑标准门禁通过，治理报告也已纠正为 DEL-025 v1.1.2 Completed、仅 P1-02 In Review；现交 QA 最终增量复验。
- QA 严格从 `f8a6b45` Git object 隔离终验通过：标准门禁全绿，另对实际夹具逐项执行磁盘级删除 12/12 与保留文件名的替换/语义漂移 12/12，全部被检出、0 个漏检。PJM 正式关闭 QA-CONTRACT-P1-02；DEL-025/S0-CONTRACT 状态不变，运行时与真实样本门禁继续 Blocked。
- Backend 以单文件提交 `a219be0` 最终校准契约差异报告时序，补齐 `0e2ad43 → ef991ea → f8a6b45` 的 Completed、问题发现与关闭链。PJM 核验提交范围、祖先关系、diff 与状态措辞均正确；静态契约治理无待办。
- 联调环境已安全收尾：PJM 只读核验 18081 已关闭、ERP MySQL 为 `exited / exit_code=0`、ERP 命名卷仍存在，stable file-secret 目录为 0700、四个文件均为 0600；未读取或回显凭据。该结果关闭本次 Backend 联调收尾，不替代 DEL-018/BUG-S0-OPS-001 的完整生命周期与 QA 门禁。
- PJM 已正式要求 QA 与 Ops 按 `S0_INT_01_OPS_ACCEPTANCE_INPUT.md` §2.2 执行八步生命周期动态复验；Ops 在此之前保持服务停止并原样保留卷/secrets。禁止 `down -v`、禁止读取/回显凭据、禁止触碰 chewy；QA 明确 Pass 前 DEL-018/BUG-S0-OPS-001 均保持 In Review。
- Backend 以 `49bef4a` 提交 DEL-025 v1.1 受控增量：新增 preview 过期重验、双向批次关联并冻结 wire envelope 与全响应 `X-Request-Id`。PJM 隔离复跑 validator（6/7/8/11/21/25）、AJV 4+6、Redocly 0/0，并完成 22/22 增量语义断言，结论 PASS。v1.0 Completed 基线继续有效，v1.1 标记 In Review，等待 Frontend/QA 对同一提交增量复核；真实 API/IAM/样本仍后移。
- Frontend DEL-028 已基于 `49bef4a` 隔离完成 v1.1 增量复核：P0=0、P1=0，28/28 语义断言通过；PJM 核验报告与既有隔离结果一致，DEL-028 标记 Completed。DEL-025 v1.1 仍等待 QA 独立增量复核，不因前端单方通过提前晋升。
- Backend 以 `c364a37` 提交 DEL-025 v1.1.1：将已冻结的 OPENING `>=0` / 其他五类流水 `>0` 升级为 OpenAPI 条件 Schema，并要求库存流水 PreviewImpact 强制复用。PJM 隔离复跑 validator 6/7/8/11/21/25、AJV 4+6 与 8+6、Redocly 0/0，并完成 13/13 专项断言，结论 PASS；v1.1.1 待 Frontend/QA 对精确提交复核，运行用例继续 Blocked。
- Frontend DEL-029 已基于 `c364a37` 隔离完成 v1.1.1 增量复核：P0=0、P1=0，数量与 PreviewImpact 独立 AJV 正负用例全通过，v1.1 wire/revalidate 无回归；PJM 核验后一致标记 DEL-029 Completed。DEL-025 v1.1.1 当前为 Backend/Frontend/PJM PASS，仍待 QA；共享工作树中的未提交 v1.1.2 不纳入本结论。
- Backend 已将 v1.1.2 稳定提交为 `6a0989a`：差异报告移除活跃二选一建议并统一 OPENING 口径，机器语义补齐不可编辑、同事务、唯一流水、余额重算与整批回滚。PJM 隔离复跑标准门禁通过，15/15 不变量断言及 7/7 validator 负向变异通过；DEL-025 v1.1.2 当前为 Backend/PJM PASS，待 Frontend/QA 对精确提交增量复核，动态 IMP 用例继续 Blocked。
- QA 已按 Ops 验收输入 §2.2 完成 stable file-secret 八步生命周期最终动态复验，包括初始化幂等、三类探针、restart、stop/start、down/up、受控轮换的新认证成功/旧认证失效、最终恢复与安全停机。PJM 只读确认三个 ERP 容器 exited/0、三命名卷保留、目录/文件 0700/0600、chewy 服务健康，BUG-S0-OPS-001 正式 Closed。DEL-018 仍因 Docker Hub TLS 外部门禁 In Review；DEL-019/BUG-S0-OPS-002 仍等待 GitHub 在线证据。
- QA 以 `4c0c745` 精确提交上述八步生命周期验收报告，仅修改 1 个 QA 文件；PJM 核验提交范围、状态边界、diff 与敏感扫描通过，BUG-S0-OPS-001 的动态证据归档完成。
- Frontend DEL-030 已基于 `6a0989a` 隔离完成 v1.1.2 增量复核：P0=0、P1=0，22/22 专项与回归断言通过；PJM 核验后一致标记 DEL-030 Completed。DEL-025 v1.1.2 当前为 Backend/Frontend/PJM PASS，仍待 QA；真实 API/DB/IAM 和 IMP-010/014～018 不提前完成。
