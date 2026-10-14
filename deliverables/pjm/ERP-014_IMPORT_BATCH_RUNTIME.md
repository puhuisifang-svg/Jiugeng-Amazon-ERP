# ERP-014 五类导入批次运行底座

| 字段 | 内容 |
| --- | --- |
| 状态 | Ready for Acceptance |
| 基线 | `main@733057c` |
| 分支 | `feature/ERP-014-import-batch-runtime` |
| 数据库版本 | V9 |

## 交付范围

- 仅开放 PRODUCTS、VARIANTS、AUXILIARY_MATERIALS、PRODUCT_AUXILIARY_MATERIALS、AUXILIARY_MATERIAL_OPENING_INVENTORY 五类导入；历史 STORE_LISTINGS 继续保留在证据契约中，但运行时拒绝。
- V9 持久化导入批次、逐 sheet/row/field 问题、预览行和操作幂等记录。
- 上传、模板下载、批次列表/详情、问题、预览、取消、过期重验、提交和结果框架 API。
- 状态严格限制为 QUEUED、VALIDATING、VALIDATED、COMMITTING、SUCCEEDED、FAILED、CANCELLED；没有 PARTIAL。
- 未注册业务处理器时提交明确返回 `IMPORT_HANDLER_NOT_REGISTERED`，不会写业务表或伪装成功；ERP-015～017 按导入类型注册处理器。
- Apache POI 5.4.1 解析 XLSX；校验模板元数据、sheet、精确表头、1,000 行上限和公式单元格。
- 上传上限 10 MiB，并在 POI 解析前检查 OOXML ZIP 条目数、展开体积、压缩比、路径穿越、宏、外链和嵌入对象。
- 受限本地 staging 使用随机不透明文件名和收紧权限，通过 `ImportFileStore` 边界为对象存储预留替换点；默认目录位于系统临时目录，不进入仓库。
- 上传及取消/重验幂等：同 key 同载荷重放，异载荷返回 409；预览 24 小时过期后只能生成新批次重验。
- 正式前端提供上传、批次列表、问题与预览、取消及重验；mock 模式只显示明确占位，不伪造批次。

## 安全与边界

- 原文件路径、凭据和 Secret 不写入数据库、API 或审计；数据库只保存随机 storage key 与 SHA-256。
- 无效扩展、MIME、OOXML、模板或表头在创建批次前拒绝，失败不会触碰产品、SKU、辅料、关系或库存业务表。
- `imports.*` 仍使用既有 19 项 capability；选品角色没有导入写权限。
- 模块不依赖 product、materialinventory 或 inventory 内部 Java 类型，Spring Modulith 边界保持通过。

## 验收门禁

- 后端：`./mvnw clean verify`。
- 前端：`pnpm quality`。
- 机器契约：`npm ci --ignore-scripts && npm test && python3 validate_contract.py`。
- 临时 MySQL 8：V1→V9、批次/预览重启持久化和 mock 隔离。
- `git diff --check`。
