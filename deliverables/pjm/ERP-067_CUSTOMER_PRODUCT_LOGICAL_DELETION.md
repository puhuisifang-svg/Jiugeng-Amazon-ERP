# ERP-067 客户商品逻辑删除与历史单据延续

## 授权与分支

- 用户批准实施“客户商品逻辑删除＋单据快照”，仅本地修改、部署，继续当前分支累计开发。
- 分支：`fix/ERP-055-shipment-sku-binding`。
- 基准 HEAD：`f1af0fc94e8bd386688b1c735bf776bba77ca8bf`；本任务未提交、未暂存、未推送，未合并 main，未远端部署。
- 检查并保留 ERP-055 至 ERP-066 全部累计修改；无新增无关修改。
- 本记录取代 ERP-066 中“客户商品独立删除受采购/发货/发票阻断”的规则；源产品/SKU 的删除保护不变。

## 实现范围与文件

- `db/migration/V37__customer_product_logical_deletion.java`：增加删除时间、删除人、删除唯一键；保留原记录与外键，同一客户/SKU 可重新建档且使用新 ID。未删除记录仍唯一。
- `catalogdeletion/CatalogDeletionService.java`：独立删除客户商品采用逻辑删除，不再因采购、收货、发货、发票历史阻断；保留版本校验、权限和审计。源产品/SKU 仍需通过原有引用保护。
- `customer/CustomerService.java`：已删除记录从列表/有效选项隐藏，禁止编辑已删除档案；专门的已有单据查询允许沿用已删除的原关联。
- `customer/CustomerProductLookup.java`：区分新建选择与已有单据关联查询。
- `customer/InvoiceService.java`：禁止基于已删除客户商品新建发票/获取新建默认值；已有发票继续使用原开票信息，允许维护发货、开票状态、物流等。新建写入时加客户商品行锁，避免与删除竞态。
- `dashboard/DashboardService.java`：同一客户商品的既有发货记录编辑和转单保留原客户信息、商品名称/SKU 快照；更换商品必须通过有效商品查询。可信快照只来自服务端已有记录，HTTP 输入不能提供快照绕过新建校验。
- `dashboard/ShipmentDispatchService.java`：已有安排允许在客户商品删除后确认发货；客户和源 SKU 停用等校验保持。
- 前端 `CatalogDeleteDialog.vue`、`CustomerManagementView.vue`：客户商品删除提示与源 SKU 删除提示分离，明确隐藏新建选项、保留历史、页面暂无恢复入口。
- 测试更新：`CatalogDeletionControllerTest.java`、`DashboardControllerTest.java`、`InvoiceControllerTest.java`、`CatalogDeleteDialog.test.ts`。

## 数据语义

- 采购明细已保存品名、编码、单位、数量、单价；收货记录基于采购明细，客户商品删除不改动这些数据。
- 发货的客户编码/特殊需求/名称以及发票的抬头/金额等已有快照继续保留，不从重新建立的客户商品取值覆盖。
- 删除客户商品不等于取消现有业务单据，也不会删除源产品、SKU 或其他客户的商品。
- 已删除客户商品仍可被已有单据追溯；重新建档不会复活旧 ID。
- 源产品/SKU 删除仍会检查所有客户商品（含逻辑删除记录）的单据引用，避免破坏历史。
- 未新增回收站/恢复入口；未来若需恢复须单独设计唯一性冲突处理。

## 验证结果

- `./mvnw clean verify`：171 tests，全部通过，包含迁移及模块检查。
- `pnpm quality`：59 files / 236 tests 全通过，lint、类型检查、构建通过。
- `git diff --check`：通过。
- 回归覆盖：采购/历史发货/发票存在时可删除客户商品；原记录保留、删除人记录、列表隐藏、重建同客户 SKU、不允许重复活跃档案；旧发票可继续维护，新发票拒绝；待发货编辑、转今日发货、今日编辑与确认发货均继续使用原名称快照，新建发货拒绝已删除商品。
- 初次新增测试缺少静态 import，已修复并重新跑完全部后端 gate；最终结果以上述 171 项为准。

## 本地部署记录

- 备份：`.local/backups/erp067-C9RdwD/pre-migration.sql.gz`，权限 600，gzip 完整性检查通过。
- 2026-10-20 仅更新 `jiugeng-mvp:0.0`，运行 `./scripts/dev/run-local-mysql-backend.sh`，使用本地持久化 MySQL；Vite 自动加载前端改动。
- 部署后检查：`/actuator/health` 为 `UP`；MySQL Flyway 版本 37，success=1；客户商品仍为 5 条，逻辑删除 0 条。
- 未在本地真实业务记录上执行删除测试；删除行为在隔离测试库验证。
- 不代表项目经理验收、main 合并或远端发布。
