# ERP-103：报关及多模块体验修复累计交接

- 项目：九更亚马逊 ERP
- Owner / 写入交接：Codex 已完成本地实施与统一验证；向 PJM 发送交接后由 PJM 独占接管
- 状态：v0.2 / Ready for PJM Review，本地统一验证完成，全部未提交
- 日期：2026-11-24，Asia/Shanghai
- 授权：用户于 2026-11-24 明确要求交接项目经理、提交远端 main 并部署；提交/合并/CI/CD/发布验证由 PJM 执行，Codex 不跟踪线上

## 任务与工作区

用户要求：报关列表状态与文件上传弹窗首行分别显示申报订单截图/发票的具体上传情况，已上传绿色、待上传黄色；列表显示创建时间并按创建时间倒序；图片与发票点击预览而非直接下载。

开始时 main 干净，基线 `a6b05352c2ac3e368e37364438561bd6b5c511d3`。通过 OpenClaw `agent:jiugeng_pjm:main` run `customs-files-20261124-slot` 获明确回复：“任务号 ERP-103；上轮 PJM 写入已结束，当前 Codex 获得独占本地写入槽；仅本地修改，不提交、推送或部署”。PJM 不再编辑、测试、切分支或重启。

已执行 `git fetch origin main`，从最新 origin/main 建立 `feature/ERP-103-customs-files`；HEAD 保持上述基线。所有变更未提交/未暂存，仅属于本任务。没有继承上一轮发布授权或旧 dirty 锁。

## 已完成与代码入口

- `CustomsDocuments.java` 的 Summary 新增 `createdAt`、`screenshotUploaded`、`invoiceUploaded`；列表 SQL 在分页前按 `created_at DESC,id DESC` 稳定排序。类别标记在同一个分页查询里读取，不由浏览器逐行查询附件。保留 `updatedAt`、原总完成状态及筛选规则，上传/修改仍更新版本和更新时间，不改创建时间。无数据迁移。
- `CustomsDocumentsView.vue` 改显示创建时间；列表与 `CustomsFilesDialog.vue` 首行复用新 `CustomsFileStatus.vue`，截图/发票分别用绿色“✓ 已上传”和黄色“待上传”标签，文字和颜色共同表达状态。列表作废标记仍保留；加载或旧接口缺字段不会误报待上传。
- `CustomsFilesDialog.vue` 点击文件名或“预览”打开新 `CustomsAttachmentPreview.vue`；“下载”仍是独立操作。预览复用 AccessDialog，Teleport 到 `#app` 保持统一选择器与主题作用域；不把鉴权 token 放进 URL，也不调用第三方 Office 查看服务。
- PNG/JPEG 用鉴权 Blob URL 在固定文档区域等比完整显示；PDF 复用 `PdfContinuousDocument` 的只读全页预览、缩略图与缩放；加载错误可重试，关闭会中止请求、释放 Blob URL，迟到响应不会重新创建资源，下载仅由单独按钮触发。
- XLS/XLSX 使用新只读 `GET /customs-documents/{documentId}/attachments/{id}/preview`，复用现有 POI，不增依赖。`CustomsSpreadsheetPreview.java` 提取文本/缓存公式结果，支持切换工作表；不执行宏、公式重算或外链。保持 `products.read` 权限、附件/单据归属及已删除单据检查，响应 no-store，不修改附件、版本或源档。
- Excel 是内容预览，不承诺复刻原始打印排版/合并单元格样式；最多前 5 表、每表 200 行和 30 列，每格 300 字，超出显示明确提示并保留下载原文件。PDF 沿用已有工具读取边界，失败可下载查看。文档预览采用最大 1000×780px，作为需要阅读票据/PDF/表格的文档展示规则，不改变商品图片共享弹窗尺寸。

## 必要局部验证

为核对新增 SQL/权限与异步预览资源行为，仅运行相关检查，没有完整 quality、clean verify 或全模块/多视口回归。

| 工作目录 | 命令 | 结果 |
| --- | --- | --- |
| `frontend` | `pnpm exec eslint src/components/CustomsAttachmentPreview.vue src/components/CustomsAttachmentPreview.test.ts src/components/CustomsFileStatus.vue src/components/CustomsFilesDialog.vue src/components/CustomsFilesDialog.test.ts src/views/CustomsDocumentsView.vue src/views/CustomsDocumentsView.test.ts src/api/customsDocuments.ts --fix --max-warnings=0` | PASS；新模板格式修正。日志 `/tmp/erp103-front-lint.log`。 |
| `frontend` | `pnpm exec vue-tsc --noEmit` | PASS，日志 `/tmp/erp103-front-types.log`。 |
| `frontend` | `pnpm exec vitest run src/components/CustomsAttachmentPreview.test.ts src/components/CustomsFilesDialog.test.ts src/views/CustomsDocumentsView.test.ts` | 3 文件 / 13 项通过；包括状态更新、创建时间展示、点击文件不下载、URL 释放、关闭中止与迟到结果、失败重试和表格文本转义。日志 `/tmp/erp103-front-tests.log`。 |
| `deliverables/backend/sprint-0/jiugeng-erp-backend` | `./mvnw --batch-mode --no-transfer-progress '-Dtest=CustomsDocumentTest#listUsesCreationTimeAndIndependentAttachmentFlags+spreadsheetPreviewIsReadOnlyBoundedAndPermissionProtected+filesDetermineCompletionAndFilterAcrossPages' package` | BUILD SUCCESS；3 项通过，0 失败/错误/跳过；覆盖创建/更新时间相反时的分页排序、截图/发票标记、既有上传完成/删除规则、XLS/XLSX 缓存公式、预览截断、401/403/跨单据 404、只读版本及 no-store。生成本地运行包，非完整后端门禁。日志 `/tmp/erp103-backend.log`。 |
| `frontend` | `node /tmp/erp103-ui-check.mjs` | PASS；一个 Chromium 合成夹具（1100×800）使用真实组件、Vite 与应用 CSS，验证类别标签、图/PDF/Excel 预览、PDF 定位、嵌套关闭、主题下拉、无自动下载/无写请求/无页面异常。已目视三类截图 `/tmp/erp103-{image,pdf,excel}-preview.png`。初次夹具关闭按钮定位同时匹配头尾两个按钮，改为底栏按钮后通过，未改业务行为。 |
| 仓库根 | `git diff --check`、变更 Markdown 本地链接检查、`git status --short` | 通过；无无关改动，无暂存。 |

