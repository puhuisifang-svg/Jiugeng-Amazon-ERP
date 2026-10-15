# ERP-021-003 产品与 SKU 重构验收记录

## 结论

ERP-021-003 已按冻结 PRD v1.0 完成技术验收，具备合并和本地真实数据服务升级条件。数据库迁移使用 V16，并同时验证空库 V1→V16 与现网 V15→V16。

## 交付范围

- 产品统一使用产品名称，产品与 SKU 均支持启用、待定、停用；新建默认待定。
- SKU 使用固定克重、乐观锁、一张受控主图，并退出条码、材质、内部规格、重量单位及动态属性的新版写入。
- 新增共享供应商档案、SKU/辅料多供应商关系、主供应商唯一规则及受控二维码。
- 产品、SKU、图片绑定、供应商引用和辅料关系通过服务端聚合事务保存。
- 产品详情提供基础信息、SKU、关联辅料、变更记录四页签；列表支持搜索与状态筛选。
- PRODUCTS、VARIANTS 导入模板升级到 2.0，其余三类模板保持 1.0。

## 权限与安全结论

- 活跃范围扩展为 24 项 capability、3 个角色映射和 15 个 API resource families。
- ADMIN 与 PRODUCT_MATERIAL 可维护供应商；SELECTION 不具备 `suppliers.read/write`。
- 供应商电话、采购链接、二维码及审计快照均执行服务端隔离，不能仅依赖前端隐藏。
- SKU 主图按 `products.read` 鉴权；供应商二维码按 `suppliers.read` 鉴权。
- JPG、PNG、WebP 执行真实解码、5 MiB、4096 像素及 1600 万像素限制；临时资源支持孤儿清理。

## 导入兼容结论

- PRODUCTS 2.0 使用 `product_name`，不再接受品牌和内部类目。
- VARIANTS 2.0 使用 `package_weight_grams` 和 SKU 三态，不再接受旧条码、材质、规格、重量单位及动态属性工作表。
- V16 后未完成的 PRODUCTS/VARIANTS 1.0 批次禁止提交并返回稳定错误码；历史已完成批次及结果文件继续可下载。
- 辅料、SKU—辅料及期初库存导入继续使用 1.0，原有语义与门禁保持不变。

## 自动化证据

- 后端 `./mvnw clean verify`：99 项通过。
- 前端 `pnpm quality`：30 个测试文件、74 项测试通过；lint、TypeScript、生产构建通过。
- 导入机器契约 npm/Python 校验通过。
- MVP 范围契约：5 类导入、24 项 capability、3 个角色、15 个 API resource families。
- `scripts/qa/verify-mysql-profile.sh`：MySQL V1→V16、重启持久化及 mock 隔离通过。
- `scripts/qa/verify-mysql-backup-restore.sh`：数据库、工作台图片、SKU/供应商图片联合备份，删除重建后元数据计数与文件哈希一致；升级前无 catalog 表场景兼容通过。

## 运维约束

- 升级前暂停工作台与产品目录写入并执行联合备份。
- MySQL、`ERP_DASHBOARD_IMAGE_DIR` 与 `ERP_CATALOG_IMAGE_DIR` 必须作为同一时间点的恢复单元。
- 恢复脚本要求两个图片目录分别提供精确确认值，并保留原目录作为回退副本。
- 生产发布后仍需使用真实脱敏样本完成业务 UAT；本记录仅代表技术验收通过。
