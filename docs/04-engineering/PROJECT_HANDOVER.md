# 九更 ERP 项目接手指南

| 字段 | 内容 |
| --- | --- |
| 项目 | 九更亚马逊 ERP |
| Owner | PJM（维护责任）；Codex（本次整理） |
| 版本 / 状态 | v0.1 / In Review |
| 更新时间 | 2026-11-21 |
| 审批人 | 待 PJM 复核；本文记录实现事实，不新增业务批准 |
| 核实基线 | 见[当前状态报告](../08-project-management/STATUS_REPORT.md)；此指南不是实时线上状态 |

## 先恢复上下文，再开始任务

1. 阅读根目录 [AGENTS.md](../../AGENTS.md)，检查实际分支、HEAD 和工作区。确认当前任务、修改归属和写入权；共享工作区不得并行改动。
2. 阅读状态报告的“当前交接与后续工作”，分清用户目标、已完成内容与尚未核实的事项。不能把待排查事项当作已授权的实施任务。
3. 从下表定位业务页面、API 和后端模块，读取相关任务记录、代码与测试。不要先逐份读取所有 Sprint 0 文档。
4. 有分歧时分别记下“历史批准规则”“当前实现”“现场观察”。代码可以证明实现，不自动证明业务批准；旧规划也不能覆盖近期明确的用户需求。
5. 多文件或跨层变更先给出短计划，按 [AGENTS.md](../../AGENTS.md) 实施和交接。合并和线上发布/重启仍由任务授权及 PJM 流程决定；用户已于 2026-11-21 长期授权按开发需要自主重启本地持久服务，无需逐次确认。

本次用户直接授权文档维护，没有新增 PJM ERP 编号；维护分支例外和当前未提交状态记在状态报告中，不应复制为后续业务任务的通用分支规则。

## 当前产品管理增量（2026-11-20，本地待验）

接手未提交工作时先读 [19 项实施记录](../../deliverables/pjm/PRODUCT_CLOSEOUT_LOCAL_2026-11-20.md)。不要仅按 main 推断本地行为：

- `product/ProductPageService` 与 `ProductPageController` 为产品主列表按父体 SQL 分页入口；页面返回全部子体及匹配 ID，父体库存不能只累计匹配子体。旧 `/skus` 仍兼容其他选择器调用。
- `product/ProductInventoryService` / `ProductInventoryController` 与 V99 管理整数件数、幂等请求和追加流水；不自动衔接旧采购/发货数据。列表 UI 与 `ProductInventoryDialog.vue` 使用 `api/productManagement.ts`。
- `ProductService.updateSku` 及对应 PUT 路由只保存目标子体。新增/复制子体仍走聚合保存；复制供应商、辅料和规格，不复制身份、库存或流水。
- `iam/PermissionCatalog` 运行时展开依赖，V98 回填已有角色并增加授权版本；产品只读依赖关联只读，维护依赖关联维护。库存入出库、产品删除依旧单独授权。
- `catalogdeletion/CatalogRestoreService` 和 `ProductRestoreDialog.vue` 查询/恢复逻辑删除资料；先父后子，客户商品不自动恢复。非零库存子体不能删除。
- 代码、迁移、用例已经维护；2026-11-20 21:40 本地故障修复已同步运行 JAR 并应用 V98/V99，针对性 8 个测试通过。性能和完整业务流程尚待交接验收，线上未更新。运行入口与故障证据见实施记录；不得再假定旧 JAR 提供新的分页接口。

### 同迭代 UI 追加入口

16 项 UI 优化已本地实现，详见上述实施记录末节。视觉样式集中于 `frontend/src/styles/productManagement.css`，作用域 `.product-ui`；`ProductUiState` 统一产品状态反馈，`ProductHistoryTable` 统一父/子体及兼容详情的前后值日志。`ProductForm.focusField` 从列表缺项入口定位字段，单个已存子体编辑使用父体摘要。库存表单新增预览和就地提示，但 API 与服务端记账规则不变；后续模型不要恢复满屏空库存窗口或单子体父体只读整栏。完整验证仍在迭代交接时进行。

## 当前样品管理增量（2026-11-21，本地待验）

