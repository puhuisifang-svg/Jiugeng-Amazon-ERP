# ERP-052 首页发货选择与导出排版修复

日期：2026-10-16

## 范围与状态

- 分支：`fix/ERP-036-material-integer-controls`
- 基础 SHA：`42b580228ffd4c3cafa5097a5e7cd0a52125cf14`
- 属于用户持续授权的 ERP-036 起本地修复批次，全部保持未提交、未暂存；不推送、不远程部署。待用户明确指令后统一向项目经理同步。
- 本轮仅前端修改，通过现有本地 Vite 服务更新；未重启后端。

## 修改

- `frontend/src/views/DashboardView.vue`：导出仅取选中项；无选中项禁用，有选中项绿色显示。保留只读用户勾选导出的能力，实际发货仍受管理权限控制。
- `frontend/src/components/DashboardShipmentRow.vue`、`frontend/src/styles/main.css`：表头与行选择列统一宽度、内边距和复选框居中方式；选中导出按钮绿色样式。
- `frontend/src/ui/shipmentExport.ts`：导出表头、数量、箱数、产品名及备注文字垂直居中，多行文字整体居中，产品图片随行高居中。
- 测试：`ShipmentDispatch.test.ts`、`DashboardFormalView.test.ts`、`shipmentExport.test.ts`，覆盖禁用/绿色状态、仅选中导出、只读选择及多行文字居中坐标。
- 本记录取代 ERP-051 中“未选中时导出全部”的行为说明。

## 验证

- `cd frontend && pnpm exec eslint src --fix`：通过。
- `cd frontend && pnpm quality`：通过，50 个测试文件、192 项测试全部通过，类型检查、lint、构建通过。
- `git diff --check`：通过。
- 本地浏览器检查：表头与行复选框对齐，未选择时导出按钮禁用。
- `git status --short`：仅当前持续修复批次变更，无无关变更；没有暂存文件。

## 限制与交接

- 本轮未修改后端，未重复运行后端全量 gate。
- 图片不可用时仍沿用导出占位与提示；导出范围为首页当前勾选项。
- 尚未提交、合并或远程部署，项目经理验收待用户发出交接指令。
