# 九更 ERP 项目接手指南

| 字段 | 内容 |
| --- | --- |
| 项目 | 九更亚马逊 ERP |
| Owner | PJM（维护责任）；Codex（本次整理） |
| 版本 / 状态 | v0.1 / In Review |
| 更新时间 | 2026-11-25 |
| 审批人 | 待 PJM 复核；本文记录实现事实，不新增业务批准 |
| 核实基线 | 见[当前状态报告](../08-project-management/STATUS_REPORT.md)；此指南不是实时线上状态 |

## 最新发布交接：ERP-112 官网及辅料/采购累计迭代（2026-11-25）

用户已授权提交远端 main 并部署，先读 [DEL-056 交接](../../deliverables/pjm/ERP112_MATERIAL_PURCHASE_RELEASE_HANDOFF_2026-11-25.md)。前端完整门禁802测试、后端456项（1项OSS探针跳过）、三尺寸受影响页面浏览器检查及官网构建/契约均通过。当前仍未提交；正式交接消息接受后由 PJM 独占写入、提交/合并/发布，Codex 不跟踪线上结果。保留现有官网与域名分流，不能用旧模板覆盖当前 Caddy。下方未授权/待统一验证为历史阶段。

## 最新维护入口：ERP-112 官网首次上线（2026-11-25）

用户直接授权 Codex 完成首次部署与服务器配置，后续自动 CI/CD 归 PJM。官网源码与独立构建在 [website](../../website/README.md)，生产使用官网专用 `/srv/jiugeng-website/current`；默认 IP 的 HTTP 80 为官网，IP:8081 为原 ERP 完整前端/API，内部 8080/4173 不变。2026-11-25 21:54 已启用根域官网、www跳根域、erp子域ERP及三域HTTPS，原IP/8081按用户确认保留。Caddy管理证书续期，全局采用 `auto_https disable_redirects` 与显式域名HTTP跳转，避免改变IP行为。精确版本、配置备份、验证边界和当前未提交工作区见[状态报告最新节](../08-project-management/STATUS_REPORT.md)。

后续维护需区分官网的 `website-version.json` 与 ERP 的 `version.json`。现有 ERP main push 流程尚未隔离官网路径，PJM 接管自动发布前需落实路径检查与独立产物发布；不要用 ERP 应用发布替代官网部署，也不要覆盖当前 Caddy 分流。下面“尚无官网代码 / 尚未实施”为历史阶段。

## 最新交接入口：ERP-104 与官网需求（2026-11-25，历史阶段）

先读[ERP-104发布交接](../../deliverables/pjm/ERP-104_RELEASE_HANDOFF_2026-11-25.md)和状态报告最新节。本轮辅料价格/字色/布局及样品面包屑已统一验证，用户授权PJM提交main并部署；正式交接后PJM独占写入，Codex不等待线上结果。不能沿用下方历史任务写入权。

[官网PRD DEL-054](../01-product/JIUGENG_CORPORATE_WEBSITE_PRD_v0.1_DRAFT.md)正文v0.6为最新需求：静态展示、自主设计、默认IP官网、临时完整ERP8081对所有外网放行、内部后端不变。尚无官网代码或入口配置实施；不要执行旧版18080端口迁移建议。PJM结束本轮并释放干净工作区后，才分配官网开发。

## 当前 ERP-103 报关文件入口（2026-11-24，本地）

先读 [DEL-053](../../deliverables/pjm/ERP-103_CUSTOMS_FILES_LOCAL_2026-11-24.md) 末尾 PJM 实际发布结果：本轮已受保护合并并完成应用生产发布；准确 SHA、备份/迁移证据边界与归档进展见状态报告，不沿用历史 Codex 写入槽。`CustomsDocuments` 列表返回创建时间及截图/发票标记并按创建时间倒序；`CustomsFileStatus` 统一两处标签。`CustomsAttachmentPreview` 在 `#app` 内显示鉴权图/PDF/Excel，PDF 复用连续只读文档。Excel 由 customs 内部 `CustomsSpreadsheetPreview` 提取缓存值，不调用外部预览服务；预览接口只读，现有下载接口继续保留。完整验证、应用发布及迁移证据边界见 DEL-053 末尾；下方本地实施段为历史技术记录。