接手先读 [17 项样品收尾记录](../../deliverables/pjm/SAMPLE_CLOSEOUT_LOCAL_2026-11-21.md)，范围明确排除 P3。样品列表不再逐行读取完整产品详情，`ProductLookup.samplePresentations` 批量提供当前关联信息；`CatalogImageGateway.copyForSample` 独立留存受控图片。样品名称/规格是登记资料，当前产品名称/状态独立展示，历史未留图记录明确标注。

V100 包含逻辑删除、恢复唯一约束、图片快照和幂等请求；V101 补 samples.read → products.read 及传递只读依赖。`SampleController` 提供版本能力探测、详情/分页历史、库位建议、重复记录定位、勾选读取和 CSV 导出。前端 `SamplesView`、`SampleDetailDialog`、`SampleLabelDialog` 使用样品命名空间 `sampleManagement.css`，待确认请求按登录用户隔离并保持原请求编号。不能移除旧后端写保护；先核实本地运行版本和迁移状态。本地是否同步以实施记录最新证据为准。

## 系统与业务地图

以下是代码定位地图，表示存在实现入口，不表示全系统每条流程都已完成生产验收。

前端相对根目录为 `frontend/src/`；后端模块相对根目录为 `deliverables/backend/sprint-0/jiugeng-erp-backend/src/main/java/com/jiugeng/erp/`。

| 业务 | 主要前端入口 | 后端模块 |
| --- | --- | --- |
| 产品、SKU、辅料、成本、供应商 | `views/ProductsView.vue`、`ProductDetailView.vue`、`MaterialsView.vue`、`ProductCostsView.vue`、`SuppliersView.vue` | `product`、`materialinventory`、`supplier` |
| 客户资料、客户商品、订单、发票 | `views/CustomerManagementView.vue`、`CustomerOrdersView.vue`、`OrderInvoicesView.vue` | `customer` |
| 采购、收货、辅料流水 | `views/PurchasesView.vue`、`PurchaseOrderDetailView.vue`、`InventoryLedgerView.vue` | `purchase`、`materialinventory` |
| 首页、待发货、今日发货、发货统计 | `views/DashboardView.vue`、`DashboardMaintenanceView.vue`、`ShipmentStatisticsView.vue` | `dashboard` |
| 报关、货代文件、HS、标签 | `views/CustomsDocumentsView.vue`；`api/customsDocuments.ts`、`forwarders.ts`、`cartonLabels.ts` | `customs`、`forwarder`、`hs`、`cartonlabel` |
| 财务账单、工厂业务 | `views/FinanceBillsView.vue`、`FactoryExpensesView.vue`；`api/finance.ts`、`factory.ts` | `finance`、`factory` |
| 样品、新品开发、上架工作台 | `views/SamplesView.vue`、`DevelopmentWorkbenchView.vue`、`LaunchWorkbenchView.vue` | `sample`、`development`、`launch` |
| 候选池、产品监控、店铺上新、采集 | `views/CandidateWorkbenchView.vue`、`ProductMonitoringView.vue`、`WatchStoresView.vue` | `selection`、`monitoring`、`watch`、`collection` |
| 用户、角色、权限、审计 | `views/UsersView.vue`、`RolesView.vue`、`AuditView.vue`；`state/session.ts` | `iam` |
| 导入、受保护图片、对象存储、删除关联检查 | `views/ImportsView.vue`；`api/catalog.ts`、`components/AuthenticatedCatalogImage.vue` | `importexport`、`catalogmedia`、`objectstorage`、`catalogdeletion` |

完整页面与入口以 [router/index.ts](../../frontend/src/router/index.ts) 和 [config/navigation.ts](../../frontend/src/config/navigation.ts) 为准；菜单是否显示受权限影响，不能据菜单缺失断定功能未实现。角色和 capability 请核对 [capabilities.ts](../../frontend/src/auth/capabilities.ts)、session 及后端 IAM，不把早期“三角色”概括当成当前权限全集。

## 关键业务规则与近期变化

