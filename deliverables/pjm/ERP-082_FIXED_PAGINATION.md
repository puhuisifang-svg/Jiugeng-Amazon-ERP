# ERP-082 分页栏固定在可视区域底部

## 问题与范围

用户指出 ERP-081 只统一分页样式，遗漏赛狐底部固定定位要求。本次修正全部使用 ListPagination 的主列表，不修改分页业务参数。

- 分支：`fix/ERP-082-fixed-pagination`。
- 干净工作区开始，fetch 后从 origin/main `36f0ef95b918c9ca0921f6a6d99aa83cb9cf4953` 创建，携入 ERP-078 至 ERP-081 累计开发。
- 用户允许验证后本地提交；本次不推送、不合并、不部署。

## 实施

1. AdminLayout 在可滚动内容区之后新增独立底部网格区域，真实预留分页高度，空区域不占空间。
2. ListPagination 将主列表分页挂载到同一壳层底部，弹窗及独立组件保持原位；透传 class 等属性，卸载时自动清理。
3. TableViewport 基于已扣除分页高度的内容区计算剩余空间，避免二次预留；保留表头固定和最小可滚动高度。
4. UI 规范升级 v1.2，明确短/长/空列表底部固定、窄屏高度占位和定位验收条件。

## 文件

- `frontend/src/layouts/AdminLayout.vue`
- `frontend/src/components/ListPagination.vue`、`ListPagination.test.ts`
- `frontend/src/components/TableViewport.vue`、`TableViewport.test.ts`
- `docs/ui/UI_STANDARD.md`
- 本任务记录。

## 验证

- `ERP_BUILD_OUT_DIR="$(mktemp -d /tmp/erp082-build-XXXXXX)" pnpm quality`：通过；63 个测试文件、260 项测试，lint/类型检查/构建全部通过。
- 新增测试覆盖主列表挂载到独立底栏、组件卸载清理、弹窗不外移、表格不重复预留分页高度。
- 浏览器以真实 AdminLayout、TableViewport、ListPagination 组合合成页面验证，不调用业务写接口。
- 1280×800 短列表实测：分页 top=751、bottom=800、高49；内容区 bottom=751。长列表滚动后底栏仍位于窗口底部。
- 800×600 验证长列表滚动及固定分页；360×640 分页换行后 top=526.5、bottom=640、高113.5，内容区 bottom=526.5，页面无整体横向溢出。
- 临时验证页面已删除，视口覆盖已恢复；`git diff --check` 提交前通过。

## 边界

无后端变更、服务重启或业务数据改动。本地 Vite 自动读取源码；未发布线上。浏览器验证是公共组件组合场景，真实业务数据发布验收仍由项目经理复核。工作区仅有本任务文件修改。
