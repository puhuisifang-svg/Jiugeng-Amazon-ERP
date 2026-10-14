# ERP-010 正式 Product / SKU 持久化

| 字段 | 内容 |
| --- | --- |
| 状态 | Ready for Acceptance |
| 基线 | `main@56ae59e` + ERP-010～020 主计划 |
| 分支 | `feature/ERP-010-product-persistence` |
| 数据库版本 | V5 |

## 交付范围

- 正式 MySQL/H2 `Product`、`ProductVariant`、`VariantAttribute` 表及 Flyway V5 迁移。
- `/api/v1/products` 查询、详情、新增、编辑与逻辑停用接口。
- 产品编码、SKU 编码和条码均按去空白后的大写规范值执行全局唯一校验。
- 同一产品内动态属性组合生成 SHA-256 规范化签名并保持唯一；同一 SKU 下属性编码忽略大小写唯一。
- 产品和 SKU 只允许逻辑停用；编辑时移除的既有 SKU 自动转为停用，不执行物理删除。
- `products.read` / `products.write` 后端动作级鉴权及同事务业务审计。
- 产品列表、新建、详情、编辑、停用页面；password/mysql 模式走正式 API，mock 模式保留原合成接口和显式提示。

## 验收证据

- 后端 `./mvnw clean verify`：46 项通过（最终提交前复跑）。
- 前端 `pnpm quality`：15 个测试文件、44 项通过（最终提交前复跑）。
- 临时 MySQL 8：V1→V5 迁移通过，Product/SKU/属性探针在数据库重启后保持，mock 专用接口继续隔离。
- `git diff --check`：最终提交前执行。

## 边界与后续

- 本版本不实现 StoreAccount、AmazonCatalogItem、StoreListing。
- SKU—辅料关系在 ERP-012 实现；正式导入在 ERP-014～ERP-017 实现。
- 当前列表为 MVP 数据量优化；分页、复杂筛选和导出可在业务样本到位后按性能门禁补充。
