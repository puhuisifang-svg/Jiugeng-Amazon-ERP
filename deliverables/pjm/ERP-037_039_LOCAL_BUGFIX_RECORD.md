# ERP-037～ERP-039 本地修复记录

- 用户授权：连续修复 Bug 并更新本地环境，待用户明确指令后才提交及同步项目经理。
- 沿用同一写入窗口及分支：fix/ERP-036-material-integer-controls。
- 基线：42b580228ffd4c3cafa5097a5e7cd0a52125cf14。
- ERP-036 原有未提交修改由本代理保留；本批仍未提交、未推送。

## ERP-037 供应商信息

辅料新增/编辑和产品 SKU 采购信息选中供应商后，使用共享 SupplierContactLinks 组件展示采购链接及手机号。采购链接优先保留已有关系专属链接，否则采用供应商 sourceUrl；手机号来自供应商档案，可点击拨号。缺失信息明确展示未填写。关系备注仍可编辑。拒绝非 HTTP(S) 采购链接。

涉及文件：frontend/src/components/SupplierContactLinks.vue 及测试、ProductForm.vue、frontend/src/views/MaterialsView.vue。

## ERP-038 产品列表图片预览

ProductsView.vue 中有图的 SKU 缩略图可点击，使用辅料管理的 image-dialog 和 material-preview 样式打开大图。上传图片请求 original 版本；URL 图片使用原链接。关闭按钮、遮罩点击及 Escape 可关闭弹窗。无图片的占位符不可点击。

涉及文件：frontend/src/views/ProductsView.vue、ProductsView.test.ts。

## ERP-039 今日发货保存及日期简化

根因：dashboard.ts 的图片上传/读取路径直接读取 localStorage，遗漏普通登录保存在 sessionStorage 的凭证；上传无 Authorization 导致 401 及会话失效事件。改用共享 getSessionToken，覆盖两种登录方式。

今日发货表单移除发货日期及最晚发货时间选择框。新增保存时使用 Asia/Shanghai 当天，兼容既有 API 自动传当天 23:59；历史记录编辑保留原发货日期，避免把旧记录移动到今天。待发货日期字段保留。

涉及文件：frontend/src/api/dashboard.ts、dashboard.test.ts、frontend/src/views/DashboardMaintenanceView.vue、DashboardMaintenanceFormal.test.ts。

## 验证与本地状态

- frontend 下 pnpm quality：PASS，45 个测试文件、166 项测试；ESLint、vue-tsc、Vite build 均通过。
- 新增回归：两种登录存储均携带上传/图片读取凭证；上海时区跨日新增发货无需日期输入；供应商链接与缺失信息；产品图片弹窗打开及关闭。
- git diff --check：PASS。
- 本地既有 Vite 5173 读取当前源码，HTTP 检查确认 dashboard.ts 已返回 getSessionToken 修复；生产构建完成。
- 本轮未修改后端，不需要重启后端；ERP-036 后端门禁结果见该任务记录。
- 未进行真实登录浏览器端到端验收；未写入业务测试记录。
- git status 中只有本批 ERP-036～ERP-039 的变更，没有发现他人或无关改动。
- 当前状态是本地实现和自动验证完成，待用户试用；不表示项目经理已验收、合并或发布。
