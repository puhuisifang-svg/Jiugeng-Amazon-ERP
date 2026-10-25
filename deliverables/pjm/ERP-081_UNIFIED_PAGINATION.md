# ERP-081 统一列表分页

## 范围与基线

- 用户要求产品及其他页面分页参考赛狐并形成规范；延续本地验证、本地提交，不推送或上线。
- 分支：`feature/ERP-081-unified-pagination`。干净工作区开始，fetch 后基于 origin/main `36f0ef95b918c9ca0921f6a6d99aa83cb9cf4953` 创建；携入 ERP-078/079/080（本分支提交 `7dedae6`、`c745265`、`40e47e0`）。
- 参考此前只读查看的赛狐商品列表底部分页：左侧统计、右侧页码、每页条数及跳页。

## 计划与实施

1. 清点分页入口和服务端/客户端页码约定。
2. 扩展 ListPagination 公共组件，实现数字页码、省略号、跳页、条数、加载禁用和边界保护。
3. 迁移现有主列表，接入真实 size 参数，保留各页面统计口径与查询条件。
4. 更新组件和页面回归，完成响应式验证并升级 UI 规范 v1.1。

## 覆盖与变更文件

- 公共组件 `frontend/src/components/ListPagination.vue` 及其测试。
- 主列表视图：ProductsView、PurchasesView、ProductDevelopmentView、ProductMonitoringView、CustomerManagementView（客户信息/客户商品）、InvoiceManagementView、CustomsManagementView、ShipmentStatisticsView、DashboardMaintenanceView（今日发货/待发货等）、AuditView。
- 原已使用公共组件的 MaterialsView、InventoryLedgerView 自动采用新布局，相关测试改为可访问名称定位。
- 监控历史同样使用公共组件，维持接口固定 20 条的容量。
- `frontend/src/api/customs.ts`、`invoices.ts` 支持传入 size，默认 20 保持旧调用兼容。已只读确认客户/发票接口 size 上限 100，不修改后端。
- 对应分页页面测试使用 aria-label 定位按钮；报关回归增加改为 50 条并回第一页的参数验证。
- `docs/ui/UI_STANDARD.md` 升级分页章节。AGENTS.md 已要求 UI 开发遵循该规范。

## 验证

- `pnpm exec eslint ... --fix`：仅格式化任务涉及组件、视图/测试及接口文件；检查未包含无关变更。
- `ERP_BUILD_OUT_DIR="$(mktemp -d /tmp/erp081-build-XXXXXX)" pnpm quality`：最终通过，63 个测试文件、257 项测试；lint、类型检查、构建通过。
- 初轮发现两处旧 mock 预期未包含新增 size，修正并完整重跑通过。
- 浏览器真实 ListPagination 合成数据验证：1280×800 左右分区、数字页码点击、跳至第 25 页、省略号；800×600 控件均在视口内且无整体横向溢出；360×640 自动换行、改为 50 条后回第一页。
- 未调用业务写接口；临时验证页已删除，视口覆盖已恢复。
- `git diff --check` 提交前通过。

## 交接边界

- 尚未推送、合并、部署；后端未更改或重启，本地 Vite 自动读取更新。
- 无分页的概览页面不人为增加分页；弹窗内客户/SKU 选项检索的上下页控件保留，规范说明其例外。
- 浏览器验证为公共组件合成场景，业务页通过自动化回归；真实数据端到端发布验收仍由项目经理进行。
- 提交前无无关修改。
