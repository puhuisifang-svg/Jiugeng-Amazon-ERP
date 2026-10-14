# ERP-011 正式辅料主数据持久化

| 字段 | 内容 |
| --- | --- |
| 状态 | Ready for Acceptance |
| 基线 | `main@172cdd7` |
| 分支 | `feature/ERP-011-material-master` |
| 数据库版本 | V6 |

## 交付范围

- 正式 MySQL/H2 `AuxiliaryMaterial` 表及 Flyway V6 迁移。
- `/api/v1/materials` 列表、详情、新增、编辑与逻辑停用接口。
- 辅料编码按去空白后的大写规范值执行全局唯一校验；保存后不可修改编码，纠错需停用旧记录并新建。
- 名称、类型、规格、单位、HTTPS 图片地址、备注及状态字段校验。
- `materials.read` / `materials.write` 后端动作级鉴权及同事务业务审计。
- 正式模式辅料页面支持新增、编辑、停用；mock 模式继续走合成接口并保留显式提示。
- 辅料只逻辑停用，不物理删除。

## 验收证据

- 后端 `./mvnw clean verify`：49/49 通过。
- 前端 `pnpm quality`：16 个测试文件、45 项测试通过；lint、类型检查和生产构建通过。
- 临时 MySQL 8：V1→V6 迁移、数据库重启持久化和 mock 接口隔离通过。
- `git diff --check`：通过。

## 边界与后续

- 本版本不实现 SKU—辅料关系，该能力属于 ERP-012。
- 本版本不实现库存余额、安全库存和库存流水，该能力属于 ERP-013。
- 图片字段只登记 HTTPS 链接，不抓取远程内容、不上传文件。