- 产品父体 / 子体提供内部主档和关联标识；客户商品维护客户独立的 SKU 编码，原客户产品编码仍为独立字段。产品界面不再要求人工填写内部编码。不要根据早期编码规范恢复旧字段。
- 报关资料的主要编辑入口已迁到客户商品，按商品独立维护；关联资料、历史快照和兼容字段不能因为界面迁移而直接删除。
- 财务账单主体、账户历史已增加客户关联。客户、商品、订单、发货、发票之间存在关联，不可把删除操作简化为任意删行。
- 产品图片使用受保护图片组件；有外部图片 URL 的路径需要分别排查。新优化包含可视区域懒加载、重复请求合并、会话隔离的短期内存缓存和上传优化。
- 业务时间显示统一北京时间；日期字段保留日历日期，时间戳提交仍采用明确的时区转换。历史采集计划的指定时区不可为统一显示而重排。
- UI 沿用紧凑导航、共享筛选/日期控件、TableViewport、ListPagination 和弹窗键盘导航；完整规则及后续修订见 [UI_STANDARD.md](../ui/UI_STANDARD.md)。

## 技术与数据路径

- 前端采用 Vue 3、TypeScript、Vite；共享壳层为 [AdminLayout.vue](../../frontend/src/layouts/AdminLayout.vue)，样式入口为 [main.css](../../frontend/src/styles/main.css)，API 封装在 `frontend/src/api/`。工具版本以 [package.json](../../frontend/package.json) 与锁文件为准。
- 后端为 Java 21、Spring Boot、Spring Modulith 模块化单体，使用 JDBC 和 Flyway。模块依赖应通过公开接口/事件，不引入对其他领域内部类型的依赖。版本见 [pom.xml](../../deliverables/backend/sprint-0/jiugeng-erp-backend/pom.xml)。
- 数据迁移既有 `src/main/resources/db/migration/` 的 SQL，也有 `src/main/java/db/migration/` 的 Java 迁移。仓库迁移文件存在不证明线上已执行；数据库 schema 版本需独立核实。不要修改已经发布的迁移来“修正历史”。
- `mock`、`test`、`mysql` 路径并存；`mockdata` 下的模拟控制器由 mock profile 激活。生产服务配置指定 mysql。不能把模拟数据或本地测试结果当成线上业务事实。
- [runtime.ts](../../frontend/src/config/runtime.ts) 要求配置 API 地址；开发认证默认 mock，生产默认 password。生产构建使用 `/api` 和 password，见 `frontend/.env.production`。开发时接入真实认证需显式核对模式，不把访问地址和认证模式混为一谈。
- 生产方案由 [infra/production](../../infra/production/README.md) 和 [Production 工作流](../../.github/workflows/production.yml) 定义：GitHub 构建，受限 SSH 部署，Ubuntu 上 systemd / Caddy / MySQL；图片和附件通过本地持久目录及对象存储实现。旧架构中的 Redis、MinIO、Compose 是历史规划/环境信息，不能未经配置和依赖核实就当作当前生产必需组件。
- 运行凭据、真实账户、业务数据和主机私有配置不纳入接手文档。只保留配置项名称和安全的代码路径，避免打印 `.env` 私有文件或配置目录内容。

## 验证入口

**验证节奏（用户约定，2026-11-20）：** 用户要求交接项目经理时，才结束一个小迭代并统一验证累计改动。实施期间默认不逐项运行完整测试、回归或多尺寸浏览器验收，仅在实际错误或关键行为不确定时做必要局部检查。记录“本地已修改，待迭代统一验证”及待验证清单；交接前执行受影响门禁，复用未被后续改动影响的有效结果。此约定不改变提交与发布须等待用户指令的边界。

以下命令在对应目录运行。安装依赖、构建和测试可能写文件，须遵守共享写入权，不在调研阶段自动执行。

| 场景 | 目录 | 命令 |
| --- | --- | --- |
| 前端完整门禁 | `frontend/` | `pnpm quality`（lint、Vitest、类型检查、构建） |
| 浏览器构建冒烟 | `frontend/` | `pnpm exec playwright test`（预先构建并安装所需浏览器） |
| 后端完整门禁 | 后端工程目录 | `./mvnw clean verify` |
| 导入契约 | `deliverables/backend/sprint-0/import-contract/tests/` | 依次执行 `npm ci --ignore-scripts`、`npm test`、`python3 validate_contract.py` |
| 发布工具改动 | 仓库根目录 | `python3 -m unittest discover -s scripts/production -p 'test_*.py'`；对改动 shell 执行 `bash -n` |
| 全部改动 | 仓库根目录 | `git diff --check`，审阅完整 diff 和 status |
| 仅文档改动 | 仓库根目录 | 检查新增/修改链接、代码路径、元数据、历史保留和证据一致性；无需运行应用完整门禁 |

