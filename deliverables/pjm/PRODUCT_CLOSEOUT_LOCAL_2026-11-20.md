# 产品管理收尾实施记录

> 2026-11-21 统一验证及授权发布交接已完成准备，后续执行状态见 [DEL-042 发布交接](CATALOG_RELEASE_HANDOFF_2026-11-21.md) 和当前状态报告。下文保留此前本地开发阶段记录。

- 用户直接授权，尚未分配 ERP 编号；分支 `codex/project-handover-docs`。
- 基线 `b0a3d559fd7fd21ed5c0a70bfffed8eacfd4cc20`；同一会话继续此前未提交改动，无并行写入者。
- 状态：本地实现及用例维护完成，21:40 已按用户故障修复指令同步本地后端，待迭代统一验证；未提交/推送/线上部署。完整测试及浏览器验收在用户要求交接 PJM 时统一执行。
- 用户随后授权全部 16 项 UI 优化，见本文末节；这是同一未交接迭代的追加范围。
- 本文是当前任务进度入口，恢复上下文时必须读取；原 UI 改动记录继续保留。

## 已批准范围与顺序

| 序号 | 实施内容 | 状态 |
| --- | --- | --- |
| 1 | 权限依赖：产品查看联动辅料/供应商查看，产品维护联动关联档案维护；服务端统一展开依赖，避免关联不可见导致误清空 | 本地实现，待验 |
| 2 | 未保存保护、保存期间关闭锁定、失败保留草稿 | 本地实现，待验 |
| 3 | 停用供应商历史关联保留且显示名称 | 本地实现，待验 |
| 4 | 区分新行移除、已存子体停用、独立删除 | 本地实现，待验 |
| 5 | 子体库存数量/入出库/调整/流水，父体汇总全部未删除子体数量 | 本地实现，待验 |
| 6 | 损耗率编辑百分比、存储比例保持兼容 | 本地实现，待验 |
| 7 | 清理旧编码文案 | 本地实现，待验 |
| 8 | 匹配数/总数、半选反馈、父体分页说明 | 本地实现，待验 |
| 9 | 更多筛选 | 本地实现，待验 |
| 10 | 筛选/页码/展开/滚动位置保留及保存定位 | 本地实现，待验 |
| 11 | 中文/北京时间导出、排序范围一致 | 本地实现，待验 |
| 12 | 子体日志及关键字段前后值 | 本地实现，待验 |
| 13 | 数据库分页及批量关联查询 | 本地实现，待验 |
| 14 | 单子体保存，避免重写其他子体 | 本地实现，待验 |
| 15 | 编辑引用资料按需加载、并行/复用 | 本地实现，待验 |
| 16 | 图片性能测量入口、适度优化、失败重试 | 代码已接入，效果待实测 |
| 17 | 复制时一并复制供应商和辅料；同名提醒 | 本地实现，待验 |
| 18 | 资料缺项提示、筛选和编辑入口 | 本地实现，待验 |
| 19 | 删除记录查询/受权限控制恢复及关联冲突校验 | 本地实现，待验 |

## 实施约束

- 保持模块边界，迁移新增版本，不改已发布迁移。
- 库存采用整数件数；手动库存与已有采购/发货历史不自动反向联动，避免重复记账。父体只汇总，不直接记流水。
- 只读角色不联动修改权限；产品维护联动供应商/辅料维护。库存入库/出库和删除/恢复为显式操作权限。
- 合成用例与必要局部静态检查可维护；不在逐项实施时运行完整回归或重启持久服务。

## 关键行为和实现入口

以下路径均相对仓库根目录；后端 Java 根目录为 `deliverables/backend/sprint-0/jiugeng-erp-backend/src/main/java/`。

