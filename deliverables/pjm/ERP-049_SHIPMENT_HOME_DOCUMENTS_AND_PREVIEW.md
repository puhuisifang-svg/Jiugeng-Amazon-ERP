# ERP-049 发货确认、产品图片与首页附件

## 工作范围与计划

- 沿用用户授权累计本地修改的分支 `fix/ERP-036-material-integer-controls`，基线 `42b580228ffd4c3cafa5097a5e7cd0a52125cf14`。开始前确认工作区为 ERP-036 至 ERP-048 既有修改，无外部修改；不切换分支、不提交、不推送。
- 实施顺序：统一转入确认及图片预览 → 新增按发货记录归属的标签接口与上传入口 → 首页带固定表头的字段展示及说明书下载 → 回归与本地更新。

## 实现

- 待发货转到今日发货采用系统 operation-dialog，展示产品名、数量、箱数、操作说明；取消不提交，确认中禁用操作，失败保留弹窗。
- 管理列表“图案”改为“产品图片”；共享 ShipmentProductImage 支持点击查看原图、关闭/遮罩/Escape，复用辅料图片预览样式。
- 今日发货列表增加商品标签和外箱标签上传/下载入口。文件限 10 MB，支持 PDF、PNG、JPG、TXT、ZPL；检查名称、扩展名和内容签名，授权下载为 attachment/no-store/nosniff。
- V27 增加 dashboard_shipment_document 表。文件绑定今日发货及产品；待发货通过来源关联读取同一批标签，往返转移无需复制。更换产品不会展示旧产品标签；只允许有效今日发货记录上传，并记录审计。
- 首页今日/待发货改为静态可滚动表格，固定表头；展示日期、产品图片、产品名称及 SKU、数量、箱数、备注、商品标签、外箱标签、说明书下载。调整紧凑间距、隔行背景、数字层次和下载链接样式。
- 说明书复用产品管理 SKU 文件接口，不重复存储；多 SKU 显示各自 SKU 和文件名，不擅自选择某个 SKU。当前发货仍为产品级记录。
- 未上传附件明确显示“未上传”；加载/上传/下载失败显示可理解的错误；成功上传使用 toast。

## 文件与验证

- 后端：ShipmentDocumentController.java、DashboardExceptionHandler.java、V27__shipment_documents.sql、DashboardControllerTest.java。
- 前端：dashboard.ts、ShipmentProductImage.vue、ShipmentFiles.vue、ShipmentManuals.vue、DashboardShipmentRow.vue、DashboardMaintenanceView.vue、DashboardView.vue、main.css；ShipmentFiles.test.ts、DashboardMaintenanceFormal.test.ts、DashboardFormalView.test.ts。
- `pnpm quality` PASS：48 文件、183 测试，Lint、类型检查、生产构建；视觉调整后再运行 `pnpm build`。
- `./mvnw clean verify` PASS：139 测试，含标签读写权限、格式/空文件/文件名拒绝、下载归属、转回待发货附件可用、非有效记录拒绝上传、审计。
- `git diff --check` PASS。浏览器检查为只读预览/取消，不上传或删除现有业务数据。

## 交接约束

- 更新本地 Vite 和 MySQL profile 应用；V27 为新增表，不改写既有业务数据。无远程部署。
- 所有修改未提交、未暂存；工作区仅含 ERP-036 至 ERP-049 累计变更。待用户明确要求提交部署后统一向项目经理同步，尚未宣称合并或验收。
- 标签上传入口位于已保存的今日发货列表；待发货未转入过今日发货时无标签。现有旧重复发货数据仍不自动清理。
