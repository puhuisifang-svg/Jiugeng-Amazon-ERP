# ERP-015 产品、SKU 与动态属性导入

| 字段 | 内容 |
| --- | --- |
| 状态 | Ready for Acceptance |
| 基线 | `main@9e4a48f` |
| 分支 | `feature/ERP-015-product-variant-imports` |
| 数据库版本 | V10 |

## 交付范围

- 为 ERP-014 批次运行时注册 `PRODUCTS` 与 `VARIANTS` 正式提交处理器，其余三类继续明确返回未注册。
- 产品按规范化 `product_code`、SKU 按规范化 `sku_code` 幂等 Upsert；动态属性按 SKU 与规范化 `attribute_code` 整体更新。
- 产品导入与 SKU/属性导入分别整批提交；提交前再次校验，任何引用、唯一性或数据库错误都会回滚整批业务写入，不产生 `PARTIAL`。
- V10 保存创建、更新、未变化计数和提交时间；同幂等键同载荷重放，异载荷返回 409。
- 正式前端支持确认提交并展示新增、更新、未变化和实际写入计数。
- SKU 包装重量单位完整保存为 `G/KG/OZ/LB`；V10 将历史非零、无单位重量按旧界面隐含兼容值补为 `KG`，历史零值归一为空。

## 校验和安全

- 编码符合 `^[A-Za-z0-9][A-Za-z0-9_-]{0,63}$`，产品/SKU 大小写不敏感唯一。
- SKU 只能引用启用产品；条码仅允许 8/12/13/14 位数字并全局唯一。
- 每个导入 SKU 必须包含 1～30 个属性；同 SKU 属性编码和展示顺序唯一，展示顺序不小于 1。
- 同产品属性组合在文件内和数据库内均唯一；重量必须大于 0 且与合法单位同时填写。
- `importexport` 只依赖 product 模块公开的 `ProductImportGateway`，不接触内部 repository/service/type。
- 每条实际创建或更新写入业务审计；不变行不生成伪审计。

## 验收门禁

- 后端：`./mvnw clean verify`，覆盖创建、更新/不变、提交幂等、延迟引用失效整批回滚、权限和 Modulith 边界。
- 前端：`pnpm quality`，覆盖提交请求和结果计数。
- 机器契约：`npm ci --ignore-scripts && npm test && python3 validate_contract.py`。
- 临时 MySQL 8：V1→V10、结果计数/产品重量单位重启持久化和 mock 隔离。
- `git diff --check`。

## 留存风险

- 1,000 行上限沿用 ERP-014；当前实现为同步事务提交，ERP-020 再按真实脱敏样本采集 P95 与 1,000 行耗时。
- 真实业务样本仍缺失；本版本只声明技术验收，不宣称真实业务 UAT 完成。
