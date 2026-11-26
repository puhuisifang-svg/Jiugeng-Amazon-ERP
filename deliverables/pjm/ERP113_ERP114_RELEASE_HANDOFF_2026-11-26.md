# ERP-113 / ERP-114 累计迭代发布交接

- 项目：九更亚马逊 ERP
- Owner：PJM；实施与本地统一验证：Codex
- 状态：In Review；本地验证通过，全部改动未提交，等待正式交接消息后 PJM 接棒
- 日期：2026-11-26，Asia/Shanghai
- 授权：用户明确要求“与项目经理交接，把修改内容提交远端main，并部署线上”。PJM 已确认接管提交、受保护PR合并、CI/CD及线上核验，Codex 不跟踪发布。

## 精确工作区与归属

- 共享仓库：`/Users/thinkperfect/.openclaw/workspace-jiugeng_pjm/jiugeng-amazon-erp`
- 分支：`fix/del056-receiver-startup-cleanup`
- HEAD / 基线：`662501194946f48272fba0c7a4912668282b55d8`
- 实施提交：**未提交**，无新增提交SHA。PJM此前明确允许在此分支承接数据同步文档和ERP-113/114，未切分支、未暂存、未推送。
- 正式交接前 Codex 独占写入，PJM暂停编辑/测试/重启。正式交接消息接受后由PJM独占；Codex停止修改，不并行提交、不等待线上发布。
- `git status --short` 全部为本轮已授权修改；无他人/未知文件、无业务数据/配置/备份入Git。最终文件内容指纹写入忽略的 `.local/erp114-release-manifest.sha256`，正式交接消息给出该清单SHA256，PJM接棒前复核。

## 最终行为

1. ERP-113采购导出图片：备注红色加粗30px，行距36px，水平/垂直居中；换行测量和绘制字号一致，长备注撑高对应行，其他列尺寸不变。
2. 辅料列表默认按档案更新时间降序、ID降序稳定分页；勾选列对齐，选中后批量采购绿色，批量删除浅红描边；移除刷新和清空勾选，库存流水入口靠右。
3. 安全库存在列表通过锚定浮层编辑，输入/单位/保存/取消同排，原单元格不变高；失败保留草稿，保存静默刷新并保持滚动，沿用精度/权限/停用及离开保护。
4. 库存弹窗只提供入库/出库新操作，移除包装换算，预计结存紧邻数量。历史盘盈盘亏与旧待确认请求原样保留；新增请求使用HTTP兼容的requestId生成器。
5. 操作原因复用EditableSelect，支持历史建议选择/删除。删除仅隐藏候选；已有流水不改动。重新使用并成功新增人工入出库会恢复该原因，幂等重放不恢复；采购收货自动原因不混入候选。
6. 独立库存流水移除顶部返回/说明卡片，辅料列显示名称。独立页和库存弹窗的采购收货单号按真实收货流水反查采购单，同页跳转保留会话，权限不足不提供链接，错误可重试。
7. 辅料详情基础资料紧凑排列，成本价两位小数；创建历史只显示初始业务值，隐藏内部ID/版本/空值和重复箭头。原有安全库存审计通过IAM公开接口合并进辅料历史，数据库统一排序/分页/计数，不复制或重写历史。`materials.read`依赖`inventory.read`，可读不等于可写。共享历史表的其他模块默认行为不变。
8. 本地数据同步记录：保留2026-11-26一次性生产快照复制到本地的文档证据和隔离说明。数据库、图片、运行配置、凭据及备份仅在忽略目录，**不得随发布上传或把本地快照导回线上**。

## 后端迁移和发布注意点

- 新迁移 `V110__inventory_reason_options.sql`：新增 `inventory_reason_hidden` 候选隐藏表；给 `purchase_receipt_line.inventory_transaction_id` 增加索引。无业务数据删除，不改既有流水。新前后端需一同发布；生产由既有流程备份后应用V110。
- 原因API：GET `/api/v1/inventory/reason-options`、POST `/api/v1/inventory/reason-options/remove`；采购关联API：GET `/api/v1/purchases/inventory-transactions/{id}/order`。采购查询留在purchase模块，未引入模块内部类型反向依赖。
- IAM `AuditHistoryLookup.HistoryScope` 扩展仅用于授权范围内的组合分页；旧单资源调用仍沿用公开入口。全后端门禁包含模块边界检查。
- 本地当前运行包 `.local/releases/backend/erp114-sort-20261126/backend.jar`，与最终应用源码一致；schema110，tmux `jiugeng-mvp`、8080/5173保留。此前本地备份在 `.local/backups/before-erp114-20261126/`；后续只换JAR的旧脚本/包均保留。
- 本地启动脚本继续保留 `ERP_STORAGE_PROVIDER=local`、禁用线上OSS与采集/仓库同步、禁用后台图片刷新等隔离项；不要复制这份本地配置到生产，也不要为了同步恢复本地生产OSS连接。
- 官网、Caddy/域名分流、发布脚本本轮无变更；沿用现有发布流程，禁止用旧方案覆盖。

