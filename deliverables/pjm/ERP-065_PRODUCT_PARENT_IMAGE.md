# ERP-065 产品父体图片

## 授权与状态

- 用户要求：产品管理父体也支持图片上传。
- 按用户持续授权，在累计分支 `fix/ERP-055-shipment-sku-binding` 开发并更新本地，不提交、推送或远程部署。
- 基线 HEAD：`f1af0fc94e8bd386688b1c735bf776bba77ca8bf`。本次修改未提交、未暂存，保留 ERP-055～064 累计修改，没有引入无关工作。

## 实现

- 新增产品、编辑父体以及原产品编辑表单的基础信息区增加父体图片上传、替换、移除。
- 每个父体维护一张独立主图；保存产品后确认绑定。上传失败保留原图，上传中禁止提交，移除后保存才生效。
- 父体列表行展示受权限保护的缩略图，点击打开大图预览；没有图片仍显示原占位。
- 父体与 SKU 图片独立，批量编辑 SKU 保留父体图片；发货图片仍来自 SKU，未改动发货取图逻辑。
- 使用现有受控图片上传流程，新增 PRODUCT_MAIN 用途，读写权限为 products.read/write；继承图片格式、5 MB 大小和像素限制、上传者绑定校验以及废弃资源清理。
- V36 新增 product.main_image_asset_id 和外键，扩展图片用途约束。父体图片替换、释放与产品修改在同一事务中，使用产品 version 防止覆盖并发修改。
- 产品审计快照记录图片资源 ID，不记录原图或本地存储路径。
- 修正表单内容区的网格行收缩，新增图片区后长表单正常滚动，描述与 SKU 区域不重叠。

## 文件

- 后端 product/ProductTypes.java、ProductController.java、ProductRepository.java、ProductService.java。
- 后端 catalogmedia/CatalogImageGateway.java、CatalogImageController.java、CatalogImageService.java。
- 新增 db/migration/V36__product_parent_image.sql。
- 前端 api/catalog.ts、api/domain.ts、components/ProductForm.vue、views/ProductsView.vue。
- 测试 ProductControllerTest.java、ProductForm.test.ts、ProductsView.test.ts。
- 本记录。

## 验证与部署

- 后端 `./mvnw clean verify`：162 tests，0 failures/errors，BUILD SUCCESS。日志 `/tmp/erp065-backend.log`。
- 前端 `pnpm quality`：58 个文件、232 tests，通过 lint、类型检查、测试和构建。日志 `/tmp/erp065-frontend.log`。
- `git diff --check` 通过，暂存区为空。
- 新增回归覆盖真实 multipart 图片上传、创建绑定、原图读取、替换/释放、移除、版本冲突、批量 SKU 修改保留父图、SKU 图片不受影响、用途不符、他人待绑定资源拒绝、权限拒绝、无效图片拒绝、上传期间禁止提交、失败保留、列表放大及编辑带回。
- Safari 验证本地新增产品入口与布局，重启后列表正常显示 3 个父体 / 4 个子体；没有向本地业务库写入测试产品或图片。实际图片上传/绑定由隔离后端测试覆盖。
- 迁移前备份 `.local/backups/erp065-AaLEZn/pre-migration.sql.gz`，51203 bytes，权限 600，gzip 校验通过。
- 仅重启 `jiugeng-mvp:0.0`，通过 `scripts/dev/run-local-mysql-backend.sh` 启动 MySQL 后端；前端 5173 热更新。未操作公网代理或隧道。
- 本地 Flyway V36 success=1，8080 健康接口 UP；产品 3、SKU 4，未修改既有业务记录。
- 不含父体多图相册、SKU 继承父图功能。正式合并、发布与验收仍由项目经理在用户下达提交指令后复核。
