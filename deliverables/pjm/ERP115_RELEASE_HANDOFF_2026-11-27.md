# ERP-115 累计迭代发布交接

- 项目：九更亚马逊 ERP
- 交接：Codex → PJM；状态：In Review（应用已恢复上线；发布稳定性配套待PJM提交）
- 日期：2026-11-27，Asia/Shanghai
- 用户授权：明确要求“与项目经理交接，把修改内容提交远端main，并部署线上”。提交、受保护PR合并、CI/CD及发布核验由PJM独占执行；Codex不跟踪线上发布。

## 工作区与写入权

### 恢复追加交接（2026-11-27 21:02 CST，Codex → PJM）

**授权与归属**：用户追加“协助处理、搞稳定”，PJM明确暂停仓库/服务器写入并把独占槽交给Codex。此处覆盖下方历史阻塞快照。正式交接消息接收后PJM接管，Codex停止修改，不再跟踪后续发布。

**工作区**：`fix/ERP-115-release-reliability`，基线完整SHA `b1eac83c52d9b73be65e3153b8d8e7c3fcb9d1f6`；本次工具/工作流/文档改动未提交，无无关修改。没有修改应用源码、业务数据、本地运行包或官网配置。下次接手须重查分支/status，不沿用历史写槽。

**应用恢复证据**：使用[45998170831恢复包](https://github.com/puhuisifang-svg/Jiugeng-Amazon-ERP/actions/runs/45998170831)（精确源码重建，非原失败run原包），目标 `7fed4af671a6572afc788554e9b3d1d0fe42aca9`，82,872,307字节，SHA256 `c015082ec6daafb7c30176fda2d0c8e3a7ea963f3e470fb64a12ccf1db2421e0`。经既有受限SSH从本地完整传输294秒；`Database backup completed`、`Deployment verified: 7fed4af671a6572afc788554e9b3d1d0fe42aca9`、exit 0，服务器部署日志时间 **2026-11-27T12:54:25Z**。原Production 45985818016仍失败，不冒称其成功。

**现场与外部核实**：ERP域名HTTPS及IP8081版本相同；客户信息/客户订单CSS哈希匹配包manifest。`systemctl is-active jiugeng caddy`均active，`curl -fsS --max-time 5 http://127.0.0.1:8080/actuator/health`为UP；只读SQL确认Flyway111 success=1及purchase_order_line.remark varchar(1000)可空。官网HTTPS及IP80的内容摘要不变，Caddy文件哈希不变。未执行真实业务写入验收。

**主机工具**：原脚本先保存至 `/srv/jiugeng/recovery/erp115-transport-20261127-205942`，在部署锁内安装并核对哈希；没有新增SSH密钥/账号/sudo权限。当前已安装哈希：

| 文件 | SHA256 |
| --- | --- |
| staged_upload.py | `607581d2a0ba75ca0e59fd8a95755a1e85c09d02b956ee3249da29a17aefc617` |
| receive_stream.py | `c9de4b7935b7ffc9fbdd7e57d248f378f876fb2eb3bd974abbe7902a03131f66` |
| receive.sh | `5f0afe570dbfc24472d6a165117d49bb12803f52c2d03394e5d621bcc09ab285` |
| ssh-entry.sh | `d00ae9d4fbcba32dbb2cf2de2ad1220f095759acd06d4d683267316b2ab415b0` |

**改动清单**：

- `scripts/production/staged_upload.py`、`upload_release.py`：1MiB分块长度/哈希校验、持久偏移、fsync、断点续传、独立staging锁和磁盘配额。完整包校验后才走原receive激活，不盲目重试激活。
- `scripts/production/receive.sh`、`ssh-entry.sh`：新增严格命令白名单，在全局部署锁内校验上传前expected-current；同步已有TERM子进程清理补丁。旧两参数入口保留兼容，不声称历史入口也有防倒退保护。
- `scripts/production/verify_retry_source.py`、`test_staged_upload.py`：拒绝被main新应用源码取代的旧包，允许纯文档更新；覆盖重试、防倒退、并发、损坏、截断、磁盘配额、命令注入拒绝。
- `.github/workflows/production.yml`：测试后、传输前留存原包/摘要/provenance 30天；使用续传客户端，传输前核对main应用源码。
- `.github/workflows/retry-deployment.yml`：验证同仓库main Production run/精确SHA/来源/原包，单独重试发布，不重建、不重复应用门禁；沿用production环境与并发组。
- `infra/production/README.md`、`docs/04-engineering/PROJECT_HANDOVER.md`、`docs/08-project-management/STATUS_REPORT.md`、本文件、`docs/DELIVERABLE_REGISTER.md`：同步故障原因、恢复结果、安装顺序及重试边界。

**验证命令与结果**（仓库根目录，2026-11-27）：

- `python3 -m unittest discover -s scripts/production -p 'test_*.py'`：18项通过。首次发现本机Python不支持file_digest，已改为流式哈希；新增测试的macOS /private路径断言已规范化，复验通过。
- `bash -n scripts/production/receive.sh scripts/production/ssh-entry.sh`：通过。
- `ruby -ryaml -e 'ARGV.each { |p| YAML.load_file(p); puts "YAML parsed: #{p}" }' .github/workflows/production.yml .github/workflows/retry-deployment.yml`：通过；并逐项检查两工作流run块Bash语法、内嵌Python编译。
- `git diff --check`、修改文档链接/路径及最终diff/status检查：通过。
- 真实受限SSH仅写合成传输缓存1,440,000字节：先提交1,048,576字节，故意截断下一块后返回拒绝、偏移保持；补齐续传成功；错误expected-current在停服前拒绝，线上版本不变。探针已移到上述备份目录 `transport-probe.verified`；staging剩余0包、锁自然释放。没有部署合成包或写业务数据。
- 原应用CI结果继续有效；本次未改应用源码，不重跑前后端完整门禁。PJM两轮只读审查（含防旧包覆盖新版阻断的修复复核）无新增安装阻断。

**证据与后续**：忽略目录保留 `.local/recovery/del058-45998170831/`、`.local/recovery-erp115-install.sh`、`.local/recovery-erp115-probe.json`、`.local/recovery-erp115-public-verification.json`。核心证据已同步本记录，不只存在聊天或临时日志。PJM须提交/PR合入工作流配套，未来CI才会使用新通道；不要手动重跑历史Production，网络问题优先使用新Retry tested deployment。旧无artifact运行走Recovery artifact+受控恢复。底层慢链路根因尚未精确定位，新机制解决中断重来及无原包可重试的问题，不承诺网络永不失败。独占槽正式交回后，后续提交、CI/CD与状态归档由PJM负责。

### PJM历史执行记录（2026-11-27，应用已合并、当时部署阻塞）

- 68项交接指纹一致，原子提交`ee468528f9cacc33aeac8dae78b3000045b29c0e`；后续仅修正HS用例入口，提交`3f0cc408dbfc9d7744e57da30ec887f7f8879841`。
- [PR #35](https://github.com/puhuisifang-svg/Jiugeng-Amazon-ERP/pull/35)保护合并，应用main `7fed4af671a6572afc788554e9b3d1d0fe42aca9`。[最终PR CI](https://github.com/puhuisifang-svg/Jiugeng-Amazon-ERP/actions/runs/45985366939)465后端（1跳过）、835前端、20浏览器通过。
- [Production](https://github.com/puhuisifang-svg/Jiugeng-Amazon-ERP/actions/runs/45985818016)实际deploy失败：19:08:10 CST接收600秒9797632字节触发总超时，19:08:18返回exit 1。无归档校验、备份或服务切换成功标记。V111没有生产迁移成功证据。
- 独立ERP HTTPS入口仍为`38ec537461c0ac6e13780120391bdf849f2e8449`，官网版本未变。本地服务、隔离配置与数据未动。未盲目重试或绕过锁。
- 当前无法通过管理通道检查锁与进程；待可用管理通道和经校验稳定CI产物投递路径恢复。详细证据和边界已写[状态报告](../../docs/08-project-management/STATUS_REPORT.md)。下面的未提交/待接棒描述保留为历史交接快照，不代表本节之后状态。

- 仓库：`/Users/thinkperfect/.openclaw/workspace-jiugeng_pjm/jiugeng-amazon-erp`
- 分支：`fix/ERP-115-material-save-idempotency`
- HEAD/基线：`45b7f5822da1fd9222ba3cda17d995860a4d17db`
- **实施改动全部未提交**；没有新增实施提交SHA，未暂存或推送。
- 本轮用户持续追加均并入ERP-115。最终status中的文件均属于本轮；未发现无关/他人修改。正式交接消息发送前Codex独占，消息被接收后PJM接管，Codex停止编辑、测试、重启。
- 文件内容清单：忽略目录`.local/erp115-release-manifest.sha256`；交接消息提供清单哈希供接棒时核对。清单包含本记录/状态/规范/总账及全部应用改动。

## 累计交付行为

1. 辅料编辑无变化保存置灰，后端同值更新不改版本/更新时间、不重复审计；普通保存不闪现“结果待确认”，真实未知结果保留原请求安全重试。
2. 采购辅料tab优先，供应商统一搜索，低频条件展开，日期范围与按钮对齐；列表移除多余标题/刷新/清空入口，供应商次级灰字，商品、数量与蓝色备注逐项对齐、跨列分隔线连续；修复续行备注误获固定列阴影，收紧列宽。
3. 采购物流统一到运单管理，新增/编辑、物流公司及单号复制、历史物流只读留存/首张带入、图片复制隔离；已有运单禁止继续独立写整单物流。按运单分块收货，支持分次收货且保留其他运单草稿；首页复用。权限、幂等、版本冲突、已收下界与余量校验保留。
4. 商品级备注贯穿新增/编辑、复制、详情、收货、列表、首页、历史及图片导出。输入用锚定备注控件，蓝字展示；填入余量改为白底蓝字按钮。首页采购日期替代单号，收货成功/失败反馈清楚并在模态层内可操作，关闭后成功提示保留。
5. 采购详情概览/操作/表格重排，确定高度消除逐帧收缩；历史隐藏技术字段、创建记录显示可读初始值。供应商关联采购显示采购名称，列表备注直接蓝字展示。
6. 客户、订单、工厂9入口统一列表/筛选/表单/弹窗，使用businessWorkbench.css显式屏幕命名空间；绿色保存、蓝色链接、红色删除；次要操作“…”；固定头尾、正文滚动、原生模态层、嵌套选择及未保存保护。打印不受新样式影响，权限与业务API不改变。
7. 更新UI规范、状态与接手指南，记录当前实现、验证范围、运行包与历史版本区别。

## 发布注意点

- **V111__purchase_line_remark.sql**：为purchase_order_line增加可空VARCHAR(1000)备注。仅增列，不删除/回填历史业务数据。PJM按现有流程先备份再应用迁移；前后端一同发布（新版采购列表依赖shipment-summaries接口）。
- 新运单修改/摘要接口及已有整单物流写保护需要新后端；不可只发布静态前端。
- 本地已在备份后应用V111；当前持久后端仍为`.local/releases/backend/erp115-supplier-purchase-name-20261127/backend.jar`，8080/5173与tmux `jiugeng-mvp`保留。本次完整测试另构建target产物，未重启服务。
- 本地使用生产快照副本与隔离存储。数据库、上传文件、凭据、备份、测试截图均在忽略目录，**不要提交或上传到线上，不得将本地数据库反向覆盖生产**。
- 官网、Caddy、域名/端口分流和发布工作流本轮未改，继续沿用现有配置。旧架构方案不能覆盖目前入口。
- 原存量已收货记录不回填到新运单；历史整单备注与旧物流资料保留，不虚构对应关系。

## 统一验证（本轮交接节点）

| 目录 | 命令 | 结果 |
| --- | --- | --- |
| frontend | `pnpm quality` | 通过：176文件、835测试；ESLint、vue-tsc、Vite生产构建通过 |
| deliverables/backend/sprint-0/jiugeng-erp-backend | `./mvnw clean verify` | BUILD SUCCESS：465项，464通过、0失败/错误、1跳过 |
| frontend | `pnpm exec playwright test e2e/operations-closeout.smoke.ts` | 6/6通过：辅料/供应商/采购及库存场景，三尺寸 |
| frontend | `pnpm exec eslint e2e/operations-closeout.smoke.ts` | 通过；仅测试夹具/选择器后续更新，应用门禁有效 |
| 仓库根目录 | `ERP_UI_WIDTH=<宽> ERP_UI_HEIGHT=<高> node .local/erp115-business-ui.cjs` | 1280×800、800×600、360×800均通过：9入口、无页面横向溢出、弹窗边界、嵌套选择和未保存保护；无写请求/页面异常 |
| 仓库根目录 | `ERP_UI_WIDTH=<宽> ERP_UI_HEIGHT=<高> node .local/erp115-connected-notes-ui.cjs` | 同三尺寸通过：首页日期替代单号、商品备注、连续分隔线、无备注阴影；手机按既有规则取消固定操作列 |
| 仓库根目录 | `ERP_UI_WIDTH=<宽> ERP_UI_HEIGHT=<高> node .local/erp115-waybill-unified-ui.cjs` | 同三尺寸通过：单一物流入口、运单编辑、两张运单独立收货、原始另一张草稿保留；全部合成拦截数据 |
| 仓库根目录 | `git diff --check`、维护文档链接/路径检查、最终diff/status审阅 | 通过；无无关变更 |

- 首次前端完整测试831通过/4失败：供应商备注旧展开断言1项、旧采购物流fixture缺少lines3项，均修复后完整门禁835通过。不是跳过失败测试。
- 后端跳过项为既有OssConnectivityTest外部探针（未提供OSS探针配置），不访问真实OSS。前端保留既有PDF大chunk提示，构建成功。
- 浏览器旧夹具补齐shipment-summaries，并改为当前运单文案；详情操作定位限定当前dialog，避免打开时误点背景列表同名按钮。合成收货检查明确定位反馈区，避免Toast与行内成功消息同role造成歧义。
- 已目视复核桌面/窄屏/手机相关截图；真实业务数据未写入。导入契约/官网未变化，无需对应门禁。日志与截图仅.local/frontend test-results，不入Git。
- 日志：`.local/erp115-frontend-quality-final.log`、`.local/erp115-backend-verify.log`、`.local/erp115-operations-browser-final.log`、`.local/erp115-business-matrix.log`、`.local/erp115-purchase-matrix.log`与手机最终复验`.local/erp115-purchase-mobile-final.log`。

## 文件范围

以下为本轮应用/测试/维护文件（本交接记录及总账更新另计）；均未提交：

- `deliverables/backend/sprint-0/jiugeng-erp-backend/src/main/java/com/jiugeng/erp/catalogmedia/CatalogImageGateway.java`
- `deliverables/backend/sprint-0/jiugeng-erp-backend/src/main/java/com/jiugeng/erp/catalogmedia/CatalogImageService.java`
- `deliverables/backend/sprint-0/jiugeng-erp-backend/src/main/java/com/jiugeng/erp/materialinventory/MaterialManagementController.java`
- `deliverables/backend/sprint-0/jiugeng-erp-backend/src/main/java/com/jiugeng/erp/materialinventory/MaterialService.java`
- `deliverables/backend/sprint-0/jiugeng-erp-backend/src/main/java/com/jiugeng/erp/purchase/PurchaseController.java`
- `deliverables/backend/sprint-0/jiugeng-erp-backend/src/main/java/com/jiugeng/erp/purchase/PurchaseManagementController.java`
- `deliverables/backend/sprint-0/jiugeng-erp-backend/src/main/java/com/jiugeng/erp/purchase/PurchaseRepository.java`
- `deliverables/backend/sprint-0/jiugeng-erp-backend/src/main/java/com/jiugeng/erp/purchase/PurchaseService.java`
- `deliverables/backend/sprint-0/jiugeng-erp-backend/src/main/java/com/jiugeng/erp/purchase/PurchaseTypes.java`
- `deliverables/backend/sprint-0/jiugeng-erp-backend/src/main/java/com/jiugeng/erp/supplier/SupplierManagementController.java`
- `deliverables/backend/sprint-0/jiugeng-erp-backend/src/main/resources/db/migration/V111__purchase_line_remark.sql`
- `deliverables/backend/sprint-0/jiugeng-erp-backend/src/test/java/com/jiugeng/erp/materialinventory/MaterialCloseoutTest.java`
- `deliverables/backend/sprint-0/jiugeng-erp-backend/src/test/java/com/jiugeng/erp/purchase/PurchaseControllerTest.java`
- `deliverables/backend/sprint-0/jiugeng-erp-backend/src/test/java/com/jiugeng/erp/supplier/SupplierCloseoutTest.java`
- `docs/04-engineering/PROJECT_HANDOVER.md`
- `docs/08-project-management/STATUS_REPORT.md`
- `docs/ui/UI_STANDARD.md`
- `frontend/e2e/operations-closeout.smoke.ts`
- `frontend/src/api/domain.ts`
- `frontend/src/api/purchaseManagement.ts`
- `frontend/src/api/purchases.ts`
- `frontend/src/components/CustomerLabelTemplates.vue`
- `frontend/src/components/CustomerOrderEditor.vue`
- `frontend/src/components/CustomerOrderProductPicker.vue`
- `frontend/src/components/CustomerSkuPicker.vue`
- `frontend/src/components/DashboardPurchaseReceipts.test.ts`
- `frontend/src/components/DashboardPurchaseReceipts.vue`
- `frontend/src/components/FactoryEditor.vue`
- `frontend/src/components/FactoryPicker.vue`
- `frontend/src/components/MaterialEditor.test.ts`
- `frontend/src/components/MaterialEditor.vue`
- `frontend/src/components/OrderShipmentDialog.test.ts`
- `frontend/src/components/OrderShipmentDialog.vue`
- `frontend/src/components/ProductHistoryTable.vue`
- `frontend/src/components/PurchaseLineRemarkEditor.vue`
- `frontend/src/components/PurchaseLogisticsDialog.test.ts`
- `frontend/src/components/PurchaseLogisticsDialog.vue`
- `frontend/src/components/PurchasePicker.vue`
- `frontend/src/components/PurchaseReceiptDialog.vue`
- `frontend/src/components/PurchaseReceiveFlow.test.ts`
- `frontend/src/components/PurchaseReceiveFlow.vue`
- `frontend/src/components/PurchaseShipmentsDialog.test.ts`
- `frontend/src/components/PurchaseShipmentsDialog.vue`
- `frontend/src/components/SupplierRelations.vue`
- `frontend/src/styles/businessWorkbench.css`
- `frontend/src/styles/purchaseManagement.css`
- `frontend/src/styles/supplierManagement.css`
- `frontend/src/ui/copyPurchaseLogistics.test.ts`
- `frontend/src/ui/copyPurchaseLogistics.ts`
- `frontend/src/ui/purchaseImageExport.test.ts`
- `frontend/src/ui/purchaseImageExport.ts`
- `frontend/src/utils/productHistory.ts`
- `frontend/src/utils/purchaseHistory.test.ts`
- `frontend/src/utils/purchaseHistory.ts`
- `frontend/src/views/CustomerManagementView.vue`
- `frontend/src/views/CustomerOrdersView.vue`
- `frontend/src/views/FactoryExpensesView.vue`
- `frontend/src/views/FactoryWorkbenchView.vue`
- `frontend/src/views/InvoiceManagementView.vue`
- `frontend/src/views/OrderInvoicesView.test.ts`
- `frontend/src/views/OrderInvoicesView.vue`
- `frontend/src/views/PurchaseOrderDetailView.vue`
- `frontend/src/views/PurchasesView.test.ts`
- `frontend/src/views/PurchasesView.vue`
- `frontend/src/views/SuppliersView.test.ts`
- `frontend/src/views/SuppliersView.vue`

## PJM接棒步骤

### PJM CI 修正（2026-11-27）

- 首轮 PR CI 45984519219 的后端与前端质量门禁通过，完整浏览器套件17通过、3失败：HS历史用例仍直接定位已收进“更多操作”的报关资料按钮。
- 仅更新 `frontend/e2e/customs-history.smoke.ts`，先打开当前商品的更多操作，再进入报关资料；历史核对、仅写目标商品及错误重试断言全部保留，未修改应用代码。
- 在frontend执行 `pnpm exec eslint e2e/customs-history.smoke.ts`、`pnpm exec playwright test e2e/customs-history.smoke.ts`，三尺寸3项通过（4.4秒），临时preview自动退出。本地持久服务与数据未动；后续以新提交CI为准。

1. 复核当前分支、基线、status及文件指纹；接棒后PJM独占写入。确认未包含.local、数据库、上传文件或凭据。
2. 按Conventional Commit整理原子提交，推送受保护PR并合入远端main；若有新改动使门禁失效，针对影响重新验证。
3. 按现有CI/CD执行前后端发布、迁移前备份和V111应用；独立核对实际deploy与用户入口version.json，并向用户报告发布结果。Codex不轮询发布。
4. PJM维护最终提交SHA、PR/CI/部署证据。当前仅“本地实现和统一验证”，不能把交接记录写成已部署或已业务验收。