完整累计门禁、真实列表与弹窗三尺寸、上传/删除后的列表标签刷新、Excel 复杂布局/大文件、PDF 加密/损坏与跨浏览器预览留到用户要求交接时统一执行。当前不宣称完整验收、合并或部署。

## 本地运行

- 新包 `.local/releases/backend/erp103-customs-files-20261124/backend.jar`，SHA-256 `d0d84c73e420d99a4dd97769a9c80114b95cfd403bd97a64bd1d2f8a4708e6de`。
- `.local/runtime/run-backend.sh` 仅切换运行包；备份 `.local/runtime/run-backend.before-erp103-20261124.sh`，此前 `customs-history-20261124` 包保留，私有运行配置不变。
- 沿用 `jiugeng-mvp:0.0`、mysql、8080。原 pane 直接运行启动器，Ctrl-C 后为 dead pane；检查端口释放后用 `tmux respawn-pane` 在原 pane 启动，没有新建竞争实例。
- `/actuator/health` 返回 `{"status":"UP"}`；启动验证既有 107 个迁移并成功启动。没有新增迁移，不清理业务库/上传目录。前端 Vite 接收热更新。
- 未向 PJM 发送发布交接；等用户明确指令后统一验证并移交。PJM 届时负责提交远端 main/CI/CD/部署，Codex 不跟踪线上。


## 同迭代追加：样品管理修复（2026-11-24）

用户直接追加五项样品问题，沿用当前本地写入槽/分支/基线，不另建任务号或继承发布授权。当前工作区仅包含本记录的报关与样品改动，全部未暂存、未提交。

- `frontend/src/components/SampleLabelDialog.vue` 去掉份数状态与控件，每条 1 张、批量最多 100 条；iframe 按真实标签数量计算高度，单条宽高 70×30mm，不再出现 240px 高的错误白纸预览。
- `frontend/src/utils/sampleLabel.ts` 屏幕标签间留 12px 灰色间隔，打印不包含该间距；新增安全文件名，单条 `样品标签-品名-库位.pdf`，批量 `样品标签-首条品名等N款.pdf`。PDF 导出仍用既有 70×30mm 页面生成器。
- `frontend/src/views/SamplesView.vue` 删除库位 datalist、建议状态/定时器/查询，改普通文本输入；标题与必填标记同一行，规格/备注使用统一标题结构。保留服务端归一化、校验、幂等、冲突及上传处理。
- `frontend/src/styles/sampleManagement.css` 库位红字浅红底；统一字段标题高度与顶部对齐，保留移动端单列；新增局部全视口透明原生模态宿主样式。
- `frontend/src/components/SampleDetailDialog.vue` 子体详情在局部原生 dialog 中 showModal，修复共享 CSS overlay 被样品原生对话框遮挡；父体详情转入子体同样受覆盖。保持共享 SkuDetailDialog 原样，不调整全局其他弹窗。
- `frontend/src/utils/sampleLabel.test.ts` 增加品名/库位、非法字符与批量文件名覆盖；`frontend/src/views/SamplesView.test.ts` 增加自由文本库位、无原生建议框/无建议请求覆盖。

局部验证（未执行全量门禁或多视口矩阵）：

| 目录 | 命令 | 结果 |
| --- | --- | --- |
| frontend | `pnpm exec eslint src/components/SampleLabelDialog.vue src/components/SampleDetailDialog.vue src/views/SamplesView.vue src/views/SamplesView.test.ts src/utils/sampleLabel.ts src/utils/sampleLabel.test.ts --max-warnings=0` | PASS。首次发现未使用 watch、文件名过滤正则的 no-control-regex，已删除无用导入并改逐字符过滤后通过。 |
| frontend | `pnpm exec vitest run src/utils/sampleLabel.test.ts src/views/SamplesView.test.ts` | 2 文件、22 项 PASS，日志 `/tmp/sample-fixes-tests.log`。 |
| frontend | `node /tmp/sample-fixes-ui-check.mjs` | Chromium 单视口 1100×800，真实组件/完整主题 CSS/合成数据；70:30 预览比、导出 1 页且实际 70×30mm、含品名库位文件名、子体 `:modal` 顶层可操作与 Escape 返回样品、无 datalist、字段顶部对齐、红色库位均 PASS；无写请求/页面异常。截图 `/tmp/sample-fixed-{label,editor,sku}.png` 已目视。夹具先前缺登录权限与日志接口 stub，补齐合成数据后通过，非业务接口故障。 |
| 仓库根 | `git diff --check`、`git status --short` | PASS；无无关改动、无暂存。 |

本次不需要数据库迁移或服务重启，Vite 热更新可用。待交接统一进行完整前端门禁、样品编辑/复制/批量标签与长内容/窄屏及 Safari 打印检查；物理打印机实际出纸需使用对应 70×30mm 纸张、100% 比例。先前后端报关验证证据保留，新增样品工作未修改后端。PJM 仍需等用户明确交接指令才接管提交/发布。


## 同迭代追加：样品库位历史重置与列表精简（2026-11-24）

用户明确要求删除全部旧样品变更历史，后续只记录库位；该明确指令覆盖旧样品记录的常规追加式保留要求，但不授权清理其他模块审计。当前仅本地执行，线上迁移由未来用户授权后的 PJM 发布负责。

### 实现

