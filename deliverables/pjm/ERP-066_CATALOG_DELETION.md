# ERP-066 商品与客户商品删除

## 授权与状态

- 用户批准父体、SKU 删除方案，并追加客户商品独立删除。
- 延续用户授权的当前分支累计开发：`fix/ERP-055-shipment-sku-binding`。
- 基准 HEAD：`f1af0fc94e8bd386688b1c735bf776bba77ca8bf`。
- 本任务与 ERP-055 至 ERP-065 累计修改均未提交；未暂存、推送、合并 main 或远端部署。等待用户后续指令及项目经理复核。
- 已检查工作区，保留全部既有累计修改，未发现本任务之外的新无关修改。

## 行为与文件

- 新增后端 `catalogdeletion/` 模块：Controller、Service、ExceptionHandler、package-info。
  - 父体删除全部 SKU 及未被业务引用的关联客户商品；SKU 删除仅清理对应 SKU 与客户商品。
  - 客户商品独立删除保留客户、源产品、源 SKU 和其他客户的商品。
  - 发货、历史发货、发货附件、发票或采购记录存在时拒绝删除；采购包括草稿、已取消与已收货记录。
  - 全量预检查、事务、版本校验、行锁、权限校验；保留审计记录，释放商品图片引用并清理关联资料。
- `customer/CustomerService.java`：客户商品使用时加锁，与删除互斥。
- `purchase/PurchaseRepository.java`：写入采购验证时锁定商品，与删除互斥。
- 前端 `api/domain.ts`、`api/customers.ts`：新增删除接口。
- 前端 `ProductsView.vue`、`CustomerManagementView.vue`：父体、SKU、客户商品删除入口、成功 toast、失败原因显示。
- 新增 `CatalogDeleteDialog.vue`：统一确认弹窗、默认聚焦取消、遮罩不关闭、提交中禁止重复操作。
- 测试：新增 `CatalogDeletionControllerTest.java`、`CatalogDeleteDialog.test.ts`，更新 `ProductsView.test.ts`、`CustomerManagementView.test.ts`。

## 验证

- 后端 `./mvnw clean verify`：通过，169 tests，0 failures/errors/skipped。
- 前端 `pnpm quality`：通过，59 test files / 235 tests，包含 lint、类型检查、测试与构建。
- `git diff --check`：通过。
- Safari 本地页面检查：父体删除确认弹窗正常显示并取消；客户商品列表存在删除入口，仍为 5 条；产品仍为 3 个父体 / 4 个 SKU。未操作任何真实记录的最终删除。
- 删除成功、关联阻断、图片释放、审计、版本、权限、参数、失败反馈等由隔离测试验证。

## 本地部署

- 2026-10-20 更新 `jiugeng-mvp:0.0`，使用 `./scripts/dev/run-local-mysql-backend.sh`。
- 本地 MySQL 持久化数据未清空。本任务无新增迁移；前端现有 Vite 服务自动更新。
- `/actuator/health` 返回 `{"status":"UP"}`。
- 重启前备份：`.local/backups/erp066-3mfCFU/pre-change.sql.gz`，权限 600，gzip 完整性验证通过。
- 未变更公共隧道/Caddy、未发布远端。

## 风险与范围

- 删除后没有页面恢复入口，确认弹窗明确警告；需要恢复时须经批准使用数据库备份。
- 当前库存模块只维护辅料库存，本任务不会删除辅料库存。商品采购/收货历史通过采购引用检查保护。
- 客户商品的源 SKU 存在采购记录时，保守拒绝该客户商品删除。
- 旧父体级且未绑定 SKU 的发货记录会阻止该父体下任意 SKU 删除，以免破坏历史。
- 当前仅完成本地实现与验证，不代表项目经理验收、main 合并或发布完成。