## 2026-11-24 累计发布交接入口

PJM 更新：DEL-052 已经受保护 PR #24 合并并完成应用生产发布；精确 SHA、CI/实际部署/入口版本及限制集中在[状态报告](../08-project-management/STATUS_REPORT.md)与 DEL-052 发布核验附录。按维护主题归档，不新增 ERP 编号。以下“本地未提交/待验证”是阶段快照；不代表当前锁或发布状态。归档文档 PR 的最终部署由同一 PJM 会话闭合，不要求 Codex 等待。

先读 [DEL-052](../../deliverables/pjm/PDF_HS_RELEASE_HANDOFF_2026-11-24.md) 和状态报告最新节：PDF 连续工作区、快捷操作/原图保真及客户商品 HS 历史引用已完成统一验证，用户授权提交 main / 部署。正式发送后 PJM 唯一写入并负责发布；Codex 不跟踪上线。下文“待交接”是开发阶段记录，不能沿用其写入锁或发布状态。

HS 核对弹窗 Teleport 到 `#app`，保持统一控件主题作用域。切勿直接移到 body：会导致原生 select 未隐藏、自定义触发器样式丢失；真实页面浏览器场景在 `frontend/e2e/customs-history.smoke.ts`，验证确认后只保存当前商品。PDF 全页/复制/缩放/导出场景在 `frontend/e2e/pdf-closeout.smoke.ts`。

## 客户商品 HS 历史引用入口（2026-11-24，本地）

[DEL-051 实施记录](../../deliverables/pjm/CUSTOMER_HS_HISTORY_LOCAL_2026-11-24.md) 与 PDF 属同一未提交迭代。`CustomerCustomsFields` 管历史查询、防迟到响应及独立核对草稿，复用 `CustomsFields` 的字段查询和 AccessDialog；只有确认才更新客户商品表单。后端入口为 customer 模块 `/customers/products/customs-history`，只读取已保存客户商品，不查询或改写其他模块内部类型。当前本地运行包见 DEL-051；完整验证/发布仍等交接指令。

## 当前 PDF 工作区入口（2026-11-24，本地未提交）

先读 [连续工作区实施记录](../../deliverables/pjm/PDF_CONTINUOUS_WORKSPACE_LOCAL_2026-11-24.md) 和状态报告最新节确认分支/写入权；旧 DEL-049 的未提交描述已是历史阶段。`PdfContinuousDocument.vue` 管多页布局、缩略图导航、滚动页码及显示缩放；`PdfToolsView.vue` 持有按页标注和导出数据；`PdfPagePreview.vue` 的 lazy/size 管离屏释放与比例占位。不要把缩放值写回 PDF 坐标或恢复 12 页分组。默认拆分方式为提取合并。 图片转 PDF 默认跟随尺寸；96 DPI 只换算纸张，不代表像素重采样。`pdfImageInput.ts` 普通 JPEG/PNG 保留原字节，EXIF 方向修正使用全像素无损 PNG，界面小图不得进入导出。FBA 全页预览也复用 `PdfContinuousDocument` 的 readonly 模式，Ctrl/Command＋滚轮在画布内缩放。删除键统一在 PDF 工作区处理，必须避开可编辑输入。完整门禁和视觉验收仍待本轮交接。

## 最新交接入口：图片性能与 PDF（2026-11-23）

