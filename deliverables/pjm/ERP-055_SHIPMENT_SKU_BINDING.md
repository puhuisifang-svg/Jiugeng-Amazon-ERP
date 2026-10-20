# ERP-055 发货管理绑定具体 SKU

日期：2026-10-17

## 授权、基线与计划

- 用户要求修复“发货图片来自 SKU、名称却是父体”的问题，并部署本地；继续保持未提交、不推送，待用户后续统一指令交接项目经理。
- 开始时 `main` 干净，fetch 后 HEAD 与 origin/main 一致：`f1af0fc94e8bd386688b1c735bf776bba77ca8bf`。
- 工作分支：`fix/ERP-055-shipment-sku-binding`。本轮所有修改未提交、未暂存。
- 顺序：持久化 SKU 绑定及旧数据保护 → 全入口取值统一 → 测试与本地部署。未修改 V1.0 已发布标签或远端环境。

## 根因及实现

- 原发货记录只有父体 productId；名称快照来自父体，图片取父体下第一个有图的 SKU，说明书列出父体下所有 SKU，台账亦以父体聚合。
- V29 为今日发货、待发货、发货台账、标签表增加可空 variant_id，旧数据不回填猜测值；名称快照字段扩到 200 字以容纳合法 SKU 名称。
- 新增/编辑必须传 productId + variantId；ProductLookup 公共边界校验 SKU 归属及父体/SKU 启用状态。productName/productCode 在已绑定记录中保存 SKU 名称/编号快照，保持父体 ID 供目录查询。
- 今日与待发货往返保留 SKU，不新增重复来源记录。待发货旧记录转今日、实际确认发货前必须先补选 SKU；发货检查 SKU 有效性。历史已发货快照保持原样，不推断或重新分摊。
- 首页及管理列表展示 SKU 名称/编号；新增/编辑按 SKU 选择。图片预览、导出图片按明确 variantId 查找；SKU 无图不回退兄弟 SKU。说明书仅列出该 SKU。
- 标签按来源、父体和 SKU 共同隔离；切换 SKU 后不再显示或下载原 SKU 标签，旧标签不自动归属新 SKU。标签数据保留，旧未绑定记录可读原标签，但不可新增标签。
- 首页旧未绑定记录标记“历史父体记录 · 待补选 SKU”，不可勾选、全选、发货或导出；编辑保存后恢复。新增列表及历史对比记录 SKU 字段。
- 统计按父体+SKU 分组，不再将同一父体的多个 SKU 合并；旧未绑定台账按父体单列且标识。汇总产品数口径明确为“SKU / 历史父体数”。

## 变更文件

- 后端 dashboard：DashboardController、DashboardTypes、DashboardRepository、DashboardService、ShipmentDispatchService、ShipmentDocumentController。
- 产品公共接口：ProductLookup、ProductLookupService。
- 数据库：`src/main/resources/db/migration/V29__shipment_sku_binding.sql`。
- 后端测试：DashboardControllerTest。
- 前端：api/dashboard.ts；DashboardShipmentRow、ShipmentProductImage、ShipmentManuals；DashboardMaintenanceView、DashboardView、ShipmentStatisticsView；ui/shipmentExport.ts。
- 前端测试：ShipmentFiles、DashboardMaintenanceFormal、ShipmentDispatch、shipmentExport。

## 验证

- `cd frontend && pnpm exec eslint src --fix`：通过。
- `cd frontend && pnpm quality`：通过，50 个文件、196 项测试，类型检查、lint、构建通过。日志 `/tmp/erp055-frontend.log`。
- `./mvnw -q -Dtest=DashboardControllerTest test`：通过。
- 后端 `./mvnw clean verify`：通过，142 项测试，0 失败/错误；含模块边界检查与迁移测试。日志 `/tmp/erp055-backend.log`。
- 新增/更新覆盖：SKU 必填、跨父体拒绝、停用 SKU 拒绝、150 字 SKU 名称、旧记录发货拒绝及补选、往返保持 SKU、同父体多 SKU 独立统计、名称快照不随改名变化、标签跨 SKU 隔离、图片不猜测、说明书单 SKU、旧记录不参与勾选/导出、日期和图片排版旧回归。
- 浏览器实际打开新增待发货，确认显示 SKU 编号 + SKU 名称，选择后图片联动；随后取消，不新增、修改、删除或确认用户业务数据。

## 本地与交接

- 前端 Vite 5173 已加载源码更新，生产构建已生成。
- 本地后端使用既有 `./scripts/dev/run-local-mysql-backend.sh`，只重启 `jiugeng-mvp:0.0` 应用，不重启 MySQL。23:43 成功应用 V29，MySQL schema 到 v29，8080 `/actuator/health` 返回 UP，5173 首页 HTTP 200；浏览器重载后仍正常登录、待发货原有 4 条记录完整可见，无业务写入。
- 不自动修改旧记录的 SKU 归属；请用户对仍需处理的旧记录逐条编辑补选。此前已发货台账保留历史父体语义。
- 标签需在补选/变更 SKU 后核对并重新上传，避免沿用错误标签。未改物流、库存或其他模块流程。
- `git diff --check` 通过，当前状态仅本任务变更，无无关或暂存修改。未提交、合并、推送或远端部署；后续交接由用户统一授权。