- `SamplesView.vue` 删除来源/所属产品列、无用状态导入，修正空态 colspan；`sampleManagement.css` 最小表宽改 900px。
- `SampleDetailDialog.vue` 移除来源标记、图片历史特殊提示，空态改“暂无库位变更记录”；`sampleHistory.ts` 只渲染库位差异，动作显示“库位变更”。来源/父体筛选、CSV、查看关联产品入口保留。
- `sample/SampleController.java` 删除新增/复制/移除/恢复记录调用；更新仅在 `locationCode` 实际变化时记录，前后快照只有该字段。其他业务更新、版本与幂等逻辑保持，失败/重试不会产生重复历史。
- `V108__reset_sample_location_history.sql` 仅 DELETE category BUSINESS、resource_type SAMPLE_LOCATION、action 为 SAMPLE_CREATE/COPY/UPDATE/REMOVE/RESTORE 的旧历史。Flyway 版本化一次执行，不是每次启动清理。新增历史保持追加式写入。
- 更新 `SampleControllerTest` 原断言并增加只记录库位、仅查询权限、重试/冲突测试；新增 `SampleHistoryResetMigrationTest` 验证五类旧样品记录删除，而其他资源、SECURITY 和其他动作保留。前端新增 `sampleHistory.test.ts`，并补充列表列数一致性用例。

### 本次必要验证

| 目录 | 命令 | 结果 |
| --- | --- | --- |
| frontend | `pnpm exec eslint src/views/SamplesView.vue src/views/SamplesView.test.ts src/components/SampleDetailDialog.vue src/utils/sampleHistory.ts src/utils/sampleHistory.test.ts --max-warnings=0` | PASS。 |
| frontend | `pnpm exec vitest run src/views/SamplesView.test.ts src/utils/sampleHistory.test.ts` | 2 文件/21 项 PASS，`/tmp/sample-location-history-front.log`。 |
| backend | `./mvnw --batch-mode --no-transfer-progress '-Dtest=SampleControllerTest,SampleHistoryResetMigrationTest' package` | 2 类/11 项 PASS，BUILD SUCCESS；`/tmp/sample-location-history-backend.log`。第一次 JSONPath Map 整体比较返回 null，但响应已有正确快照；改用解析 JSON 节点等值断言后通过。首次从仓库根调用 mvnw 路径不存在，已改为 backend 目录。 |
| 仓库根 | `git diff --check`、`git status --short` | PASS，无无关改动/暂存。 |

这是处理数据清理和记录边界的局部验证；没有完整 quality/clean verify 或视口矩阵，累计交接检查仍待用户发起。

### 本地数据与运行包

- 确认原本地 backend 为 mysql、127.0.0.1:13306 的 jiugeng_erp。先停止 `jiugeng-mvp:0.0`，使用 Docker 内 mysqldump `--single-transaction --quick --no-tablespaces --routines --triggers --set-gtid-purged=OFF jiugeng_erp` 备份至 `.local/backups/sample-history-before-v108-20261124.sql`，验证 Dump completed 与权限 600；备份在忽略目录，不进入 Git。
- 新包 `.local/releases/backend/sample-location-history-20261124/backend.jar`，SHA-256 `bfa0d804bcf7300fe438077d213e8e2c92c0bb9f77fc5b57505597037f71ae59`；同目录保留 `previous-run-backend.sh`，旧 erp103-customs-files JAR 未删除，私有运行配置未变。
- 原 tmux pane 使用 `respawn-pane` 启动新包；`curl --fail --silent http://127.0.0.1:8080/actuator/health` 返回 UP，Flyway V108 success=1，目标样品旧历史 count=0。迁移前后样品行数及排除目标范围后的审计行数用 `cmp .local/backups/sample-history-v108-before-counts.txt .local/backups/sample-history-v108-after-counts.txt` 比对相同；保留业务数据与上传文件。
- 生产未连接、未执行清理、未交接发布。PJM 后续发布前需确认完整备份和 V108 删除范围；此迁移无法仅回退应用恢复历史，如需恢复应从备份提取对应样品记录，不能覆盖后续业务数据。


## 同迭代追加：样品操作入口精简（2026-11-24）

- `frontend/src/views/SamplesView.vue` 工具栏与弹窗标题改“导出”；移除复制操作入口及新增时的“保存并继续”，新操作仅新增/编辑并在成功后关闭。移除新建复制和连续保存分支；保留旧 pending.copying 载荷恢复，旧 again 不再触发自动继续。
- `frontend/src/styles/sampleManagement.css` 为已移除切换定义局部中性灰样式，保留返回/选中态及 aria-pressed，不改变其他按钮颜色。
- `frontend/src/views/SamplesView.test.ts` 将旧复制/继续新增用例更新为旧待确认复制请求使用原键恢复、正常保存关闭及入口移除断言。
- 本次仅 `git diff --check` PASS、检查最终局部差异与工作区；没有运行测试/完整门禁/浏览器矩阵。此前 SamplesView 通过证据已被本次改动更新，相关测试和视觉检查待交接统一执行。无后端、迁移、服务重启，Vite 热更新使用本地修改。
- 分支和基线未变，所有改动仍未提交/未暂存，仅本轮授权范围；没有向 PJM 发出发布指令。

- 样品列表追加精简：按用户要求移除品名/规格下“当前产品图 · 历史未留图”提示，仅删除 `SamplesView.vue` 展示节点；图片来源、回退及留存逻辑不变。`git diff --check` 通过，不为本次文案删除重复运行测试，随本轮交接统一验证。


## 同迭代追加：供应商界面精简与历史清理（2026-11-24）

用户授权四项本地变更，当前分支/写入权不变，无发布授权：

