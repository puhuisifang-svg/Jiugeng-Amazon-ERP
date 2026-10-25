# ERP-078：产品导入导航、图片与新增表单适配

日期：2026-10-25。分支：`fix/ERP-078-product-usability`。
基线：`36f0ef95b918c9ca0921f6a6d99aa83cb9cf4953`。开始时 main 工作区干净，fetch 后从 origin/main 创建任务分支。用户本轮要求修复，不沿用上轮发布授权；本任务未提交、未推送、未部署线上。

## 问题与实现

1. 产品导入使用 `/imports`，其导航元数据固定归属辅料。新增 `/products/imports` 及批次详情路由，保持产品管理导航与面包屑；通过组件 props 保留批次打开、返回和重新校验的来源路径，原辅料路由不变，导入权限不变。
2. 产品列表缩略图原为 46px 且 object-fit:cover，矩形图片被裁剪。父体/SKU 图片及占位、点击按钮统一 80px；以 contain 显示完整图片，保留点击大图能力。产品表单图片同样不裁剪。
3. 新增/编辑产品表单底部负边距超出 overflow:hidden 的容器，导致按钮被裁掉。移除负边距，底部操作区支持换行，按钮禁止收缩，内容区保持独立滚动。

## 修改文件

- `frontend/src/views/ProductsView.vue`：产品导入入口。
- `frontend/src/router/index.ts`：产品归属导入路由及权限。
- `frontend/src/views/ImportsView.vue`、`ImportDetailView.vue`：导入来源路径传递。
- `frontend/src/styles/main.css`：图片尺寸、比例与表单底部布局。
- 对应 ProductsView、ImportsView 和 router 测试：新增 3 项回归。

## 验证

- `ERP_BUILD_OUT_DIR="$(mktemp -d /tmp/erp078-build-XXXXXX)" pnpm quality`：PASS，63 个测试文件 / 251 tests，lint、vue-tsc 和生产构建通过。输出 `/tmp/erp078-build-JiNQIf`，未覆盖线上版本化静态目录。
- 实际浏览器合成布局页挂载 ProductForm、使用实际项目样式，在 800×600、360×640 下检查：新增 SKU、取消、创建产品均完整位于弹窗和视口范围内。
- 实际浏览器以 300×100 横图和 100×300 竖图验证：均为 80×80 容器、contain，两端标记完整可见。
- 临时布局测试页面已删除，临时浏览器页已关闭，窗口尺寸覆盖已恢复。无业务写入、无账号变更、无服务重启。
- 本地登录态未提供，因此没有伪称完成真实业务账号下的端到端导入操作；跳转/导航通过组件与路由回归验证。
- 仅前端变化，后端和导入契约未改动，无需对应门禁。`git diff --check` PASS，工作区仅本任务改动。

本地 Vite 预览（5173）已加载源码变化，用户可登录检查；线上仍为上一发布版本。后续提交发布需项目经理复核本任务差异及完整 SHA。
