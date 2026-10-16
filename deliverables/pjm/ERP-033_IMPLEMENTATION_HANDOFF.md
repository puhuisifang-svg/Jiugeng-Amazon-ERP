# ERP-033 新品开发实施交接

日期：2026-10-16。实现分支：`feature/ERP-033-new-product-development`；基线：`433e5581d46ac3a57cf1102a9844a20c5ed05a67`。提交 SHA 由交接消息提供，本文随实现一并提交。PJM 独立复核与合并结论另行登记。

## 已实现

- [PRD v1.0](ERP-033_NEW_PRODUCT_DEVELOPMENT_PRD.md)：已读取赛狐列表/新增需求表单，未向赛狐写入数据。
- 商品 / 产品管理 / 新品开发列表、服务端筛选分页、详情编辑、来源候选快照和双向入口。复用现有 UI 与 products.read/write。
- 候选通过同步事件生成唯一草稿；行锁序列化评估/审批，唯一键防重复，同事务失败整体回滚。V25 补建历史已通过候选。
- 基本信息、商品方案、供应/打样/研究、开发周期、推荐星级、已有产品关联；数值/日期/长度校验，版本冲突保留输入，未保存离开提示。
- 草稿→进行中→完成，草稿/进行中可填写原因作废。完成需验收结论与启用产品；终态只读。审计追加不记录敏感正文。
- 正式候选页面不再无条件标识 SYNTHETIC；mock 不调用正式开发接口、不显示候选联动按钮。

主要文件：后端 `development/`、`selection/CandidateApproved.java`、`SelectionService.java`、`SelectionRepository.java`、V25 迁移及开发/迁移测试；前端 `ProductDevelopmentView.vue`、`api/development.ts`、候选两页面及路由/组件测试；范围契约与权限矩阵；MySQL HTTP 验证脚本。

## 验证结果

| 命令 / 检查 | 结果 |
| --- | --- |
| 后端 `./mvnw clean verify` | PASS，132 tests，0 failures/errors/skipped；含 Modulith 架构验证 |
| 前端 `pnpm quality` | PASS，43 test files / 151 tests；ESLint、vue-tsc、Vite build 通过 |
| `python3 scripts/quality/validate-mvp-scope.py` | PASS，5 导入类型、27 capabilities、18 API 资源族 |
| `scripts/qa/verify-mysql-profile.sh` | PASS，真实隔离 MySQL V25 迁移、开发全链路 HTTP、MySQL/后端重启、领域数据与审计持久化；监控/采购回归通过 |
| `scripts/qa/verify-mysql-backup-restore.sh` | PASS，25 migrations；数据库与两类图片备份恢复一致 |
| 指定本地正式 MySQL 执行 `node scripts/qa/verify-development-http.mjs` | PASS，候选审批/唯一草稿、权限、校验、并发版本、关联成果、完成只读 |
| `git diff --check` | PASS；仅 ERP-033 文件变更 |

测试覆盖：历史 V24→V25 回填仅 APPROVED 且重复迁移不重复；审批/淘汰竞争只有一个成功；开发写入异常回滚候选审批与审计；审批快照不随候选评估变化；三角色/匿名权限、分页参数、负数/日期、旧版本、开始/完成/作废约束；UI 筛选分页、权限只读、失败保留输入、未保存路由保护、候选入口和 mock 隔离。

## 已执行的运行环境操作（PJM 授权窗口内）

- 停止 `jiugeng-mvp:0.0` 后确认 8080 无监听，备份真实 MySQL 和两类图片；未重启 MySQL 容器，未操作其他用户会话。
- 备份：`.local/backups/erp033/dashboard-20261016T102529Z.tar.gz`，SHA-256 `dbbf3e613769ca05069178969d026e91cdd88291981be4af2b47a8657d9aabca`。归档受本地忽略规则保护，不提交。
- 使用既有 `scripts/dev/run-local-mysql-backend.sh` 启动新版；健康检查 UP；前端已重新构建，既有 Caddy / tunnel 未替换。
- 公网 `/product-development` HTTP 200，浏览器使用临时账号登录并读取新品开发列表成功。当前入口为 https://andreas-reel-proceedings-problems.trycloudflare.com/product-development ，仍属于既有临时 tunnel，不作为永久域名承诺。
- 浏览器验证了菜单商品→产品管理→新品开发，以及创建合成候选→REVIEWING→APPROVED→进入开发草稿→必填拦截→保存→开始开发→原因作废→只读和历史，公网查看已作废列表。视觉检查了列表与详情。
- 所有本轮合成候选/开发/产品 HTTP 与 UI 资料已按生成 ID 清理；临时账号停用、会话删除、凭据文件删除，安全审计保留。原业务候选未被审批或修改。测试页关闭，用户原有 Safari 页面保留。

## 风险与后置

- P0 仅现有美国站候选；开发员/采购员为文本，非组织人员分配。没有 1688/AI、自动抓取、附件、利润计算、自定义字段引擎或自动商品/采购建档。
- 只有通过候选可建需求，已完成/作废不恢复；来源候选继续可评估，但开发快照固定。完成产品之后如被停用，历史关联仍保留并显示停用。
- 回滚应用时保留 V25 与开发数据，禁止用旧备份覆盖发布后的业务数据。数据库恢复只在明确停写和人工确认的恢复流程进行。
- 本任务不修改 ERP-031/ERP-032 功能与提交。实现完成后交出独占窗口，PJM 验证 exact SHA、门禁、diff 与运行状态后记录正式验收/合并状态。