- `SupplierEditor.vue` 去掉保存并继续及 continuing/again 分支，保存成功统一关闭；旧 pending 中的原 requestId/载荷仍可恢复，旧 again 不再开启下一份草稿。供应商复制独立功能保留。
- `SupplierDetailDialog.vue` 移除变更记录页签、请求和分页状态，保留基础资料与关联业务；后台后续操作审计和兼容 history API 不改动，用户要求的历史数据通过一次性迁移清理。
- `SupplierDeleteDialog.vue` 删除停用函数、按钮与事件；`SuppliersView.vue` 删除对应监听，保留列表独立停用与影响确认。`supplierManagement.css` 对 body 下的 `.row-more-actions__menu button.supplier-danger` 指定红字，解决 Teleport 离开 supplier-ui 后规则失效。
- V109 只删除 category BUSINESS/resource_type SUPPLIER 且动作为 SUPPLIER_CREATE、SUPPLIER_UPDATE、SUPPLIER_DELETE、SUPPLIER_ARCHIVED_LINK_REMOVE 的已有记录。保留同资源类型下 MATERIAL_SUPPLIERS_REPLACE 辅料关联记录、其他模块与安全审计。新 `SupplierHistoryClearMigrationTest` 验证这个边界；旧应用行为测试不因一次性清理而取消未来审计断言。
- 更新 SupplierEditor/DeleteDialog 测试，覆盖旧连续保存请求恢复后关闭及删除弹窗不再调用停用。

局部验证：

| 目录 | 命令 | 结果 |
| --- | --- | --- |
| frontend | `pnpm exec eslint src/components/SupplierEditor.vue src/components/SupplierDetailDialog.vue src/components/SupplierDeleteDialog.vue src/views/SuppliersView.vue --max-warnings=0` | PASS。 |
| frontend | `pnpm exec vitest run src/components/SupplierEditor.test.ts src/components/SupplierDeleteDialog.test.ts` | 2 文件/8 项 PASS，`/tmp/supplier-simplify-front.log`。 |
| backend | `./mvnw --batch-mode --no-transfer-progress '-Dtest=SupplierHistoryClearMigrationTest' package` | 1 项 PASS、BUILD SUCCESS，`/tmp/supplier-simplify-backend.log`。 |
| frontend | `node /tmp/supplier-delete-color-check.mjs` | Chromium 1100×800，真实 RowMoreActions/全局主题 CSS、合成入口；按钮确实在 supplier-ui 外，computed color 为 rgb(180,35,24)，PASS。 |
| 根目录 | `git diff --check`、最终局部 diff 与工作区检查 | PASS；无无关改动/暂存。 |

没有完整 quality、clean verify 或视口矩阵；本轮累计门禁仍留用户要求交接时统一执行。

本地同步：

- 停止原 jiugeng-mvp:0.0，确认 8080 释放；Docker mysqldump 使用 `--single-transaction --quick --no-tablespaces --routines --triggers --set-gtid-purged=OFF jiugeng_erp`，完整备份至 `.local/backups/supplier-history-before-v109-20261124.sql`，Dump completed/权限 600 验证通过。备份、私有配置和业务数据不入库。
- 新包 `.local/releases/backend/supplier-ui-history-20261124/backend.jar`，SHA-256 `3e59c641b0c529cce849ac3189f8e34f089272cce352d28d9cc55b68cf115bfe`。同目录保存 previous-run-backend.sh；上轮 sample-location-history JAR 保留，原配置不变。
- 原 pane respawn 启动，`curl --fail --silent http://127.0.0.1:8080/actuator/health` 为 UP，V109 success=1、目标旧记录 count=0。`cmp .local/backups/supplier-history-v109-before-counts.txt .local/backups/supplier-history-v109-after-counts.txt` 相同（供应商数量、排除删除范围后的审计数量）。上传文件不改动，临时数据库客户端配置已删除。
- 生产未连接/未清理，PJM 未来获发布授权后需确认备份再应用 V109。仅回退 JAR 不恢复被删除旧历史，需要从备份按目标范围提取恢复，不能覆盖之后业务。


## 同迭代追加：SKU 编辑页签与库存操作修复（2026-11-24）

- `ProductForm.vue` 在采购/辅料页签隐藏空 sku-editor-grid；维护供应商 RouterLink 去掉 target=_blank/rel=noopener，改当前页导航并保留原草稿离开保护；辅料备注按钮增加局部绿色 class。
- `productManagement.css` 为单 SKU 的 product-create-form 设置 auto/minmax(0,1fr)/auto 三行：上下文、滚动正文、底栏；原两行配置把上下文当可伸缩区，导致内容较少页签顶部巨大留白。
- `ProductInventoryDialog.vue` 新按钮仅入库/出库；operations 中历史调整类型、旧请求确认/权限保留，确认后切回可用入/出库类型。原因选填，前端不再阻断空值。
- `ProductInventoryController.java` 去掉 reason 的 NotBlank；`ProductInventoryService.java` 将 null/空白归一为空串，仍限制 500 字，幂等比较、入账和审计使用同一个归一值，保留数量、库存上下限及权限。没有迁移、没有修改既有流水或原因，也没有修改辅料库存。
- `ProductInventoryDialog.test.ts` 覆盖空原因、仅入出库按钮、旧盘亏 pending 原键确认；`ProductCloseoutTest` 增加 DTO 校验/服务空原因、空白重试、原因冲突和超长拒绝，沿用权限/库存边界用例。

局部验证（无全量门禁/视口矩阵）：

| 目录 | 命令 | 结果 |
| --- | --- | --- |
| frontend | `pnpm exec eslint src/components/ProductForm.vue src/components/ProductInventoryDialog.vue src/components/ProductInventoryDialog.test.ts --max-warnings=0` | PASS。 |
| frontend | `pnpm exec vitest run src/components/ProductInventoryDialog.test.ts` | 8 项 PASS；`/tmp/product-sku-fixes-front.log`。 |
| backend | `./mvnw --batch-mode --no-transfer-progress '-Dtest=ProductCloseoutTest' package` | 5 项 PASS、BUILD SUCCESS；`/tmp/product-sku-fixes-backend.log`。 |
| frontend | `node /tmp/product-sku-ui-check.mjs` | Chromium 1100×800，真实 ProductEditDialog/ProductForm、Vue Router、完整主题和合成数据；三个页签上下文高度<60px、导航间距<30px；辅料备注绿色；维护供应商同页导航、只有一个浏览器页、sessionStorage 保留；改草稿后先继续填写再确认放弃的路由保护通过。没有写请求/页面异常；截图 `/tmp/product-sku-materials-fixed.png` 已目视。此为组件场景，不冒充真实线上认证测试。 |
| 根目录 | `git diff --check`、局部最终 diff / status 检查 | PASS；无无关改动或暂存。 |