## 统一验证（2026-11-26）

| 目录 | 命令 | 结果 |
| --- | --- | --- |
| frontend | `pnpm quality` | PASS：174文件、812测试，lint、vue-tsc、Vite构建通过 |
| deliverables/backend/sprint-0/jiugeng-erp-backend | `./mvnw clean verify` | BUILD SUCCESS：460项，459通过，0失败/错误，1跳过 |
| frontend | `pnpm exec playwright test e2e/operations-closeout.smoke.ts` | 原有3个场景通过；新3场景初次因测试夹具遗漏采购checks响应及把窄屏横向滚动当布局位移而失败，已修正夹具和断言 |
| frontend | `pnpm exec eslint e2e/operations-closeout.smoke.ts` | 新浏览器用例补充后通过；不改变应用代码，前述完整应用门禁仍有效 |
| frontend | `pnpm exec playwright test e2e/operations-closeout.smoke.ts --grep 'material inventory iteration'` | 新3场景通过；合计6场景覆盖1280×800、800×600、360×800 |
| 仓库根目录 | `node .local/erp113-export-review.cjs` | 合成短/长多行/空备注导出PNG，红字30px、横向中心x=1516、短备注垂直中心断言通过；目视确认长备注完整 |
| 仓库根目录 | `git diff --check`、维护文档本地链接路径检查、最终diff/status审阅 | PASS，无无关改动 |

- 后端跳过项为 `OssConnectivityTest.privateV4UploadDownloadAndCleanup`：未配置外部OSS探针，未触及真实OSS；非本轮新增跳过。
- 前端构建保留既有PDF模块大chunk提示，不阻断构建，本轮未调整PDF模块。
- 浏览器全为合成数据API夹具：选择对齐/绿色采购/红色删除、无刷新/清空、库存浮层不改变行高与纵向位置、失败/恢复单元用例、详情历史可读性、两处收货跳转、旧编辑器/采购/收货入口等。窄屏数据表保留横向滚动。
- 合成截图目视复核，覆盖三种尺寸；浏览器未验证线上业务数据。后端集成测试覆盖原因隐藏及幂等、真实采购收货关系/权限、组合历史分页及只读约束。
- 日志：`.local/erp114-handoff-frontend.log`、`.local/erp114-handoff-backend.log`、`.local/erp114-handoff-browser.log`、`.local/erp114-handoff-browser-recheck.log`。视觉产物：`frontend/test-results/` 内material-safety/history/inventory三尺寸截图及 `.local/erp113-purchase-remarks.png`。这些测试产物忽略、不提交。
- 导入契约、官网源码未变，本轮无需对应全量门禁。没有尚未解决的测试失败或实施阻塞。

## 文件清单

以下代码、测试、已有维护文档均属于本轮；另加本交接文件与 `docs/DELIVERABLE_REGISTER.md` 的登记更新：

