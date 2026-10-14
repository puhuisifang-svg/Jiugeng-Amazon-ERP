# ERP-012 SKU—辅料单层关系

| 字段 | 内容 |
| --- | --- |
| 状态 | Ready for Acceptance |
| 基线 | `main@88968b2` |
| 分支 | `feature/ERP-012-sku-material-relations` |
| 数据库版本 | V7 |

## 交付范围

- 正式 MySQL/H2 `ProductAuxiliaryMaterial` 单层关系表及 V7 迁移。
- 按 Product 或 SKU 查询，以及单条新增、调整、逻辑停用 API。
- “应用到全部 SKU”生成每个启用 SKU 的独立关系；已有关系不覆盖，之后可逐条调整。
- SKU + 辅料唯一；标准用量必须大于零，损耗率限制在 0～1，单位必填。
- 只允许引用启用 Product、ProductVariant 和 AuxiliaryMaterial。
- `products.*` 与 `materials.*` 双能力约束，以及新增、调整、停用、批量应用审计。
- 产品详情正式模式提供对应辅料维护；mock 模式明确只读且不伪装写入。

## 架构边界

- 关系实现归属 Material / Inventory 模块，通过关系自身仓储读取产品表稳定标识，不依赖 Product 模块内部 Java 类型。
- 当前仍为单层关系，不引入多层 BOM、成本、替代料、采购或生产领料。

## 验收证据

- 后端 `./mvnw clean verify`：52/52 通过，包含 Spring Modulith 边界验证。
- 前端 `pnpm quality`：17 个测试文件、46 项测试通过；lint、类型检查及生产构建通过。
- 临时 MySQL 8：V1→V7、关系探针重启持久化与 mock 隔离通过。
- `git diff --check`：通过。
