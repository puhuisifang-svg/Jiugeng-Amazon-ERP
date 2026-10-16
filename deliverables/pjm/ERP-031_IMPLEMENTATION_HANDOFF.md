# ERP-031 采购单流程完善交接

日期：2026-10-16。

## 版本与授权

- 用户要求完善实际采购流程、产品与辅料分 Tab、UI 一致、移除采购看板和收货记录菜单。
- PJM 分配 ERP-031，确认独占共享仓库及部署时段；授权从保留 ERP-030 的 `5e49e9f1463304e3ee9fbc54297e0eb6255fb81e` 创建分支。
- 分支：`feature/ERP-031-purchase-order-workflow`。
- 功能提交：`cb480eca74b7f7c988e39dad961b7f5a7ff6f50b`。
- 最终应用代码：`2ffff9ac031f57c33626b4f4a9383992e3e94d67`（前端布局微调；后端与功能提交一致）。
- 未推送、未合并 main；项目经理独立验收与合并状态另行记录。本文件为文档补充，不改变运行代码。

## 已实现

- 采购菜单仅保留采购单；旧 `/purchases`、`/purchases/receipts` 重定向采购单，历史收货记录仍在详情内。
- 产品/辅料独立 Tab，URL 保存类型；各自新建、供应商/单号/项目搜索、状态和交期筛选、逾期提示、20/50/100 分页。
- 单供应商、不得混单；供应商关联项目选择、重复项目阻止、单位和逐行金额、总金额、交期、收货信息、备注。供应商选择覆盖全部分页，异步切换忽略旧响应。
- 草稿编辑、确认下单（不自动发送供应商）、分批收货、完成、取消或关闭未收余量。提交再次验证供应商/项目供货资格。
- 收货数量不预填；用户选择实际时间与数量；超收校验，未知网络结果保留原请求号与载荷重试，离开前提示；辅料收货与库存原子提交。
- 取消必须填写原因，乐观版本锁与订单行锁保护；已收数量和库存不撤销。详情显示已关闭余量而不是待收。
- 延续系统橙色操作按钮、卡片、表格、筛选与弹窗样式；无新增采购权限。

## 文件范围

后端 `purchase/PurchaseController.java`、`PurchaseTypes.java`、`PurchaseRepository.java`、`PurchaseService.java`；新增 V24 迁移，`PurchaseControllerTest.java`。

前端 `api/purchases.ts`、`views/PurchasesView.vue`、`views/PurchaseOrderDetailView.vue` 及测试；`router/index.ts` 及测试；`layouts/AdminLayout.test.ts`；`styles/main.css`。

验收脚本 `scripts/qa/verify-purchase-http.mjs`、`verify-mysql-profile.sh`；计划 `ERP-031_PURCHASE_ORDER_WORKFLOW.md`。

## 验证结果

| 命令/验收 | 结果 |
| --- | --- |
| 后端 `./mvnw clean verify` | PASS：125 tests，0 failures/errors/skipped |
| frontend `pnpm quality` | PASS：40 files / 135 tests；lint、类型检查、生产构建通过 |
| `./scripts/qa/verify-mysql-profile.sh` | PASS：V24 迁移，采购两类真实 HTTP 流程、产品完成、辅料入库和余量关闭、MySQL/服务重启、mock 隔离 |
| `./scripts/qa/verify-mysql-backup-restore.sh` | PASS：隔离库与两类图片联合恢复，V24、计数/哈希及旧备份兼容 |
| `python3 scripts/quality/validate-mvp-scope.py` | PASS：5 imports / 27 capabilities / 3 roles / 17 API families |
| `git diff --check` | PASS |
| 部署环境 `verify-purchase-http.mjs` | PASS：权限、供货关系、产品采购收货闭环、辅料入库/取消原因/禁止继续收货 |
| 浏览器实操 | PASS：菜单只剩采购单、Tab、合成辅料草稿（10 × 2.5）、交期与地址保存、确认下单、收货4待收6、关闭余量保留已收4、详情历史 |
| 公网入口 | 旧收货路径转入采购单；登录后可见双 Tab；页面构建与本地静态服务一致 |

日志：`/private/tmp/erp031-backend-quality.log`、`erp031-frontend-quality.log`、`erp031-mysql-gate.log`、`erp031-backup-drill.log`（临时日志，不保证永久留存）。

## 部署与数据保护

- 既有 `jiugeng-mvp` 后端已重启，MySQL V23→V24 成功，8080 健康 UP。前端构建由既有 4173 静态服务读取；5173 开发服务指向权威共享仓库。
- 停写备份：`.local/backups/erp031/dashboard-20261016T095349Z.tar.gz`。
- SHA256：`9bed3d520c123fd438b06ac88256ff5fb1e1732f1654d55c0657f94a458e7c04`。
- 实际备份保留 V23 数据及图片；恢复演练在隔离合成环境进行，没有覆盖真实工作库。
- 仅新增可空字段，旧单据显示“未约定/未填写/历史单据未记录”。回滚可恢复前端与旧应用代码并保留新增字段；不得直接恢复旧备份覆盖部署后的业务数据。
- 验收账号已禁用，临时合成采购单、供货关系及其合成库存已清理；保留审计证据。未删除用户历史采购/收货记录。交接时工作区无无关修改。

## 边界与风险

- 未新增付款、审批、发票、质检、退货、多仓或成品库存；产品收货仍只是到货登记，辅料收货进入固定仓。
- 交期/收货信息可选，兼容旧草稿；采购单确认后锁定业务字段。取消接口现在要求 reason，旧客户端需随本次前端一起更新。
- 分页与搜索目前在前端完成，后端保留原采购列表 API；大量历史单据时需另做服务端分页。新建草稿遇到未知网络结果会提示先查列表，尚未新增建单幂等接口；收货已有幂等保护。
- 既有公网访问是临时隧道，不代表高可用生产托管。独立验收/合并由 PJM 记录。