本地新包 `.local/releases/backend/product-sku-fixes-20261124/backend.jar`，SHA-256 `10dec3e85d3153bfe26d2fc842c4dc349eaaa3ff801d4cbb70dd27cb804a7208`；同目录 previous-run-backend.sh 与旧 supplier-ui-history JAR 保留。无新迁移，已有 V108/V109 不重复清理；原 mysql 数据、图片、凭据配置保留。原 pane 停止后端后首次 respawn 时进程尚在退出（端口已释放），工具拒绝；确认 pane_dead=1 后在原 jiugeng-mvp:0.0 respawn 成功，没有创建竞争实例。`curl --fail --silent http://127.0.0.1:8080/actuator/health` 返回 UP。

所有改动仍在当前独占分支未提交；待用户要求交接时统一验证累计改动并由 PJM 提交/发布，Codex 不跟踪线上。


### 本轮追加：回收站入口统一（2026-11-24）

产品列表“已删除”、样品列表“已移除”按钮改为“回收站”，同步删除/移除提示里的入口称呼；样品回收站按钮移到工具栏末尾并 margin-left:auto 右对齐，延续中性灰色和返回在库登记状态。去掉样品在库列表的“登记样品与库位，照片与产品资料分别留存”文案，回收站内的恢复提示保留。改动为 `ProductsView.vue`、`SamplesView.vue`、`sampleManagement.css`；`git diff --check` 通过，按约定不单独跑功能测试/回归，统一交接验证。未改数据/后端/服务，全部未提交。

- 产品备注颜色追加：采购信息与关联辅料的备注列操作统一绿色（`productManagement.css` 限定 relation-edit-action 下的 relation-note-toggle），新增和编辑共用生效；不影响工具栏刷新档案。`git diff --check` 通过，本次样式微调不重复测试，随迭代统一验证。


### 本轮追加：选择辅料缩略图与筛选控件（2026-11-24）

`ProductMaterialPicker.vue` 新增 48×48 图片列，复用 MaterialImage 的鉴权缩略图、无图状态与大图预览，产品新增/编辑共享；同步空态 colspan。搜索与类型标签对齐，输入及共享 single-select-trigger 统一紧凑控件高度、边框/字体，窄屏两行同宽。不使用原生替代下拉，不改已有选中/过滤/分页与提交逻辑。完整累计验证留交接。

- 本项局部验证：frontend 执行 `pnpm exec eslint src/components/ProductMaterialPicker.vue --fix --max-warnings=0` PASS（修正 th 换行格式）；`node /tmp/material-picker-ui-check.mjs` PASS，Chromium 1100×800 真实组件/完整主题/合成图片验证两个筛选控件 32px 同高同顶、原生 select 隐藏、48px 鉴权缩略图、无图占位及大图原生顶层预览。截图 `/tmp/material-picker-fixed.png` 已目视。夹具首次 select 匹配到筛选/分页两个节点，限定筛选作用域后通过，非业务错误。`git diff --check` PASS；未运行完整门禁、单测套件或视口矩阵。未提交/部署，无服务重启。


### 本轮追加：采购选择弹窗尺寸与滚动（2026-11-24）

- `PurchasePicker.vue` 自行引入采购主题，增加 purchase-picker-dialog 专用作用域、结果滚动容器和列/选择按钮 class；供应商名称标题与项目名称/单位区分，保留全部原请求/搜索/分页/选择事件逻辑。
- `purchaseManagement.css` 专用选择器覆盖 purchase-ui 的 fit-content 高度，固定上限 1000×720px 并受视口减 24px 限制；AccessDialog 正文 flex 分区，搜索不滚动、结果独立滚动/表头吸顶，分页固定，图片 48px、名称换行，窄屏调整列宽。没有影响采购编辑和其他弹窗。
- 必要局部检查：`pnpm exec eslint src/components/PurchasePicker.vue --fix --max-warnings=0` PASS；`node /tmp/purchase-picker-ui-check.mjs --check` PASS（frontend 目录，Chromium 1100×800，实际 AccessDialog/PurchasePicker/主题，合成 API 无写请求）。两类弹窗均 1000×720，第一行选择与分页在视口内、原生顶层可操作，翻到第二页选择后回到原采购编辑弹窗并回填对应合成记录。`/tmp/purchase-picker-{supplier,item}-fixed.png` 已生成，项目图已目视；两行结果修复前实测供应商 1000×321、项目 1000×385，修复后稳定。夹具最初项目名称与单位共处 td 导致 exact 文本匹配失败，定位改为首行后验证通过，不是业务故障。
- `git diff --check` PASS；本次没有单测套件/完整门禁/视口矩阵，累计验证留交接；前端热更新，无后端、数据或服务变更，未提交/未部署。


### 本轮追加：辅料关联供应商与保存入口（2026-11-24）

- `MaterialEditor.vue` 自带辅料样式，关联供应商改为搜索框/内联搜索按钮与共享选择控件两列对齐，显示加载、错误和无匹配反馈；只有超过 20 条才显示原分页。新增、编辑共用布局，不改关联/主供应商规则。`materialManagement.css` 限定编辑器作用域，两控件统一 32px，窄屏单列。
- 移除“保存并继续”及继续新增分支，保存成功关闭；旧 pending 的原请求仍可恢复确认，旧 again 字段不再触发下一张空表单。`MaterialEditor.test.ts` 增加该兼容场景。
- 局部验证：frontend 执行 `pnpm exec eslint src/components/MaterialEditor.vue --fix --max-warnings=0` PASS；`pnpm exec vitest run src/components/MaterialEditor.test.ts` 5 项 PASS（日志 `/tmp/material-editor-simplify-tests.log`）。`node /tmp/material-supplier-ui-check.mjs` PASS，真实组件/主题、Chromium 1100×900、合成只读 API：控件 32px 同高同顶、单页隐藏分页、共享选择器关联并默认主供应商、无继续保存按钮、无页面异常；`/tmp/material-supplier-fixed.png` 已目视。`git diff --check` PASS。
- 未运行完整门禁/回归矩阵，累计验证仍留用户要求交接时；前端本地热更新，未提交/部署，无数据或后端修改。