- `deliverables/backend/sprint-0/jiugeng-erp-backend/src/main/java/com/jiugeng/erp/iam/AuditHistoryLookup.java`
- `deliverables/backend/sprint-0/jiugeng-erp-backend/src/main/java/com/jiugeng/erp/iam/AuditRepository.java`
- `deliverables/backend/sprint-0/jiugeng-erp-backend/src/main/java/com/jiugeng/erp/iam/AuditService.java`
- `deliverables/backend/sprint-0/jiugeng-erp-backend/src/main/java/com/jiugeng/erp/materialinventory/InventoryController.java`
- `deliverables/backend/sprint-0/jiugeng-erp-backend/src/main/java/com/jiugeng/erp/materialinventory/InventoryRepository.java`
- `deliverables/backend/sprint-0/jiugeng-erp-backend/src/main/java/com/jiugeng/erp/materialinventory/InventoryService.java`
- `deliverables/backend/sprint-0/jiugeng-erp-backend/src/main/java/com/jiugeng/erp/materialinventory/MaterialManagementController.java`
- `deliverables/backend/sprint-0/jiugeng-erp-backend/src/main/java/com/jiugeng/erp/purchase/PurchaseController.java`
- `deliverables/backend/sprint-0/jiugeng-erp-backend/src/main/java/com/jiugeng/erp/purchase/PurchaseRepository.java`
- `deliverables/backend/sprint-0/jiugeng-erp-backend/src/main/java/com/jiugeng/erp/purchase/PurchaseService.java`
- `deliverables/backend/sprint-0/jiugeng-erp-backend/src/main/resources/db/migration/V110__inventory_reason_options.sql`
- `deliverables/backend/sprint-0/jiugeng-erp-backend/src/test/java/com/jiugeng/erp/materialinventory/InventoryControllerTest.java`
- `deliverables/backend/sprint-0/jiugeng-erp-backend/src/test/java/com/jiugeng/erp/materialinventory/MaterialCloseoutTest.java`
- `deliverables/backend/sprint-0/jiugeng-erp-backend/src/test/java/com/jiugeng/erp/purchase/PurchaseControllerTest.java`
- `docs/04-engineering/PROJECT_HANDOVER.md`
- `docs/08-project-management/STATUS_REPORT.md`
- `docs/ui/UI_STANDARD.md`
- `frontend/e2e/operations-closeout.smoke.ts`
- `frontend/src/api/inventoryOptions.ts`
- `frontend/src/api/purchases.ts`
- `frontend/src/components/InventoryReason.test.ts`
- `frontend/src/components/InventoryReason.vue`
- `frontend/src/components/MaterialDetailDialog.vue`
- `frontend/src/components/MaterialInventoryDialog.test.ts`
- `frontend/src/components/MaterialInventoryDialog.vue`
- `frontend/src/components/ProductHistoryTable.test.ts`
- `frontend/src/components/ProductHistoryTable.vue`
- `frontend/src/styles/materialManagement.css`
- `frontend/src/ui/purchaseImageExport.ts`
- `frontend/src/utils/materialHistory.test.ts`
- `frontend/src/utils/materialHistory.ts`
- `frontend/src/utils/productHistory.ts`
- `frontend/src/views/InventoryLedgerView.test.ts`
- `frontend/src/views/InventoryLedgerView.vue`
- `frontend/src/views/MaterialsManagement.vue`
- `frontend/src/views/MaterialsView.test.ts`

## PJM 下一步

1. 接受正式交接后复核分支/基线/内容指纹和完整diff，接回独占写入权。若origin/main已变化，按现有受保护PR流程整理分支，不能覆盖共享未提交改动。
2. 原子提交本轮变更并推进受保护PR合并到main；按既有CI/CD发布前后端和V110。无需再次向用户询问发布授权。
3. PJM负责实际deploy步骤、目标站点版本及必要健康核验，并向用户报告结果；Codex不轮询或等待。当前只确认实现/本地验证/交接，不宣称已合并、已部署或业务验收。
4. 后续发布证据由PJM更新；本次验证不要求反复生成“最终文档SHA”并为纯归档无限追加发布。保留本地持久服务与隔离数据，不清理用户本地数据。

## PJM发布执行（2026-11-26）

- 38项交接指纹全部匹配、门禁日志核实；后端静态复核无阻断。未重复有效的本地全套门禁，未改本地数据/服务/隔离项。
- 原子提交 `abfd2f6d51d551e0cc03721d822e6cc3dbf50e5b`，通过 [PR #33](https://github.com/puhuisifang-svg/Jiugeng-Amazon-ERP/pull/33) 受保护合并；应用main `38ec537461c0ac6e13780120391bdf849f2e8449`。
- [PR CI](https://github.com/puhuisifang-svg/Jiugeng-Amazon-ERP/actions/runs/45846449230) 成功：后端460项、零失败/错误、1条件OSS跳过，前端174文件812测试，浏览器20项通过。
- [Production](https://github.com/puhuisifang-svg/Jiugeng-Amazon-ERP/actions/runs/45847167342) 自动启动并构建精确main；实际部署结果待核验，不提前声称备份/迁移/切换通过。
- 既有服务器180秒空闲/600秒总接收限制及进度日志有安装证据；最后的启动窗口TERM/KILL修正已随本PR合入，但未收到主机补同步回执，不宣称最终脚本已安装。

### 最终应用发布证据

- 上述Production最终success，实际deploy非skipped。北京时间23:21:00接收82,844,677字节完成，耗时69秒；23:21:06数据库备份完成；23:21:35 `Deployment verified: 38ec537461c0ac6e13780120391bdf849f2e8449`，脚本后端健康和内部前端版本验证通过。
- PJM独立读取ERP HTTPS `/version.json`：HTTP200，匹配上述应用main；官网HTTP200，摘要保持 `ae09c183535a6ecfd9e432ba87cb7c073d3e37b109c4e2846b81451cbe784d5a`。未改官网分流、本地隔离配置或服务。
- [应用main CI](https://github.com/puhuisifang-svg/Jiugeng-Amazon-ERP/actions/runs/45847167358) 通过。V110有精确构建、生产备份和启动校验的间接证据；没有生产Flyway逐行查询证据，不以本地schema110替代。
- 日志 `/tmp/del057-production.log`；本归档为纯文档，不触发新ERP发布，最终文档main与应用版本可不同。此前“待核验”保留为历史阶段，由本节覆盖。