用户已明确授权本轮全部累计改动提交远端 main 并部署。分支 `feature/ERP-101-image-performance`，基线 `dfba431424226445db34aa1c937915d85edef9dd`，实施内容仍未提交。完整前后端门禁通过，最终后端 lease 防护定向 8 项通过，三尺寸共 13 个独立浏览器场景通过；跳过与边界见 [DEL-049 发布交接](../../deliverables/pjm/IMAGE_PDF_RELEASE_HANDOFF_2026-11-23.md)。正式发送交接后唯一写入权归 PJM，Codex 停止修改，不跟踪线上发布。PJM 须同步实际 Caddy 缺省缓存头并使用最终 SHA 构建，不能复用本地旧 JAR。

本节覆盖下方开发阶段“无发布授权 / 待统一验证”的历史表述；不表示已提交、已合并、已部署或已业务验收。

## 先恢复上下文，再开始任务

1. 阅读根目录 [AGENTS.md](../../AGENTS.md)，检查实际分支、HEAD 和工作区。确认当前任务、修改归属和写入权；共享工作区不得并行改动。
2. 阅读状态报告的“当前交接与后续工作”，分清用户目标、已完成内容与尚未核实的事项。不能把待排查事项当作已授权的实施任务。
3. 从下表定位业务页面、API 和后端模块，读取相关任务记录、代码与测试。不要先逐份读取所有 Sprint 0 文档。
4. 有分歧时分别记下“历史批准规则”“当前实现”“现场观察”。代码可以证明实现，不自动证明业务批准；旧规划也不能覆盖近期明确的用户需求。
5. 多文件或跨层变更先给出短计划，按 [AGENTS.md](../../AGENTS.md) 实施和交接。合并和线上发布/重启仍由任务授权及 PJM 流程决定；用户已于 2026-11-21 长期授权按开发需要自主重启本地持久服务，无需逐次确认。

本次用户直接授权文档维护，没有新增 PJM ERP 编号；维护分支例外和当前未提交状态记在状态报告中，不应复制为后续业务任务的通用分支规则。

## 当前同迭代 PDF 追加（2026-11-23，本地未提交）

先读 [DEL-048 PDF 收尾记录](../../deliverables/pjm/PDF_CLOSEOUT_LOCAL_2026-11-23.md)，仅批准 3、5、6、8、9、10、12、15、19，其他清单项未实施。沿用下节图片性能分支及 Codex 写入权，保留所有累计修改和 PDF 源文件命名约定，完整验证仍待用户交接指令。

`PdfToolsView.vue` 保持四个页签独立内存状态；`pdfPreviewPool.ts` 管共享 PDF.js 文档引用和两路渲染，`PdfPagePreview` 管可见区域/取消/重试；`pdfImageInput` 管 JPEG/PNG 编码及导入预算，预览小图不能误用于导出。`PdfTextLayer` 与导出共用 `pdfTextLayout` 的 PDF 点坐标文字绘制。图层数组顺序就是绘制顺序，复制图片必须拥有独立 Blob URL；释放共享文档时不得中断其他持有者。本模块不上传原文件、不保存草稿，无后端迁移。

## 当前图片性能迭代（ERP-101，2026-11-23，本地未提交）

接手先读 [DEL-047 图片性能实施记录](../../deliverables/pjm/ERP-101_IMAGE_PERFORMANCE_LOCAL_2026-11-23.md)。当前分支 `feature/ERP-101-image-performance`，基线 `dfba431424226445db34aa1c937915d85edef9dd`，PJM 明确分配任务和 Codex 唯一写入权；用户批准 1–7、保持显示大小、不接 CDN。以下旧迭代的未提交状态已经过时，当前待验和运行包以 DEL-047 为准。

图片调查从 `catalogmedia/CatalogImageVariants`（历史衍生图回填）、`ImageHttpResponse`（鉴权后的私有缓存）、`ExternalImageService/Fetcher`（外链镜像/条件更新/来源安全）、`LocalCatalogImageStore`（内存与持久读缓存）开始。各领域公开 `ExternalImageSource` 提供自己的 URL 和权限，禁止直接依赖别的领域内部类型。V107 新增镜像/衍生图/清理/草稿授权记录；原受控图片与独立业务快照不改写。

