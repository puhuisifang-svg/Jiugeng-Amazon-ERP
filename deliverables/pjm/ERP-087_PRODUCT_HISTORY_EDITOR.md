# ERP-087 父体变更记录与统一编辑弹窗

## 基线与计划

- 用户要求恢复父体变更记录并语义化操作列，修复详情编辑布局、复用列表编辑。
- 起始 ERP-086 分支干净；fetch 后 origin/main 为 `36f0ef95b918c9ca0921f6a6d99aa83cb9cf4953`。从该基线新建 `fix/ERP-087-product-history-editor`，携入 ERP-078 至 ERP-086 累计本地提交。
- 计划：恢复基础信息/变更记录两页签；按现有后台审计类型映射中文；抽出完整编辑弹窗供列表及详情共用；测试与真实组件浏览器验收。
- 授权仅本地实现、提交，不推送、合并、线上发布或服务重启。

## 原因与修复

原详情仅直接渲染 ProductForm，没有列表编辑弹窗的 product-create-dialog / product-create-form 布局约束，缺少限高、内部滚动及底部操作区。

- 新增 ProductEditDialog，复用列表完整布局及 ProductForm。ProductsView 和 ProductDetailView 共用同一组件；标题区分编辑产品/编辑SKU/添加SKU，保留字段与子体定位、保存失败保留输入；保存中禁止关闭，阴影点击不清空。
- 父体恢复“基础信息 / 变更记录”，仍不恢复返回/刷新工具栏和其他父体页签。历史按需加载，错误可重试，空记录提示，序列校验避免旧请求覆盖。
- productHistory 工具将实际后台 PRODUCT_CREATE / PRODUCT_UPDATE / PRODUCT_DISABLE、导入及文件上传等操作映射成中文；未知类型显示其他操作，title 保留原始代码。子体日志同步复用映射，不修改历史内容或数据库。
- UI_STANDARD 更新最新父体范围与编辑复用要求。

## 文件与验证

- 新增 `frontend/src/components/ProductEditDialog.vue` 及测试。
- 新增 `frontend/src/utils/productHistory.ts` 及测试。
- 修改 ProductsView、ProductDetailView 及其详情测试、SkuDetailDialog、UI_STANDARD、本记录。
- `pnpm exec vitest run src/views/ProductDetailView.test.ts src/views/ProductsView.test.ts src/components/ProductEditDialog.test.ts src/utils/productHistory.test.ts`：40 项通过。
- 最终 `ERP_BUILD_OUT_DIR="$(mktemp -d /tmp/erp087-verified-build-XXXXXX)" pnpm quality`：lint、67 文件/292 测试、类型检查、构建均通过；日志 `/tmp/erp087-verified-quality.log`。中途修复新增测试的 DOMWrapper 类型断言及模板格式警告。
- 真实 ProductForm + 合成数据浏览器验收：1280×800 左父体右 SKU、内部滚动、底部按钮；800×600 保存按钮 bottom543 < 600；360×640 bottom603 < 640，文档宽360无横向溢出。关闭后仍在详情。
- 列表“编辑父体”同样打开 ProductEditDialog，1280×800 保存按钮 bottom743 < 800。详情历史显示“修改产品资料”，内容不改写。
- `git diff --check` 通过，临时页面及浏览器标签已清理、视口恢复。

## 交接边界

无后端改动，无业务数据写入，无真实保存/文件上传/线上验证；后端门禁不适用。工作区仅本任务修改。中文操作只解释接口已知类型，不推断未知操作或历史字段差异。待项目经理独立验收，不宣称已部署。
