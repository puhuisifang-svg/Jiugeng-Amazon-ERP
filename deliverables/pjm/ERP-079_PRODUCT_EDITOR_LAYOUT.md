# ERP-079 产品新增与编辑左右布局

## 范围与授权

- 用户要求参考赛狐多属性商品左右布局，保留已有功能。
- 用户授权本次及后续验证通过的修改可先提交本地；不包含推送、合并 main 或线上部署。
- 分支：`feature/ERP-079-product-editor-layout`。
- 基线：`origin/main` 的 `36f0ef95b918c9ca0921f6a6d99aa83cb9cf4953`。
- ERP-078 经用户授权先本地提交为 `d6eb893`，再以 `2ac2356` 带入本分支，保留导入导航、图片和底部按钮修复。

## 实施计划与结果

1. 只读查看赛狐参考页，将父体资料与 SKU 编辑区分栏。
2. 共用 ProductForm：左栏父体基础信息和图片，右栏 SKU 资料及原有页签；新增、编辑复用同一布局。
3. 弹窗最大宽度调整至 1440px；900px 以下切换上下排列；操作按钮保留在底部。
4. 保留全部字段、上传、校验、增删 SKU、取消和保存逻辑；新增布局与增删回归测试。

## 变更文件

- `frontend/src/components/ProductForm.vue`：布局容器、分栏样式、SKU 数量提示。
- `frontend/src/components/ProductForm.test.ts`：父体/SKU 分区及增删、取消回归。
- `frontend/src/styles/main.css`：共用新增/编辑弹窗宽度。
- 本任务记录。

## 验证

- `pnpm exec eslint src/components/ProductForm.vue src/components/ProductForm.test.ts --fix`：通过。
- `ERP_BUILD_OUT_DIR="$(mktemp -d /tmp/erp079-build-XXXXXX)" pnpm quality`：通过，63 个测试文件、252 项测试通过，类型检查、lint 和构建通过。
- 浏览器使用临时合成页面挂载真实 ProductForm，检查 1280×800 左右布局、父体底部字段滚动、800×600 上下布局与按钮边界、360×640 窄屏按钮完整显示；临时页面已移除。
- `git diff --check`：提交前通过。

## 交接边界

- 本次仅本地提交，不推送、不合并、不部署，不重启服务或修改业务数据。
- 本地 Vite 可读取源码更新。浏览器验证采用合成组件页面，未执行真实业务新增/编辑保存端到端操作。
- 后端未改变，不运行后端 gate。发布由项目经理独立复核。
- 提交前仅有上述任务文件变更，无无关修改。