前端统一入口 `AuthenticatedCatalogImage`、`api/catalog.ts` 与 `utils/catalogImageCache.ts`。真实认证环境外链经后端受控存储；保留的 mock 环境仍直接显示模拟外链且关闭后台抓取。URL 字段变化立即重新解析；同 URL 内容默认每小时条件检查、至少每日完整检查，失败保留上次成功内容。此前“外链只保存 URL”描述仅代表历史行为，镜像并非独立冻结快照。列表/弹窗的视觉尺寸不变。

生产代理旧配置强制 `no-store` 会抵消后端缓存。仓库 Caddy 模板已使用 `?Cache-Control` 仅在响应缺少该头时兜底；未来 PJM 发布需检查实际配置并校验/重载，不应认为 JAR 部署自动完成代理变更。本轮仅本地实现，待用户交接指令再统一验证，无提交或部署授权。

## 2026-11-23 累计迭代交接入口

用户已授权发布辅料、供应商、采购及样品操作 UI，最终统一验证与写入权移交见 [DEL-046](../../deliverables/pjm/OPERATIONS_RELEASE_HANDOFF_2026-11-23.md)。正式移交后提交/合并/CI/CD/发布核实归 PJM，Codex 不继续编辑或跟踪发布。以下开发阶段的未提交/待验描述需结合该记录读取。

交接修复：`InventoryOpeningImportService` 对期初数量和安全库存按档案单位预校验，避免计数单位小数到提交才失败；`RowMoreActions` 等布局定位完成再聚焦，并只在滚动导致触发按钮位置变化时关闭，忽略弹层自身及已完成定位的排队滚动事件。保留菜单内部滚动和 Escape 焦点恢复。

## 当前采购管理追加（2026-11-22，本地待统一验收）

先读 [DEL-045 采购收尾](../../deliverables/pjm/PURCHASE_CLOSEOUT_LOCAL_2026-11-22.md)：1–22 与 24 已实现；23/25 排除。辅料、供应商、采购仍是同一未提交累计迭代，不新开写入槽。当前本地运行包和 V105/V106 证据以 DEL-045 为准。

后端 `PurchaseManagementController` 承担 SQL 分页/保存去重/搜索选择/导出/批次/分页历史；`PurchaseCloseout` 管数量、留存图片和批次收货。`ProductPurchaseInventory` 是公开产品入库接口，新收货与库存同事务；旧产品收货不补账。档案停用不阻断原约定收货，但新增/增加数量继续验证有效关联。已有发货或收货后禁止编辑明细/删除；批次数量不可改，本轮不做退货纠错。

前端 `PurchasesView`、`PurchaseOrderDetailView` 和首页共用 `PurchaseReceiveFlow`；`purchasePending` 按用户隔离原请求恢复，不能对结果不明操作生成新请求。`PurchaseShipmentsDialog` 管多运单及分项数量；`PurchasePicker` 分页搜索；`PurchaseRelatedItem` 复用只读关联档案/库存。`purchaseAmounts` 保留四位单价，行金额精确舍入；`.purchase-ui` 是本模块样式边界。外链图只留 URL，受控图独立复制；新产品单位件，旧单位不偷偷换算。完整验收留到用户要求交接时。

## 当前供应商管理追加（2026-11-21，本地待统一验收）

先读 [DEL-044 供应商收尾](../../deliverables/pjm/SUPPLIER_CLOSEOUT_LOCAL_2026-11-21.md)，范围 1–20、排除 P3；沿用当前辅料累计分支和 Codex 写入权，未提交。前端 `SuppliersView` 接入服务端分页，`SupplierEditor` 保留原请求与冲突草稿；`SupplierDetailDialog` 是列表、产品和辅料共用只读入口，`SupplierRelations` 按业务权限与类型分页，删除检查也复用。

