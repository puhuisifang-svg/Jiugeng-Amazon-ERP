# 图片预览 UI 统一：本地实施记录

- 项目：九更亚马逊 ERP
- Owner：Codex 实施；PJM 后续复核
- 版本 / 状态：v0.1 / In Review；本地已修改，待迭代统一验证
- 更新时间：2026-11-20，Asia/Shanghai
- 任务：用户直接要求修正产品图片弹窗关闭按钮、参考样品管理并检查其他功能和统一图片大小；没有新增 ERP 编号。
- 分支：`codex/project-handover-docs`
- 基线：`b0a3d559fd7fd21ed5c0a70bfffed8eacfd4cc20`
- 提交：未提交、未推送、未合并、未发布。原有文档、产品列表及账号展示修改为本会话同一实施者所有，完整保留。

## 原因与处理

代码排查发现产品、辅料、供应商、首页及发货图片等仍使用 `.image-dialog button` 的绝对定位关闭按钮；部分弹窗图片又使用原始宽高，只设置最大尺寸，导致弹窗和按钮位置随图片变化。样品列表通过 `PurchaseItemImage` 使用独立标题栏的弹窗，但图片仍按自然尺寸显示。

新增 `frontend/src/components/ImagePreviewDialog.vue` 统一展示结构：桌面最大 840×680px，窄屏为视口减 24px；标题栏和底栏固定，图片区占剩余空间并使用 contain，关闭按钮不覆盖图片。小图可等比放大，横竖图完整显示，原图自身留白不裁除。原生模态框配合 Teleport，支持嵌套、Escape、背景关闭与关闭后焦点恢复；沿用全局键盘焦点管理。

## 接入范围

| 文件（相对 frontend/src） | 使用场景与保留行为 |
| --- | --- |
| `views/ProductsView.vue` | 父子体图片；保留保存图片、下载中禁用及失败提示 |
| `views/MaterialsView.vue`、`SuppliersView.vue` | 辅料、供应商图片/二维码；保留鉴权图片和外链路径 |
| `views/DashboardView.vue` | 首页产品图案，保留 dashboard 原图接口 |
| `views/ProductDetailView.vue`、`ProductCostsView.vue` | 产品详情、成本图；详情预览明确使用原图 |
| `components/PurchaseItemImage.vue` | 样品产品/存放照片、采购、SKU 选择及其他共用场景 |
| `components/CustomerProductImage.vue`、`ShipmentProductImage.vue` | 客户商品与发货，保留原商品定位和鉴权 |
| `components/ProductImagePreview.vue` | 产品监控/详情与店铺商品图，保留失败提示和缩略图样式 |
| `views/CandidateWorkbenchView.vue`、`DevelopmentWorkbenchView.vue` | 候选与开发商品图 |
| `components/FinanceVoucher.vue` | 图片凭证；附件请求与 object URL 清理保持原逻辑 |

同步更新 UI 规范、状态报告与交付物登记。未修改后端接口、权限、图片缓存、缩略图尺寸、业务数据或服务配置。PDF、打印标签、导出长列表图片和内嵌上传预览采用各自展示规则；剩余 `.image-dialog` 调用为资源历史文字记录，不属于图片预览。

## 验证记录与待办

- 本轮遵守用户约定：不执行逐功能完整测试或浏览器回归。新增/调整测试仅准备好，尚未执行；先前 708 项测试及 4 项浏览器通过记录不覆盖本轮。
- 已对接入代码作静态审阅，执行 `git diff --check`；对本次修改的非密集模板 Vue 文件使用 `pnpm exec eslint --fix --fix-type layout` 做局部格式整理，命令退出码 0；`git diff --check` 通过。这不等同于前端完整门禁。
- 新增 `ImagePreviewDialog.test.ts`，覆盖打开/关闭、Escape/背景、点击图片或保存不误关、焦点恢复；更新受 Teleport 和销毁时机影响的页面/组件测试。
- 扩充 `frontend/e2e/products.smoke.ts`：关闭按钮在弹窗内、图片在标题栏下方、保存可达、contain、不同原图比例弹窗尺寸不变、Escape，三种视口输出预览截图。
- 交接时统一执行 `frontend/` 的 `pnpm quality` 与 `pnpm exec playwright test`；补充各接入模块和嵌套弹窗视觉验收，包括 1280×800、800×600、360×800，方/横/竖/小图、缺图/失败、长标题、下载反馈、关闭和焦点。当前没有视觉验收结论。
- 本轮未启动临时或持久服务，无需重启后端；未暂存任何文件。交接前重新检查完整累计 diff 和工作区归属。

收到用户明确交接指令后统一验证并交接 PJM；提交 main 和部署仍须用户授权，不能以本记录代替验收或发布确认。