### 本轮追加：未保存确认全站统一（2026-11-24）

- 客户信息/客户商品（CustomerManagementView）、发票信息、旧报关资料、首页今日/待发货、产品成本的旧内联/独立确认卡改为共享 UnsavedChangesDialog。原表单保持挂载；默认聚焦继续填写、Esc 继续、显式放弃关闭、恢复焦点由共享组件处理。成本删除/生效确认不变。
- `uiConfirmation.ts` 的 confirmAction 增加显式 unsaved/discardLabel 选项；`UiConfirmationHost.vue` 对该选项呈现同一共享组件，仍使用原 Promise、并发拒绝和路由/卸载取消。非未保存操作继续走原通用确认。不按文案自动猜测类型。
- 同步接入样品、辅料、供应商、采购/收货、仓库、开销、角色权限、系统设置、候选/开发工作台及其编辑、报关仓库、安排发货等已有未保存提示。离开/刷新/重新加载/返回列表的放弃按钮保留对应语义。样品 pending 保存结果不明仍用通用提示，不声明丢弃请求；版本更新通知未检测 dirty，仍保留原刷新确认。
- 更新 UiConfirmationHost、客户资料、发票、首页发货、产品成本的现有测试定位和草稿保留断言；共享 host 增加安全焦点/Esc/显式放弃与只打开一层确认测试。

局部检查（非完整门禁/回归）：

- frontend：`pnpm exec eslint src/state/uiConfirmation.ts src/components/UiConfirmationHost.vue src/components/UiConfirmationHost.test.ts src/views/CustomerManagementView.vue src/views/InvoiceManagementView.vue src/views/CustomsManagementView.vue src/views/DashboardMaintenanceView.vue src/views/ProductCostsView.vue --fix --max-warnings=0` PASS；修复两个模板空行警告。首次误从仓库根运行找不到 eslint，随后改 frontend。
- frontend：`pnpm exec vitest run src/components/UiConfirmationHost.test.ts src/views/CustomerManagementView.test.ts src/views/InvoiceManagementView.test.ts src/views/DashboardMaintenanceFormal.test.ts src/views/ProductCostsView.test.ts -t 'unsaved dialog|protects dirty|keeps input on mask|keeps draft input|preserves|discard'`：8 PASS、1 旧测试仍定位 danger-button 失败；已修正该测试。日志 `/tmp/unified-unsaved-tests.log`。
- frontend：`pnpm exec vitest run src/views/CustomerManagementView.test.ts src/views/DashboardMaintenanceFormal.test.ts -t 'intentional close paths|discards'`：首页两场景 PASS（客户用例名称未匹配，跳过）；`pnpm exec vitest run src/views/CustomerManagementView.test.ts -t 'allows deliberate discard'`：修正后 1 PASS。日志 `/tmp/unified-unsaved-recheck.log`、`/tmp/unified-unsaved-customer-recheck.log`。合计 11 个局部用例通过，无未解决失败；其余用例留迭代交接。
- frontend：`node /tmp/customer-unsaved-ui-check.mjs` PASS；Chromium 1100×800、真实 CustomerManagementView/主题/路由、合成只读接口。编辑客户商品确认层 440px、原生 top layer、继续按钮默认焦点、Esc 保留同一个 input DOM 和草稿、明确放弃才关闭；无写请求/页面异常。截图 `/tmp/customer-unsaved-fixed.png` 已目视。
- 根目录：`git diff --check` PASS；同一 ERP-103 写入槽，累计改动均未提交，未触发发布/服务重启。全量 quality/所有接入页面检查待用户要求交接时统一完成。


## 最终统一验证与发布交接（2026-11-24）

本节取代前文各追加阶段的“待交接/仅本地授权”当前状态，前文保留为实施过程证据。用户本轮明确指令：“与项目经理交接，把修改内容提交远端main，并部署线上。”

### 精确状态与责任

- 任务 ERP-103 / DEL-053；分支 `feature/ERP-103-customs-files`；HEAD/基线 `a6b05352c2ac3e368e37364438561bd6b5c511d3`。
- 实施提交：**未提交、无暂存**，PJM 接管后复核当前 diff，提交并合并/推送 main，按既有 CI/CD 部署。Codex 未推送、未触发线上发布。
- 共 81 个变更文件（71 已跟踪修改、10 新文件），全部属于用户累计授权的本迭代；无他人/无关改动。下方清单包含文档自身。
- 本地后端仍使用 product-sku-fixes-20261124 的持久化运行包，mysql/上传资料保留。本次完整测试使用测试配置，没有重启本地服务，没有访问生产数据。Playwright 临时 4188 preview 随测试结束退出。
- 实现/本地统一验证：完成；提交/main 合并/线上部署：待 PJM；业务验收：未宣称。

### 本轮范围

1. 报关：分类文件状态、创建时间倒序、图片/PDF/Excel 只读预览。
2. 样品：70×30 标签、每样品一张及含品名文件名、红色库位、文本库位输入与对齐、子体顶层弹窗、列表/操作精简、只记录库位变更。
3. 供应商：删除红字、删除确认去掉停用、移除详情变更页签和保存并继续；一次性清理既有指定历史。
4. 产品：SKU 页签顶部空白、同页维护供应商、采购/辅料备注绿色、库存仅入出库新入口且原因选填、回收站命名、选择辅料缩略图与筛选对齐。
5. 采购：供应商/项目选择弹窗稳定尺寸、独立滚动、分页/选择可达。
6. 辅料：关联供应商紧凑布局，去掉保存并继续，保留旧 pending 确认。
7. 全站：未保存确认使用共享弹窗，继续/Esc 保留草稿，离开/刷新等场景使用对应文案；保存结果不明请求不当作普通草稿丢弃。

### 统一门禁及结果

