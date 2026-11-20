# 子体详情可读性与父体删除保护

- 项目：九更亚马逊 ERP
- Owner：Codex 实施；PJM 后续复核
- 版本 / 状态：v0.4 / In Review；本地已修改，待迭代统一验证
- 更新时间：2026-11-20，Asia/Shanghai
- 任务来源：用户直接要求三项修改；未分配新 ERP 编号
- 分支：`codex/project-handover-docs`
- 基线：`b0a3d559fd7fd21ed5c0a70bfffed8eacfd4cc20`
- 提交：未提交、未推送、未合并、未部署。此前本地修改均为本会话同一实施者所有，完整保留；无其他写入者改动或暂存文件。

## 实现与文件

- `frontend/src/components/SkuDetailDialog.vue`：移除“查看产品完整资料”及不再使用的 RouterLink/隐藏属性；采购信息调用已有供应商关联接口取得名称、主供应商、链接与备注；辅料调用已有关系接口取得名称、用量、单位、损耗率和关联状态。按页签加载，权限不足不请求；加载失败可重试，切换内容和卸载时忽略过期响应，不以 UUID 或内部编码兜底。
- `frontend/src/api/domain.ts`：增加 `variantSupplierLinks` 客户端方法，复用 `/v1/suppliers/variant/{id}`，mock 模式返回空列表。辅料复用 `productMaterialRelations`，不新建后端读取接口或扩大权限。
- `frontend/src/views/ProductDetailView.vue`：移除过时 `hide-parent-link` 参数。
- `frontend/src/views/ProductsView.vue`、`components/CatalogDeleteDialog.vue`：依据完整父体的子体数组和数量阻止确认删除，不依赖筛选后可见行；显示先删除全部子体的原因。更新成功提示和说明，移除父体级联删除文案。
- `deliverables/backend/sprint-0/jiugeng-erp-backend/src/main/java/com/jiugeng/erp/catalogdeletion/CatalogDeletionService.java`：沿用事务、父体与子体行锁，存在任何未删除子体即返回 HTTP 409 / `PRODUCT_HAS_VARIANTS`；启用、待定、停用同样处理。校验发生在删除及审计写入前；空父体仍走原权限、版本与逻辑删除路径。子体单独删除及历史资料保留规则不变。
- 同步 UI 标准、状态报告与交付物登记；无数据库迁移或模块内部类型跨域依赖。

## 验证与剩余工作

按用户约定本次不运行应用测试、完整门禁或浏览器回归。已审阅变更并执行 `git diff --check`；使用 `pnpm --dir frontend exec eslint --fix --fix-type layout src/components/SkuDetailDialog.vue src/components/CatalogDeleteDialog.vue src/views/ProductsView.vue` 整理局部模板格式，退出码 0。测试用例已经准备但尚未执行：

- `SkuDetailDialog.test.ts`：名称/采购链接/用量展示，无完整资料跳转，无权限不请求，失败重试与晚到响应隔离。
- `ProductsView.test.ts`：存在子体时禁用确认，不发父体删除；无子体可确认，服务端拒绝时保留原因；子体删除原行为保留。
- `CatalogDeletionControllerTest.java`：三种子体状态均拒绝父体删除，父子记录/客户商品/父体版本不变、不写成功删除审计；删除子体后才可删除父体。
- 历史目录、发货和采购回归中的父体删除准备动作改为先删子体再删父体，继续验证历史发票、发货、采购、媒体、资料和审计保留。涉及 `DashboardControllerTest.java`、`PurchaseControllerTest.java`。

本迭代交接前统一执行：

1. `frontend/`：`pnpm quality`，合并此前 UI 累计改动执行 `pnpm exec playwright test` 和必要浏览器验收。
2. `deliverables/backend/sprint-0/jiugeng-erp-backend/`：`./mvnw clean verify`。
3. 仓库根：`git diff --check`、审阅累计 diff / status。门禁前结果均不可表述为已通过本轮验收。

待验收重点：有/无权限、停用供应商及辅料关系、加载失败重试、快速切换子体、筛选后隐藏的子体、停用子体、最后一个子体删除后刷新版本，以及旧页面直接提交父体删除的服务端拒绝。未启动或重启服务、未操作真实业务数据。提交 main 和部署等待用户指令，由 PJM 负责。


