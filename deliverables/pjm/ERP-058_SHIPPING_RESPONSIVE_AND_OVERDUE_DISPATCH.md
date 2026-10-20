# ERP-058 发货列表窄屏适配与跨日确认发货

日期：2026-10-18。延续 ERP-055～057 本地累计修复批次。

## 授权与基线

- 用户要求修复发货管理小窗口操作按钮不可达，并在今日发货管理增加发货按钮，支持往日未完成记录。
- 分支 `fix/ERP-055-shipment-sku-binding`；基线 `f1af0fc94e8bd386688b1c735bf776bba77ca8bf`。
- 保留此前同批未提交改动，不切分支、不暂存、不提交、不推送。检查未发现无关修改。
- 计划：列表容器与固定操作列 → 复用发货接口并增加确认弹窗 → 放开过去日期校验 → 回归与本地更新。

## 行为与文件

- `frontend/src/styles/main.css`：今日/待发货表格限制在容器内横向滚动，操作列固定右侧；按钮双列排列，极窄屏单列；分页可换行；发货统计分组窄屏改为单列。
- `frontend/src/views/DashboardMaintenanceView.vue`：今日发货有效记录增加“发货”，系统风格确认框展示产品、SKU、原计划日期、数量、箱数与备注；失败保留弹窗，保存中禁止重复操作，同记录同版本重试复用幂等键。成功刷新列表并 toast 提示。
- `ShipmentDispatchService.java`：允许今日及以前日期的有效记录确认发货，禁止未来日期提前确认；统计保留 planned_date 并按北京时间实际确认日写 business_date。原有权限、SKU、版本、状态、事务锁及幂等校验不变。
- `DashboardMaintenanceFormal.test.ts`、`DashboardControllerTest.java`：覆盖按钮、确认取消、失败重试及幂等键、过去日期成功、未来日期拒绝、原计划日期与实际日期分离，以及既有权限/版本/重复提交/统计。

## 验证与本地部署

- `cd frontend && pnpm exec eslint src --fix`；`pnpm quality`：通过，50 文件、200 测试，类型检查、lint、构建通过。日志 `/tmp/erp058-frontend.log`。
- `cd deliverables/backend/sprint-0/jiugeng-erp-backend && ./mvnw clean verify`：通过，142 测试，无失败/错误/跳过。日志 `/tmp/erp058-backend.log`。
- `git diff --check`：通过。
- Safari 本地页面检查标准及放大后的窄视口，右侧操作列及按钮可见；检查后恢复原缩放。未对用户记录实际执行发货或删除。
- 本地 Vite 更新源代码；仅重启 jiugeng-mvp 后端，沿用 mysql profile，不重启数据库、不清空业务数据。本轮无新增数据库迁移。

## 边界与交接

- 首页仍只展示当天计划；往日未完成记录通过今日发货管理处理。确认只记录已发货及统计，不触发物流或扣库存。
- 本轮代码与前批修改仍未提交；等待用户明确指令后交项目经理统一复核、提交及正式部署。不宣称合并、正式发布或项目经理验收完成。