| 工作目录 | 命令 | 结果 |
| --- | --- | --- |
| frontend | `pnpm quality` | lint PASS；171 文件、793 测试 PASS；typecheck 发现 MaterialEditor 旧 submit(false) 参数和 UiConfirmationHost 可选 discardLabel 的 exactOptionalPropertyTypes 两处问题，已修正。日志 `/tmp/erp103-handoff-frontend.log`。 |
| frontend | `pnpm exec eslint src/components/MaterialEditor.vue src/components/UiConfirmationHost.vue --max-warnings=0` | 修正文件 lint PASS；其他 lint 结果可复用。 |
| frontend | `pnpm exec vitest run src/components/MaterialEditor.test.ts src/components/UiConfirmationHost.test.ts` | 修正影响的 2 文件/10 测试 PASS；其余 783 测试所覆盖代码未改变。日志 `/tmp/erp103-handoff-front-fix-tests.log`。 |
| frontend | `pnpm run build` | vue-tsc 与 Vite 构建 PASS；仅现有 chunk 大于 500kB 提示，无阻断。日志 `/tmp/erp103-handoff-front-build.log`。按约定复用已通过门禁分段，不重复跑全量。 |
| deliverables/backend/sprint-0/jiugeng-erp-backend | `./mvnw clean verify` | BUILD SUCCESS；449 项，0 失败/错误，448 通过、1 跳过。OssConnectivityTest 因未设置 ERP_OSS_PROBE 跳过（未访问外部 OSS）。日志 `/tmp/erp103-handoff-backend.log`。 |
| frontend | `pnpm exec playwright test e2e/products.smoke.ts e2e/catalog-closeout.smoke.ts e2e/operations-closeout.smoke.ts --output=/tmp/erp103-handoff-e2e-artifacts` | 9 PASS，Chromium 1280×800/800×600/360×800；真实构建应用、合成接口，产品列表/库存/样品/辅料/供应商/采购布局及操作可达。日志 `/tmp/erp103-handoff-e2e.log`。 |
| frontend | `node /tmp/{purchase-picker-ui-check,erp103-ui-check,sample-fixes-ui-check,material-picker-ui-check,material-supplier-ui-check,customer-unsaved-ui-check}-{800,360}.mjs --check`（逐一执行） | 12 组件场景 PASS，800×600 与 360×800；复用前文桌面证据。检查分页选择回填、图/PDF/Excel、标签尺寸/命名、样品子体、缩略图、共享未保存保护。日志 `/tmp/erp103-handoff-ui-matrix.log`。 |
| frontend | `node /tmp/erp103-preview-ready-check.mjs` | 360px PDF 明确等第二页 aria-busy=false 且 canvas 宽度 600，再截图，确保实际文字渲染完成，非默认画布/加载占位。`/tmp/erp103-pdf-preview-360-ready.png` 已目视。 |
| 根目录 | `git diff --check`、变更 Markdown 本地链接校验、最终 diff/status 核对 | PASS；无无关变更/暂存，导入契约未改，不适用 import-contract 门禁。 |

上述临时脚本/截图不是唯一交接依据：本仓库已提交基线中的 e2e、此次修改/新增的组件与后端测试均保留，具体变更可从下方文件清单和本记录重建。800/360 的采购选项宽表仍在结果区内横向滚动，名称与操作可滚动到达；窄屏正文可纵向滚动，底栏固定。

### 发布注意与验证边界

- **V108/V109 为用户明确要求的历史清理**：PJM 部署前确认生产数据库可恢复备份。V108 仅 BUSINESS/SAMPLE_LOCATION 的五类旧样品事件；V109 仅 BUSINESS/SUPPLIER 的 CREATE/UPDATE/DELETE/ARCHIVED_LINK_REMOVE，保留 MATERIAL_SUPPLIERS_REPLACE、安全审计及业务主数据。两项本地已应用并有备份，范围与保留计数已验证。回退 JAR 不恢复已删历史，须从备份按范围提取，不能覆盖后续业务数据。
- Excel 为文本/缓存值预览，最多 5 sheet、200 行、30 列、单格 300 字；不还原完整打印布局，不运行宏/公式，超出内容下载原件。已有权限、归属、失败重试与截断测试通过。
- 本轮无 Safari 实机/物理打印机出纸验证；本机未安装 Playwright WebKit，不为此扩大安装范围。70×30 PDF 页尺寸/打印预览自动检查通过，实际出纸仍依赖驱动纸张和 100% 比例设置。未连接线上测速/业务验收。
- Codex 交接后停止修改及测试；PJM 接管提交、合并、部署及发布状态报告，按用户要求不由 Codex 轮询发布。

### 最终文件归属清单