## 同迭代追加：更多资料、紧凑表格与数量列入口

用户继续要求增强采购/辅料信息展示和 UI，并让列表供应商/辅料列可点击。仍在原分支、同一写入权下追加，没有提交或发布。

- `SkuDetailDialog.vue`：按用户对留白与篇幅的反馈，采购和辅料改为紧凑表格，移除概览栏和大卡片。供应商名称/主供应商/类别、联系人/微信、采购/电话、状态并排；辅料图片/名称/类型、规格、用量/单位、损耗率、关联状态/辅料状态并排。两类备注、联系图片、资料单位和关联更新时间放在整行展开区，默认收起，切换子体/页签后重置；保留 64px 图片和预览。复用 TableViewport、PurchaseItemImage 与 SupplierContactLinks；使用共享密度变量，关联页签只有表格滚动，窄屏可横向滚动。
- 新增可选 `initialTab`，直接进入采购或辅料页签，普通详情维持基础信息。复用现有 `domainApi.supplier` / `domainApi.material`，不改变后端读取权限。先展示关联数据，最多四个详情请求并发，对 ID 去重；队列在离开页签后停止继续请求，旧响应忽略。失败项单独重试，无数据使用明确加载或错误状态。
- `ProductsView.vue`：供应商数量按钮显示“家”、辅料显示“种”，点击打开当前子体对应页签；按钮可键盘聚焦，沿用权限过滤。父体无直接关联，原“—”保持。
- `SkuDetailDialog.test.ts`、`ProductsView.test.ts`：补充字段、展开/收起、直接页签、局部重试不重拉关联、损耗率转换与列表入口恢复基础页签断言，更新原名称展示断言。用例已准备，未运行。
- 前次局部格式命令：`pnpm --dir frontend exec eslint --fix --fix-type layout src/components/SkuDetailDialog.vue src/views/ProductsView.vue`，退出码 0；`git diff --check` 通过。没有执行应用测试、浏览器验证、服务启停或数据写入。
- 紧凑表格调整后仅执行 `pnpm --dir frontend exec eslint --fix --fix-type layout src/components/SkuDetailDialog.vue src/components/SkuDetailDialog.test.ts`（退出码 0）与 `git diff --check`；用例和浏览器布局仍待统一验证。
- 交接验收追加：长供应商/辅料名称、无联系方式、无图片、单项详情失败、权限不足、多关联滚动、桌面/窄屏/手机表格布局与行展开/收起、联系图片嵌套预览，以及数量列直达正确子体/页签。继续与上文及此前 UI 累计改动统一验证，不能引用历史通过结果宣称已验收。


## 同迭代追加：新增与编辑的供应商、辅料紧凑列表

- 用户明确要求产品新增和编辑中的供应商、辅料同步优化；继续同一分支、同一未提交迭代，无新增 ERP 编号。
- `frontend/src/components/ProductForm.vue`：关联编辑改成表格，选择与计数集中工具栏；主供应商、采购/电话直接展示，辅料用量与单位同列、损耗率独立列，关系备注默认收起并标明是否已填写。输入保持 40px，取消原卡片布局；保留添加、去重、解除、校验、草稿及整体保存语义。新增、编辑、复制和单 SKU 编辑共用。保存中禁用关系数据修改。
- `frontend/src/components/ProductSupplierFilter.test.ts`：补充选择供应商/辅料、切换主供应商、收起备注不丢失、跨页签定位无效用量、数值/单位原样提交、保存中禁用、解除后空状态的用例；未执行。
- 同步 UI_STANDARD、STATUS_REPORT、DELIVERABLE_REGISTER。仅执行 `pnpm --dir frontend exec eslint --fix --fix-type layout src/components/ProductForm.vue src/components/ProductSupplierFilter.test.ts`（退出码 0）和 `git diff --check`，未执行应用测试或浏览器验证，未启停服务、提交或部署。
- 待迭代验收补充：新增/编辑/复制入口，多 SKU 相同关联对象的备注独立性，窄屏横向滚动与操作可达性，长名称、零关联、多关联，以及保存失败后草稿保留。原供应商档案管理、辅料选择器与后端接口未修改。
