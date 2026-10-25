# ERP-086 父体详情精简

## 基线与授权

- 用户要求删除父体详情面包屑下的返回/刷新按钮，只保留基础信息，子体详情由 SKU 概览进入。
- 起始 ERP-085 分支，工作区干净；fetch 后 origin/main 为 `36f0ef95b918c9ca0921f6a6d99aa83cb9cf4953`。新分支 `fix/ERP-086-parent-detail-scope` 从 origin/main 创建并携入 ERP-078 至 ERP-085 累计本地提交。
- 计划：精简父体展示及无用请求，复用现有 SKU 弹窗，验证选中子体定位、权限及响应式布局。仅本地提交，不推送、部署或重启服务。

## 变更

- `ProductDetailView.vue`：移除顶部工具栏及父体其他页签/内容，保留基础信息、父体编辑/停用/图片和 SKU 概览；概览按钮打开所点子体，关闭后仍在父体页。子体编辑传递选中 variantId。面包屑返回保留。
- `SkuDetailDialog.vue`：从 ProductsView 抽出原有子体详情弹窗，两个入口共享基础、采购、规格、辅料、图片、说明书、标签和日志功能。父体入口隐藏回到当前父体的重复链接。
- `ProductsView.vue`：复用共享弹窗，原列表详情和编辑行为不变。
- `ProductDetailView.test.ts`：更新精简后的断言，加入第二个子体定位、编辑定位、关闭返回概览、权限和旧分区 URL 兼容性。
- `docs/ui/UI_STANDARD.md`：明确父体只保留基础及概览，覆盖 ERP-085 过度扩展规则。
- 旧 variants/materials/documents/changes URL 仍能打开父体基础信息，无空白页，不请求已移除的父体历史或关联资料。不修改业务数据或 API 合约。

## 验证

- `pnpm exec vitest run src/views/ProductDetailView.test.ts src/views/ProductsView.test.ts`：31 项通过。
- `ERP_BUILD_OUT_DIR="$(mktemp -d /tmp/erp086-build-XXXXXX)" pnpm quality`：lint、65 文件/283 测试、类型检查、构建通过。日志 `/tmp/erp086-quality.log`。
- 浏览器使用真实组件及两条合成 SKU 验收：1280×800 父体无冗余工具栏/页签，点击第二条准确显示第二个子体；800×600 弹窗边界 32–768；360×640 弹窗边界12–348，文档无横向溢出，关闭后父体主内容宽345。
- `git diff --check` 通过；临时页面已删除、临时浏览器标签已关闭、视口覆盖已恢复。

## 交接

无无关修改，无后台改动，无真实业务保存及线上发布验证；现有文件及采购功能保持原子体弹窗行为。本地提交后待项目经理独立验收。