Vitest 测试多数与源码同目录；浏览器冒烟在 `frontend/e2e/`，使用独立 4188 端口；后端测试在 `src/test/java/`。CI 的具体门禁见 [.github/workflows/ci.yml](../../.github/workflows/ci.yml)。本地持久服务沿用 `jiugeng-mvp` tmux 和既有配置，用户已授权开发期间自主同步、启动和重启，无需逐次确认。MySQL 数据在应用重启后保留；禁止把重启当作清库授权。涉及迁移先备份、保留旧运行包，重启后做必要健康与接口检查；纯文档维护无需重启。

## “合入了，但线上好像没生效”的只读排查

1. 找到具体功能提交，使用 `git merge-base --is-ancestor <功能SHA> <目标SHA>` 确认包含关系；退出码 0 才表示祖先关系成立。
2. 使用 `gh run list --workflow production.yml --limit 5 --json databaseId,headSha,status,conclusion,url`，再用 `gh run view <运行ID> --json jobs,headSha,url` 看实际 deploy 步骤。工作流整体显示成功但发布 job 被跳过，不证明已发布；手动重跑要看最终那次执行。
3. 核对用户实际访问的入口，读取该入口 `/version.json`，比较完整 SHA。不要仅检查本地 HEAD 或另一入口的版本；公网地址使用受控渠道获取，不在文档写入生产标识或凭据。
4. 如仍存疑，从该入口 `index.html` 指向的实际资源核对代码特征。版本文件代表前端发布版本，不能单独证明浏览器已加载新 bundle、所有后端实例一致或数据库已完成迁移。
5. 确认部署后再检查具体问题：浏览器旧页面、首次/重复请求、资源体积、接口耗时、错误码、缓存失效、代理/对象存储链路。记录实际测量，不能用“可能缓存”代替结论。

`release.py` 将完整 SHA 写入 `frontend/version.json` 并打包校验清单；`receive.sh` 检查健康和服务端前端版本。发布门禁包括仓库变量 `PRODUCTION_DEPLOY_ENABLED` 和服务器端开关；是否启用须现场核对。只读排查不授权修改门禁、重跑发布或重启线上服务。

图片缓存案例：`utils/catalogImageCache.ts` 的缓存是会话隔离、内存级、60 秒 TTL，限制约 32 MiB / 80 条；页面刷新清空，过期后再访问重新请求。该优化不承诺首次加载瞬间完成。应分别测量首次访问、60 秒内重复访问及过期后的访问。

## 文档如何保持有效

| 发生的变化 | 必须同步的记录 |
| --- | --- |
| 功能或业务规则变化 | 对应任务记录；涉及 UI 则更新 UI 规范；状态报告记录影响和验证结果 |
| 模块、代码入口、运行方式变化 | 本指南；必要时更新架构/开发/运维文档并说明旧基线差异 |
| 合并或发布 | 状态报告写入完整 SHA、证据链接、核实时间和范围；不把测试通过当作发布 |
| 中断、换模型、尚有未完成工作 | [交接模板](../../templates/SESSION_HANDOFF_TEMPLATE.md)；状态报告指向具体交接文件 |
| 新增正式文档 | [总索引](../INDEX.md) 与 [交付物总账](../DELIVERABLE_REGISTER.md) |

每个任务的实施者负责在交接前更新记录，PJM 负责复核与验收。入口保持简短，版本事实集中到状态报告。旧报告归档；已批准的历史文档保留原文和审批状态，新发现的差异以带日期的说明补充。不得把历史报告中的未解决风险原样复制成今天仍然存在的故障。

这些规则依赖后续任务执行者遵守，目前没有新增定时自动维护或强制 CI 文档同步检查。