| 领域 | 文件入口 | 行为与边界 |
| --- | --- | --- |
| 权限 | `com/jiugeng/erp/iam/PermissionCatalog.java`、`RoleRepository.java`、`IamTypes.java`；前端 `auth/capabilities.ts` | 产品只读联动供应商/辅料/库存只读；产品维护联动供应商/辅料维护。库存入出库、删除/恢复仍独立授权，不能将只读角色提升为可写 |
| 库存 | `product/ProductInventoryController.java`、`ProductInventoryService.java`；`frontend/src/components/ProductInventoryDialog.vue` | 入库/出库/盘盈/盘亏；正整数件数、必填原因、防负库存、请求幂等、流水追加；父体与子体同序加锁，与删除互斥。结果不明时前端保留原请求，刷新后仍可恢复重试 |
| 列表和导出 | `product/ProductPageController.java`、`ProductPageService.java`、`ProductRepository.java`、`ProductController.java`、`ProductSkuExportService.java`；`frontend/src/views/ProductsView.vue` | `/api/v1/products/page` 按父体 SQL 分页；批量加载关联。返回所有子体、匹配 ID 和全部子体库存，筛选不缩小父体库存；导出复用相同筛选/排序、中文状态和北京时间，最多 10000 个子体 |
| 保存和日志 | `product/ProductService.java`、`ProductController.java`；`frontend/src/api/productManagement.ts`、`ProductDetailView.vue`、`SkuDetailDialog.vue` | PUT `/api/v1/products/{id}/skus/{variantId}` 只更新目标 SKU，保留申报资料与名称样式保存；版本冲突拒绝覆盖。按 PRODUCT_VARIANT 查询子体日志，显示关键字段前后值；关联/主图变化也留日志 |
| 编辑体验 | `frontend/src/components/ProductForm.vue`、`ProductEditDialog.vue` | 脏表单关闭/路由离开确认、保存/上传锁定、失败留草稿；采购/辅料按需读取，停用供应商补取历史名称，库存仅在辅料选择时加载。百分比显示对应比例存储；已有子体只能停用，未存新行可以移除 |
| 复制和缺项 | `frontend/src/views/ProductsView.vue`、`ProductForm.vue` | 复制采购关联、辅料用量/损耗/备注、基本资料；清除身份、版本与申报档案，不复制库存或流水。同父体同名提醒，不拦截；图片/规格/重量/供应商缺项提示及筛选 |
| 恢复 | `catalogdeletion/CatalogRestoreController.java`、`CatalogRestoreService.java`、`CatalogDeletionService.java`；`frontend/src/components/ProductRestoreDialog.vue` | GET `/api/v1/products/deleted`，POST `/api/v1/products/deleted/{PRODUCT或SKU}/{id}/restore`；先父后子，校验版本/有效标识/关联档案；客户商品不自动恢复。非零库存阻止子体删除 |
| 图片 | `frontend/src/utils/catalogImageCache.ts`、`AuthenticatedCatalogImage.vue` | 保留懒加载、鉴权会话隔离、内存去重缓存；最多 6 个并发图片传输，失败可就地清除该缓存并重试；聚合命中/失败/字节/耗时计数不记录图片 ID 或令牌 |

- 主列表状态保存在当前用户的 sessionStorage；恢复页码、大小、筛选、展开与滚动。保存后定位父体所在页及目标子体，不符合筛选时提示。
- 子体库存尚无流水时显示 0，并可用“未建账”筛选区别于发生过出入库后的零结存。停用但未删除子体仍计入父体库存。
- 原 `/products/skus` 保留其他调用者兼容路径；本次 SQL 分页优化覆盖产品主列表和产品导出，不宣称所有 SKU 选择器均已迁移。
- 采购/发货单据不会自动生成产品库存流水。首次上线后需根据实际盘点手工建立库存，不用合成数据或历史单据推测真实结存。
- 复制含已停用主档的历史关联时，服务端仍会阻止给新子体新增停用供应商/辅料；保留历史关联不等于允许新引用停用档案。

## 数据迁移与运行状态

- 新增 `db/migration/V98__product_permission_dependencies.java`：回填既有角色依赖，增加角色版本及成员授权版本，使旧会话按现有 IAM 规则重新获取授权。
- 新增 `src/main/resources/db/migration/V99__product_inventory.sql`：库存余额与追加流水，SKU 外键、非负余额、正数量、同 SKU 请求编号唯一索引；已有业务库存不自动回填。
- 未修改既有已发布迁移；生产迁移未执行。本地 V98/V99 已在下述故障修复中应用。
- 上轮实现结束时未同步本地后端，导致热更新前端请求新 API 失败。现已按用户故障修复指令重启 `jiugeng-mvp` 后端，运行本地新 JAR；具体证据见下。
- V98 涉及权限补全、V99 涉及新写入数据，发布前 PJM 须核对迁移与回退安排，不能把前端刷新视为后端生效。

## 用例维护（已编写，尚未执行）

