# ERP-016 辅料与 SKU—辅料关系导入

## 交付状态

| 项目 | 内容 |
| --- | --- |
| 分支 | `feature/ERP-016-material-relation-imports` |
| 基线 | ERP-015 已验收主线 |
| 状态 | 开发完成，等待项目经理验收 |
| 范围 | `AUXILIARY_MATERIALS`、`PRODUCT_AUXILIARY_MATERIALS` |

## 实现范围

- 在 `materialinventory` 根包提供稳定的 `MaterialImportGateway`；`importexport` 仅依赖公开边界，不访问模块内部 Repository 或类型。
- 辅料按规范化 `material_code` 幂等 upsert，保留首次落库的展示编码；支持名称、类型、规格、基础单位、HTTPS 图片、状态和备注。
- SKU—辅料关系按规范化 `sku_code + material_code` 幂等 upsert；只允许引用启用产品、SKU 和辅料，用量单位必须等于辅料基础单位。
- 上传校验与提交前校验执行同一组领域规则；提交阶段业务写入、结果计数和成功审计位于同一事务，任一失败整批回滚。
- 复用 ERP-014 的提交幂等机制：同 key 同载荷重放，异载荷返回 `409 IDEMPOTENCY_KEY_REUSED`。
- V11 将辅料规格列扩展到 500 字符，与受控字段字典一致；MySQL 重启探针覆盖该迁移。

## 校验规则

- 编码符合 `^[A-Za-z0-9][A-Za-z0-9_-]{0,63}$`，文件内大小写不敏感判重。
- 辅料类型：`LABEL / BOX / BAG / MANUAL / INSERT / OTHER`。
- 基础单位：`PCS / SET / ROLL / SHEET / M / KG`；图片仅接受无用户信息的 HTTPS URL。
- 辅料状态：`ACTIVE / INACTIVE`，映射为持久层 `ACTIVE / DISABLED`。
- 标准用量 `> 0` 且最多 4 位小数；损耗率 `0..1`；关系启用值仅接受 `TRUE / FALSE`。
- 关系引用必须存在且启用，且关系单位必须与辅料基础单位一致。
- 单文件数据行上限沿用运行时的 1000 行。

## 验收覆盖

- 两类处理器创建、更新、无变化计数与审计。
- 依赖顺序、缺失/停用引用、单位不匹配和文件内重复键。
- 校验后引用状态变化导致提交拒绝，且不产生部分业务写入。
- 1000 行辅料导入性能门禁。
- 后端全量、前端质量、导入契约、H2/MySQL V1→V11 迁移、MySQL 重启持久化与 mock 隔离。

## 范围边界

- 本迭代不实现期初库存导入；`AUXILIARY_MATERIAL_OPENING_INVENTORY` 继续明确拒绝提交，留给 ERP-017。
- 不包含多层 BOM、替代料、版本或成本核算。