```text
 M deliverables/backend/sprint-0/jiugeng-erp-backend/src/main/java/com/jiugeng/erp/customs/CustomsAttachments.java
 M deliverables/backend/sprint-0/jiugeng-erp-backend/src/main/java/com/jiugeng/erp/customs/CustomsDocuments.java
 M deliverables/backend/sprint-0/jiugeng-erp-backend/src/main/java/com/jiugeng/erp/product/ProductInventoryController.java
 M deliverables/backend/sprint-0/jiugeng-erp-backend/src/main/java/com/jiugeng/erp/product/ProductInventoryService.java
 M deliverables/backend/sprint-0/jiugeng-erp-backend/src/main/java/com/jiugeng/erp/sample/SampleController.java
 M deliverables/backend/sprint-0/jiugeng-erp-backend/src/test/java/com/jiugeng/erp/customs/CustomsDocumentTest.java
 M deliverables/backend/sprint-0/jiugeng-erp-backend/src/test/java/com/jiugeng/erp/product/ProductCloseoutTest.java
 M deliverables/backend/sprint-0/jiugeng-erp-backend/src/test/java/com/jiugeng/erp/sample/SampleControllerTest.java
 M docs/04-engineering/PROJECT_HANDOVER.md
 M docs/08-project-management/STATUS_REPORT.md
 M docs/DELIVERABLE_REGISTER.md
 M docs/INDEX.md
 M docs/ui/UI_STANDARD.md
 M frontend/src/api/customsDocuments.ts
 M frontend/src/components/CustomsFilesDialog.test.ts
 M frontend/src/components/CustomsFilesDialog.vue
 M frontend/src/components/CustomsWarehouseManager.vue
 M frontend/src/components/DashboardPurchaseReceipts.vue
 M frontend/src/components/MaterialEditor.test.ts
 M frontend/src/components/MaterialEditor.vue
 M frontend/src/components/OrderShipmentDialog.vue
 M frontend/src/components/ProductForm.vue
 M frontend/src/components/ProductInventoryDialog.test.ts
 M frontend/src/components/ProductInventoryDialog.vue
 M frontend/src/components/ProductMaterialPicker.vue
 M frontend/src/components/PurchasePicker.vue
 M frontend/src/components/SampleDetailDialog.vue
 M frontend/src/components/SampleLabelDialog.vue
 M frontend/src/components/SupplierDeleteDialog.test.ts
 M frontend/src/components/SupplierDeleteDialog.vue
 M frontend/src/components/SupplierDetailDialog.vue
 M frontend/src/components/SupplierEditor.test.ts
 M frontend/src/components/SupplierEditor.vue
 M frontend/src/components/UiConfirmationHost.test.ts
 M frontend/src/components/UiConfirmationHost.vue
 M frontend/src/components/WarehousePageShell.vue
 M frontend/src/components/launch/CandidateAssessmentDialog.vue
 M frontend/src/components/launch/DevelopmentProjectDialog.vue
 M frontend/src/state/uiConfirmation.ts
 M frontend/src/styles/materialManagement.css
 M frontend/src/styles/productManagement.css
 M frontend/src/styles/purchaseManagement.css
 M frontend/src/styles/sampleManagement.css
 M frontend/src/styles/supplierManagement.css
 M frontend/src/utils/sampleHistory.ts
 M frontend/src/utils/sampleLabel.test.ts
 M frontend/src/utils/sampleLabel.ts
 M frontend/src/views/CandidateWorkbenchView.vue
 M frontend/src/views/CustomerManagementView.test.ts
 M frontend/src/views/CustomerManagementView.vue
 M frontend/src/views/CustomsDocumentsView.test.ts
 M frontend/src/views/CustomsDocumentsView.vue
 M frontend/src/views/CustomsManagementView.vue
 M frontend/src/views/DashboardMaintenanceFormal.test.ts
 M frontend/src/views/DashboardMaintenanceView.vue
 M frontend/src/views/DevelopmentWorkbenchView.vue
 M frontend/src/views/FactoryExpensesView.vue
 M frontend/src/views/InvoiceManagementView.test.ts
 M frontend/src/views/InvoiceManagementView.vue
 M frontend/src/views/MaterialsManagement.vue
 M frontend/src/views/ProductCostsView.test.ts
 M frontend/src/views/ProductCostsView.vue
 M frontend/src/views/ProductDevelopmentView.vue
 M frontend/src/views/ProductsView.vue
 M frontend/src/views/PurchaseOrderDetailView.vue
 M frontend/src/views/PurchasesView.vue
 M frontend/src/views/RolesView.vue
 M frontend/src/views/SamplesView.test.ts
 M frontend/src/views/SamplesView.vue
 M frontend/src/views/SettingsView.vue
 M frontend/src/views/SuppliersView.vue
?? deliverables/backend/sprint-0/jiugeng-erp-backend/src/main/java/com/jiugeng/erp/customs/CustomsSpreadsheetPreview.java
?? deliverables/backend/sprint-0/jiugeng-erp-backend/src/main/resources/db/migration/V108__reset_sample_location_history.sql
?? deliverables/backend/sprint-0/jiugeng-erp-backend/src/main/resources/db/migration/V109__clear_supplier_change_history.sql
?? deliverables/backend/sprint-0/jiugeng-erp-backend/src/test/java/com/jiugeng/erp/sample/SampleHistoryResetMigrationTest.java
?? deliverables/backend/sprint-0/jiugeng-erp-backend/src/test/java/com/jiugeng/erp/supplier/SupplierHistoryClearMigrationTest.java
?? deliverables/pjm/ERP-103_CUSTOMS_FILES_LOCAL_2026-11-24.md
?? frontend/src/components/CustomsAttachmentPreview.test.ts
?? frontend/src/components/CustomsAttachmentPreview.vue
?? frontend/src/components/CustomsFileStatus.vue
?? frontend/src/utils/sampleHistory.test.ts
```


## PJM 发布执行复核（2026-11-24）

PJM 已按用户明确授权接管唯一写入权，复核 81 项文件归属与最终交接清单完全一致，基线仍为 `a6b05352c2ac3e368e37364438561bd6b5c511d3`。既有统一门禁日志已复核，未无理由重跑本地全量测试，未重启本地服务。

已审查 V108/V109 的类别、资源和动作白名单及隔离迁移测试：其他业务主数据、安全审计、MATERIAL_SUPPLIERS_REPLACE 均不在删除范围。生产既有 receive.sh 在停止唯一写入服务后调用 backup.sh，完整 mysqldump 经 gzip 完整性检查和 SHA-256 留存后才切换新包并启动 Flyway；备份失败中止。备份位于同盘，不是异地灾备；本轮未取得管理通道验证恢复演练。需要恢复旧历史时应由管理员从迁移前备份在隔离库提取限定记录，核对后恢复，不能仅回退 JAR 或整库覆盖后续写入。

接手时最新 Production 45383897405 仍失败，原因为 Another deployment is running；GitHub 无活动 Production。准备按受保护 main 五项检查完成本轮 PR，并核验本轮真实 deploy、入口版本及备份日志；若仍占锁则停止重试、保留证据并报告管理员通道阻断。此节不宣称已部署。当前阶段与后续精确 PR/SHA/运行证据持续记录在 `/tmp/jiugeng-pjm-release-progress.md`，最终结果补充本记录与状态报告，不为文档自身 SHA 递归归档。