- 后端新增 `ProductPermissionDependencyTest`、`ProductCloseoutTest`、`ProductRestoreTest`：只读/维护权限依赖、库存操作权限、幂等/冲突/不足、筛选父体完整库存、单 SKU 不重写兄弟及版本冲突、有库存禁止删除、父子恢复顺序和过期版本。
- 后端 `ProductControllerTest` 追加中文导出及时间格式断言；`IamControllerTest`、`SupplierControllerTest`、`InventoryControllerTest` 的旧只读角色预期更新为关联可读、写入仍拒绝。此前删除父体相关用例继续保留。
- 前端新增 `ProductFormProtection.test.ts`、`ProductInventoryDialog.test.ts`、`ProductRestoreDialog.test.ts`；更新列表/详情/角色能力/关联编辑用例，覆盖新 API、分页状态恢复、库存汇总、关联复制、百分比和未保存保护。
- 图片组件与缓存用例补充失败重试、并发上限及排队取消；`e2e/products.smoke.ts` 合成拦截更新为新分页 API，不访问真实业务数据。

## 局部静态检查与待交接

为排查跨层接口和类型不一致进行了编译/类型/局部 lint；未运行应用测试、完整 quality/verify 或浏览器回归。初次类型检查发现筛选类型断言与合成辅料夹具缺少 enabled，已修正；最终结果见下。

- 后端目录：`./mvnw -q -DskipTests test-compile`（编译主代码和测试代码，不执行测试）。
- 前端目录：`pnpm exec vue-tsc --noEmit`。
- 前端局部 ESLint：产品管理 API、权限映射、表单/库存/恢复/图片组件及对应测试、ProductsView/ProductDetailView、图片缓存和合成浏览器用例；仅布局/属性次序自动修正。
- 根目录：`git diff --check` 与本次文档相对链接检查。
- 末次检查结果：后端 test-compile、前端 vue-tsc 和局部 ESLint 均退出 0；`git diff --check` 通过；6 份当前文档共 130 个相对链接检查无缺失。随后仅补齐旧权限测试预期和文档结果，应用代码未变化。前述历史全量测试结果不能覆盖本次增量。

局部 ESLint 完整命令（在 `frontend/`）：

```bash
pnpm exec eslint src/api/productManagement.ts src/auth/capabilities.ts src/auth/capabilities.test.ts src/components/ProductForm.vue src/components/ProductFormProtection.test.ts src/components/ProductInventoryDialog.vue src/components/ProductInventoryDialog.test.ts src/components/ProductRestoreDialog.vue src/components/ProductRestoreDialog.test.ts src/components/AuthenticatedCatalogImage.vue src/components/AuthenticatedCatalogImage.test.ts src/components/SkuDetailDialog.vue src/components/ProductSupplierFilter.test.ts src/views/ProductsView.vue src/views/ProductsView.test.ts src/views/ProductDetailView.vue src/views/ProductDetailView.test.ts src/utils/catalogImageCache.ts src/utils/catalogImageCache.test.ts e2e/products.smoke.ts --max-warnings=0
```

交接项目经理时统一验证：

1. 后端 `./mvnw clean verify`；前端 `pnpm quality`；本次未变更导入契约，无需单独重跑导入契约门禁。
2. 临时、隔离的合成数据库验证 V98/V99（包含 MySQL 兼容性）及权限回填/会话刷新；非用户指令不得重启持久服务或套用生产数据。
3. 并发库存变更、断网重试、出库不足、库存与删除冲突、日志/恢复权限及事务回滚；恢复不能自动复活客户商品。
4. 列表筛选/半选/父体分页/导出范围一致，父体库存包含未匹配和停用子体；单 SKU 保存不改兄弟、申报资料/格式保存仍正常，父体聚合版本冲突可理解。
5. 桌面 1280×800、窄屏 800×600、手机 360px 的累计 UI 验收；新增库存/恢复、关闭确认、关联编辑、停用历史记录、图片弹窗一起检查。
6. 图片冷/热缓存请求数、首屏图片完成耗时和失败重试；大列表 SQL 查询数量及响应时间。现仅有代码优化和测量入口，不声称已测得提速百分比。

## 所有权和交接状态

这是同一用户授权迭代的累计工作区，包含早先文档、列表、预览、关联详情与编辑改动，未创建新分支或 worktree。无其他代理并行写入；未暂存、提交、推送、联系 PJM 发布或部署。任务尚未分配正式 ERP 编号；以本记录和用户指令作为当前范围依据。先前交接文档保留历史状态，当前表单/权限/库存行为以本记录为准。