后端新增 `supplier/SupplierManagementController` `/manage` 接口与 V104 幂等表。旧 API 保留兼容，新增影响确认和原请求保护适用于新管理接口；历史电话/微信审计继续脱敏。`CatalogImageGateway.copyForSupplier` 独立复制受控图，不能共享来源资产。样式限定 `.supplier-ui`，备注展开而非撑高行。完整质量/回归在用户要求交接时执行；供应商阶段运行包和备份见 DEL-044，当前包已由 DEL-045 取代。

## 当前辅料管理增量（ERP-100，2026-11-21，本地待统一验收）

先读 [DEL-043 实施与待验记录](../../deliverables/pjm/ERP-100_MATERIAL_CLOSEOUT.md)。当前分支 `feature/ERP-100-material-closeout`，基线 `f87d7c98b9a32a1c73a4441abcfb8caad5f668d3`，用户批准 1–22、排除 23/24，Codex 独占写入，尚未提交。下方产品/样品增量已在该基线中，保留其技术地图，不继承历史未提交状态。

`MaterialsView` 按认证模式分流 `MaterialsManagement` 与保留的 `MaterialsMockView`。新接口集中在 `api/materialManagement.ts`、后端 `materialinventory/MaterialManagementController`，通过 `/manage/capabilities` 避免旧运行包误写。编辑器用聚合资料与单事务保存，不恢复逐个供应商/成本分步保存；原请求恢复按用户隔离。版本覆盖新管理接口，旧选择器兼容 API 仍保留。

`MaterialEditor/DetailDialog/InventoryDialog/SupplierDetail/Image` 使用 `.materials-ui` 紧凑样式；列表 SQL 分页及当前页余额/成本，供应商搜索和详情页签按需加载。`MaterialQuantity` 与前端 `ui/materialQuantity.ts` 明确测量单位四位/计数单位整数，包装换算使用精确字符串；基础单位使用后不得通过编辑或导入切换。

V102 增加版本与幂等记录，V103 回填权限依赖及既有删除能力；不要把依赖补全理解成授予库存出入库/成本修改权限。`SupplierAssignmentGateway`、`CatalogImageGateway` 是公开跨模块接口。`PurchasesView` 只接收人工可修改的安全库存差额，不含已排除的报价/自动采购建议。完整验证与具体运行包见实施记录，不能把局部用例通过视为发布验收。

用户最新协作规则：系统修改先本地累计，用户要求交接时统一验证；之后提交/合并/部署与发布结果由 PJM 处理，Codex 不轮询、不等待线上发布。本地持久服务可自主同步/重启，迁移前备份，保留业务数据和文件。

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

图片缓存案例（ERP-101 本地实现）：`utils/catalogImageCache.ts` 是会话隔离的 60 秒内存缓存，限制 32 MiB / 200 条；页面刷新清空。普通有效图片另有浏览器私有缓存，过期/刷新仍向服务端鉴权并用 ETag 校验，可返回 304；待绑定和敏感用途图片仍 no-store。应分别测量首次访问、内存命中、刷新后的 304 及来源更新；不能只看网络请求数量。`window.jiugengImageDiagnostics()` 与 `scripts/diagnostics/catalog-image-performance.js` 输出匿名耗时/大小，不输出图片地址或账号信息。

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


### 样品库位历史新规则（2026-11-24，本地 ERP-103 同迭代追加）

`sample/SampleController.recordLocationChange` 是样品记录的唯一写入口：仅库位实际修改时追加 `SAMPLE_UPDATE`，快照只有 `locationCode`。新增、复制、移除、恢复及其他字段不记入样品变更记录。前端 `sampleHistory.ts` 只解释库位；列表不展示来源/所属产品列，详情不展示来源标记，关联产品查看入口仍保留。

V108 是用户明确要求的一次性旧样品历史清理，限定共用 `audit_event` 的样品 BUSINESS 五类动作，不清空整个审计表。迁移前必须备份；本地已应用，生产未应用，PJM 发布时需审查清理范围并确认备份。旧历史无法通过回退 JAR 恢复，只能从迁移前备份恢复对应范围，不能整库覆盖后续业务。实施与验证证据见 [DEL-053](../../deliverables/pjm/ERP-103_CUSTOMS_FILES_LOCAL_2026-11-24.md)。


