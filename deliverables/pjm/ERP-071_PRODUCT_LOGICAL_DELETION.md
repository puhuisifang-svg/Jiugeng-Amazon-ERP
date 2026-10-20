# ERP-071 产品管理关联商品删除

## 调查和授权

- 用户要求调查并修复产品管理指定商品无法删除。只读查询确认存在采购及今日/待发货关联；代码仍对源产品执行硬删除前引用拦截，ERP-067 仅改变了客户商品删除。
- 当前分支 `fix/ERP-055-shipment-sku-binding`；基准 HEAD `f1af0fc94e8bd386688b1c735bf776bba77ca8bf`，延续累计开发及本地部署授权。保留先前全部修改，未提交、未推送、未合并 main。
- 实施计划：产品/SKU 逻辑删除与列表过滤 → 新建入口拒绝、原单据延续 → 回归测试 → 备份、本地迁移和健康检查。不对用户指定商品执行实际删除。

## 变更

- `V39__product_logical_deletion.sql`：产品及 SKU 删除时间/删除人；保留主档、外键和编码唯一性。
- `CatalogDeletionService`：父体/SKU 删除标记及客户商品级联逻辑删除；保留版本、权限、事务、审计。图片和说明书不释放、不清除。
- `ProductRepository`：列表/详情/编辑过滤已删除记录；编码保留，防止旧单据串到新身份。
- `ProductLookup` / `ProductLookupService`：区分正常选择与已有单据读取，原停用规则保持。
- `CustomerService`、`DashboardService`、`ShipmentDispatchService`：新建拒绝删除档案，既有发货沿用原身份及快照，可编辑/转单/发货。
- `PurchaseRepository` / `PurchaseService`：新建及新增明细不可选删除商品；原采购明细可编辑、提交及收货，编辑保留品名/编码/单位快照。
- `ProductImportService`：删除编码禁止导入覆盖；`ProductCustomsService`、`SkuDocumentController`、`ProductMaterialRelationRepository`、`MaterialImportService` 拦截已删除档案的新维护入口。历史说明书仍可下载。
- `ProductsView.vue`：删除确认说明改为逻辑删除、保留单据及编码。
- 测试：`CatalogDeletionControllerTest`、`DashboardControllerTest`、`PurchaseControllerTest`、`ProductsView.test.ts`。

## 限制与交接

- 删除是从目录隐藏，不是取消已有采购/发货，也不会删除历史业务。
- 编码保留，不支持原编码重新建档；暂无页面恢复入口。未增加永久物理清除功能。
- 实际业务记录不用于破坏性验证；删除测试在隔离测试库执行。
- `./mvnw clean verify`：177 tests 全通过，BUILD SUCCESS。覆盖父体/SKU 逻辑删除、图片/说明书/发票保留、列表隐藏、删除编码禁止导入覆盖、新业务拒绝、既有发货编辑/转单/确认、采购草稿编辑/提交/收货。
- `pnpm quality`：61 files / 240 tests 全通过；lint、类型检查、构建通过。
- 初次回归发现非法客户商品引用的状态码从 400 变为 404，已恢复兼容；新增采购测试缺少 delete 静态导入，已修正。最终全量门禁通过。
- 备份 `.local/backups/erp071-rJ5Gn3/pre-migration.sql.gz`，权限 600，gzip 校验通过。
- 仅重启本地 `jiugeng-mvp:0.0`，执行 `scripts/dev/run-local-mysql-backend.sh`；前端 Vite 自动更新。
- `git diff --check` 通过，暂存区为空；除本次变更外保留全部先前累计修改，无无关新增修改。
- 部署后后端健康 `UP`，MySQL Flyway V39 / success=1；只读检查目标商品及 SKU 仍保留，目标未标记删除。没有替用户删除真实数据。