最终工作区复核：分支和 HEAD 未变化；`git status --short` 所列均为本次会话累计授权范围，无不相关改动；暂存区为空。

## 本地产品列表故障修复（2026-11-20 21:40 +08:00）

- 用户报告“本地服务产品管理挂了”，本次恢复本地服务属于该故障修复授权；未联系 PJM 发布或变更线上。
- 根因：Vite 已热更新到 `/api/v1/products/page`，而 `run-backend.sh` 仍启动旧 JAR。旧后端将 `page` 匹配为产品 ID，页面报“产品不存在：page”。这是本地前后端版本没有同步，不是产品数据丢失。
- 修复前确认 8080 使用 `mysql` profile，连接本机 13306；先通过容器内 mysqldump 创建本地完整库备份（忽略目录、权限 600），保留旧启动脚本和旧 JAR。未把凭据或业务数据写入版本库。
- 运行 `./mvnw -q -Dtest=ProductCloseoutTest,ProductRestoreTest,ProductPermissionDependencyTest test`：8 个测试通过（4 + 2 + 2），覆盖新分页、库存/权限、恢复逻辑；这是故障针对性检查，非全量回归。
- `./mvnw -q -DskipTests package`：通过。本地 JAR：`.local/releases/backend/products-closeout-20261120-214012/backend.jar`；SHA-256 `00667bb3b71eb28772ead61943b6d9e6e619c65d830f5df0bc4bdc500917a6cf`。
- 更新忽略目录内 `.local/runtime/run-backend.sh` 指向该 JAR，保留原参数，优雅停止旧 Java 后在原 tmux pane `%6` 重启。21:40:23 Flyway 确认应用 2 项迁移，到 v99；21:40:24 服务启动。
- `curl --noproxy '*' -fsS http://127.0.0.1:8080/actuator/health`：`UP`。最初 Python urllib 探针受本机代理影响返回 502，不能据此判定应用故障；本地检查须绕过代理。
- 通过 Safari 刷新用户原有 `127.0.0.1:5173/products` 页面，已见正常产品列表、库存列与父体/子体分页统计，原错误消失。未写入测试商品、未执行真实库存变更。
- 当前仍待累计完整门禁、浏览器多尺寸及性能实测；前述“未执行用例”状态仅对本节列出的 8 个测试更新为通过，其余待验不变。
- 后续跨层修改必须同时确认本地运行 JAR 与前端请求兼容，不能把编译通过当作页面可用。同步本地服务与合并 main/线上部署是不同状态，仍遵守各自授权边界。

## 16 项 UI 优化追加（2026-11-20，同一迭代）

用户在查看评审清单后明确要求全部优化。继续使用当前分支与未提交工作区，无新的 ERP 编号；未联系 PJM 发布、未提交、未改线上。这一节替代前文关于库存表单、单子体编辑布局和缺项展示的旧 UI 描述，不改变库存账务与权限规则。

| 序号 | 已实现的本地改动 | 主要入口 |
| --- | --- | --- |
| 1 | 库存摘要、操作区、流水三段；空记录随内容收缩，有记录限制表格高度；Safari 原生 dialog 使用 fit-content 避免 auto 拉满高度 | `ProductInventoryDialog.vue`、`productManagement.css` |
| 2 | 入库/出库/盘盈/盘亏使用权限过滤的分段按钮，提交文案随类型变化 | 同上 |
| 3 | 当前数量、增减、预计结存；整数范围、出库不足、结存上限与空原因就地提示；加载失败不能按零库存提交 | 同上 |
| 4 | 提交中/成功/拒绝/结果待确认分开显示；待确认保持原请求，408 与服务端/网络异常保留重试编号 | 同上 |
| 5 | 流水数字右对齐、正负号与文字/颜色、时间/操作人分层、最新行标记、长原因展开 | 同上 |
| 6 | 子体库存数字可直接打开操作与流水；未建账有文字；父体为全部子体合计，不随筛选缩减 | `ProductsView.vue` |
| 7 | 已存单子体编辑仅保留父体摘要，正文专注当前子体；整体/新增父体保留原父子结构 | `ProductEditDialog.vue`、`ProductForm.vue` |
| 8 | 复用上传组件 compact 模式；图片链接折叠，移除操作轻量化，格式限制用一行说明 | `ProductForm.vue` |
| 9 | 子体详情只显示一次可预览主图与品名；状态/父体集中在标题，基础信息显示规格、重量与关联入口 | `SkuDetailDialog.vue` |
| 10 | 产品命名空间统一关闭按钮、主要动作蓝色、边框/间距；不覆盖其他业务模块的绿色确认规范 | `styles/productManagement.css` |
| 11 | 父体底色减弱、保持 80px 图片与子体缩进；库存/重量右对齐；供应商/辅料数量为文字链接 | `ProductsView.vue` |
| 12 | 待补数量可展开；具备维护权限可定位图片、规格、重量、供应商字段；只读只展示缺项 | `ProductsView.vue` → `ProductForm.focusField` |
| 13 | 导入降为次级，筛选宽度收敛；显示生效条件标签，可逐一移除；更多筛选显示高级条件数量 | `ProductsView.vue` |
| 14 | 关联编辑的数量右对齐、单位同排、主供应商标识、轻量备注/解除；加载失败不回退显示 UUID | `ProductForm.vue` |
| 15 | 恢复列表将父体信息放在名称下，恢复说明和错误分区；父/子体/兼容详情路由复用前后值日志表 | `ProductRestoreDialog.vue`、`ProductHistoryTable.vue`、`utils/productHistory.ts` |
| 16 | 产品列表/详情/流水/恢复统一加载、空、错误/重试状态；表单窄屏换行、弹窗最大视口约束 | `ProductUiState.vue`、`productManagement.css` |

