# ERP-050 首页确认发货、发货导航与统计

## 任务与边界

- 用户指定本地实现、本地更新，不提交、不推送、不远程部署。沿用累计修改分支 `fix/ERP-036-material-integer-controls`，基线 `42b580228ffd4c3cafa5097a5e7cd0a52125cf14`。开始及结束检查均无无关改动；工作区为 ERP-036 至 ERP-050 累计未提交变更。
- 计划：检查菜单/权限/生命周期 → 首页单选与确认 → 独立发货台账及终态 → 筛选统计 → 回归和本地页面检查。
- 本轮发货为系统内“确认实际已发货”，只记录事实及统计，不触发物流下单，不扣减辅料库存，不实现部分发货、取消发货、退货或更正流程。界面确认文案明确告知。
- 参考 ERP 中先确认交付操作、再形成交付记录/文档的流程思路：[Odoo 官方交付文档](https://www.odoo.com/documentation/18.0/applications/inventory_and_mrp/inventory/shipping_receiving/advanced_operations_shipping/print_on_validation.html)。本地统计口径是本项目设计，不宣称符合特定行业认证标准。

## 行为

- 首页今日发货去掉日期字段；待发货最晚日期红色加粗，仍只到日期。
- 有发货管理权限的用户可单选一条记录；左下角“前往今日发货管理”旁显示“一键发货”。未选择时禁用，确认弹窗展示产品、数量、箱数、备注和影响说明；取消不写入，保存时禁用重复操作，失败可沿用请求标识重试。
- 发货确认只接受北京时间当天的有效今日安排；行锁、版本校验、幂等键和来源唯一约束防止重复发货。成功后源记录进入 SHIPPED 终态，不能编辑/删除/转回待发；首页刷新移除，保留审计。
- V28 Java migration：扩展今日发货状态约束，创建 dashboard_shipment_dispatch 与日期索引。Java migration 为兼容 MySQL DROP CHECK 与 H2 DROP CONSTRAINT 语法，明确 checksum。
- 台账独立保存来源、产品编号/名称、关联 SKU、数量、箱数、备注、计划日期、确认日期/时间、操作人快照；后续产品改名不改写历史。SKU 通过 ProductLookup 公共模块边界读取，不跨用内部类型。当前发货是产品级，关联 SKU 仅供参考，不按 SKU 分摊数量。
- 新增“发货 → 发货管理”菜单，包含今日发货管理、待发货管理、发货统计。原路由不改以兼容书签；原工作台维护仅保留新品待开发。所有入口/API 复用 dashboard.shipments.manage 权限。
- 统计以确认发货时间的北京时间日期为准。默认本月至今，支持今日/近7天/近30天、起止日期（最多366天）、产品名称/编号/SKU/备注关键词筛选。
- 汇总为发货记录数、套数、箱数、去重产品数；每日汇总、产品数量排行前10、分页明细。汇总始终按全部筛选结果计算，不只统计当前页；关键词按字面值匹配。明细含操作人、发货快照、记录号。只读，不提供改写/删除台账入口。

## 变更文件

- 后端：db/migration/V28__shipment_dispatch.java；dashboard/ShipmentDispatchController.java、ShipmentDispatchService.java、DashboardTypes.java、DashboardExceptionHandler.java；product/ProductLookup.java、ProductLookupService.java；DashboardControllerTest.java。
- 前端：layouts/AdminLayout.vue、router/index.ts、api/dashboard.ts、components/DashboardShipmentRow.vue、views/DashboardView.vue、DashboardMaintenanceView.vue、新增 ShipmentStatisticsView.vue、styles/main.css；对应导航/首页测试及新增 ShipmentDispatch.test.ts。

## 验证与本地交接

- `pnpm quality` PASS：49 文件、188 测试，Lint/类型检查/生产构建通过。统计筛选栏压缩为单行、四项指标同排后再次 `pnpm build` PASS。
- `./mvnw clean verify` PASS：141 测试。新增权限拒绝、旧版本冲突、同请求重试不重复、重复发货拒绝、已发货禁止修改/删除/转回、首页移除、快照稳定、审计唯一、日期范围、关键词转义、跨页汇总与日边界覆盖。
- `git diff --check` PASS，暂存区为空。没有提交 SHA，全部仍为本地未提交修改。
- 浏览器检查：首页单选、按钮启用及确认弹窗后取消；待发货最晚日期红色显示。未替用户确认任何实际发货。
- 本地 MySQL profile 应用重启、V28 迁移成功，健康检查 HTTP 200；浏览器确认新菜单三入口、统计查询正常返回空台账（未造假回填历史发货）。不重启数据库，不清理既有记录。待用户最终指令后统一同步项目经理，未宣称合并/验收/远程发布。

## 限制与后续

- 历史归档/删除/转移不能推断为实际发货，因此不会自动回填成已发货台账；新统计从明确确认发货开始。
- 发货后无撤销入口，误操作需单独设计带原因、审计与反向统计的冲正流程，不应直接删除台账。
- 本轮不接入承运商、运单号、库存扣减或 SKU 级发货拆分；如需要应单独确认业务规则。
