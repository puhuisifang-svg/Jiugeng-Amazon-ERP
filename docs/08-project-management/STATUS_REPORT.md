# 九更 ERP 当前状态报告

| 字段 | 内容 |
| --- | --- |
| 项目 | 九更亚马逊 ERP |
| Owner | PJM（维护责任）；Codex（本次事实整理） |
| 版本 / 状态 | v0.2 / Active（持续维护；本次修订待 PJM 复核） |
| 更新时间 / 时区 | 2026-11-27 / Asia/Shanghai |
| 审批人 | 用户已授权 2026-11-27 ERP-115累计迭代提交main并部署；PJM执行，不代表业务验收 |
| 证据范围 | 仓库代码、GitHub Production 记录、用户实际访问入口的公开前端版本及静态资源 |

本报告是带日期的快照，不是实时监控。接手时重新检查 HEAD、工作区及当前任务归属；发布状态由 PJM 维护，Codex 不跟踪线上发布。[2026-10-15 原报告](archive/STATUS_REPORT_2026-10-15.md)已归档，保留原始事实及当时风险，不再作为今天的待办清单。




## PJM 归档与 ERP-116 写入权交接（2026-11-27）

- 稳定性配套已由 [PR #38](https://github.com/puhuisifang-svg/Jiugeng-Amazon-ERP/pull/38) 受保护合入 `c6438152c48fe57214d11dc8cd1046d4d44b90ee`；[该 SHA 的 CI](https://github.com/puhuisifang-svg/Jiugeng-Amazon-ERP/actions/runs/46004921654) 成功。下面“仓库配套尚未提交”为历史交接快照。
- 本次核查 [新 Production](https://github.com/puhuisifang-svg/Jiugeng-Amazon-ERP/actions/runs/46004921599) 仍在运行，尚未核验该 SHA 实际上线；应用恢复成功证据仍见下节，不改写原失败 run 的结论。后续发布核验仍由 PJM 负责，不要求 Codex 等待。
- 用户授权继续产品成本本地开发，分配 **ERP-116｜产品成本父子体展示与成本合计**。从上述最新 main 建立 `feature/ERP-116-product-cost-summary`，在工作区干净后将独占写入权交给 Codex；PJM 不并行编辑、测试、重启或切分支。
- 范围：成本列表按产品管理父体/子体组织，明确成本合计及计算口径；核对单位成本与版本状态的用途并简化展示，但保留成本版本、并发及历史业务保护。本轮仅本地实现与验证，未授权提交、推送或发布。保留本地数据、持久服务及 OSS/采集隔离配置。

## ERP-115 发布恢复与传输稳定性（2026-11-27 21:02 CST）

用户追加授权Codex协助恢复并提高稳定性；PJM明确释放本地/服务器独占槽并仅只读审查。本节覆盖下面早期“部署阻塞”快照，业务验收仍未执行。

- **应用已恢复上线**：恢复包来自[Recovery artifact 45998170831](https://github.com/puhuisifang-svg/Jiugeng-Amazon-ERP/actions/runs/45998170831)，源码为已通过CI的 `7fed4af671a6572afc788554e9b3d1d0fe42aca9`；这是精确源码重建包，不是失败run原包。82,872,307字节，SHA256 `c015082ec6daafb7c30176fda2d0c8e3a7ea963f3e470fb64a12ccf1db2421e0`。
- 通过既有受限SSH身份从本地投递，294秒完整接收并校验，随后 `Database backup completed`；服务器记录 `2026-11-27T12:54:25Z ...7fed4af...`，接收器输出完整 `Deployment verified` SHA，exit 0。原失败Production仍是失败，未改写工作流结论。
- ERP域名HTTPS与IP的8081入口 `/version.json` 均200且等于上述SHA；客户信息/客户订单CSS与恢复包manifest哈希一致。服务器 `jiugeng`、`caddy` active，健康UP；Flyway111 success=1，采购行remark为可空varchar(1000)。官网域名及IP80内容摘要仍为 `ae09c183535a6ecfd9e432ba87cb7c073d3e37b109c4e2846b81451cbe784d5a`；Caddy、官网、生产业务数据与本地服务未另行调整。
- 失败主因已确认在整包传输：旧路径600秒仅收到9,797,632字节，尚未停服；本地路径同一恢复包约294秒传完。底层慢链路成因未定，不笼统归咎缓存或部署锁。
- 稳定性修复：1MiB分块校验/持久偏移/自动续传；网络上传不持有部署锁；原备份、迁移、完整包校验及健康保护继续执行。新入口在部署锁内核对上传前服务器版本；重试工作流拒绝应用源码已被main替代的旧包，允许纯文档推进。
- 新主机脚本已备份安装到原root目录，备份目录 `/srv/jiugeng/recovery/erp115-transport-20261127-205942`；无新增SSH身份或sudo权限。真实受限SSH合成1,440,000字节探针验证：截断分块拒绝、1,048,576偏移保留、续传完成、错误expected-current拒绝且线上版本不变。探针已移到备份目录归档，staging剩余0包。
- 仓库配套尚未提交：分支 `fix/ERP-115-release-reliability`，基线 `b1eac83c52d9b73be65e3153b8d8e7c3fcb9d1f6`。Production留存30天原包/哈希/来源记录；新增 `retry-deployment.yml` 只重试已测试原包，无需重建/重跑应用门禁。PJM两轮只读审查后无安装阻断；**须由PJM提交/合入工作流后，未来CI才使用新传输**。旧二参deploy入口仅保留兼容，不能宣称全部历史入口都有防倒退检查。
- 定向验证：`python3 -m unittest discover -s scripts/production -p 'test_*.py'` 18项通过；Shell语法、两工作流YAML及内嵌脚本语法、`git diff --check`通过。未改应用源码，复用原CI，不重复完整前后端门禁。详见[交接追加记录](../../deliverables/pjm/ERP115_RELEASE_HANDOFF_2026-11-27.md)。

## ERP-115 PJM发布结果（2026-11-27，历史部署阻塞）

恢复准备追加：新增独立手动`recovery-artifact.yml`，仅在main运行，核验精确源码SHA属于main且同仓库该SHA的push CI成功后，仅重建/安全解包验证并留存私有Actions产物（包、SHA256、provenance，30天）。不使用生产凭据、不部署、不重跑已有效应用测试。该workflow路径不触发Production，未修改原发布工作流。目标应用SHA仍为`7fed4af671a6572afc788554e9b3d1d0fe42aca9`，原CI证据45985818018；新的重建包不冒充失败run原包。服务器进程/锁及稳定投递仍需可用管理通道，不得据产物准备完成宣称上线。

- 原子提交 `ee468528f9cacc33aeac8dae78b3000045b29c0e`，68项文件与交接清单逐一吻合。HS浏览器旧操作入口修正提交 `3f0cc408dbfc9d7744e57da30ec887f7f8879841`，只改用例及交接说明。
- [PR #35](https://github.com/puhuisifang-svg/Jiugeng-Amazon-ERP/pull/35) 五项保护检查通过并合入应用main `7fed4af671a6572afc788554e9b3d1d0fe42aca9`。[PR CI](https://github.com/puhuisifang-svg/Jiugeng-Amazon-ERP/actions/runs/45985366939)后端465项（1条件跳过）、前端835项、完整浏览器20项通过；[main CI](https://github.com/puhuisifang-svg/Jiugeng-Amazon-ERP/actions/runs/45985818018)成功。
- [Production](https://github.com/puhuisifang-svg/Jiugeng-Amazon-ERP/actions/runs/45985818016)应用、浏览器、导入契约及精确SHA打包通过，但实际deploy失败（非skipped）：2026-11-27 19:08:10 CST记录`Upload total timeout: bytes=9797632`，接收600秒约9.34MiB，平均约16KiB/s；19:08:18返回exit 1。没有出现归档校验、生产备份或部署验证成功标记。按接收器顺序，失败发生在停服/备份/激活之前；V111无生产应用成功证据。
- 失败后独立HTTPS入口仍返回 `38ec537461c0ac6e13780120391bdf849f2e8449`；官网版本摘要仍为 `ae09c183535a6ecfd9e432ba87cb7c073d3e37b109c4e2846b81451cbe784d5a`。没有修改官网/Caddy分流、本地tmux或数据隔离，没有回传本地数据库或文件。不能称本轮已上线，未进行生产业务验收。
- 当前无活动Production；本机SSH agent无身份、保护凭据库为空，本轮工具没有可操作的Safari原生CUA；无法直接确认服务器锁/进程和健康。超时返回不等于已现场确认锁释放。未盲目重跑、删锁或延长时限。
- 恢复条件：通过可用的已授权服务器管理通道检查接收进程、锁及当前版本；准备由CI精确SHA构建且校验过的发布包，经稳定路径投递，再沿用备份/迁移/健康/版本保护。该run仅保留扫描报告，未上传release包artifact；不能以本地运行JAR替代。传输慢已证实，底层网络原因未确定。
- 证据本地保留：`/tmp/erp115-ci-failure.log`、`/tmp/erp115-pr-ci.log`、`/tmp/erp115-production-failed.log`。后续PJM继续负责恢复，Codex不等待发布。

## ERP-115 发布交接（2026-11-27，历史交接节点）

用户已明确授权本轮累计修改提交远端main并部署。详见[最终交接记录](../../deliverables/pjm/ERP115_RELEASE_HANDOFF_2026-11-27.md)。分支 `fix/ERP-115-material-save-idempotency`，基线 `45b7f5822da1fd9222ba3cda17d995860a4d17db`；所有实施修改仍未提交，正式交接发送后由PJM独占提交/合并/发布，Codex停止编辑并不跟踪发布。

统一验证完成：前端`pnpm quality`176文件835测试通过、类型/构建通过；后端`./mvnw clean verify`465项中464通过、1既有OSS探针跳过；`pnpm exec playwright test e2e/operations-closeout.smoke.ts`6场景通过；客户/订单/工厂9入口及采购列表/运单收货三尺寸合成检查通过。旧测试夹具/交互断言已修正并复验，无未解决测试失败。V111为采购行新增可空备注列，前后端须一同发布，先备份；本地数据/文件不得回传生产。官网/Caddy无修改。

文档路径及`git diff --check`通过，变更文件均本轮所有。日志、完整命令、边界及清单见交接。此处只记录本地验证与交接，不代表已合入、已部署或业务验收；下面“尚未授权/待统一验证”均为早期实施快照，以本节为准。

## ERP-115 辅料无改动保存与确认提示（2026-11-27，本地已修复）

### 最新追加：客户、订单、工厂 UI 统一（2026-11-27，本地）

- 用户授权将客户信息、客户商品、发票管理（含历史台账）、客户订单，以及工厂员工、工价、报工、工资、其他开销共9个入口纳入当前ERP-115迭代；分支仍为 `fix/ERP-115-material-save-idempotency`，基线 `45b7f5822da1fd9222ba3cda17d995860a4d17db`。本次仅前端与维护文档，未提交、未发布、未重启后端或修改数据库。
- 新增 `frontend/src/styles/businessWorkbench.css`，显式限定business-workbench/business-dialog并仅作用屏幕：筛选32px、表单40px、紧凑表格、统一状态/金额/备注；新建保存绿色，查询与链接蓝色，删除红色。客户与工厂行内次要动作收进共享“…”菜单，保留原权限（只读客户仍可直达客户商品）。订单低频筛选可展开，隐藏不清除原条件。
- 客户/历史发票表单固定标题与操作区，正文滚动；订单编辑使用原生模态层，订单操作、物流、发票和安排待发货统一AccessDialog，保留原提交、错误及未保存保护。选择器/工厂详情采用确定高度避免TableViewport测量与自动高度相互收缩；工厂父体筛选替换遗漏的原生select。订单发票抬头以资料网格展示，工厂字段/按钮对齐，附件列表紧凑化；打印不受新样式影响。
- 必要局部验证：`pnpm --dir frontend exec vue-tsc --noEmit`通过；涉及的13个Vue文件及2个测试文件以 `pnpm --dir frontend exec eslint src/components/CustomerLabelTemplates.vue src/components/CustomerOrderEditor.vue src/components/CustomerOrderProductPicker.vue src/components/CustomerSkuPicker.vue src/components/FactoryEditor.vue src/components/FactoryPicker.vue src/components/OrderShipmentDialog.vue src/components/OrderShipmentDialog.test.ts src/views/CustomerManagementView.vue src/views/CustomerOrdersView.vue src/views/FactoryExpensesView.vue src/views/FactoryWorkbenchView.vue src/views/InvoiceManagementView.vue src/views/OrderInvoicesView.vue src/views/OrderInvoicesView.test.ts --fix` 检查通过。`pnpm exec vitest run src/views/OrderInvoicesView.test.ts src/components/CustomerOrderEditor.test.ts src/components/OrderShipmentDialog.test.ts src/views/CustomerManagementView.test.ts` 首次27通过/4失败，3项为新原生弹窗的jsdom缺少showModal模拟，1项发现只读菜单入口变化；已补模拟并恢复只读直链。随后 `pnpm --dir frontend exec vitest run src/components/OrderShipmentDialog.test.ts src/views/CustomerManagementView.test.ts` 22/22通过，与未改动的发票/订单编辑9项合计31项通过。发票Esc测试改为原生cancel事件，验证继续填写/放弃关闭。
- `node .local/erp115-business-ui.cjs`：1440×1000 Chromium合成数据，9入口弹窗边界、订单嵌套选择、发票未保存退出保护通过，写请求0、页面异常0；截图已局部目视复核。最终样式复核后重跑该脚本通过，`git diff --check`通过。完整前后端门禁、累计回归和视口矩阵继续留待用户要求PJM交接时统一执行；本次不是完整业务验收。



采购列表列宽收紧（2026-11-27）：仅采购管理主表调整商品目标220px、备注110px、物流140px，并缩小商品图文间距；移除商品内部原280px固定宽度，保持48px缩略图、长备注/物流换行及原商品标题提示。首页及编辑表不受影响。局部`git diff --check`通过，视觉统一复核留待ERP-115交接，未提交/发布。

备注列阴影修复（2026-11-27）：商品分行后，续行备注为DOM最后单元格，误命中ui-action-table通用last-child固定操作列规则。已在purchaseManagement.css限定当前分组表：末格不是table-actions时取消sticky/right/z-index/box-shadow，真实操作列仍固定。`node .local/erp115-connected-notes-ui.cjs`追加检查所有备注均static且无阴影、操作列仍sticky，通过；原连续边线检查通过、页面异常0，`git diff --check`通过。仅本地样式修复，完整验证仍并入ERP-115交接。

供应商列表备注直显（2026-11-27）：名称/分类下方原“查看备注”替换为蓝色纯文本“备注：内容”，无点击交互，保留换行与长文本折行；空备注不占行，删除展开状态及额外备注行。仅前端展示，沿用ERP-115本地迭代；ESLint与`git diff --check`通过，视觉复核并入交接统一验证，未提交或发布。

### 最新追加：采购列表商品备注与连续分隔线（2026-11-27，本地）

- 采购管理列表在数量/单位后增加蓝色商品备注；首页首列改为采购日期/供应商，日期保留进入采购详情的链接，删除独立日期列和备注中重复的品名。
- 两处从单元格内各自堆叠商品改为每商品一行、订单级信息rowspan合并；商品/数量/备注共享行高与连续底边，长备注换行仍能对应商品。按订单保持交替底色，保留采购列表展开/收起与首页前两项摘要、空明细占位及历史整单备注。
- 局部样式故障检查：`node .local/erp115-connected-notes-ui.cjs`通过，合成两商品数据核对日期替换单号、6列表头、采购备注位置、相邻列底边高度及无间隙，页面异常0；两处截图已目视检查。相关Vue文件ESLint、`git diff --check`通过。更新首页现有测试断言，统一测试执行仍留待ERP-115交接；未写业务数据、未重启后端、未提交或发布。

### 最新追加：首页采购日期、备注与收货反馈（2026-11-27，本地）

- 首页采购待接收新增采购日期（订单创建时间按北京时间显示日期）及备注列；商品备注蓝字保留换行，与商品摘要同取前两项，多商品标注品名；历史整单备注另行保留。复用现有接口，无后端或迁移变更。
- PurchaseReceiveFlow成功反馈明确登记项数，重放请求提示未重复入账；确定失败显示红色原因并保留数量，超时等未知结果保留原请求供安全重试。弹窗打开时Toast位于当前模态弹窗子树，关闭后由首页/采购详情承接成功消息，避免随组件销毁丢失。刷新期间保留原弹窗，禁用重复提交；最新数据读取失败明确显示重试入口。
- 局部检查：在frontend执行`pnpm exec vitest run src/components/DashboardPurchaseReceipts.test.ts src/components/PurchaseReceiveFlow.test.ts`，13项通过；`pnpm exec vue-tsc --noEmit`通过。相关文件ESLint及`git diff --check`通过。
- `node .local/erp115-dashboard-feedback-ui.cjs`使用合成拦截数据检查日期、商品备注、失败提示可关闭且保留输入、重试成功后首页保留绿色提示，页面异常0；两张.local截图已目视检查。浏览器检查发现放在模态窗外的Toast受inert影响，已移入当前弹窗子树后复核通过。未写真实业务数据。
- 仍在ERP-115独占写槽，未提交/推送/发布；本地后端未重启，继续使用上一节供应商采购名称运行包。累计完整门禁与视口矩阵留待用户要求项目经理交接时执行。

### 最新追加：供应商关联采购名称（2026-11-27，本地）

- 继续ERP-115本地迭代，用户要求供应商详情→关联业务→采购单显示采购名称。SupplierManagementController关联分页接口新增可选purchaseName，取采购行保存的首项品名；多行显示“首项品名 等 N 项”，无明细历史单回退“未命名采购”。在当前页查询内读取名称与项数，不逐单发请求；采购查看权限检查仍在查询前。
- SupplierRelations采购记录以名称为主、单号置于下方灰字，状态放右侧与查看操作同列；保留现有跳转。接口原code/name（单号/状态）语义不变，产品与辅料关联不增加采购名称。前端兼容旧接口无purchaseName时回退单号。
- 局部检查：`./mvnw -q '-Dtest=SupplierCloseoutTest#purchaseRelationsIncludeStoredItemNamesAndKeepOrderIdentity+relationPagesEnforceDomainReadPermissions' package` 2项通过（空/单/多明细名称、单号状态保留与无采购权限拒绝）；初次合成单号长度超过32被约束拒绝，缩短fixture后通过。`pnpm exec eslint src/components/SupplierRelations.vue src/api/domain.ts --fix`、`pnpm exec vue-tsc --noEmit`、`git diff --check`通过。
- 无新数据库迁移；保留此前V111、本地数据及上传文件。运行包更新至`.local/releases/backend/erp115-supplier-purchase-name-20261127/backend.jar`，旧JAR与启动脚本保留；tmux持久服务重启后`curl --noproxy '*' --max-time 2 -fsS http://127.0.0.1:8080/actuator/health`返回UP。完整回归及视觉验收仍在用户要求交接时统一执行，未提交或部署线上。

收货商品备注追加（2026-11-27）：普通确认采购收货PurchaseReceiptDialog及按运单收货PurchaseReceiveFlow均在“待收”后、“本次实收”前新增只读商品备注列，显示已有line.remark，蓝字、保留换行、空值显示“—”；与底部本次收货备注分开。沿用现有接口，无数据迁移和收货行为变化。窄屏表格内部横向滚动。局部格式与`git diff --check`检查通过，视觉及完整验证留到ERP-115交接，未提交或发布。

### 最新追加：采购详情排版与高度反馈修复（2026-11-27，本地）

- 用户反馈详情列表缓慢收缩。合成数据浏览器复现35帧：dialog 881→815px、table 538→472px，每帧约缩小2px；原因是TableViewport按随内容变化的dialog边界测量，再反过来改变dialog高度，属于ResizeObserver布局反馈而非CSS动画。
- 仅为采购详情增加专用固定可用高度（最大800px、视口减24px）与flex内容滚动布局；表格在剩余空间滚动，固定标题/关闭按钮与底栏，不改全局TableViewport算法。短屏回退弹窗内容滚动，保留窄屏操作按钮与表格横向滚动。
- 概览集中订单号/状态、灰色下划线供应商及采购总额；基础资料紧凑排列，空整单备注不占行；页签与操作同排，绿色收货、白底蓝字运单、浅红关闭余量；采购/已收/待收拆列，数值右对齐，备注蓝色，图片保留72px与原预览。刷新按钮保留但降为次级文字；有已载入内容时不插入加载文字撑动布局。
- 检查：`pnpm exec eslint src/views/PurchasesView.vue src/views/PurchaseOrderDetailView.vue --fix`、`pnpm exec vue-tsc --noEmit`、`git diff --check`通过；`pnpm exec vitest run src/views/PurchaseOrderDetailView.test.ts` 4项通过。
- 针对布局故障执行 `node .local/erp115-detail-layout-ui.cjs`：1440×1000合成数据，35帧中dialog固定800px、table固定469.5px；历史/明细切换高度保持，历史分页可见，无页面异常；截图`.local/erp115-purchase-detail-after.png`已目视检查。历史fixture初次误用操作编码导致脚本等待，修正为实际PURCHASE_ORDER_CREATE后通过；未触碰业务数据。
- 分支仍为ERP-115原分支，本次3个前端文件及文档均本轮所有，未提交/发布。完整门禁与视口矩阵继续留待用户要求交接时统一执行，无本地后端重启。

商品备注颜色追加（2026-11-27）：新增/编辑采购单的商品备注正文改为与“选择项目”一致的蓝色 `#2864c5`；仅调整显示颜色，局部 diff 检查通过，视觉验证并入迭代交接。

### 最新追加：填入数量改为按钮（2026-11-27，本地）

用户要求管理运单“填入全部剩余数量”和首页待接收“填入本运单待收数量”改成白底蓝字按钮。已在PurchaseShipmentsDialog和共享PurchaseReceiveFlow统一为32px浅蓝边框次级按钮，移除原下划线，保留原填入逻辑、禁用保护、悬停与焦点状态。仍属ERP-115本地未提交迭代，仅样式变更，`git diff --check`通过；未重复运行应用测试，视觉复核并入交接统一验证。

### 最新追加：商品备注与收货排版（2026-11-27，本地）

继续 ERP-115 独占写槽，分支与基线不变；本轮所有修改未提交、未推送、未发布。

- 用户确认按采购商品分别填写备注。V111 为 purchase_order_line 增加可空 remark（1000 字）；新增/编辑明细在金额前增加备注列，复用上新监控的铅笔与320px锚定浮窗交互，确定后写入采购草稿，整单保存时提交。详情增加商品备注列；编辑、再次采购、审计、CSV及图片导出保留商品备注。历史整单备注不回填为各商品备注，继续保留并在导出中附加，旧数据无需重写。
- 正常保存仅显示“保存中”，pendingSave在请求未结束时不显示黄色待确认提示；真正超时仍锁定并恢复原请求，保留幂等保护。采购保存使用现有createRequestId以兼容HTTP入口。
- 运单复制为“物流商 单号”，按钮更名“复制物流信息”；HTTP无Clipboard API时使用当前弹窗内文本复制回退，失败仍明确提示。
- 首页和采购共用收货弹窗：数量列固定并右对齐，日期及备注控件统一40px，起止边界对齐；“填入本运单待收数量”加下划线。
- 局部验证：后端 `./mvnw -q -Dtest=PurchaseControllerTest#itemRemarksPersistIndependentlyThroughEditCopyAuditAndExport package` 1项通过，验证独立备注、修改清空、复制、历史/导出、长度限制；前端 `pnpm exec vue-tsc --noEmit` 通过；`pnpm exec vitest run src/views/PurchasesView.test.ts src/components/PurchaseShipmentsDialog.test.ts src/ui/purchaseImageExport.test.ts` 23项通过；复制回退追加 `pnpm exec vitest run src/ui/copyPurchaseLogistics.test.ts src/components/PurchaseShipmentsDialog.test.ts` 9项通过。复制测试初次因DOM测试环境无clipboard属性失败，修正测试注入后通过。
- 合成数据浏览器检查：`node .local/erp115-receipt-align-ui.cjs` 日期/备注均40px、顶部差0、填入链接有下划线、无页面异常；`node .local/erp115-item-remark-ui.cjs` 浮窗宽320px、确认后草稿保留、无页面异常。截图在.local，已目视复核。首轮备注脚本误用不存在的menu角色定位，改为现有菜单选择器后通过，未写业务数据。
- 本地迁移前已备份MySQL（hex-blob转储、gzip校验）及启动脚本到 `.local/backups/before-erp115-item-remarks-20261127/`，旧JAR保留。当前持久服务为 `.local/releases/backend/erp115-item-remarks-20261127/backend.jar`，tmux `jiugeng-mvp:0.0`；数据库V111 success=1、remark列存在，8080 health返回UP。此前运单运行包记录属于历史状态。
- 完整前后端门禁、累计跨模块回归和视口矩阵仍待用户要求交接时统一执行；本次局部检查不代表全迭代验收。

### 最新追加：统一运单与按运单收货（2026-11-27，本地）

最终本地运行核验：8080进程指向 `erp115-waybills-images-20261127/backend.jar`，`curl --noproxy '*' --max-time 2 -fsS http://127.0.0.1:8080/actuator/health` 返回 `{"status":"UP"}`。首次respawn时旧pane仍在退出，确认其已dead且端口空闲后重启成功；无竞争实例。

PJM确认继续并入ERP-115跨前后端范围，Codex独占写槽，累计28个变更文件均本轮所有；没有新的提交/发布授权。下方“填写物流/批次独立入口”属于已被本次方案替代的实施历史。

- 采购列表只保留“管理运单”，通过一次批量摘要请求展示当前页真实运单，详情入口也统一。每张运单绑定商品、发货数量及已收数量；可修改运单资料和分配数量。修改不能低于已收数量、超过扣除其他运单后的可分配数量、移除已收商品；重复单号禁止重新登记。后端保留订单行锁、订单/运单版本核对，修改请求使用既有purchase_write_request表幂等，不新增迁移。
- 已有运单后，旧PUT单据物流接口返回409 PURCHASE_UNIFIED_LOGISTICS_REQUIRED，避免旧客户端继续写独立物流。历史单据物流保留只读；无运单时首次登记带入旧资料，由用户明确分配数量。历史图片经CatalogImageGateway.copyOrderWaybill验证原订单归属后复制独立副本，原图/原引用保留，重试不会重复复制。已有收货不回填、不重复入库。
- PurchaseReceiveFlow取消批次下拉，所有运单直接分块；每块独立填写数量、时间、备注并确认。一次收货只提交该运单；成功后刷新进度、保留其他草稿，未完成订单继续留在弹窗。未知结果只允许重试对应原请求，其他块暂时锁定；权限、超量、单位、并发和库存事务仍由后端校验。首页通过同一组件更新待接收列表，采购详情同步刷新。无运单订单保留直接实物收货路径；已有运单但尚未分配的余量需登记对应运单后接收。
- 后端 `./mvnw -q -Dtest=PurchaseControllerTest package`：28项通过，覆盖旧物流写入阻断、重复单号、已收下界/其他运单占用、编辑与收货幂等、摘要接口、权限、历史图片独立复制及原引用保留。旧测试将第二张运单复用同一单号的夹具改为不同单号，超量用例仍有效。
- 前端 `pnpm exec vitest run src/components/PurchaseReceiveFlow.test.ts src/components/PurchaseShipmentsDialog.test.ts src/components/DashboardPurchaseReceipts.test.ts src/views/PurchasesView.test.ts`：27项通过；搜索测试改为触发表单submit（脱离DOM的测试挂载不自动触发默认submit），包含两运单隔离、其他草稿保留、原请求恢复/锁定、编辑已收下界等。相关10个文件ESLint及 `pnpm exec vue-tsc --noEmit`通过。
- `node .local/erp115-waybill-unified-ui.cjs` 使用本地拦截合成数据，核对单一入口、编辑既有运单、两块收货且无下拉、分别提交2/3数量、另一草稿保留，合计收5、页面异常0；`.local/erp115-unified-manager.png`、`.local/erp115-waybill-receive.png`已目视检查。未拿真实业务单做收货测试。
- 当前本地运行包 `.local/releases/backend/erp115-waybills-images-20261127/backend.jar`；原包与 `.local/backups/before-erp115-waybill-images-20261127/run-backend.sh`保留，启动配置只换JAR，数据隔离不变，无数据库迁移。受影响完整门禁/全宽度矩阵仍等用户交接统一执行；未提交/推送/部署。

采购历史与发货批次追加（2026-11-27，本地实现）：新增 `frontend/src/utils/purchaseHistory.ts` 业务白名单格式化，采购详情通过共享历史表的purchase模式使用；隐藏ID、请求标识、版本及冗余PO编号，创建只展示非空业务资料，真实修改逐字段显示前后值，商品数量/单价独立对比，新增/删除商品、运单图片更换和收货摘要保留。批次历史里的商品引用映射到采购明细名称，找不到时显示“历史商品”，不回退到原始JSON或ID。历史数据库不变，材料/产品历史默认模式不变。

`PurchaseShipmentsDialog.vue` 重排为品名/供应商摘要、批次卡片、首批空状态和登记表单；批次卡展示日期/包裹/物流/逐商品已收进度。表单支持填入未分配余量、取消登记保护和移除本次运单图，保存/待确认恢复沿用原载荷。修复此登记入口仍直接调用crypto.randomUUID的问题，改用既有createRequestId兼容工具；日期、包裹数、备注纳入未保存保护。仅本地，累计19个变更文件均本轮所有，无提交/发布。

定向验证：`cd frontend && pnpm exec vitest run src/utils/purchaseHistory.test.ts src/components/PurchaseShipmentsDialog.test.ts src/components/ProductHistoryTable.test.ts`，11项通过（创建记录断言首次把不渲染的before占位也计入，现初始before为空并通过）。`pnpm exec eslint src/utils/purchaseHistory.ts src/utils/purchaseHistory.test.ts src/components/ProductHistoryTable.vue src/views/PurchaseOrderDetailView.vue src/components/PurchaseShipmentsDialog.vue src/components/PurchaseShipmentsDialog.test.ts`、`pnpm exec vue-tsc --noEmit`、`git diff --check`通过。`node .local/erp115-history-shipment-ui.cjs` 使用本地拦截合成数据检查空状态、填入余量、已有批次和业务历史，页面异常0；四张`.local/erp115-shipments-*.png`/`.local/erp115-purchase-history.png`截图已目视检查。完整门禁与宽度矩阵仍留待用户交接。

物流空状态/弹窗追加修正（2026-11-27）：移除操作分隔竖线，操作栏使用不换行的内容宽度并保留列最小宽，修复“填写物流”较长时第二入口折行的问题。`PurchaseLogisticsDialog.vue` 顶部改为品名与供应商，逐项展示商品名称，多商品区域限高滚动，去掉PO编号。累计12个未提交文件，均ERP-115本轮。`cd frontend && pnpm exec eslint src/views/PurchasesView.vue src/components/PurchaseLogisticsDialog.vue --fix`通过；再次执行 `node .local/erp115-logistics-layout.cjs`：三种物流状态同排且左对齐，1000px窄视口同排检查通过，弹窗显示合成品名/供应商且无PO编号，页面异常0。列表与弹窗截图已目视检查；完整门禁待交接，无提交/发布。

物流列排版修订（2026-11-27）：上版按钮偏右源于全局 `.data-table button { align-self:end }`。现改为左对齐摘要、独立横向操作栏，编辑/填写与批次入口同排，移除大按钮和箭头。`cd frontend && pnpm exec eslint src/views/PurchasesView.vue --fix`通过。针对实际样式故障执行 `node .local/erp115-logistics-layout.cjs`：1440×900下使用本地拦截的三条合成数据，运单号/空资料/图片运单三种状态均首按钮左对齐、操作同排，摘要高度72/28/51px，页面异常0。`.local/erp115-logistics-layout.png`已目视检查；未接触业务数据，完整门禁仍留待交接。

用户反馈编辑未修改直接保存仍生成变更记录、正常保存出现“结果待确认”。PJM确认DEL-057编辑已结束，授予Codex独占本地修复槽；从干净且与origin/main一致的 `45b7f5822da1fd9222ba3cda17d995860a4d17db` 建立 `fix/ERP-115-material-save-idempotency`。未提交/推送/发布，本轮没有新的发布授权。

- 根因1：`MaterialService.update` 无条件更新版本、时间和主数据审计；聚合保存也无条件替换供应商并追加关系审计，甚至空关系也写入。
- 修复：编辑器无改动时保存按钮置灰，修改后启用、改回原值再次置灰；待确认原请求仍可重试。程序触发原样提交仍不发写请求；后端经验证规范化后比较主数据，相同则保留版本/时间/审计。供应商按ID排序后比较关系字段，空串/null等价，无变化不替换关系；真实主数据/关系修改仍记录。仅成本变化时保留对应成本审计并更新档案时间/版本，使列表排序与并发保护继续有效；幂等请求记录和原请求恢复机制保留。既有历史审计未修改。
- 根因2：提交前为幂等恢复缓存原请求，UI只要存在pending就显示“保存结果待确认”，导致正常在途请求也出现警告。
- 修复：在途请求显示“保存中”，不显示待确认警告或“稍后确认”；只在请求结果不明/恢复遗留请求且未在提交时显示。确定的校验失败清除pending并显示具体错误；真实超时保留原载荷重试，不能为去提示而取消防重复保护。未读取生产请求日志，本次不推断线上某次持续错误的具体HTTP原因。

后续交互调整：无改动保存置灰已本地实现，补充按钮启用/还原断言，待本轮交接统一验证；下列通过结果属于此前修复。

采购管理追加（2026-11-27）：PJM确认沿用ERP-115及独占写槽。辅料tab置首且无指定type时默认辅料，显式产品/日常物品链接保持原类型，键盘切换顺序同步。移除重复页头和列表业务说明，新建移至列表操作栏最左；移除行内供应商“筛选”和刷新，保留供应商详情入口。搜索栏增加独立供应商选择，复用PurchasePicker的分页搜索；筛选模式不限制启用状态或现有业务类别，建单模式仍只选当前类别启用供应商。选择条件后点击搜索应用，重置/移除条件保留。创建日期、预计交期分别使用DatePicker范围绑定，独立弹性容器防止全局label规则挤压；窄屏换行。搜索/新建绿色，更多筛选浅蓝，重置文字样式。

采购列表同轮细化（2026-11-27）：删除“清空选择”按钮（表头及单行反选仍保留）；供应商详情入口改灰色下划线，列表隐藏PO编号，详情/导出/搜索标识保留。商品改48px原尺寸图片与名称/规格对齐、72px行单元，数量同高，多项轻分隔；取消单项数量尾注。物流列去除两行未填写占位，空资料直接填写，已有公司/单号/运单图片标记按层次显示，查看/编辑沿用权限和取消状态限制；将“批次 / 多运单”入口从更多菜单移到物流列，不变更原单据物流或批次数据。仅模板/CSS改动；`cd frontend && pnpm exec eslint src/views/PurchasesView.vue --fix`及`git diff --check`通过，视觉验证按约定留待交接，覆盖空物流/公司信息/图片运单、只读/取消订单、多商品对齐。

本次新增变更文件：`PurchasesView.vue`、`PurchasesView.test.ts`、`PurchasePicker.vue`；累计11个文件均本轮所有。`cd frontend && pnpm exec eslint src/views/PurchasesView.vue src/components/PurchasePicker.vue src/views/PurchasesView.test.ts --fix`通过；`git diff --check`通过。新增默认tab/供应商筛选及日期范围绑定回归用例，更新原刷新失败用例为搜索失败；本次未运行回归或完整门禁。待用户请求交接时统一执行累计前端检查与日期展开、窄屏、供应商搜索/重置视觉验证；未提交/推送/部署。


定向检查（实际bug修复，非完整迭代回归）：

- `frontend`：`pnpm exec vitest run src/components/MaterialEditor.test.ts`，15项通过；覆盖无改动不发写、正常在途不警告、真实超时重用请求、确定失败、历史价格精度及旧请求恢复。
- `frontend`：`pnpm exec eslint src/components/MaterialEditor.vue src/components/MaterialEditor.test.ts`、`pnpm exec vue-tsc --noEmit` 通过。
- 后端：`./mvnw -q -Dtest=MaterialCloseoutTest package`，15项通过并产出本地运行包；覆盖不变版本/时间/审计、关系顺序与空值等价、真实修改/成本/权限/事务回滚及幂等。首次新增测试使用三位小数价格被现有两位规则拒绝，已修正为合法等值5.6后通过。
- 无数据库迁移。旧JAR保留于原目录，旧启动脚本备份 `.local/backups/before-erp115-20261127/`；仅替换JAR路径为 `.local/releases/backend/erp115-20261127/backend.jar`，保留全部本地数据隔离项，沿用tmux定向重启。`curl --noproxy '*' --max-time 5 -fsS http://127.0.0.1:8080/actuator/health` 返回UP；`git diff --check` 与三份维护文档本地链接路径检查通过，最终工作区仅本任务8个文件。
- 完整前端 `pnpm quality`、后端 `./mvnw clean verify` 和累计UI验收留到用户要求交接时统一执行；不跟踪线上发布。


## DEL-057 PJM发布执行（2026-11-26）

- 38项交接指纹全部匹配，门禁日志已核实；后端只读复核未发现阻断。保留本地服务及隔离数据，不重复有效的本地全套门禁。
- 原子提交 `abfd2f6d51d551e0cc03721d822e6cc3dbf50e5b`，经 [PR #33](https://github.com/puhuisifang-svg/Jiugeng-Amazon-ERP/pull/33) 五项检查受保护合入应用main `38ec537461c0ac6e13780120391bdf849f2e8449`。
- [PR CI](https://github.com/puhuisifang-svg/Jiugeng-Amazon-ERP/actions/runs/45846449230) 成功：后端460项、零失败/错误、1条件OSS跳过，前端174文件812测试，浏览器20项通过。
- [Production](https://github.com/puhuisifang-svg/Jiugeng-Amazon-ERP/actions/runs/45847167342) 已自动启动，实际部署结果待核验。V110新增表/索引，不删除历史数据；不包含或回灌本地生产快照、私有配置、备份或图片。
- 官网/Caddy分流未改。最后的接收器启动窗口TERM/KILL修正已合入，但没有新主机同步回执，不宣称服务器已安装该最后版本；此前180秒空闲/600秒接收限制及进度日志已有安装证据。

发布核验：上述Production最终success，实际deploy非skipped。北京时间23:21:00接收82,844,677字节完成（69秒），23:21:06数据库备份完成，23:21:35部署健康与版本验证通过。PJM独立读取ERP HTTPS `/version.json`：HTTP200，精确匹配应用main；官网HTTP200且摘要仍 `ae09c183535a6ecfd9e432ba87cb7c073d3e37b109c4e2846b81451cbe784d5a`。[应用main CI](https://github.com/puhuisifang-svg/Jiugeng-Amazon-ERP/actions/runs/45847167358) 也已通过。

V110仅有精确构建、生产备份与应用启动校验的间接证据，未直查生产Flyway表，不宣称逐条数据库核验。本次未修改官网/Caddy或本地隔离配置，应用已上线；纯文档归档不再触发ERP部署，因此最终文档main与应用入口SHA可不同。接收器最终脚本安装回执及官网自动服务器投递仍是独立未闭合项，不影响本次已验证应用部署事实。

本节覆盖下方交接时“全部未提交/待部署”的阶段记录。原始运行日志保存 `/tmp/del057-production.log`，不随代码上传。

## 当前发布交接：ERP-113 / ERP-114（2026-11-26）

用户已要求提交远端main并部署。Codex本地统一验证完成，精确范围、分支/基线、V110迁移、文件清单和发布边界见[DEL-057交接](../../deliverables/pjm/ERP113_ERP114_RELEASE_HANDOFF_2026-11-26.md)。前端174文件/812测试及lint/类型/构建通过；后端460项（459通过、1项外部OSS探针跳过）；原有3个浏览器场景与新增3个三尺寸场景通过，采购30px居中备注导出已复核，diff/文档路径检查通过。新增浏览器用例的夹具/横向滚动断言修正已复查，应用完整门禁无需重跑。

全部仍未提交，基线 `662501194946f48272fba0c7a4912668282b55d8`、分支 `fix/del056-receiver-startup-cleanup`。正式交接消息接受后PJM独占接管提交、PR合并、CI/CD与线上结果核验，Codex停止写入并不跟踪发布。本地服务和生产快照隔离配置保留，数据/凭据/备份不随提交发布。下方“未授权/待统一验证”均为实施期间历史状态；不覆盖本节最终交接。


## ERP-114 辅料库存操作与流水（2026-11-26，本地已实现，待迭代统一验证）

PJM 明确继续授予 Codex 独占写入槽，与 ERP-113 同轮，保留数据同步文档及采购备注30px改动。分支 `fix/del056-receiver-startup-cleanup`，基线 `662501194946f48272fba0c7a4912668282b55d8`；全部未提交、未推送、未部署。当前工作区改动均属已授权累计迭代。

- 独立库存流水移除顶部返回/说明卡片，辅料列优先显示名称。独立流水和辅料库存弹窗共用 `InventoryReason.vue`；采购收货单号点击后按实际收货流水关联查采购单，同页跳转保留登录态；需采购读取和库存读取权限，失败可重试，不按备注猜测订单 ID。
- 库存弹窗只提供入库/出库，移除包装换算和安全库存设置；预计结存收紧到数量右侧。旧盘盈/盘亏历史仍可读，已保存的待确认调整继续按原载荷重试。新增请求使用兼容 HTTP 环境的 `createRequestId`。
- 操作原因复用 `EditableSelect`，可输入、选择和删除历史候选。删除仅隐藏候选，不删除/修改历史流水；再次用该原因成功创建新人工入出库会恢复候选，幂等重放不恢复；采购收货自动原因不进入候选。
- 辅料列表库存列直接编辑安全库存，保留单位精度、权限、停用保护、失败重试和未保存离开提示；编辑期间阻止筛选/分页刷新隐藏当前编辑行。
- 后端新增 `/api/v1/inventory/reason-options`、`/reason-options/remove` 和采购模块 `/api/v1/purchases/inventory-transactions/{id}/order`。V110 新增候选隐藏表及收货关联索引，未跨模块引用内部类型，未删除任何业务历史。

针对新增持久化/跳转与原请求恢复风险执行定向检查（非完整交接门禁）：

- `pnpm exec vitest run src/components/InventoryReason.test.ts src/components/MaterialInventoryDialog.test.ts src/views/MaterialsView.test.ts src/views/InventoryLedgerView.test.ts`：已有三个文件14项通过；新原因组件测试首次因测试文件缺括号未解析，补齐后 `pnpm exec vitest run src/components/InventoryReason.test.ts` 3项通过，合计17项。后续只补筛选函数的安全库存编辑保护，完整门禁待交接。
- `pnpm exec vue-tsc --noEmit`：通过。`pnpm exec eslint src/api/inventoryOptions.ts src/api/purchases.ts src/components/InventoryReason.vue src/components/InventoryReason.test.ts src/components/MaterialInventoryDialog.vue src/components/MaterialInventoryDialog.test.ts src/views/InventoryLedgerView.vue src/views/InventoryLedgerView.test.ts src/views/MaterialsManagement.vue src/views/MaterialsView.test.ts`：通过。
- 后端 `./mvnw -q '-Dtest=InventoryControllerTest#reasonOptionsPreserveLedgerAndRespectPermissions,PurchaseControllerTest#inventoryLinkResolvesReceiptAndRequiresPermissions' test`：2项通过，0失败/错误；涵盖删除候选保留流水、重复隐藏/幂等恢复、真实收货关联、404/权限/未登录。`./mvnw -q -DskipTests package` 产出本地运行包（未执行完整测试）。
- 本地更新前使用容器 `mysqldump --single-transaction --quick --no-tablespaces --hex-blob --routines --triggers --set-gtid-purged=OFF` 备份运行数据库，gzip校验通过；备份和旧JAR/私有配置保留于 `.local/backups/before-erp114-20261126/`，均未入Git。新运行包 `.local/releases/backend/erp114-20261126/backend.jar`，仅替换既有启动脚本JAR路径，保留生产快照隔离配置。原本地8080进程定向停止后在 `jiugeng-mvp:0.0` 重启；health为UP，schema110成功且新表/索引存在，未登录原因接口401，前端5173为200。

- `git diff --check` 通过；三份维护文档共76个本地链接路径检查通过。运行脚本与备份逐字对比确认仅JAR路径变化；`git status --short` 仅包含当前已授权累计迭代，无无关改动。

待用户要求交接时合并执行前端 `pnpm quality`、后端 `./mvnw clean verify` 和受影响页面布局/交互验收，包含候选删除、采购跳转、列表安全库存、旧待确认请求及ERP-113长备注导出。本轮未做浏览器视觉验收，不把局部测试或服务健康等同于业务验收；无需跟踪线上发布。


### ERP-114 同轮追加：辅料列表与详情（2026-11-26）

PJM 再次确认 Codex 独占同轮补充写入，分支/基线不变，未提交/发布。

- 表头与行勾选统一44px居中列、16px勾选框；批量删除改为浅红底、红色边框/文字及选中数量。详情基础资料使用紧凑并排字段，备注独立一行，成本价显示两位小数。
- 辅料模式的历史展示隐藏内部ID/版本；创建和导入创建只显示已填初始值，不重复“未填写→”，受控图片用“已设置图片”代替资产ID；修改保留前后差异和清空值。其他模块的共享历史表默认展示不变。
- 安全库存原本已记录 `INVENTORY_SAFETY_STOCK_UPDATE` 审计，遗漏原因是辅料历史只查 `AUXILIARY_MATERIAL`。通过 IAM 公共审计接口增加限定资源类型/操作的组合查询，在数据库统一排序、分页、计数；有库存读权限时加入该辅料已有安全库存记录，不重写/补造历史，也不混入其他库存动作。权限模型中 `materials.read` 本身依赖 `inventory.read`；读取不等于可修改。
- 新增 `materialHistory` 格式化及共享历史表选择性渲染用例；`pnpm exec vitest run src/utils/materialHistory.test.ts src/components/ProductHistoryTable.test.ts` 3项通过，`pnpm exec vue-tsc --noEmit` 通过。此次变更文件定向 ESLint 修整通过。
- 合成数据浏览器定向复核：`node .local/erp114-detail-ui.cjs`，1280×800单尺寸，勾选中心差小于1px、删除按钮字色/底色断言通过，创建记录无内部ID/多余箭头、安全库存差异保留，页面JS错误0；基础资料和历史截图已目视复核，保留于 `.local/erp114-material-basic.png`、`.local/erp114-material-history.png`。只拦截合成API，不使用或修改实际业务数据；初次脚本误拦截Vite源码路径，改为仅拦截 `/api/v1/` 后通过。
- 后端新增定向用例覆盖历史安全库存、跨页计数/无重复、其他辅料/动作隔离、只读用户禁止修改及未登录限制。首次用例的审计操作者、创建附属事件计数和权限依赖假设不符现有契约，已修正测试数据/预期；最终运行结果和本地运行包更新见下条。
- 最终后端 `./mvnw -q '-Dtest=MaterialCloseoutTest#materialHistoryIncludesExistingSafetyChangesWithCorrectPagingAndPermissions' package` 通过：1项、0失败/错误，产物已更新至 `.local/releases/backend/erp114-detail-20261126/backend.jar`。本次无新增迁移，schema仍110；原ERP-114运行包保留，旧启动脚本备份于 `.local/backups/before-erp114-detail-20261126/`。仅替换启动脚本JAR路径，保留存储/后台任务隔离，定向重启 `jiugeng-mvp:0.0` 后health返回UP。
- 最终定向ESLint、`git diff --check` 和维护文档链接路径检查通过；所有未提交文件仍为已授权累计迭代。前述“未做浏览器验收”仅指首批范围，本次追加仅做上述单尺寸合成数据复核，完整门禁及累计范围验收仍待用户交接指令。



### ERP-114 安全库存改为锚定浮层（2026-11-26，同轮追加）

PJM 确认继续独占同轮前端写入。按用户附件，将列表行内展开替换为原生顶层 popover：输入/单位、保存和取消同排，下方小字显示当前值。表格保留原值和高度，浮层随滚动/窗口变化定位并避开视口边缘；Esc/取消关闭，有输入草稿时点击外部保留内容，既有离开确认与失败重试保留。保存后静默刷新列表并保持滚动位置，避免加载提示挤动表格。本次仅前端修改，无后端重启或迁移。

- `node .local/erp114-safety-ui.cjs`：1280×800合成数据定向复核通过；浮层打开、取消、保存后三次行位置/尺寸与初始值一致，浮层未越界，页面错误0。截图 `.local/erp114-safety-popover.png` 已目视复核。
- `pnpm exec eslint src/views/MaterialsManagement.vue src/views/MaterialsView.test.ts` 与 `pnpm exec vue-tsc --noEmit` 通过；既有安全库存用例改为表单提交，并增加浮层不在单元格内部的断言，单元测试留待迭代统一执行。`git diff --check` 通过。
- 未提交/推送/部署，保留全部同轮改动；完整门禁仍待交接指令。


- ERP-114 工具栏追加（2026-11-26）：库存流水入口固定靠右，移除手动刷新按钮；既有刷新失败测试改由查询提交触发，保留错误重试。仅本地前端改动，`git diff --check` 通过；按迭代约定不追加全量验证。

- ERP-114 工具栏追加：勾选辅料后批量采购按钮绿色白字，悬停保持绿色；清空勾选恢复默认次要按钮。复用共享选中态，原权限和停用项校验保留；仅本地样式调整，`git diff --check` 通过，待迭代统一验证。

- ERP-114 工具栏追加：按用户要求移除“清空勾选”按钮，保留表头与行勾选的取消选择交互。仅本地模板调整，`git diff --check` 通过，随本轮统一验证。

- ERP-114 列表排序追加（2026-11-26）：辅料列表默认按辅料档案更新时间 `m.updated_at DESC` 排序，时间相同用 `m.id DESC` 保证分页顺序稳定；排序在服务端分页前完成。无迁移，未改历史数据。`./mvnw -q -DskipTests package` 通过，运行包更新至 `.local/releases/backend/erp114-sort-20261126/backend.jar`；原包保留，启动脚本备份于 `.local/backups/before-erp114-sort-20261126/`，仅替换JAR路径，隔离配置不变。在既有tmux定向重启后health为UP，`git diff --check` 通过。未执行测试套件，完整验证仍留待交接。

## ERP-113 采购导出图片备注排版（2026-11-26，本地待统一验证）

PJM 将独占写入槽交给 Codex，明确允许承接数据同步的两份未提交文档，在 `fix/del056-receiver-startup-cleanup`、基线 `662501194946f48272fba0c7a4912668282b55d8` 上继续同一待提交迭代，不切分支或为清锁提交。本次 `frontend/src/ui/purchaseImageExport.ts` 将备注由左对齐改为水平居中，保留垂直居中、红色加粗；字号24px改26px后按用户追加要求再加4px，最终30px，行距36px，换行测量与行高同步调整。UI规范已同步。仅本地实现，未提交/推送/发布；`git diff --check` 通过。按既定节奏未逐项运行完整测试；交接时统一验证短备注、多行长备注与导出图片实际排版，并运行受影响前端门禁。未修改本地服务或数据隔离配置。

## 线上数据同步到本地（2026-11-26，已完成）

用户明确授权用线上快照替换本地全部 ERP 业务数据。PJM 交出本地服务/数据及文档独占写入权，导出期间暂停发布。本次为用户直派数据维护，无新增 ERP 编号；分支 `fix/del056-receiver-startup-cleanup`，基线 `662501194946f48272fba0c7a4912668282b55d8`。仅修改本地数据、忽略的运行配置及本节/接手指南，未提交代码或发布。

- 来源为 2026-11-26 11:02 CST 的生产事务快照，schema 109、121 张表，配套持久目录包括 catalog/dashboard 图片、货代模板和导入暂存。生产只读查询与导出，没有停服、改业务数据或配置。Safari 窗口恢复后通过既有云助手和 Workbench 文件管理下载，未放宽 SSH 权限或设置公开备份地址。
- 下载包 510,781,440 字节，SHA256 `d0f49ee682df22ecb1110adc87e9512fe878b51bf1d2b6b1595add97f1da47de`；压缩包与内层两份文件哈希均匹配。保存在受保护且 Git 忽略的 `.local/backups/prod-sync-20261126-1102/`。先恢复到临时本地库完成检查，确认 25,012 个图片引用零缺失、12 份已有外箱标导出 content 完整，账户默认值加密 key 匹配；未输出业务记录或凭据。
- 预备备份 `.local/backups/before-prod-sync-20261126-0045/` 保留；正式替换前停止本地后端，最终备份位于 `.local/backups/before-prod-sync-20261126-1110/`，包含数据库、图片、导入暂存、私有运行配置/密钥和模板。旧目录也移入该目录保留恢复入口；这是备份，不再参与本地业务运行。
- 最终备份命令：容器 `mysqldump -uroot --single-transaction --quick --no-tablespaces --hex-blob --routines --triggers --set-gtid-purged=OFF jiugeng_erp` 经 gzip 保存，凭据仅从容器已有 secret 读取；四份 gzip 校验及 `shasum -a 256 -c .local/backups/before-prod-sync-20261126-1110/SHA256SUMS` 全部通过。只重建 ERP 容器的 `jiugeng_erp`，同机其他项目 MySQL/Redis 未触及。
- 快照导入后、启动前逐表核对，121 张表记录数全部一致。仅本地清空 `iam_session`、`iam_password_reset_challenge`，原线上账号和密码哈希保留，使用时须重新登录；没有重置密码。验证临时数据库已删除。
- 34,201 个复制文件逐个 SHA256 与下载副本匹配。运行脚本 `.local/runtime/run-backend.sh` 及环境文件改成本地存储；运行进程实际验证 `ERP_STORAGE_PROVIDER=local`、Amazon 采集/仓库同步 false、图片后台刷新 false，且无 OSS 凭据路径。导入暂存改为 `.local/runtime/import-staging`；自动清理 cron 暂停，短信/邮件找回发送入口关闭。不能在后续重启时恢复生产 OSS 或采集开关。
- 沿用原本地 JAR 与 `jiugeng-mvp`，未借数据同步更换应用版本。`sh -n .local/runtime/run-backend.sh` 通过；`curl --noproxy '*' --max-time 5 -fsS http://127.0.0.1:8080/actuator/health` 返回 UP；本地5173登录入口200；未登录产品API401；运行库 schema109、会话和重置令牌均0。一次 Python urllib 健康请求受环境代理影响返回502，显式直连复核成功。未运行全量回归，未代用户执行业务登录/业务验收。

上述为一次性快照，不会与线上自动同步。本地配置/数据/备份均未入 Git；文档 diff 检查与链接校验通过。交接后由 PJM 接回操作权；后续任务需重新确认当前所有权。

## DEL-056 应用上线核验（2026-11-26 00:06 CST）

[恢复Production 45650240074](https://github.com/puhuisifang-svg/Jiugeng-Amazon-ERP/actions/runs/45650240074) 最终success，实际deploy非skipped。接收82,836,154字节，耗时66秒，15/30/45/60秒分别累计18,939,904 / 37,584,896 / 56,262,656 / 74,514,432字节，未触发接收超时。00:05:28数据库备份完成，00:05:55 `Deployment verified: 14c67cf04576215a6ae7f95b5c28a467d0ef8fd2`，部署脚本健康与内部版本验证通过。PJM独立读取ERP HTTPS `/version.json` HTTP200并匹配同SHA；官网HTTP200，内容摘要仍ae09c183535a6ecfd9e432ba87cb7c073d3e37b109c4e2846b81451cbe784d5a。无需新增迁移，未改分流。应用发布已完成，不代表底层网络故障根因已确定。

仍待闭合：main CI45650240148的启动瞬间TERM清理测试失败已定位、修复并推送e0b799eaed9ce03247314ee7ebd26271dff330c6（100次启动测试及9项工具测试通过），但尚未合并或安装主机。新receive.sh SHA256 f28d124dac213958056baff493bfcd53dce0079ab49711edea91b9caaac01102，helper不变。已请求CUA协助会话在无接收进程/锁空闲后备份同步；其操作期间PJM不执行远端写入或部署。创建后续PR时GitHub GraphQL返回内部错误，REST无有效返回，尚未确认PR创建，不能绕过分支保护。官网自动服务器投递仍为独立未闭合项。

## DEL-056 服务器恢复回执（2026-11-25，PJM接回操作槽）

以下为具有原生Safari CUA通道的协助会话回执，PJM未冒称亲自执行云助手操作：

- 只读复核 `t-bj0746xm0iklvr4`（23:54:52 CST）：进程链和starttime匹配，临时包21,823,488字节、两次大小/mtime不变且停滞超过20分钟，旧应用健康。
- 定向清理 `t-bj0746xqoqxf1fk`（23:56:19，ExitCode0）：即时核对PID身份/父子关系/starttime、head stdout包及旧current；备份残包与进程证据，再观察5秒无增长，TERM已确认head及仍存的原会话。结果 `VERIFIED_SESSION_EXITED`、`LOCK_AVAILABLE`、`RECOVERY_CLEANUP_PASS`。未unlink锁、未停应用。
- 备份根目录 `/srv/jiugeng/recovery/del056-20261125-2356/`：残包、processes-before.txt、scripts/receive.sh.previous、helper-previously-absent.txt；旧receive.sh SHA-256 `50f5ad123fd0170378f6ef17dc486bc63964e284f50e52a5409f2bca2c211943`。
- GitHub raw首次下载发生curl35连接重置，尚未安装；改由云助手传递源码，双哈希一致后安装。最终 `t-bj0746xygtkkh6o`（23:58:44，ExitCode0）helper先、receiver后原子替换，均root:root 0755，来自提交 `2fb8852d3a8b59c61d7461d3bd57bffbd5b7ac72`。
- 安装哈希：receive.sh `547ace3a3145a6413dc3641634c82bdc7fc5b02a314df45b52a1f9c35f82082a`；receive_stream.py `c9de4b7935b7ffc9fbdd7e57d248f378f876fb2eb3bd974abbe7902a03131f66`。bash语法、Python AST、15字节EOF和0.2秒idle探针通过，结果 `INSTALLED_HELPER_PROBES_PASS`、`SCRIPTS_INSTALLED_PASS`、`LOCK_AVAILABLE_AFTER_INSTALL`。证据 install-direct-result.txt 位于上述备份根目录。
- 安装后jiugeng/caddy active、health UP，current仍为旧应用 `365892ccb4c5d5eb151420bd0cb7c66c739f8586`；未改Caddy、SSH/sudo、端口、业务数据、应用包或本地仓库。
- 操作槽明确归还后，PJM核对干净分支及五项必需检查，通过 [PR #32](https://github.com/puhuisifang-svg/Jiugeng-Amazon-ERP/pull/32) 受保护合并，应用目标main为 `14c67cf04576215a6ae7f95b5c28a467d0ef8fd2`。[恢复Production](https://github.com/puhuisifang-svg/Jiugeng-Amazon-ERP/actions/runs/45650240074) 自动启动，最终结果待核验。
- 超时补丁避免永久接收残留，不等于解决底层跨境传输。若再次失败，应按进度字节和实际速率选择经校验的稳定产物通道，不循环重试或盲目延长时限。上述回执覆盖此前“未清理/未安装”的阶段描述。

## DEL-056 SSH接收异常诊断与恢复（2026-11-25）

调查会话通过已登录Safari阿里云云助手完成只读诊断（记录 `t-bj0746w71gfhszk`，23:38:59 CST），不是新凭据缺失：23:39生产jiugeng/caddy均active、health UP、current及公网版本仍旧SHA。残留部署链为sshd 90167→sshd 90287→sudo 90290→receive.sh 90291→head 90297；head等待pipe_read，临时包21,823,488字节、mtime23:33:16，磁盘可用35G。此为调查会话报告的时点证据，不代表进程持续存在。

原receive.sh在完整读入stdin后才校验、备份和停服；该证据将异常定位到SSH流接收阶段，尚不能细分网络慢/断流或EOF丢失。恢复前必须重新核对进程身份、包是否停止增长，再定向结束失联接收会话并确认flock自然释放；禁止直接unlink锁或盲目重跑。

PJM已确认Safari控制台标签存在，但当前PJM进程没有屏幕录制/辅助访问权限，Apple Events JavaScript也未启用；原生CUA能力不在当前工具列表。故管理入口可用不等于本会话可执行远程操作。尚未终止任何生产进程、删除锁或重跑发布；需要恢复已有CUA操作通道，不需要提供秘密。

接收层补丁：512MiB上限、180秒空闲/600秒总接收超时、15秒字节进度及TERM/HUP/INT子进程清理；9项发布工具测试通过，包含EOF、断流、持续慢流总时限、超限及TERM后接收子进程退出/临时目录清理。补丁尚未安装服务器，不能宣称远端已生效；当前应用、Caddy、端口及本地服务均未改。

## DEL-056 发布复核（2026-11-25）

累计实施提交 `6a156daf2e6db4a089364d2efc74d90c3b61750e` 经 [PR #30](https://github.com/puhuisifang-svg/Jiugeng-Amazon-ERP/pull/30) 受保护合入应用 main `fc8891cbbd6db4e3eccd6e74c26a6453d54a9d80`。PR与main CI通过：后端457项零失败/错误、1项条件OSS跳过，前端802项，浏览器17项。[Production](https://github.com/puhuisifang-svg/Jiugeng-Amazon-ERP/actions/runs/45640730028) 实际SSH部署因作业30分钟时限于15:33 UTC被取消，未见备份完成/部署验证标记。15:34 UTC后ERP HTTPS仍为旧SHA `365892ccb4c5d5eb151420bd0cb7c66c739f8586`（HTTP200），本轮目标未确认上线。当前管理通道不可用，无法检查服务器实际接收进程与锁；未盲目重跑或绕过锁。恢复管理访问后先检查再安全续跑，发布尚未闭环。

官网独立CI成功；其8个静态源码文件与HTTPS线上逐文件一致，内容摘要仍为 `ae09c183535a6ecfd9e432ba87cb7c073d3e37b109c4e2846b81451cbe784d5a`。当前源码与线上一致，但官网自动服务器投递仍待可用管理通道，未宣称已实现。未修改现有Caddy/域名/IP分流或本地持久服务。本轮无数据库迁移。文档归档已与ERP自动发布路径隔离，避免纯文档重复部署。

完整证据见 [DEL-056发布结果](../../deliverables/pjm/ERP112_MATERIAL_PURCHASE_RELEASE_HANDOFF_2026-11-25.md)。以下交接阶段的“全部未提交”等描述为历史快照，由本节覆盖。

## 当前发布交接：官网、辅料与采购（2026-11-25）

PJM接手补充：已修复成本清空重建复用版本号的并发覆盖问题，无新增迁移；相关31项后端测试通过。新增独立官网CI精确提交产物及ERP发布路径隔离，当前混合提交仍走完整ERP门禁。三个工作流actionlint通过。官网现有内容摘要与源码构建一致；自动服务器投递仍待可用管理通道，不冒充已接通，现网Caddy及域名/IP分流保持。具体证据见DEL-056末尾PJM复核节。

用户已要求“与项目经理交接，把修改内容提交远端main，并部署线上”。累计范围为 ERP-112 官网、辅料成本价/单位/删除/HTTP保存、采购导出备注及表单/首页收货UI；沿用 feature/ERP-112-corporate-website、基线 c410b33a61a60aeb1700ed4501b8ecd9d5e8453f，全部未提交。发布授权覆盖本轮，不代表已合并或上线。

- 完整前端 pnpm quality 通过，171文件802测试、lint/类型/构建通过；后端 ./mvnw clean verify 通过，456项中455通过，OSS联网探针1项跳过。官网2项契约及构建通过；受影响页面1280/800/360宽度的3个浏览器场景通过，局部1440采购/首页截图检查结果复用。
- 官网预览占用4188，浏览器检查采用临时4190并已自动停止；现有本地ERP/MySQL和官网服务保持。无数据库迁移，无本轮线上操作。
- 交接文件：[DEL-056](../../deliverables/pjm/ERP112_MATERIAL_PURCHASE_RELEASE_HANDOFF_2026-11-25.md)，包含完整命令、改动、证据位置及发布边界；统一日志/截图保留 .local/verification/handoff-20261125/。状态与文件归属已检查，无无关修改。文档路径和 git diff --check 在发送前复核。
- 正式交接消息接受后 PJM 独占写入，负责提交、受保护合并main、部署和结果闭合。Codex 不轮询线上发布。PJM 必须保留当前官网/ERP域名及IP分流；官网独立自动发布后续由PJM接管，不能把ERP发布包当官网包。

本节覆盖下方“待交接/未授权”的历史状态；ERP本轮变更尚未发布，官网此前独立上线事实保持原记录。

## 采购表单与首页待接收布局（2026-11-25，同一本地迭代追加）

沿用 feature/ERP-112-corporate-website、基线 c410b33a61a60aeb1700ed4501b8ecd9d5e8453f 及已授权本地迭代，全部未提交、未发布，保留之前官网与辅料改动。

- PurchasesView 与 purchaseManagement.css：新建/编辑/再次采购共用表单，供应商、日期、收货信息在宽屏同排、40px 等高；中窄屏恢复两列/单列。项目选择按钮采用浅底蓝字，取消继承表格旧实心蓝样式；表格按图片/项目/数量/包裹数/价格/税/金额/操作划分列宽，数量和单位组合排列，控件统一40px，金额右对齐，移除操作红字。明细独立滚动，底栏保留合计，表内不重复显示合计。
- DashboardPurchaseReceipts：独立商品摘要样式，以48px原有缩略图和文字横向排列，品名及收货进度上下排列；移除该摘要对固定行高的依赖，长名称自然换行。搜索区域收紧，状态改轻量标签，确认收货用紧凑绿色按钮，宽表保留横向滚动。
- `pnpm exec eslint src/views/PurchasesView.vue src/components/DashboardPurchaseReceipts.vue --fix --max-warnings=0` 与 `pnpm exec vue-tsc --noEmit` 通过。仅做局部视觉诊断：前端目录 `node /tmp/jiugeng-purchase-layout.cjs` 使用本地 Vite、独立 Chromium 和拦截的合成接口数据，1440×900截图已检查；三项顶部控件均40px且同一Y坐标，首页摘要为flex且内容高度未溢出。截图 `/tmp/jiugeng-purchase-editor.png`、`/tmp/jiugeng-dashboard-purchases.png`；未写业务数据。
- 本次仅前端热更新，无后端重启或迁移。完整门禁、手机与不同业务类型的统一浏览器验收仍留交接节点；当前不是完整回归结果。

## 辅料删除与采购图片备注（2026-11-25，同一本地迭代追加）

沿用下节分支、基线和用户授权写入槽，保留已有官网与辅料改动，全部未提交、未发布。

- 辅料列表更多操作的删除文字改红色，使用 body 下的菜单样式覆盖，确保 Teleport 后仍生效。删除检查不再把单纯 material_standard_cost 行作为业务引用；删除未使用辅料时在同一事务清除当前价格并追加审计，保留历史记录，解决旧核价资料的外键阻断。真实库存、采购、产品用量及成本版本引用仍阻止删除；批量任一项失败不删除任何项。不批量清除用户业务数据，无迁移。
- 采购图片导出追加备注列，取采购单备注并带到该单每条明细，红色 #b42318、bold 24px；保留手动换行并按列宽自动换行，行高适配备注，原名称和图片列宽保持。无备注留空，同日不同采购单的备注不混用。
- 定向检查：后端目录 `./mvnw -q -Dtest=MaterialCloseoutTest,CostControllerTest test`：22 项通过，覆盖旧核价删除、审计保留、库存/成本版本引用阻断及批量原子性；`./mvnw -q -DskipTests package` 通过。前端 `pnpm exec vitest run src/ui/purchaseImageExport.test.ts`：3 项通过；`pnpm exec eslint src/ui/purchaseImageExport.ts src/ui/purchaseImageExport.test.ts src/views/MaterialsManagement.vue --max-warnings=0` 通过。类型检查发现测试回调 this 缺少类型，已补 HTMLCanvasElement 后重检。
- 重检 `pnpm exec vue-tsc --noEmit` 通过；`git diff --check` 通过。本地后端新包 `.local/releases/backend/material-delete-price-20261125/backend.jar`，同目录保留 previous-run-backend.sh，上一包保留；继续 tmux jiugeng-mvp 和原 MySQL 配置，进程确认运行新包，backend.pid 已同步，`./scripts/dev/smoke-local-mysql-backend.sh` 通过。完整门禁和真实浏览器图片、菜单验收仍在用户要求交接时统一执行。

## 辅料成本价简化（2026-11-25，本地实现，待统一验收）

用户明确确认 ERP-112 官网已结束编辑，允许辅料修改合入当前本地迭代。沿用 feature/ERP-112-corporate-website，基线 c410b33a61a60aeb1700ed4501b8ecd9d5e8453f；保留 website/ 及已有文档改动。全部未提交，未请求发布，线上不变。

- 去掉核定原因、确认及重新核定勾选；成本价直接填写、修改或清空，基本校验为非负金额、最多两位小数。自动追加辅料变更记录与原成本审计；保留权限、版本冲突及整个表单原子保存，无成本读取权限时隐藏历史金额。
- 成本价不再单独锁定单位；库存、采购、产品用量、成本版本引用仍锁定。未使用辅料修改单位时保留原金额精度、同步计价单位，导入同样处理；未改价格不回写界面舍入值。
- 旧待确认载荷和哈希保持兼容；新增 cost 字段缺省表示不改、null 金额表示清空，并用 simpleCostVersion 阻止旧服务静默丢金额。辅料保存改用已有 HTTP 兼容请求编号工具；其他模块直接 randomUUID 调用不在此次范围。
- 后端目录 `./mvnw -q -Dtest=MaterialCloseoutTest,MaterialControllerTest,CostControllerTest test`：28 项通过。前端目录 `pnpm exec vitest run src/components/MaterialEditor.test.ts src/utils/requestId.test.ts src/utils/productHistory.test.ts`：3 文件 22 项通过。覆盖价格写入/清空、精度、旧哈希重试、权限、回滚、单位修正与真实引用锁定。
- 前端 `pnpm exec eslint src/components/MaterialEditor.vue src/components/MaterialEditor.test.ts src/components/MaterialDetailDialog.vue src/api/materialManagement.ts src/utils/productHistory.ts e2e/operations-closeout.smoke.ts --max-warnings=0`、`pnpm exec vue-tsc --noEmit` 通过。后端 `./mvnw -q -DskipTests package` 通过；最后补齐删除影响预览对成本版本引用的判断，已编译，统一回归待交接。
- 本地沿用 tmux jiugeng-mvp、原 MySQL profile 与配置；数据库 V109/success=1，本次无迁移。旧运行包保留，启动脚本备份 `.local/releases/backend/material-simple-cost-20261125/previous-run-backend.sh`；新包为同目录 backend.jar。Vite 已返回新编辑器，核定原因已移除。
- 本地启动检查：`./scripts/dev/smoke-local-mysql-backend.sh` 通过；`curl -fsS http://127.0.0.1:8080/actuator/health` 返回 UP。进程已确认运行上述新包和 mysql profile，backend.pid 已同步；`git diff --check` 通过。工作区仅本次辅料改动及用户授权保留的官网/文档修改，无暂存或提交。
- 待交接统一执行前端 pnpm quality、后端 ./mvnw clean verify、浏览器新增/编辑/清空/单位修正及历史显示检查；未运行完整回归，业务验收未完成。

## ERP-112 域名与 HTTPS 已启用（2026-11-25 21:54，Asia/Shanghai）

用户明确回复“确认，立即配置”，批准补齐解析、启用三域 HTTPS 并保留 IP/8081。当前仍为 `feature/ERP-112-corporate-website`、HEAD `c410b33a61a60aeb1700ed4501b8ecd9d5e8453f`；全部官网实现及文档未提交、未推送、未合并，无其他任务变更。本节覆盖下方待确认阶段。

- DNS：阿里云新增根域 `@` 与 `erp` 的 A 记录，指向用户指定的既有服务器，TTL 10分钟；原 `www` A 记录保留。Google Public DNS HTTPS 查询三域均返回同一目标地址、TTL600。本机早期普通 DNS 查询仍见旧缓存，未据此否定控制台提交结果。
- 路由：`https://jiugengerp.cn/` 为官网，`https://www.jiugengerp.cn/` 301到根域且保留路径/查询，`https://erp.jiugengerp.cn/` 为原ERP。三域HTTP均301到对应HTTPS；IP的HTTP官网与8081 ERP继续可用。内部8080/4173、业务包、数据库及上传文件未改动。
- 启用：核对现用配置 SHA 与备份相同，`caddy validate --config /srv/jiugeng-website/backups/domains-20261125-182024/Caddyfile.domains --adapter caddyfile` 通过；原子替换 `/etc/caddy/Caddyfile`，`systemctl reload caddy` 与 `systemctl is-active --quiet caddy` 通过。21:52:28 云助手 ExitCode0、`DOMAINS_ENABLED_IP_PROBES_PASS`，没有重启ERP后端或修改已有安全组规则；443原已放行。
- TLS：三域系统信任链及主机名校验通过，证书有效期至2027-02-23；Caddy已启用自动证书管理/续期，未来续期结果尚未发生。证书私钥未读取或导出。
- 验收：21:54:14 云助手 Python `ssl.create_default_context` + `http.client` 检查三域TLS、HTTP跳转、www HTTPS路径与查询保留、官网四项资源200/未知路径及API404、ERP登录/深链接200/匿名`/api/v1/auth/me`401、IP及4173版本一致、未知HTTP Host404，输出 `ALL_DOMAIN_CHECKS_PASS`（ExitCode0）。结果保存在上述备份目录 `verification-https.txt`。
- 外网：Safari官网与ERP登录页均显示安全连接，截图已保存本地交付目录；本机 Python `urllib.request.urlopen` 验证根域官网版本和ERP版本均200，www版本URL保留查询参数跳到根域后200。官网内容摘要仍为 `ae09c183535a6ecfd9e432ba87cb7c073d3e37b109c4e2846b81451cbe784d5a`，ERP版本仍为 `365892ccb4c5d5eb151420bd0cb7c66c739f8586`。
- 维护：域名配置回退用上述目录 `Caddyfile.original`，校验后恢复并reload可回到原IP入口；当前DNS新增记录另由控制台管理。后续自动CI/CD仍归PJM。已登录的业务写入、附件读写和业务验收未代验；用户未提供可核实的公开备案号，本轮未编造或新增页脚编号。

## ERP-112 域名配置准备（2026-11-25 18:20，历史阶段）

用户告知备案通过并授权配置域名，继续当前 ERP-112 写入槽与原基线，现有未提交修改均为本任务。阿里云 DNS 控制台核对仅有 `www` A 记录，根域和 `erp` 尚无记录；目标安全组已有 IPv4 TCP443 允许规则。拟补根域/ERP 两条 A 记录，启用根域官网、www 保留路径跳根域、ERP 子域完整应用及三域 HTTP→HTTPS；保留既有 IP/8081 入口。

服务器已备份到 `/srv/jiugeng-website/backups/domains-20261125-182024/`，包含 `Caddyfile.original`、`original.sha256`、`Caddyfile.domains`。云助手 `caddy validate --config <Caddyfile.domains> --adapter caddyfile` ExitCode 0，尚未替换现用配置或 reload。配置将全局 `auto_https off` 改为 `disable_redirects`，显式配置域名跳转，同时保留 IP HTTP 行为。`website/deploy/domains.caddy.example` 同步三域规则；本地 `python3 -` 拼合四个模板后运行 `caddy adapt --adapter caddyfile` 通过，`git diff --check` 通过。DNS 添加根域表单已填写但未提交。

浏览器工具对网络路由变更要求提交前确认，本轮已发一次批量确认卡片，等待用户答复；未用前一轮8081的确认替代本次新域名操作。域名尚未上线、证书尚未签发。收到确认后提交两条解析、启用上述已校验配置，再检查 TLS、重定向、官网404、ERP版本/匿名鉴权与旧IP入口。用户未提供可核实的备案号，本轮未添加猜测编号。

## ERP-112 首次上线与 IP 入口切换完成（2026-11-25 17:43，Asia/Shanghai）

用户直接授权 Codex 负责首次部署、服务器配置，并在提交前确认“放行 8081 并完成切换”；后续自动 CI/CD 由 PJM 负责。本节覆盖下方“等待管理通道 / 暂存 / 待备案 / 尚未切换”的历史阶段描述。域名、DNS 和证书仍按用户安排留到备案通过后配置。

- 工作区：`feature/ERP-112-corporate-website`，HEAD/基线 `c410b33a61a60aeb1700ed4501b8ecd9d5e8453f`，官网实现与文档仍未提交、未推送、未合并。首次上线通过用户已登录的阿里云云助手执行，不是 GitHub Production 流程。全部修改归 ERP-112，无其他任务修改。
- 实际配置：默认公网 IP 的 HTTP 80 返回独立官网；指定 IP 的 HTTP 8081 提供原 ERP 前端和 `/api/*` 到原内部 8080 的代理。未知 Host 返回 404。原 4173 回环探针和 ERP 应用包、数据、上传文件均未改动，未重启 ERP 后端。
- 网络：目标实例安全组已新增允许 IPv4 TCP 8081、来源 `0.0.0.0/0`；既有规则保留。主机 UFW 检查为 inactive，本轮未启停主机防火墙。此次只配置用户确认的 IPv4 入口。
- 备份：服务器 `/srv/jiugeng-website/backups/20261125-173820/` 保留 `Caddyfile.original`、`original.sha256`、`Caddyfile.phase1`（旧入口加 ERP 8081）、`Caddyfile.phase2`（官网80加 ERP8081）。两阶段完整配置均经 `caddy validate --config <阶段文件> --adapter caddyfile` 通过，原子替换 `/etc/caddy/Caddyfile` 后执行 `systemctl reload caddy`。
- 执行证据：17:38:21 第一阶段云助手 ExitCode 0；Safari 从公网打开 8081，显示 ERP 登录页后再切默认入口。17:42:30 第二阶段 ExitCode 0，输出 `WEBSITE_AND_ERP_CHECKS_PASS`。中途一次验收误用了不存在的 `/api/auth/me`，脚本自动恢复第一阶段；核对代码改为 `/api/v1/auth/me` 后复验通过，匿名状态为 401。
- 生产检查：Python `urllib.request` 对回环地址携带实际 Host，官网首页含真实公司名称和已确认联系方式；CSS/JS/两项 SVG/版本文件均为200；未知路径和官网 API 路径为404；ERP `/login` 为200、`/version.json` 与原4173逐字节一致；未知 Host 在80/8081均为404。Safari 外网已目视默认 IP 的官网完整首屏、主体与联系方式，以及8081的登录页。
- 官网内容版本：`ae09c183535a6ecfd9e432ba87cb7c073d3e37b109c4e2846b81451cbe784d5a`，生产 `website-version.json` 一致。ERP `/version.json` 保持 `365892ccb4c5d5eb151420bd0cb7c66c739f8586`。本次只更新入口，不把仓库文档基线当成 ERP 新版本。
- 验证边界：未替用户登录或进行真实业务写入；图片/PDF/上传下载的已登录业务验收仍待使用方确认。官网已有本地四宽度 Chromium 检查与本轮 Safari 外网目视，不声称完成 Edge 或全部跨浏览器验收。未发送测试邮件或拨打电话。
- 本轮收尾：根目录 `git diff --check` 通过；`python3 -` 内联 Markdown 路径检查通过（官网 README、接手指南、状态报告共77个本地链接）。最终仅 `website/` 与两份上述维护文档有本任务修改，无暂存或其他任务变更；未因入口配置变化重跑不受影响的 ERP 应用门禁。
- 后续：PJM 接管自动构建、官网独立发布/回退及工作流路径隔离，合并前注意现有 main push 会触发 ERP Production。当前源码未提交；本节不代表业务验收，也不授予其他代理并行写入权。维护入口见[官网说明](../../website/README.md)。

## ERP-112 双 IP 入口预演（2026-11-25，历史阶段）

用户确认默认 IP 提供官网，IP:8081 提供 ERP，域名配置留到备案通过。目标 ECS 的官网 config-pending 目录已保存独立 IP 路由片段。生产机云助手单次回环预演通过：首页与官网静态产物一致，/api/test 返回官网 404；临时绑定回环 8081 的 ERP 首页、/home、/version.json 与原 4173 入口逐字节一致，匿名业务 API 返回 401 或 403。未验证已登录业务操作。预演监听已退出，公网入口尚未切换，域名未配置。[阿里云对 IP 网站的备案说明](https://help.aliyun.com/zh/icp-filing/basic-icp-service/product-overview/icp-filing-requirements-for-a-regular-website)明确涵盖中国内地 IP 对外提供服务；按接入商规则等待首次备案获批。获准公开后先验证 ERP 替代入口，再切默认 IP。

## ERP-112 官网首次部署进展（2026-11-25，生产机已暂存，待备案）

Codex 使用已登录的阿里云控制台，核对北京目标 ECS 与既有入口一致。云助手发送官网 ZIP 至单台实例成功，主机通过 `sha256sum -c` 和 ZIP 逐文件比对。内容版本为 `ae09c183535a6ecfd9e432ba87cb7c073d3e37b109c4e2846b81451cbe784d5a`。独立版本目录在 `/srv/jiugeng-website/releases/<内容摘要>/`，专用 `current` 指向该版本，待启用片段在 `/srv/jiugeng-website/config-pending/website-<内容摘要>.caddy`。检查 Caddy 账号可读、现用 Caddy 服务 active 及现有 ERP 回环版本探针可读。

生产机预演：初次 `caddy validate --config <临时配置>` 因本机预演使用的 `persist_config off` 不受服务器版本支持而退出。移除此测试选项，使用临时 XDG 目录隔离，在同机执行校验与回环 Caddy 服务测试，云助手返回成功；首页、版本文件字节一致，/api/test 返回官网 404 页面。临时服务已随命令退出。生产 Caddy 主服务、DNS、安全组和原 ERP 公网入口未切换。

同一账号「我的备案」显示进行中的首次备案订单处于管局审核，页面显示已提交 12 个工作日。按[接入商的首次备案开放规则](https://help.aliyun.com/zh/icp-filing/the-influence-of-the-record-during-the-site-visit)，待核准票据、域名及实际备案号后再更新页脚和产物，继续正式入口验证与切换。后续自动 CI/CD 仍归 PJM。以下“等待生产管理通道”为前一阶段记录。

## ERP-112 官网首次部署（2026-11-25，等待生产管理通道）

用户最新授权 Codex 执行首次部署和服务器配置，后续自动 CI/CD 归 PJM。沿用官网开发分支与基线，改动未提交。首次部署配置与回退步骤见[官网部署说明](../../website/README.md)。官网内容 SHA-256 为 `ae09c183535a6ecfd9e432ba87cb7c073d3e37b109c4e2846b81451cbe784d5a`，此次 ZIP 的 SHA-256 为 `a53d24102692bbc577d260c200dc465707dbbfbfb68b91e3da2de2963aee05dd`。

`python3 website/build.py` 与 `python3 -m unittest discover -s website/tests -v` 通过（2 项）。`node /tmp/erp112-preview.cjs`、`node /tmp/erp112-contact-check.cjs`、`node /tmp/erp112-release-browser.cjs` 通过。Chromium 在 1280/800/360/320 四种宽度模拟 100ms 延迟、200000 字节/秒下载，首次内容绘制 320–328ms，传输 19362 字节；Safari、Edge 实机与外网体验尚待验。Caddy v2.11.7 在本机完成四个模板的组合解析和官网片段运行验证，产物逐文件读取一致，未知路径与 /api/test 均为官网 404。公网 DNS 只读查询：根域无 A/AAAA，www 有 A，ERP 子域 NXDOMAIN。尚未核实域名接入/备案放行状态。

本机 SSH agent 无已加载身份，可见云控制台要求登录，项目已有 ERP 部署账号只允许应用接收命令。已请求生产管理通道。生产应用、DNS、监听和安全组尚未操作，待接入后按官网说明核查并执行。恢复任务时检查当前分支与工作区归属，并以目标入口核验作为上线依据。

## ERP-112 九更官网本地开发（2026-11-25）

用户授权开发，PJM 已从干净 main `c410b33a61a60aeb1700ed4501b8ecd9d5e8453f` 将唯一写入槽交给 Codex。当前分支 `feature/ERP-112-corporate-website`，本地未提交。用户确认法定全称为厦门九更网络科技有限公司，并于本轮补充授权展示合作邮箱与手机号。合作区提供邮件、拨号链接及复制反馈。本期采用 DEL-054 约定的独立展示版式。首页、原创标识及海港插画、移动导航、无效地址页面、搜索索引文件和独立构建预览见[官网开发说明](../../website/README.md)。

局部验证：`python3 website/build.py` 生成独立静态产物；`python3 -m unittest discover -s website/tests -v` 两项通过；`node /tmp/erp112-preview.cjs` 通过 Chromium 1280×800 首屏渲染、320px 手机菜单/焦点/锚点/横向溢出检查及接口和未知路径返回真实 404，桌面截图已目视。联系方式追加后重新构建并复验两项契约通过；`node /tmp/erp112-contact-check.cjs` 核对本地预览的链接目标、两项复制、手动复制回退及 320px 布局通过。交接时仍需统一浏览器/视口及性能验收。发布前需确认官网与 ERP 的工作流隔离（现有 main push 会触发 ERP 流程）、公开时机及合作渠道实际可达性。此次只启动回环地址官网预览，无线上操作。

## PJM 发布核验与下一阶段（2026-11-25）

ERP-104 的15个文件原子提交为 `2cf21d9c6f9b5a19eaf1ec7376a539a2acef1e4e`，经[PR #28](https://github.com/puhuisifang-svg/Jiugeng-Amazon-ERP/pull/28)五项必需检查后受保护合并。应用 main 为 `365892ccb4c5d5eb151420bd0cb7c66c739f8586`。

[PR CI](https://github.com/puhuisifang-svg/Jiugeng-Amazon-ERP/actions/runs/45563211636)、[main CI](https://github.com/puhuisifang-svg/Jiugeng-Amazon-ERP/actions/runs/45563565759)与[Production](https://github.com/puhuisifang-svg/Jiugeng-Amazon-ERP/actions/runs/45563565678)成功。PR CI 后端449项零失败/错误、1项OSS条件跳过，前端171文件/795项及浏览器17项通过。未重复运行有效本地门禁。

实际SSH deploy非跳过：北京时间14:56:18归档校验OK，14:56:22生产数据库备份完成，14:56:49部署接收器验证后端健康UP及完整版本。PJM独立读取真实入口`/version.json`为上述应用main，HTTP200，首页200。健康为接收器日志证据，未直查生产数据库或执行恢复演练。本轮无新迁移，未改Caddy、DNS、安全组、8081或默认IP入口；未重启本地服务。

本节是已上线应用的证据归档，不声称包含本节的文档提交已部署。仅归档一次；归档PR、最终main及其Production终态由PJM在`/tmp/jiugeng-pjm-release-progress.md`和最终报告维护，不为文档自身SHA递归创建PR。下方“未提交/待执行”为历史交接快照。

下一阶段分配 **ERP-112｜九更单页静态官网本地开发**（ERP-105～111已被历史Backlog占用，不复用）。以DEL-054正文v0.6为需求基线，独立`website/`目录、静态产物和文档；Logo、视觉和一般文案可自主设计，公司主体及联系方式不编造。开发与本地验证可进行，**不授权本阶段提交、推送、部署、开放端口或切换生产入口**。正式唯一写入槽以PJM收尾消息为准；从届时干净最新main创建`feature/ERP-112-corporate-website`，不沿用历史写入锁。未来上线先验证完整ERP临时8081入口，再切默认IP官网，内部8080/4173和ERP数据保持不变。

## 当前发布交接：ERP-104 修复与官网需求归档（2026-11-25）

用户已明确授权交项目经理提交远端main并部署。[DEL-055交接](../../deliverables/pjm/ERP-104_RELEASE_HANDOFF_2026-11-25.md)记录本轮完整范围。分支`feature/ERP-104-material-ui-fixes`，基线`78eea545253685f1967b917d5936e4713e4c050f`，未提交/未暂存，无无关变更。Codex发送正式交接后由PJM唯一写入、提交、合并和部署，Codex停止修改且不跟踪线上。

前端`pnpm quality`通过：171文件/795测试、lint、类型检查及构建；追加E2E的定向eslint通过。样品/库存3场景通过，辅料测试定位修正后运营模块3场景通过，共覆盖1280/800/360三个尺寸；辅料两张截图已目视。根目录diff与文档路径检查通过。无后端/数据库/部署脚本改动，不运行其全量门禁。

官网仅需求文档DEL-054 v0.6，页面和8081入口尚未实现；本次不替换IP默认页面或放行端口。下面“待统一验证/仅本地授权”及v0.2–v0.5段落为开发阶段历史；当前验证和发布授权以本节及DEL-055为准。官网需PJM收尾释放干净写入槽后再开发。

## 新需求草案：九更公司官网与 ERP 域名分流（2026-11-25）

最新补充（v0.6，覆盖v0.5来源限制）：用户明确安全组与防火墙对所有外网放行TCP8081，取消员工来源IP白名单。方案要求IPv4 `0.0.0.0/0`；如实际提供公网IPv6入口则同时允许`::/0`；ERP登录与权限不变，不开放内部8080/4173或其他端口。VPN不作为访问前置条件。仅更新方案，未操作安全组、防火墙或线上服务；仍待用户发布指令交PJM实施。

最新补充（v0.5，覆盖下面过渡建议）：用户明确默认HTTP IP入口用于官网，需具体ERP替代访问方式。PRD建议新增Caddy临时8081入口，提供现有完整ERP前端与同源API，原内部8080/4173、应用与数据不动；先验证再切换IP官网。仅允许批准员工来源，HTTP明文不能等同加密或免备案；需要加密时用VPN/获准隧道，禁止复用受限部署账号。主域备案后验证正式ERP域名，再撤临时入口。端口占用/生产配置未核查，入口未开通，仅文档修订。文档本地链接及`git diff --check`通过。

最新补充（v0.4）：修正备案过渡期遗漏。官网先本地制作，现有ERP IP入口不提前覆盖；主域完成备案/接入、ERP子域实际可用后再将IP转官网。如确需提前展示IP官网，先落实并验证员工专用内部访问，不默认开放额外公网端口。保留现状不代表对IP访问合规性或持续可用性的保证。以下v0.3的IP官网规则为最终目标，切换前置条件以v0.4为准。此次仅文档修改。

最新补充（v0.3）：现阶段用于备案的静态企业展示，收敛为单页区块；用户授权Codex自主设计Logo、视觉和一般文案。HTTP公网IP应直接返回与官网域名相同的静态页面，不用手填默认端口，不跳ERP或IP HTTPS；具体IP不写入仓库。用户反馈现IP入口为ERP，待实施时核实并仅调整入口路由。新增IP资源/导航/接口隔离验收，ERP代码、内部服务和数据仍不动。备案公开时机按首次/接入/变更类型和接入商要求核实，不承诺页面即可通过备案。本文后续v0.2段落为上次方案演进记录，页面范围以v0.3为准。

用户最新要求覆盖此前端口约定：根域/www访问九更官网，erp.jiugengerp.cn访问现有九更ERP；不定义端口，优先保持当前ERP系统不变。[DEL-054 PRD](../01-product/JIUGENG_CORPORATE_WEBSITE_PRD_v0.1_DRAFT.md)已更新为v0.2（保留原文件路径），取消公网8080、官网固定80和后端迁至18080的旧方案。官网仍按跨境电商企业展示，保留五页首期建议、参考分析、素材清单。

方案仅在需要时调整DNS/域名绑定/证书及入口站点分流，建议标准HTTPS无显式端口访问。保留ERP应用、内部端口、systemd、健康/回退脚本和4173探针；官网独立资源、构建、发布和回退，检查工作流触发以避免官网提交意外发布ERP。若现有ERP已绑定目标子域则无需入口迁移；实际DNS、代理和证书由PJM实施前核实。仅更新文档/索引，未修改应用或线上配置；保留当前ERP-104未提交实现。文档链接检查与`git diff --check`通过，应用门禁不适用。

## 当前本地迭代：ERP-104 辅料成本与按钮/布局修复（2026-11-25）

PJM 已明确释放 ERP-103 写入槽，并授权 Codex 从干净 main `78eea545253685f1967b917d5936e4713e4c050f` 建立 `feature/ERP-104-material-ui-fixes`。当前唯一写入者 Codex；仅本地，未提交/未部署，不沿用上一轮发布授权。此前 `/tmp` 补丁等待状态已结束，本次已实际应用到项目并由 Vite 热更新。

- 辅料新增/编辑成本价统一两位显示，输入 5.6 离焦补为 5.60，新填写价格最多两位。历史六位金额仅格式化显示，未变更价格时不发送 costPrice，不因改名等操作回写舍入金额；原 pending 请求保持原载荷确认。后端精度/成本核算接口不改。
- 关联供应商表格透明按钮明确字色，备注/采购链接绿色、解除红色、供应商名称蓝色；修复白字继承。共享样式排查发现候选/开发主题的白底按钮同样未指定字色，已补深色，主按钮独立配色保留。
- material-fields 网格及标签顶部对齐，基础单位锁定说明不再拉伸同排规格输入框。真实组件修复前规格 53px 且向下偏移 13px；修复后两输入均 40px、同顶。

局部验证：frontend `pnpm exec vitest run src/components/MaterialEditor.test.ts` 7 项 PASS；`pnpm exec eslint src/components/MaterialEditor.vue src/components/MaterialEditor.test.ts src/views/LaunchWorkbenchView.vue --max-warnings=0` PASS；`node /tmp/erp104-material-ui-check.mjs` PASS（Chromium 1100×900，真实 MaterialEditor/主题、合成只读接口，验证 5.60、可读颜色、备注展开和字段位置；截图 `/tmp/erp104-material-fixed.png` 已目视）；根目录 `git diff --check` PASS。完整 quality、跨模块/多尺寸 UI 验证留用户要求交接时统一执行，不重复上一轮完整门禁。无后端/数据库/服务重启。

变更文件：`MaterialEditor.vue`、`MaterialEditor.test.ts`、`materialManagement.css`、`candidateWorkbench.css`、`LaunchWorkbenchView.vue`，以及本状态报告/UI 规范。本地全部未暂存、未提交，无无关改动。

### ERP-104 追加：样品管理面包屑（2026-11-25）

用户指出样品管理左上角面包屑错误。`frontend/src/router/index.ts` 删除误设的“产品管理”中间层和 `/products` 链接，现为“商品 / 样品管理”，与商品菜单下独立模块一致。仅修改路由展示元数据，路径、权限、组件及导航分组不变。`git diff --check` PASS；此小项不单独跑完整测试，随本迭代交接统一验证。用户消息的第 2 项尚未完整，已请求补充，未猜测修改范围。

## PJM 最新发布结果：ERP-103 / DEL-053（2026-11-24）

[PR #26](https://github.com/puhuisifang-svg/Jiugeng-Amazon-ERP/pull/26) 五项检查全通过，23:12:34正常合入 main `0a58e771d9cee076f18d3d72c234bff4b90cf658`；原子功能提交 `5ef6d7bfdab2fd933a2ecfa0f7c9dfe0f2075c5f`。[PR CI](https://github.com/puhuisifang-svg/Jiugeng-Amazon-ERP/actions/runs/45422203080)、[main CI](https://github.com/puhuisifang-svg/Jiugeng-Amazon-ERP/actions/runs/45422825153) 与 [Production](https://github.com/puhuisifang-svg/Jiugeng-Amazon-ERP/actions/runs/45422824958) 均成功，实际 SSH deploy 非跳过。

北京时间23:19:50迁移前数据库备份完成，23:20:19接收器验证后端健康UP与服务端完整SHA，23:21独立真实入口version.json返回相同完整SHA、首页200。V108/V109范围与备份机制已核对；生产迁移仅有精确构建/启用Flyway/健康启动的间接证据，未直查迁移表、删除计数或本次备份恢复。只回退JAR不能恢复历史，恢复须从迁移前备份提取限定范围，不能覆盖后续业务写入。

旧Production占锁未在本轮重现，无当前部署阻断；未删锁或绕过受限SSH。既有Caddy管理限制、Safari/实物打印/业务验收边界保留。完整证据见 [DEL-053实际发布结果](../../deliverables/pjm/ERP-103_CUSTOMS_FILES_LOCAL_2026-11-24.md)。仅归档一次，本归档提交最终部署状态记录在 `/tmp/jiugeng-pjm-release-progress.md`，不为文档自身SHA递归创建PR。下文为实施阶段快照，不能据此恢复旧写入锁或未发布结论。

## 当前发布交接：ERP-103 多模块体验修复（2026-11-24，DEL-053）

用户已明确授权交接项目经理、提交远端 main 并部署。分支 `feature/ERP-103-customs-files`，HEAD/基线 `a6b05352c2ac3e368e37364438561bd6b5c511d3`；81 个变更文件均属于本轮授权范围，全部未提交/未暂存。Codex 完成本地验证并发送交接后，唯一写入权移交 PJM；提交、合并、CI/CD 和发布状态由 PJM 负责，Codex 不跟踪线上。

累计涵盖报关文件状态/创建时间/预览、样品标签与历史/操作精简、供应商历史/删除 UI、产品 SKU/库存/辅料选择、采购选择弹窗、辅料关联供应商和全站未保存确认。前端 793 测试及 lint 通过，类型检查发现的 2 项已修正，受影响 10 测试/lint 和完整构建复验通过；后端 clean verify 449 项，448 通过、1 外部 OSS 探测未启用跳过。三尺寸应用 E2E 9 项、补充组件窄屏场景 12 项通过；文档链接和 diff 检查通过。

**发布前由 PJM 确认数据库备份**：V108/V109 清理用户要求的旧样品/供应商指定历史，保留其他审计和业务数据；仅回退应用不能恢复已删历史。本地已备份应用且验证范围，未操作生产。未做 Safari 实机/物理打印机出纸测试。完整范围、文件归属、准确命令和边界见 [DEL-053 最终交接](../../deliverables/pjm/ERP-103_CUSTOMS_FILES_LOCAL_2026-11-24.md)。以下追加段为历史实施记录，其中“待交接”状态由本段取代；未宣称已合并、部署或业务验收。

### 同迭代追加：样品管理五项修复（2026-11-24）

用户在 ERP-103 本地写入期间追加样品修复，沿用 Codex 独占写入及当前分支，不另切分支、不继承发布授权。样品标签每条固定 1 张、70×30mm 等比预览，导出含品名/库位；列表库位红字；新增/编辑取消原生 datalist 与建议查询，改直接输入并统一字段对齐；查看子体使用局部原生模态承载，位于样品/父体详情之上，保留共享子体 UI。

局部 ESLint、2 文件/22 项测试与单视口真实组件检查通过（PDF 实际页尺寸/单页/命名、预览比例、子体顶层及 Escape、表单对齐/无下拉、红字）。未运行完整门禁或视口矩阵，未重启服务/写业务数据。详见 DEL-053 追加记录；完整累计验证仍待用户要求交接。全部修改未提交、未暂存。

### 同迭代追加：样品列表精简与库位历史重置（2026-11-24）

用户追加去掉列表“来源 / 所属产品”列、详情来源标记，并要求只记录库位变更、删除旧历史。`SampleController` 仅在更新造成库位实际变化时追加只含 `locationCode` 的前后快照；新增/复制/其他字段修改/移除/恢复不再生成样品历史。仍保留原权限、版本冲突与幂等保护。

新增 V108 一次性清理迁移，用户明确授权的范围仅为 `BUSINESS` + `SAMPLE_LOCATION` + 五种旧样品动作；这是对旧样品记录的一次性重置，不改变其他模块追加式审计规则。本地完整数据库已备份，旧运行包/启动器保留；mysql 已应用 V108、目标旧历史剩余 0，样品数据与其他审计记录数量不变。新运行包 `sample-location-history-20261124`，健康 UP。线上未操作，须以后由 PJM 在发布前备份并应用迁移。

局部前端 2 文件/21 项、后端 2 类/11 项（含清理范围）及 ESLint 通过，完整累计门禁仍待交接；详细命令、备份位置和风险见 DEL-053 追加记录。当前所有改动未提交/未暂存，仅属本轮授权范围。

### 同迭代追加：样品操作入口精简（2026-11-24）

列表与弹窗“导出盘点清单”统一为“导出”；“已移除 / 返回在库登记”采用浅灰底灰字次要按钮。操作列去掉“复制到其他库位”，新增表单去掉“保存并继续”，成功保存直接关闭。保留旧版本待确认复制请求的原幂等键/载荷恢复，但不再自动继续新增。仅前端改动，无服务重启或数据库操作。

已调整相关测试覆盖，按用户节奏本次仅检查 diff；本次测试、样式与完整累计验证留到交接，之前 SamplesView 测试结果不代表本次修改已验。详见 DEL-053。

### 同迭代追加：供应商界面精简与旧历史清理（2026-11-24）

用户追加供应商四项修改，沿用当前独占分支和本地授权：去掉保存并继续、详情变更记录页签、删除弹窗的停用按钮，删除操作红字覆盖 Teleport 菜单。旧待确认保存请求仍可使用原键恢复，成功统一关闭；列表独立停用操作保留。

V109 按用户明确要求一次性清理供应商四类 BUSINESS 旧记录，保留同表的辅料关联与安全审计；后台后续审计机制/接口不变。已备份本地完整数据库、保留旧 JAR/启动器，mysql V109 success、目标旧记录为 0，供应商和其他审计数量相同；运行包 supplier-ui-history-20261124，健康 UP。生产未操作。

局部 8 项前端测试、1 项迁移范围测试、组件 ESLint 和实际弹出菜单红字检查通过；完整累计门禁仍待交接，详见 DEL-053。全部未提交/未暂存。

### 同迭代追加：SKU 编辑与库存操作修复（2026-11-24）

修复单 SKU 编辑弹窗的父体上下文/正文/底栏三行布局，采购/规格/辅料页签不再把空白撑到顶部；采购入口改当前页 RouterLink，避免新页丢失 sessionStorage 登录，未保存保护仍有效。关联辅料备注按钮绿色。

库存新操作只显示入库/出库；保留历史盘盈/盘亏名称、旧待确认请求恢复及原权限。操作原因前后端改选填，null/空白归一为空串，保留 500 字、数量/库存边界与幂等检查。局部前端 8 项、后端 5 项、ESLint 和一个真实组件浏览器场景通过；完整累计门禁待交接。新本地 mysql 包 product-sku-fixes-20261124 健康 UP，无新迁移/清理，全部未提交。

### 同迭代追加：选择辅料图片与筛选对齐（2026-11-24）

ProductMaterialPicker 增加 48×48 辅料缩略图/无图占位/点击预览，新增与编辑共享；搜索和类型选择器统一 32px 高度与对齐。局部 ESLint、diff 检查及单视口真实组件检查通过，完整累计验证待交接。前端热更新，无后端或数据改动，仍未提交。

### 同迭代追加：采购供应商/项目选择弹窗（2026-11-24）

采购新增、编辑共用 PurchasePicker；改专用 1000×720px 上限、随视口约束的稳定弹窗，搜索/分页固定，结果区独立滚动、表头吸顶、名称换行、48px 图片。仅前端布局修改，接口与选中业务不变。ESLint/diff 和单场景真实组件检查通过（两类弹窗尺寸、分页可见、翻页选择回填到父弹窗）；完整累计验证待交接，无提交/部署。

### 同迭代追加：辅料关联供应商布局（2026-11-24）

辅料新增/编辑的关联供应商改紧凑双列：搜索按钮合并到搜索框、共享选择器同高对齐，只有超过一页才显示分页，窄屏单列。移除“保存并继续”，成功保存关闭，旧待确认请求仍按原键恢复。局部组件 5 测试、单场景 UI/选择行为与差异检查通过；完整累计验证待交接。仅本地未提交/部署，详见 DEL-053。

### 同迭代追加：未保存确认 UI 统一（2026-11-24）

客户信息/商品、发票、旧报关、首页发货和成本旧提示改共享 UnsavedChangesDialog；原表单保持挂载。异步确认 host 支持显式 unsaved 选项，采购/收货、仓库、辅料/供应商/样品、开销、角色、设置、候选/开发等已有未保存提示同步统一；保留 pending 请求确认语义。11 个局部用例（含修正旧定位后的复查）、客户商品真实组件单场景 UI 检查及 diff 通过，完整累计验证待交接；未提交/部署。准确命令与证据见 DEL-053。

## PJM 发布核验：PDF 连续工作区与 HS 历史引用（2026-11-24，DEL-052）

- 维护主题按 DEL-052 归档，不新增或推断 ERP 编号。PJM 独占写入；全部 27 项经原子提交 `7dcf216a30695c7b13d3e597268a84a617beefa5` 发布，无共同作者推断。
- [PR #24](https://github.com/puhuisifang-svg/Jiugeng-Amazon-ERP/pull/24) 在五项必需检查成功后按保护规则合并（北京时间 19:10:33），应用 main SHA：`45242af63d2d53bf4ed36bfa63f5f0c845419fdb`。无强推、无管理员绕过。
- [PR CI](https://github.com/puhuisifang-svg/Jiugeng-Amazon-ERP/actions/runs/45381237422)、[main CI](https://github.com/puhuisifang-svg/Jiugeng-Amazon-ERP/actions/runs/45381744243) 成功；[Production](https://github.com/puhuisifang-svg/Jiugeng-Amazon-ERP/actions/runs/45381744230) 从精确 main SHA 重新构建，后端 443 项（0 失败/错误、1 外部 OSS 条件跳过）、前端 169 文件/778 项及全套 17 个浏览器场景通过。
- Production 实际 `Deploy through restricted SSH command` 成功，非 skipped：19:16:12 包 SHA-256 校验 OK，19:16:16 `Database backup completed`，19:16:40 `Deployment verified: 45242af63d2d53bf4ed36bfa63f5f0c845419fdb`。未使用本地开发 JAR。
- 19:17 独立请求既有线上入口 `/version.json`，HTTP 200 且完整 revision 匹配应用 main；首页 HTTP 200。服务健康为部署接收脚本检查 loopback `/actuator/health` UP 及前端版本的流水线证据；公网 `/actuator/health` 是 HTML，`/api/actuator/health` 为 404，不冒充公开健康 JSON 或直接服务器检查。
- 无新增迁移、依赖或生产代理配置；未直查生产 DB，不宣称迁移表核验。本地 mysql、上传文件、`jiugeng-mvp` 未重启或清理。ERP-101 Caddy 管理权限问题仍未解决，本轮不改代理。
- 本次发布证据以独立受保护文档 PR 归档；该 PR 合并会再次发布文档 SHA，PJM 须在同一执行会话验证最终实际部署、入口版本及 main 一致后结案。最终回执保存在执行进度/当日记忆及用户报告，不递归提交仅为记录自身 SHA 的文档。功能业务验收不由上线成功代替。

完整范围、直接/间接证据与限制见 [DEL-052 发布核验附录](../../deliverables/pjm/PDF_HS_RELEASE_HANDOFF_2026-11-24.md#pjm-发布核验附录2026-11-24)。以下均为开发/交接阶段快照，不能沿用历史未提交状态或写入权。

## 发布前交接快照：PDF 连续工作区与 HS 历史引用（2026-11-24，DEL-052）

用户已明确授权累计修改提交远端 main 并部署。分支 `codex/pdf-continuous-workspace`，HEAD/基线 `9954ef8bd15465ed82b545b98411e9cf07e2f892`，全部修改未提交、未暂存，均属本轮。任务编号协调异常和分支例外见实施记录；PJM 接管时补归档。

统一验证通过：前端 `pnpm quality` 169 文件 / 778 项；后端 `./mvnw clean verify` 443 项、0 失败/错误、1 外部 OSS 跳过；三尺寸 PDF/HS 与 15 页场景共 7 个独立 Chromium 检查通过，截图已目视。精确命令、测试假设修正、范围及未测限制见 [DEL-052 正式交接](../../deliverables/pjm/PDF_HS_RELEASE_HANDOFF_2026-11-24.md)。完整门禁后仅完善测试及文档，应用代码未变。

正式发送后唯一写入权归 PJM，负责提交/合并、CI/CD、部署和发布结果；Codex 停止修改，不跟踪线上发布。无新增迁移/代理配置，本地业务数据及服务保留。本节覆盖下方开发阶段的无发布授权/待验表述，不表示已合并或已上线。

## 同迭代追加：客户商品 HS 历史资料（2026-11-24，本地未提交）

用户要求输入同一 HS 时参考历史资料，必须弹窗修改/核对确认后带入。已新增按 HS 精确匹配、服务端分页和客户读取权限的历史查询；客户商品报关字段出现来源选择及可编辑核对弹窗。确认前不带入历史值，取消保留原输入，旧响应不能影响已切换商品；保留当前版本且带入为草稿，仍需保存客户商品。

沿用 `codex/pdf-continuous-workspace` 与 Codex 唯一写入权，保留 PDF 累计修改；无新提交/发布授权。前端局部 11 项及后端 1 项集成场景、定向 lint/类型通过，完整门禁留交接；本地后端已更新、mysql 健康 UP，无迁移/清数据。实现、运行包与待验见 [DEL-051](../../deliverables/pjm/CUSTOMER_HS_HISTORY_LOCAL_2026-11-24.md)。本地运行包以该记录为准，旧图片运行包记录是历史状态。

同迭代 UI 精修：HS 历史核对弹窗由 1000px 宽改为 640px 内容高度，两列横排紧凑字段；单一来源直接显示摘要，多来源才提供下拉；核对勾选和操作集中到固定底栏。保留确认后才带入的规则，本次仅差异检查，视觉验收仍待统一交接。

来源区域继续精修为浅底紧凑卡片，商品/客户/SKU/时间分层，明确“仅引用，不修改来源”。已按 GET 查询→独立草稿→当前商品保存路径复核只读来源；加强实际来源对象不变的断言，新增断言及视觉验证待交接统一执行。本次差异检查通过，仍未提交。

用户截图反馈后修复来源双控件：Teleport 从 body 改到 `#app`，恢复共享原生隐藏/自定义选择器样式。组件定向 5 项与两文件 lint 通过；Chromium 合成夹具已验证实际控件显示、鼠标/键盘、焦点与窄屏，截图已目视检查。仅本缺陷定向验收，完整累计门禁仍待交接，见 DEL-051 追加记录。

## 当前本地迭代：PDF 连续工作区（2026-11-24）

用户直接要求拆分默认提取合并、取消 12 页分组及参考 iLovePDF 连续编辑。已在 `codex/pdf-continuous-workspace` 本地实现：整份文档连续勾选；左缩略图、中间连续编辑、右属性栏；页码跳转、缩放与按需释放画布。基线 `9954ef8bd15465ed82b545b98411e9cf07e2f892`，开始时 main / origin main 一致且干净。上一轮修改已见于本地 PR #23 合并历史，线上不核查。

PJM 协调请求返回模型错误，未获新任务编号；本次用户直接维护主题、分支例外和唯一实际写入者 Codex 的依据见 [实施记录](../../deliverables/pjm/PDF_CONTINUOUS_WORKSPACE_LOCAL_2026-11-24.md)。修改全部未提交、未暂存，无发布授权。局部 15 项加导航 1 项、定向 lint 与类型检查通过；完整前端门禁、PDF 导出和多视口检查留待交接统一执行。没有后端/数据库修改或服务重启。

同迭代补充：右侧图层支持选目标页并“复制到该页”，成功自动定位并选中新副本，内容/样式保留且与原图层独立；原本页复制保留。新增用例待交接统一运行，前述 16 项结果不代表此追加已验证。详见同一实施记录的跨页复制节。

继续追加：图片转 PDF 默认跟随原图尺寸，96 DPI 仅换算纸张大小；移除普通 JPEG 的有损重压缩，旋转/镜像照片按原像素转无损 PNG。编辑支持 Ctrl/Command＋滚轮文档缩放，修复文字点击不聚焦导致键盘删除失效并增加“删除选中”；FBA 改为全部页连续预览。必要局部检查共 21 项通过（含跨页复制）、定向 lint/类型通过；完整门禁、真实浏览器与打印验证仍待交接，未提交/部署。

缩放补充：编辑与 FBA 连续预览最小比例由 50% 降至 10%，按钮和 Ctrl/Command＋滚轮共用 10%–200% 范围；本次仅差异检查，边界验证留到交接。

本节覆盖下方历史工作区归属；旧阶段“未提交”不代表当前 main 的上一轮功能仍未合并。

## 当前发布交接：图片性能与 PDF（2026-11-23，DEL-049）

用户已明确授权本轮全部累计改动提交远端 main 并部署。分支 `feature/ERP-101-image-performance`，基线 `dfba431424226445db34aa1c937915d85edef9dd`，实施内容仍未提交。完整前后端门禁通过，最终后端 lease 防护定向 8 项通过，三尺寸共 13 个独立浏览器场景通过；跳过与边界见 [DEL-049 发布交接](../../deliverables/pjm/IMAGE_PDF_RELEASE_HANDOFF_2026-11-23.md)。正式发送交接后唯一写入权归 PJM，Codex 停止修改，不跟踪线上发布。PJM 须同步实际 Caddy 缺省缓存头并使用最终 SHA 构建，不能复用本地旧 JAR。

本节覆盖下方开发阶段“无发布授权 / 待统一验证”的历史表述；不表示已提交、已合并、已部署或已业务验收。

## 当前追加：PDF 编辑收尾（2026-11-23，本地未提交）

用户仅批准清单 3、5、6、8、9、10、12、15、19。当前 Codex 在既有本地累计分支追加：共享 PDF 文档/两路按需渲染与取消重试、图片格式/容量/像素控制、共用文字绘制规则、紧凑工具栏和窄屏折叠、实时拆分摘要、当前页图层操作、轻量成功反馈及分类错误。原图片性能与 PDF 命名改动保留，无新增发布授权。

详细范围与文件入口见 [DEL-048 PDF 收尾](../../deliverables/pjm/PDF_CLOSEOUT_LOCAL_2026-11-23.md)。局部 15 项及补充 3 项通过，类型/定向 lint 检查通过；不代表完整门禁或真实浏览器视觉/性能验收。完整验证仍在用户要求交接时统一执行。本次仅前端与文档，未重启后端、未改数据库、未提交。

## 同迭代追加：PDF 编辑导出文件名（2026-11-23，本地未提交）

用户要求四个页签导出沿用源文件名。本次在现有 Codex 本地累计迭代追加，保留 ERP-101 未提交工作、不切分支。`frontend/src/views/PdfToolsView.vue` 的拆分单文件、编辑和 FBA 转换直接保留源 PDF 文件名（含扩展名大小写），去除操作/尺寸后缀；图片转 PDF 取当前排序第一张图片的文件主名并使用 `.pdf`；拆分多个文件时下载 `源名称.zip`，包内为 `源名称-001.pdf` 等，避免覆盖。图片移除或重排后使用新的首图名称，各页签文件仍相互独立。

仅调整下载命名，不改变 PDF 内容处理。`git diff --check` 通过；按既定节奏不单独跑完整门禁或回归，四个页签命名及多文件压缩包下载核对纳入交接待验。无提交、推送或发布。

## 当前本地迭代：ERP-101 图片性能（2026-11-23）

用户批准优化项 1–7，系统显示尺寸不变，外链转受控存储并跟踪来源变化，不接 CDN。PJM 分配 ERP-101 并交出唯一写入权；Codex 在 `feature/ERP-101-image-performance`，基线 `dfba431424226445db34aa1c937915d85edef9dd` 实施，全部修改尚未提交，无本轮发布授权。此前 DEL-046 已发布，不再是当前未提交工作。

已实现小尺寸衍生图及历史分批回填、鉴权后 ETag/304、前端可见区域优先队列与渐进预览、外链受控镜像及更新/失败保留、OSS 本地持久读缓存和匿名测速工具。列表/弹窗的展示尺寸保持原样。Safari 经用户授权开启开发功能并只读测速：抽样重复请求仍为 HTTP 200，60 KiB，约 1.3–3.4 秒，响应含重复 `no-store`；不能据此单独归因数据库或 OSS。Caddy 模板已改为缺省缓存头，实际生产配置须在获授权发布时由 PJM 同步。

局部后端 14 项、前端更新后 11 项通过，类型检查和变更文件 lint 通过；完整前后端门禁、跨模块回归和多尺寸视觉按约定留到用户要求交接时统一执行。本地数据库已备份，V107 成功，后端已更新并健康 UP；mock profile 的任务关闭配置是打包后的补充，不影响当前 mysql 运行行为。详细实现、精确命令、运行包/备份及待验项见 [DEL-047 实施记录](../../deliverables/pjm/ERP-101_IMAGE_PERFORMANCE_LOCAL_2026-11-23.md)。尚无优化后的线上速度结论。

## PJM 发布核验（2026-11-23，DEL-046）

- 用户授权累计范围已通过受保护 main 流程发布；唯一写入者为 PJM，Codex 不等待或跟踪发布。
- 累计功能提交：`6198abeae0ac997add5d0675d9db383e710cccf3`；测试扫描误报说明提交：`d915dc44d374037818bf4913a5cd1c25781882b3`。未修改业务逻辑、未绕过分支保护。
- [PR #21](https://github.com/puhuisifang-svg/Jiugeng-Amazon-ERP/pull/21) 于北京时间 14:14:24 合入；应用发布 main SHA：`aa5e69b391b230b414f194b97e21fcf5afb19624`。
- [PR CI](https://github.com/puhuisifang-svg/Jiugeng-Amazon-ERP/actions/runs/45122376121)、[main CI](https://github.com/puhuisifang-svg/Jiugeng-Amazon-ERP/actions/runs/45122775556) 全部通过。
- [Production](https://github.com/puhuisifang-svg/Jiugeng-Amazon-ERP/actions/runs/45122775575) 实际 SSH deploy 成功（不是 skipped），北京时间 14:21:30 记录数据库备份完成，14:21:56 记录目标 SHA 部署验证成功。
- 对此前用户实际使用入口独立读取 `/version.json`，返回上述完整 SHA；部署接收脚本同时验证后端 `/actuator/health` 为 UP 和服务器前端版本。未使用本地旧运行 JAR。
- V102–V106 随精确提交构建；生产 mysql 配置启用 Flyway 并在迁移时校验，成功启动与健康通过提供间接迁移证据。当前没有服务器管理身份，未直查生产 `flyway_schema_history`；CI 的 H2 V106 日志不当作生产数据库证据。
- 完整证据与边界见 [发布记录](../../deliverables/pjm/OPERATIONS_RELEASE_RESULT_2026-11-23.md)。本节覆盖下方开发阶段的未提交、待验、待发布描述；后续纯文档提交不改变本次应用范围。

## 本次累计迭代发布交接（2026-11-23）

用户已授权将辅料、供应商、采购及样品操作 UI 累计修改交项目经理提交远端 main 并部署。分支 `feature/ERP-100-material-closeout`，基线 `f87d7c98b9a32a1c73a4441abcfb8caad5f668d3`，Codex 交接前尚未提交。正式移交后唯一写入权归 PJM，Codex 停止修改和服务操作，不轮询、不等待线上发布。

后端完整门禁 430 项（0 失败/错误、1 个外部 OSS 探测跳过），补充采购用例 26 项通过；前端完整 quality 163 文件/748 项、类型及构建通过。菜单最后修正另有定向单测/静态检查及三尺寸浏览器复核，精确结果见 [DEL-046 交接记录](../../deliverables/pjm/OPERATIONS_RELEASE_HANDOFF_2026-11-23.md)。期初导入增加数量单位预校验，样品更多菜单修正滚动关闭时机。尚未宣称合并或线上发布成功，由 PJM 执行并回报。

下方各模块“待统一验证/未授权发布”仅为开发阶段快照；本轮最终范围、验证、运行包差异和移交约定以 DEL-046 为准。

## 当前累计追加：样品操作按钮统一（2026-11-23）

用户要求样品操作列“更多操作”与产品列表一致。已将样品触发按钮纳入 `styles/main.css` 的产品行操作共享样式（透明底、蓝灰文字、紧凑点击区），并在 `sampleManagement.css` 对齐标签打印与更多按钮、统一间距。继续复用 RowMoreActions，菜单功能和权限不变。沿用当前分支及 Codex 唯一写入权，保留辅料/供应商/采购累计改动，未提交、未推送。纯样式修正，`git diff --check` 检查；样品/产品视觉核对随累计迭代交接统一执行，不为此运行全量测试或重启后端。

## 当前累计追加：采购管理收尾（2026-11-22）

用户批准 1–22 及 24，排除 23（收货纠错/退货）、25（结算/表现统计）。沿用辅料/供应商累计分支与 Codex 唯一写入权，未提交、未推送。已实现数量精度与新产品收货入库、保存/收货原请求恢复、版本冲突、独立留图、权限依赖、服务端分页、紧凑列表/编辑/详情、复制导出及多批次运单与收货关联。历史产品收货不补账。

详见 [DEL-045 实施映射及验证边界](../../deliverables/pjm/PURCHASE_CLOSEOUT_LOCAL_2026-11-22.md)。局部后端 30 项、前端原 28 项及补充后的两文件 13 项通过，类型/定向 lint 通过；完整门禁仍在用户要求交接时统一执行。已备份本地 MySQL，运行包同步且 V105/V106 成功、健康 UP，原数据/上传文件保留。当前运行包以 DEL-045 为准，下面供应商与辅料运行记录是历史阶段。

## 当前累计追加：供应商管理收尾（2026-11-21）

用户批准第 1–20 项，P3 第 21–23 项不做。按用户持续本地累计的指令，保留 ERP-100 辅料改动，在同一分支 `feature/ERP-100-material-closeout` 追加供应商收尾，未新建或冒用 PJM 编号；唯一写入者仍为 Codex，尚未提交或推送。见 [DEL-044 实施映射与待验记录](../../deliverables/pjm/SUPPLIER_CLOSEOUT_LOCAL_2026-11-21.md)。

已实现服务端分页、紧凑列表/编辑/统一详情、原请求重试、版本冲突与关闭保护、状态/分类影响提示、分类关联分页、历史、复制、连续新增及 CSV。供应商局部后端 14 项、前端 11 项、类型/定向 lint 通过；完整门禁和回归仍在交接节点统一执行。迁移前已备份本地 MySQL，V104 成功，最新供应商运行包已启动，页面读取和空白弹窗可用；没有向本地业务库写入测试资料。供应商阶段运行版本见 DEL-044；当前已由 DEL-045 取代，下面 V102/V103 的记录为上一阶段。

## 当前本地迭代：ERP-100 辅料管理（2026-11-21）

用户批准清单 1–22，明确排除 P3 第 23、24 项。PJM 已交出唯一写入权，Codex 在 `feature/ERP-100-material-closeout` 本地实现，基线 `f87d7c98b9a32a1c73a4441abcfb8caad5f668d3`；尚无新提交、未推送。详细映射、文件、局部检查及待验项见 [DEL-043 辅料收尾](../../deliverables/pjm/ERP-100_MATERIAL_CLOSEOUT.md)。

已实现聚合原子保存、单位/版本/原请求保护、权限依赖、分页及按权限导出、紧凑列表/详情/供应商/库存、复制连续新增和测量单位四位精度。后端局部 15 项、前端局部 13 项、类型/定向 lint 通过，完整门禁与回归仍按用户约定在交接节点统一执行。本地已备份 MySQL、应用 V102/V103 并同步运行包，保留数据/文件；运行细节见实施记录。

本地 Git 历史确认产品/样品累计迭代已包含在当前 main 基线（合并 PR #20）；下方发布交接和更早“本地未提交”段落是历史快照，不代表今天的工作区。线上状态交 PJM，本轮不查询、不等待 CI/CD 或部署。

## 本次发布交接（2026-11-21，执行中）

用户已明确授权向项目经理交接全部累计修改、提交远端 main 并部署线上。Codex 统一验证通过：后端 412 项（0 失败/错误、1 个外部 OSS 探测跳过），前端 156 文件 / 750 项及构建通过，7 项浏览器冒烟覆盖三种尺寸。项目经理只读核实 main 需受保护 PR 流程、自动部署开关已开启。最终提交/PR/线上版本尚待 PJM 执行后记录，不将授权当作发布完成。

最终范围、修正、精确命令、运行版本差异与写入权见 [发布交接 DEL-042](../../deliverables/pjm/CATALOG_RELEASE_HANDOFF_2026-11-21.md)。下方“待交接”“未发布”等是此前阶段事实，当前以本节及后续 PJM 发布证据为准。

## 最新本地追加：样品管理收尾（2026-11-21）

用户批准 P1/P2 第 1–17 项，明确不做 P3（手动转 SKU、数量/借还、扫码盘点）。已实现权限依赖、批量图片信息/独立留图、逻辑移除/恢复、分页历史、原请求重试、紧凑表单/详情、筛选与库位建议、复制/连续新增、批量标签和盘点 CSV。见 [样品收尾实施与待验记录](../../deliverables/pjm/SAMPLE_CLOSEOUT_LOCAL_2026-11-21.md)。

2026-11-21 00:41 起已按用户指令备份本地 MySQL、同步样品 JAR，并在原 tmux 会话重启前后端。实查 V100/V101 均成功，后端健康 UP，前端 HTTP 200；Safari 样品页旧接口提示消失、新增弹窗可用，未提交业务写入。版本兼容保护继续保留。完整门禁、多视口及性能测量留到累计迭代交接。未提交、未发布线上。

用户同时长期授权本地持久服务按开发需要自主同步/重启，无需逐次确认；MySQL 数据随应用重启保留，迁移前备份。该规则已写入 AGENTS.md、README、接手指南与运行手册，线上发布/重启仍遵守明确授权及 PJM 流程。

## 最新本地追加：产品管理 UI（2026-11-20）

用户已批准全部 16 项 UI 优化，当前已写入本地未提交版本：库存分段操作/预计结存/流水、单子体紧凑编辑、图片区、详情、筛选条件标签、缺项定位、恢复及前后值日志统一展示。实施映射与待验项目见 [产品收尾记录的 UI 追加节](../../deliverables/pjm/PRODUCT_CLOSEOUT_LOCAL_2026-11-20.md#16-项-ui-优化追加2026-11-20同一迭代)。这是同一尚未交接的迭代；本轮不涉及新后端接口，未重启服务、未提交/发布。只做必要静态与本地布局检查，完整门禁、多视口和业务回归留到用户要求交接时执行。

## 最近核实的代码与发布

| 项目 | 2026-11-20 核实结果 |
| --- | --- |
| main 基线 | `b0a3d559fd7fd21ed5c0a70bfffed8eacfd4cc20`；本次 `git fetch origin main` 后本地基线与 origin/main 一致 |
| 合并记录 | [PR #19](https://github.com/puhuisifang-svg/Jiugeng-Amazon-ERP/pull/19)，Git 历史显示 2026-11-20 10:59:35 +08:00 合入 |
| Production | [运行 44664621971](https://github.com/puhuisifang-svg/Jiugeng-Amazon-ERP/actions/runs/44664621971) 最终执行成功；实际 SSH deploy 步骤于 2026-11-20 15:25:43 +08:00 完成 |
| 线上前端 | 本次只读调查用户 Safari 实际访问的产品管理入口；其 `/version.json` 返回与上述 main 相同的完整 SHA |
| 实际资源 | 该入口 HTML 引用 `index-DxaASYOj.js`；在线图片组件 `AuthenticatedCatalogImage-Plx_TAar.js` 含 IntersectionObserver，缓存逻辑在实际依赖中可见 |
| 本轮验证边界 | 核实发布记录和线上前端资源；未登录服务器核对运行 JAR / 数据库迁移，也未进行全系统业务验收或图片请求耗时测量 |

该发布成功记录包含后端门禁、前端 quality、浏览器冒烟、导入契约、打包和部署步骤成功。它们是该 SHA 的历史流水线结果，不是本次文档维护重新执行的测试。

## 当前实现范围

已存在产品/SKU、辅料关系与库存、供应商、采购收货、客户资料/商品/订单/发票、发货、财务、报关/货代/标签、工厂、样品与新品开发、候选池/监控采集、IAM/审计和导入导出实现。代码定位见[接手指南](../04-engineering/PROJECT_HANDOVER.md)。

仓库既有持久化业务和生产发布路径，也保留 mock/test 路径；不能再用“仅本地 synthetic MVP”概括全系统。各业务完整性、数据真实性和用户验收状态需按具体任务和环境确认，不从目录或菜单推断。

### 最近合入的行为变化

以下提交均包含在上述 main 基线中，提交详情可由 `git show <SHA>` 追溯：

| 提交 | 变化 |
| --- | --- |
| `fadabdddc61b7b576460c865b87aa089f1296f95` | 报关资料编辑入口迁到客户商品 |
| `0dcc75e7a058923ff0957ec62e8c588b523722e2` | 客户独立 SKU 编码，产品界面隐藏内部编码 |
| `7ac5a9a85472cd96ea5dabdaac5a1c5895b15b40` | 受保护图片缓存、懒加载和上传优化 |
| `5e6a5f2c57b8182be4428a46ef22ca574336f333` | 报关发货人与商品关联客户 |
| `c4c290fa1f3c1c8137b0c42cdfcdc4a731048b79` | 财务主体及账户历史关联客户 |
| `954adf71f3f5fd7449275071120c8fd01535d5c7` | 样品标签打印、FBA 标签空白裁剪 |

## 已知限制与待核实事项

1. **图片速度体验仍待定位。** 用户反馈产品管理图片加载优化似乎无效。本轮已确认优化包含在已发布版本及在线资源中；未证明用户当前已打开页面加载的是哪个 bundle，也未测量网络请求。缓存为 60 秒内存缓存，刷新清空；首次/重复加载要分别测量。此处登记后续排查线索，不等于新增修复任务已分派。
2. **历史规划与实现存在差异。** 总体架构、早期活跃范围契约、编码规则、Backlog 仍包含早期边界；不能把其历史批准状态理解为完整的当前实现说明。本轮建立入口并标注适用范围，尚未重写全部领域 PRD/API/数据字典或替 PJM 重新审批。
3. **运行环境仍需现场核实。** 2026-10-15 报告中的 Docker 拉取、GitHub 首次推送、业务输入门禁不能直接当作今天的阻塞；GitHub 发布已具新证据，其他旧风险未逐项复验。
4. **监控覆盖不夸大。** 历史 ERP-099 报告仅有限验收，价格实值、大店完整性、多日稳定性不能据历史局部成功认定全部解决；本轮未重新验证采集。
5. **文档维护不是自动运行。** 已建立入口、更新责任和交接模板；未新增定时任务或强制 CI 检查。后续实施者须在交接前更新，PJM 复核。

## 文档维护交接（本地整理已完成，待提交）

- 用户发布约定（2026-11-20）：后续系统修改先在本地完成和验证，等待用户明确发布指令，再由 Codex 交接项目经理提交/合并到 main 并部署线上。实现、测试完成不等于发布授权；不得提前推送、合并或触发生产发布。当前文档也保持本地状态。
- 目标：让新模型不依赖聊天记忆，快速了解现状并恢复未完成工作；不改业务代码。
- 任务标识：2026-11-20 项目接手文档维护（用户直接请求；没有新分配的 ERP 编号）。
- 分支：`codex/project-handover-docs`。本次为直接授权文档维护采用独立分支，未冒用 PJM 编号；后续 ERP 实施仍遵守 AGENTS 的任务分支规则。
- 基线：上表 main 完整 SHA；修改目前**未提交、未推送、未合并**。本次未授权发布。
- 修改范围：根 AGENTS/README、接手指南、本报告及历史归档、总索引、交付物总账、会话交接模板，以及旧文档入口适用范围提示。
- 决策：当前状态集中维护；旧报告保留归档；已批准历史规范不改写审批结论；不新增模型专属“记忆库”或定时自动化。
- 验证：见下方本次文档检查记录；未运行前后端完整门禁，因为未修改应用或发布脚本。未启动/重启服务，未读写业务数据。
- 工作区归属：本次结束时仅上述文档修改，无暂存和无其他任务修改；下一位接手者必须重新核对，不能将本段当作实时锁。
- 下一步：本地文档待用户复核；收到用户明确发布指令后再交接 PJM 提交、合并及部署。如继续图片性能调查，先核对实际页面版本，再测量首次/重复图片请求，不重复猜测是否漏发布。

## 当前任务：产品列表图片前置与 UI 优化

- 验证节奏更新（用户约定，2026-11-20）：以用户要求交接项目经理为小迭代结束节点，后续小功能先累计本地修改和待验证项，交接前统一验证受影响范围；期间仅为实际错误或关键行为不确定做必要局部检查。下方产品列表测试是约定前已完成的有效记录，不因本次规则文档更新重跑应用测试。交接时检查后续改动是否使既有结果失效；提交和发布仍须用户指令。
- 用户直接分配，2026-11-20；本地修改，等待用户验收与发布指令。
- 分支沿用 `codex/project-handover-docs`，基线不变，已有本会话文档改动完整保留；未切分支、提交、推送或发布。
- 图片位于勾选框后的第一业务列，兼容历史列偏好；优化父子层次、图片/缺图框、选中反馈和手机操作列。
- 本地验证：`pnpm quality` 152 文件 / 708 测试通过，lint/类型检查/构建通过；Playwright 4 项通过，三种尺寸截图已复核；`git diff --check` 通过。
- 完整范围、验证及后续动作见[本地实施交接](../../deliverables/pjm/PRODUCT_LIST_UI_LOCAL_2026-11-20.md)。后续接手应同时识别此前文档维护与本次前端修改，不把它们当作他人的未知改动。

## 本轮追加：账号展示去重与父子行层级

- 用户直接要求，2026-11-20；继续在同一分支、本会话写入权内修改，原有本地改动完整保留；未提交、未推送、未发布。
- `AdminLayout.vue` 原先直接拼接账号显示名与角色名，导致同名重复；现按名称去重并保留不同角色。
- `ProductsView.vue` 子体图片及缺图占位右移 24px，补充树形连接线、子体文字标识；父体浅蓝灰底与分组边界、子体白底，保留选中反馈。
- 本地已修改，待迭代统一验证。已补充账号展示用例和浏览器缩进/底色断言，尚未执行；前述 708 测试及 4 项浏览器结果是追加修改前的历史结果，不覆盖本轮新代码。
- 待交接统一检查：前端 quality、浏览器冒烟与三尺寸视觉验收（含图片隐藏、缺图、选择和账号换行）；无需后端门禁。具体记录见[本地实施交接](../../deliverables/pjm/PRODUCT_LIST_UI_LOCAL_2026-11-20.md)。

## 本轮追加：全系统图片预览统一

- 用户要求修正产品缩略图预览关闭按钮错位，参考样品管理，并排查其他模块、统一图片展示大小。
- 新增 `ImagePreviewDialog.vue`：原生顶层弹窗，独立标题栏和底栏，统一最大 840×680px、视口减 24px，图片等比完整显示。产品“保存图片”与下载错误提示保留。
- 已按代码入口检查并迁移产品/辅料/供应商/首页/成本及公共图片组件，覆盖样品、采购、客户商品、发货、监控和财务等使用方；详情预览补用原图。PDF、导出长图和资源历史文字弹窗保持各自展示规则。
- 本地已修改，待迭代统一验证；未运行应用测试或浏览器验收，前述历史测试结果不能用于本轮验收。统一验证范围扩大为公共图片组件、各接入页面与嵌套弹窗，详见[本地交接记录](../../deliverables/pjm/IMAGE_PREVIEW_UI_LOCAL_2026-11-20.md)。
- 原分支与基线不变，全部修改未提交、未推送、未合并、未发布；没有启动或重启服务。

## 本轮追加：子体详情可读性与父体删除保护

- 用户直接要求移除完整资料跳转、关联项显示名称、父体仅在子体全部删除后允许删除。
- `SkuDetailDialog.vue` 改为按需读取供应商关联和辅料关系接口中的名称，保留数量/采购链接等资料；增加权限、加载、重试和请求过期保护，移除父体跳转链接。
- `ProductsView.vue` 与 `CatalogDeleteDialog.vue` 阻止有子体父体的确认删除；`CatalogDeletionService.java` 在事务内锁定父体和未删除子体后校验，所有子体状态均阻止父体删除，返回 `409 PRODUCT_HAS_VARIANTS`，不触发级联逻辑删除。
- 本地已修改，待迭代统一验证；本轮累计验证范围现包括前端 quality、浏览器验收及**后端 `./mvnw clean verify`**。新测试及历史删除回归已适配“先删子体、再删父体”，尚未执行；无数据库迁移。
- 原分支、基线和写入归属不变，全部未提交、未发布。详见[本地交接](../../deliverables/pjm/SKU_DETAIL_DELETE_GUARD_LOCAL_2026-11-20.md)。

### 子体关联详情继续完善（同迭代追加）

- 用户要求采购/辅料展示更多资料并优化 UI，以及列表供应商/辅料列可点击查看。
- `SkuDetailDialog.vue` 按用户密度反馈改为紧凑表格，移除概览栏和大卡片；联系人/微信、采购/电话与辅料规格/用量/损耗率并排展示，备注、联系图片和更新时间按行展开，默认收起。保留辅料图片、类型及两种状态；损耗率按百分比显示。关系数据先展示，详情最多四个并发请求，局部失败可单独重试。
- `ProductsView.vue` 子体供应商/辅料数量可直接打开对应详情页签；普通详情保持基础信息入口，权限不足不开放资料入口。
- 增补直接打开页签、资料字段、展开/收起、损耗率、部分失败重试及列表入口测试；尚未执行。局部格式整理与 `git diff --check` 通过，浏览器紧凑表格与展开布局与累计前后端门禁留到交接统一执行。无新增后端变更，未提交或部署。详见前述[本地交接](../../deliverables/pjm/SKU_DETAIL_DELETE_GUARD_LOCAL_2026-11-20.md)追加记录。

### 产品新增与编辑的关联表单（同迭代追加）

- `ProductForm.vue` 的供应商/辅料区域改为可编辑紧凑表格，选择与数量同排、备注按需展开；辅料用量和单位同列，输入保持 40px，保存中禁用关系数据修改。共用表单覆盖新增、编辑、复制和单 SKU 编辑，原保存规则及损耗率比例不变。
- `ProductSupplierFilter.test.ts` 增补关系编辑、备注保留、校验定位、提交值及禁用/解除用例，尚未执行；仅局部格式检查和 `git diff --check` 通过。本地未提交、未部署，待迭代统一门禁和浏览器布局验收。详见[本地交接](../../deliverables/pjm/SKU_DETAIL_DELETE_GUARD_LOCAL_2026-11-20.md)。

### 产品管理收尾扩展（本地实现，待迭代统一验证）

用户批准按序实施 19 项收尾清单，并修订权限为角色依赖联动、库存为子体可操作及父体汇总、复制包含供应商与辅料。已完成本地代码与用例维护；具体行为、迁移及待验边界见[产品收尾实施记录](../../deliverables/pjm/PRODUCT_CLOSEOUT_LOCAL_2026-11-20.md)。继续同一未提交工作区，未授权发布。新增 V98 权限依赖回填和 V99 子体库存表；21:40 按用户故障修复指令同步本地后端并应用 V98/V99；原“产品不存在：page”已消除，当前 Safari 产品列表正常、后端 health 为 UP。针对性 8 个后端测试通过；完整门禁仍在交接前统一执行。线上未变更。

## 本次文档检查记录

2026-11-20 在仓库根目录执行：

- `git diff --check`：通过。
- `python3 -` 内联文档检查：通过；检查本次 14 份 Markdown 的 152 个本地链接，以及接手指南模块表的 60 个前后端路径，缺失项为 0。检查从 `git diff --name-only` 与 `git ls-files --others --exclude-standard` 收集文件，解析 Markdown 相对链接并用 `Path.exists()` 校验；模块表路径按前后端根目录分别检查。
- `git diff`、`git status --short` 和新增文件内容审阅：仅本次文档范围；无暂存、无业务代码改动或其他任务修改。

相对链接检查不验证外站可达性或锚点，不代表业务验收。未运行应用测试；前述 Production 成功记录仅作发布事实证据。

## 后续更新要求

状态更新必须带日期、基线完整 SHA、证据、适用环境和未核实范围。完成或取消的任务及时移入对应交接记录；替换大段历史状态时归档旧版。下一位模型应先读本报告，再按照[接手指南](../04-engineering/PROJECT_HANDOVER.md)定位代码，不能仅凭“上次已完成”继续合并或部署。

## 接收启动窗口修正（2026-11-26）

合并PR32后的main CI在TERM清理测试中出现5秒等待超时（PR和Production前置工具测试曾通过）。本地40次启动窗口复现出现2次相同失败：TERM早于接收子进程exec完成时可能被忽略。修复清理逻辑，在TERM后对同一个只负责临时文件接收的直接子进程补KILL并wait，不涉及应用服务；100次启动窗口测试及完整9项发布工具测试通过。此为真实修复，不靠重复运行原失败门禁放行。服务器目前仍是此前安装的2fb8852版本，新增清理修正尚未同步。

PJM尝试在浏览器门禁阶段取消恢复Production，但GitHub取消接口报500/无有效返回，未确认取消；重新读取时流程已进入实际deploy。未再次中断传输、未启动并行部署。最终结果另记。
