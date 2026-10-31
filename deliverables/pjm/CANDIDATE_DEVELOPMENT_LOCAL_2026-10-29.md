# 候选评估与新品开发：本地交付记录

## 授权与版本

用户于 2026-10-29 直接授权开发和本地部署，无须项目经理审批；提交 main 仍由项目经理协助。本次没有新的 ERP 编号，沿用当前共享分支 `fix/ERP-099-product-detail-ui`，未切换分支。基准 HEAD：`33122a4c08831689db5454320143fb6fc6235089`。

**改动尚未提交，没有 push 或合并 main。** 原有产品监控详情 UI、对应测试及两份 PRD 是本次开始前的未提交改动，已保留，没有替其他工作提交。共享工作区仍为脏状态，不宣称工作区已清空。

## 本地使用

保持原端口：前端 `http://localhost:5173`，后端 `http://127.0.0.1:8080`。

- 选品 → 候选池：产品监控/上新监控转入、手动 ASIN、非亚马逊产品。录入销售证据、规格、客户需求、改进要求、成本、评分和风险调查，再提交评审。
- 候选审批通过，在同一事务创建开发草稿并保留批准基线；详情可双向跳转。
- 商品 → 产品管理 → 新品开发：确认负责人和计划，依次完成方案、供应与打样、样品验证、定型风险复核、商品/SKU 交接。
- 手动添加新品需求需要独立立项审核；旧项目可以显式初始化到新工作台，不补造历史审批。
- 风险评估包含商标、版权、外观设计、功能结构专利和类目合规。记录调查市场、检索过程、证据、判断依据、授权范围及期限；有疑点或过期授权不能送审通过。
- 风险调查及最终判断由人员完成。没有接入专利库自动检索或自动法律结论。审核当前沿用管理员权限；编辑仍使用选品/商品写权限。

## 主要文件与行为

- `frontend/src/views/LaunchWorkbenchView.vue`、`frontend/src/api/launch.ts`、`frontend/src/components/launch/`：共用列表/详情工作台、分组页签、结构化资料、附件、成本计算、阶段和审批弹窗、变更对照与版本记录、未保存保护。
- `frontend/src/router/index.ts`：候选池、新品开发列表与详情接入，并修正详情面包屑。
- `frontend/src/views/ProductMonitoringView.vue`：产品监控加入候选入口，沿用现有来源链。
- `launch/LaunchWorkspace.java`、`LaunchRules.java`、`LaunchAttachments.java`：乐观版本、状态转换、风险/阶段门槛、成本计算和受权限保护的证据文件。
- `selection/CandidateWorkbench.java`、`CandidateCollectionTarget.java`、`SelectionService.java`、`SelectionResearch.java`：候选接口、前台数据采集观测、来源与评估分离、旧接口不可绕过评审。
- `development/DevelopmentWorkbench.java`、`DevelopmentService.java`：开发接口、批准基线、方案变更、打样和定型交接；保存名称/负责人后列表和搜索立即同步。
- `collection/AmazonPublicAdapter.java`、`CollectionService.java`：复用亚马逊前台采集引擎，允许候选部分字段观测，不将未知值当零。
- V55、V56、V57：工作台、不可覆盖版本记录、非 ASIN 候选支持、观测及证据附件。
- 新增 `LaunchWorkbenchTest.java`、`LaunchWorkbenchView.test.ts`；既有测试适配新建记录评审门槛。上新详情测试固定合成时钟，避免日期变化导致失效。

## 验证

- `ERP_BUILD_OUT_DIR=/tmp/launch-workbench-build pnpm --dir frontend quality`：lint、类型检查、91 文件 / 365 测试、构建通过。
- `./mvnw clean verify`：260 项，0 失败、0 错误、1 环境跳过，BUILD SUCCESS。唯一环境跳过为未设置 `ERP_OSS_PROBE` 的外部 OSS 连通性探测。
- MySQL：备份恢复到临时数据库，迁移 V54 → V57 和认证 CRUD 演练成功；正式本地数据库已迁移。
- 本地 MySQL 合成流程：非亚马逊候选 → 风险复核 → 送审 → 批准 → 自动开发草稿 → 开始开发成功。20 USD 售价、8 USD 成本与 30% 合计费率，贡献 6 USD、利润率 30%。
- 浏览器：新建后详情跳转、面包屑、候选到开发跳转、阶段推进、缺少报价阻断、管理员风险操作、列表分页；1280×800、800×600、360px 宽度检查，页面无全局横向溢出。
- 合成候选/开发记录已清理，追加审计和版本历史保留；临时验收账号停用、会话撤销；临时数据库和 18080 演练服务已移除。既有业务记录未清理。

## 部署与恢复材料

本地数据库备份：`.local/backups/launch-workbench-20261029/before.sql.gz`；同目录保存原后端启动脚本。启动入口 `.local/runtime/run-backend.sh`，tmux `jiugeng-mvp:backend`；前端仍为原 Vite 服务 `jiugeng-mvp:frontend`。

后端制品 `.local/releases/backend/launch-workbench-20261029/backend.jar`。数据库已经发生 V55–V57 升级，若需要回退应连同备份进行评估，不能仅替换旧 jar 并声称回退完成。未修改公网 Caddy 构建或隧道；本记录只证明本地版本。

## 当前范围限制

本版交付核心闭环，不代表 PRD 的每项扩展设计均已实现：独立审批权限配置、同来源拆分多个候选方案、高级组合筛选/导出、免打样等例外审批及独立经营分析还未提供。当前必须项要求实际验证通过；不存在免打样捷径。仅提供美国站选择，采集字段受亚马逊前台可见内容限制。