### 供应商详情精简与旧记录清理（2026-11-24，本地同迭代）

供应商详情仅保留基础资料/关联业务；SupplierEditor 成功保存统一关闭，不再保存并继续；SupplierDeleteDialog 仅做删除及关联检查，停用走列表独立入口。删除菜单 Teleport 到 body，红字样式须覆盖该位置。

V109 用户授权一次性清除 SUPPLIER_CREATE/UPDATE/DELETE/ARCHIVED_LINK_REMOVE 的 BUSINESS 旧记录；不能仅按 resource_type SUPPLIER 清表，因为 `MATERIAL_SUPPLIERS_REPLACE` 辅料记录也使用该资源类型。后台未来操作审计与接口保留。本地已备份/应用，生产待 PJM 获授权后备份并迁移。详见 [DEL-053](../../deliverables/pjm/ERP-103_CUSTOMS_FILES_LOCAL_2026-11-24.md)。


### SKU 编辑布局与库存原因选填（2026-11-24，本地同迭代）

单 SKU 编辑 `ProductForm` 多一个父体上下文，`productManagement.css` 用三行 grid 防止顶部被撑开；供应商维护链接使用同页路由保留 sessionStorage，既有离开路由的草稿保护仍有效。`ProductInventoryDialog` 隐藏新盘盈/盘亏入口，但 operations 字典/旧 pending 恢复保留；`ProductInventoryController/Service` 允许原因 null/空白并归一为空串，仍校验最多 500 字与请求重复一致性，无 schema 变更。当前本地运行包为 product-sku-fixes-20261124，证据见 [DEL-053](../../deliverables/pjm/ERP-103_CUSTOMS_FILES_LOCAL_2026-11-24.md)。


### 未保存确认入口（2026-11-24，本地 ERP-103 追加）

关闭草稿使用 `frontend/src/components/UnsavedChangesDialog.vue`（AccessDialog 已接入）。异步路由/刷新/返回保护通过 `frontend/src/state/uiConfirmation.ts` 的 `confirmAction(message, { unsaved: true, discardLabel })`，由 UiConfirmationHost 呈现同一组件；不用页面私有确认卡或自动按文本猜测类型。原表单保持挂载、Esc/继续不丢输入；结果不明的幂等 pending 请求仍按独立语义确认。本地未提交状态和局部验证见 DEL-053，完整门禁留交接。


### 辅料成本价简化（2026-11-25，本地待发布）

同迭代补充：MaterialManagementController.impact 不再因单纯价格记录禁删；MaterialService.delete 在真实业务引用检查后，通过 CostService.saveField 清除当前价格并追加审计，再删除主数据，全部同事务。历史核价数据无需迁移或手工先清价格；历史审计保留。采购图片生成位于 frontend/src/ui/purchaseImageExport.ts，备注来源为采购单 remark，随每条明细导出红色加粗文字。

用户确认官网 ERP-112 已停止编辑，授权合入当前本地迭代。MaterialEditor 通过聚合接口可选 `cost: { netPrice, version }` 保存；缺省表示保持原值，金额 null 表示清空。CostService.saveField/alignUnit 同步金额与单位、追加成本审计及辅料 MATERIAL_COST_UPDATE 历史，无成本查看权限时隐藏历史金额。原 material.costPrice 和旧请求哈希格式保留用于待确认请求重试；simpleCostVersion 能力标记避免新前端连接旧后端时丢价格。

基础单位锁定不再由 material_standard_cost 单独触发；库存、采购、产品用量及 sku_cost_version 引用仍保护单位。导入单位修正也同步成本单位，无新迁移。辅料保存复用 utils/requestId.ts 兼容 HTTP；其他页面直接 randomUUID 调用未在此轮扩展修复。运行包与检查见状态报告最新节。
