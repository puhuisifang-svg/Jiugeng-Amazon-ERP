# ERP-060 开票信息、默认客户与客户商品发货

日期：2026-10-19。

## 授权与基线

- 延续用户授权的当前分支累计开发，仅本地更新，不提交、不推送。分支 `fix/ERP-055-shipment-sku-binding`，基线 `f1af0fc94e8bd386688b1c735bf776bba77ca8bf`。
- 用户明确确认：现有SKU与历史未完成发货归入“九更”，并将其设为默认客户；未知供货价留空，不填0。
- 保留ERP-055～059既有未提交修改。开始/结束均检查分支及状态，无无关改动，无暂存文件。
- 计划：开票和默认客户模型 → 发货绑定及客户快照 → 迁移现有SKU/未完成发货 → 客户商品选择器与列表/导出 → 测试、备份、本地迁移。

## 实现

- 客户开票信息：抬头、纳税人识别号、注册地址、注册电话、开户行、银行账号。均可选，未猜测税号或银行数据；表单可编辑，主列表不展开银行资料，审计不写入这些敏感字段。
- 客户列表显示默认客户标记及“设为默认”入口（打开已勾选的编辑弹窗，保存后生效）。独立单行preferences保证最多一个默认客户，更新锁定该行；切换后旧默认客户版本递增，避免旧表单覆盖。默认客户不可直接停用/取消，需先设置另一启用客户。
- 客户商品供货价允许NULL，前端显示“未定价”，空输入保存NULL；0仍代表明确填写0，二者不混淆。金额格式/范围校验继续有效。
- 今日/待发货新增使用客户+客户商品选择器，两级均支持搜索与分页；默认客户自动带出，编辑保持原客户，切换客户立即清空原商品。无商品时提示先建客户商品，不猜测其他客户的SKU。
- 后端强制要求customerProductId并校验客户、客户商品、SKU状态及商品对应关系，旧的直接SKU请求不能绕过。
- 发货记录保存客户商品快照（客户名、客户商品编码、特殊需求及关联标识）。今日/待发货互转沿用快照；编辑可更新快照；已发货账本保留原快照。客户开票资料及供货价不扩散到首页发货接口。
- 首页、管理列表及导出显示客户/客户商品编码，特殊需求随发货展示；统计按客户商品区分同一SKU在不同客户的记录，未绑定的旧已发货记录明确显示历史状态。搜索支持客户快照和备注。
- 标签文件增加客户商品绑定，列表/下载同时校验SKU和客户商品，换客户后不能误读原客户标签；说明书仍沿用SKU。
- 发货表单放弃确认采用隐藏保留表单，继续填写不会重新初始化客户选择器；保存中拦截重复提交及关闭。

## 迁移及本地数据

- V31：新增开票字段、唯一默认客户配置、发货客户关联与快照、标签客户商品关联，供货价改为可空。
- V32：一次性创建/复用九更客户、设置默认，为所有现有SKU建九更客户商品（不重复），未知供货价NULL。只回填ACTIVE今日发货和PENDING待发货，记录迁移审计；已发货、归档及无明确SKU的历史记录不猜测、不改写。
- 新增SKU不会持续自动复制给所有客户，后续需在客户商品中建档；本轮不涉及订单、税额计算、开票接口、收款或库存扣减。
- 迁移前只读核对：1个既有客户、1条客户商品、4个SKU、3条SHIPPED今日发货、3条TRANSFERRED_TO_TODAY待发货，没有未完成发货。
- 先停止本地后端并备份MySQL到 `.local/backups/erp060-yjYdE4/pre-migration.sql.gz`（Git忽略，权限600，gzip完整性检查通过），未重启/清空MySQL。
- 通过 `scripts/dev/run-local-mysql-backend.sh` 更新本地后端；V31/V32 success=1，健康检查UP。默认客户九更、状态ACTIVE，九更客户商品4条、4条价格NULL；客户总数2、客户商品总数5。原3条已发货、3条待发货历史及3条发货统计记录保留。
- Vite加载最新源码，生产构建已更新。没有执行实际发货、删除用户记录或写入测试订单。

## 文件与质量门禁

- 后端customer模块：CustomerTypes、CustomerService、CustomerController、新公开边界CustomerProductLookup；DashboardController/Types/Service/Repository、ShipmentDispatchService及ShipmentDocumentController接入客户商品快照与校验。
- 数据迁移：`V31__customer_billing_and_shipping.sql`、`db/migration/V32__internal_customer_shipping_backfill.java`。
- 前端：customers/dashboard API类型；CustomerManagementView；新增ShipmentCustomerPicker及测试；DashboardMaintenanceView、DashboardShipmentRow、DashboardView、ShipmentStatisticsView、shipmentExport及相关测试。
- 后端测试：CustomerControllerTest开票/默认/停用/NULL价格；DashboardControllerTest强制绑定、SKU匹配、快照互转及禁用发货；CustomerShippingMigrationTest验证未完成回填、已完成不变、NULL价格和重启不重复迁移。
- `./mvnw clean verify`：通过，149测试、零失败/错误/跳过，模块边界检查通过。日志 `/tmp/erp060-backend.log`。
- `pnpm quality`：通过，53文件、214测试，lint、类型检查、构建通过。日志 `/tmp/erp060-frontend.log`。
- `git diff --check`：通过。回归中修正了客户选择器在放弃确认后重建导致选择丢失的问题，并更新了新增客户文案、导出行垂直位置及历史变更类型断言。

## 待验收与交接

- Mac锁屏，CUA无法检查真实浏览器页面；已告知用户解锁后继续视觉验收，不宣称浏览器验收完成。
- 全部修改仍未提交，等待用户统一指令后交项目经理复核、提交main及正式部署；不宣称已合并、正式发布或项目经理验收通过。