### 兼容与验收边界

- 仅前端与文档追加，本轮无后端/API/迁移变更，无需再次重启本地服务。前文 21:40 同步的本地 JAR 继续兼容。
- `ProductUiState` / `ProductHistoryTable` 是产品范围复用组件；样式入口 `productManagement.css` 使用 `.product-ui` 命名空间。保留共享 `AccessDialog`、`TableViewport`、`ListPagination`、`SingleFileUpload`、`UnsavedChangesDialog` 行为。
- 库存预览不是库存承诺，服务端仍持锁校验；读权限不获得入出库能力，父体不开放记账。
- 保留上传/保存锁定、未保存提醒、单 SKU 保存与所有关联值；UI 收起字段不删除业务数据。
- 本轮通过 Safari 在本地实际查看列表、空库存弹窗、子体详情和单子体编辑，仅打开/关闭，未提交商品/库存操作。发现库存原生弹窗 auto 高度仍拉满，改为 fit-content 后现场确认空白消失。这是针对布局风险的检查，非完整浏览器验收；未做多视口矩阵。
- 用例已维护：库存预计结存/超量阻断/加载失败/权限/安全重试，单子体摘要与缺项定位，筛选标签/库存入口，日志前后值；测试执行留到统一交接。
- 静态检查：限定本轮文件 ESLint（16 文件，含自动格式化）通过；TypeScript 检查发现并修正可选 focusField 和测试权限参数类型，最终结果见下面的检查记录。未执行 `pnpm quality`、Vitest 全套、后端完整门禁或性能测量。
- 后续交接仍需对累计迭代执行受影响完整门禁，重点验证 1280×800、800×600、360×800 下多流水、长名称、关联长表、嵌套预览、权限与请求结果不明场景。

### 本轮最终静态检查记录

- `frontend/` 下 `pnpm exec vue-tsc --noEmit`：通过，退出 0。
- 下列限定文件 ESLint：通过，退出 0。补齐历史操作标签原有测试后再次执行 `pnpm exec eslint src/utils/productHistory.test.ts --max-warnings=0`，通过。
- 根目录 `git diff --check`：通过；暂存区为空，分支仍为 `codex/project-handover-docs`，修改保持未提交；状态清单均属于当前会话累计授权范围。
- 本轮没有执行 Vitest、完整质量门禁或真实库存写入，统一迭代验收仍待用户指令交接。

```bash
pnpm exec eslint src/components/ProductInventoryDialog.vue src/components/ProductUiState.vue src/components/ProductHistoryTable.vue src/components/ProductForm.vue src/components/ProductEditDialog.vue src/components/SkuDetailDialog.vue src/components/ParentProductDetailDialog.vue src/components/ProductRestoreDialog.vue src/views/ProductsView.vue src/views/ProductDetailView.vue src/utils/productHistory.ts src/utils/productHistory.test.ts src/components/ProductForm.test.ts src/components/ProductFormProtection.test.ts src/components/ProductInventoryDialog.test.ts src/views/ProductsView.test.ts --fix
```