本版没有采购下单、付款或亚马逊发布动作。完成开发只表示资料、验收和自有商品关联完成。

最终部署确认：后端健康 `UP`，前端 HTTP 200；`git diff --check` 通过。制品 SHA256：`7cd38b12845c5842b7399a1b53b3c57e75073747f684dbb2fc315dd960f60da2`。未暂存任何文件，未引入本任务以外的新修改；原有未提交交接文件仍保留。

## 追加修复：首页新品待开发联动

用户直接要求修复；沿用原分支，改动仍未提交，无 main 操作。

原因：首页只读取 dashboard_pending_product 独立手工清单。新增 development 模块公开的只读 DevelopmentWorklist 投影，dashboard 服务合并未完成开发项目与原手工待办；不复制开发数据或反向写入项目。待接手、待立项审核、开发中、待完成审核、已暂停纳入，完成和终止排除。按预计完成日期排序，未安排日期置后；首页显示前10条，数量是完整统计。

首页显示项目编号、状态、阶段、负责人、计划日期，缺少日期显示“待安排”，不误判逾期；有商品读取权限可点名称进入开发详情。主入口改为新品开发，旧手工待办保留单独入口。进入首页/窗口重新聚焦/每60秒/手工刷新重新读取当前数据。

本次文件：development/DevelopmentWorklist.java；dashboard/DashboardController.java、DashboardService.java、DashboardTypes.java；DashboardControllerTest.java；frontend/src/api/dashboard.ts、views/DashboardView.vue、DashboardFormalView.test.ts。

验证：后端 ./mvnw clean verify 通过，261项、0失败、0错误、1环境跳过；前端 ERP_BUILD_OUT_DIR=/tmp/launch-home-build pnpm --dir frontend quality 通过，366项测试，lint、类型检查及构建通过。新增回归覆盖非终态/终态筛选、资料同步、详情链接、无日期和旧手工待办。测试清理同时隔离开发工作台的测试夹具，避免其他测试项目污染首页总数。

原5173/8080端口已部署；浏览器确认现有2个开发项目显示及详情跳转成功，未修改业务记录。临时验收账号已停用并撤销会话。后端健康UP。无新增迁移；原版jar保留在launch-workbench-20261029目录。本次制品：.local/releases/backend/launch-home-20261029/backend.jar；SHA256：82e5ba253d3c9458e820ea312b3e95626aa6f16d39e07885090c00ebe27f1fd9。

仍保留前序未提交文件，未暂存任何文件；git diff --check通过。本次未更新公网静态构建。

## 生产端口径修正（2026-10-29）

用户指出工作台是生产端清单，只有通过的项目可见，要求同时修改首页及“工作台 / 新品待开发管理”。本次按“立项通过后交给生产端开展开发”解释实现，已向用户说明该口径；最终验收通过不是本次准入点。

- 新增 ProductionDevelopmentView.vue 替换原菜单的手工维护界面，保持原路径与权限。只读显示已通过标识、项目编号、开发状态、阶段、负责人、计划日期和说明；可跳转原开发详情。提供关键词/日期搜索、固定分页，不提供无审核手工新增/完成按钮。
- 首页与完整管理页共用 DevelopmentWorklist：候选状态 APPROVED 或开发立项 APPROVE 追加记录作为批准依据；只包含待接手、进行中、待完成审核，排除待立项审核、暂停、终止、已完成。普通草稿和无审批的历史项目不因状态名被放行。
- 首页不再混入 dashboard_pending_product 手工记录，原数据不删除。首页管理链接恢复至工作台新品待开发管理。
- 新增 /api/v1/dashboard/production-products 分页查询，沿用 dashboard.pending-products.manage 权限；新增参数校验400响应处理。
- 测试覆盖审批前后准入、状态排除、候选真实审批联动、页面筛选和详情入口、未安排日期，以及手工记录不能混入。前端质量门禁：368项通过。

文件：DevelopmentWorklist.java、DashboardService.java、DashboardController.java、DashboardExceptionHandler.java、DashboardControllerTest.java、LaunchWorkbenchTest.java；frontend/src/api/dashboard.ts、router/index.ts、views/DashboardView.vue、DashboardFormalView.test.ts、新增 ProductionDevelopmentView.vue / .test.ts。当前分支不变，所有改动未提交；保留前序文件，无 main 操作。

生产端部署确认：原5173/8080端口，后端健康UP；制品 production-approved-20261029/backend.jar，SHA256 `bd9996b9a50c270f20a844d980d6c487e3261ced6e404846af1a9dcdc42e133a`。后端完整门禁261项、0失败、0错误、1环境跳过。浏览器确认首页与管理页一致，仅1个已批准项目，未审核项目不再出现；800px及360px布局、管理入口跳转通过。原业务数据未修改；临时QA账号已停用。无新迁移、未暂存和提交，前序未提交文件继续保留。

命名调整：按用户要求，首页“新品待开发”改为“样品待开发”，工作台维护菜单、路由标题、面包屑及管理入口改为“样品待开发管理”，空状态同步为样品。更改 DashboardView.vue、router/index.ts、兼容旧维护组件标题及对应已有测试文案；前端完整 quality 通过（368测试、lint、typecheck、build），原5173服务已返回新文案。无后端修改，无服务重启；git diff --check通过，未提交main，保留前序未提交文件。
