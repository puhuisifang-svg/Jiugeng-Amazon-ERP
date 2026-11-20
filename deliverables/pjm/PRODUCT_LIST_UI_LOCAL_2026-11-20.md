# 产品管理列表图片前置与 UI 优化

- 项目：九更亚马逊 ERP
- Owner：Codex 实施，PJM 后续复核
- 版本 / 状态：v0.1 / In Review（本地待用户验收）
- 更新时间：2026-11-20，Asia/Shanghai
- 审批人：待用户 / PJM 复核
- 任务来源：用户直接要求“列表的图片字段移动到第一列，同时优化列表 UI”；未新增 PJM ERP 编号。
- 分支：`codex/project-handover-docs`；基线 `b0a3d559fd7fd21ed5c0a70bfffed8eacfd4cc20`；未提交、未推送、未合并、未发布。

## 本地范围与实现

本会话已有文档维护的未提交改动，归属已核实为同一实施者；按用户连续本地修改、等待发布指令的约定保留这些内容，在原分支追加本次产品列表修改，未切换分支或混入他人改动。后续由 PJM 按文档/界面范围整理提交；不将本次处理视为并发写入的授权。

- `frontend/src/views/ProductsView.vue`：选择框之后，图片为首个业务列，品名第二；读取旧偏好、列设置、恢复默认均统一顺序，保留图片隐藏及其他列排序能力。父子行层次、缺图占位、图片边框、状态和选择反馈改为更清晰的局部样式；保留 80×80 图片、预览和缓存逻辑。手机取消固定操作列，允许横向滚动访问。
- `frontend/src/views/ProductsView.test.ts`：更新列顺序回归，覆盖旧偏好、父子行顺序、重新启用图片、保存/恢复、可选列排序和选择状态。
- `frontend/e2e/products.smoke.ts`：独立浏览器使用合成账号与商品、拦截所有 API，验证三个尺寸的图片位置、大小、预览、页面无整体溢出、分页及滚动后操作可达。不连接或修改业务数据。
- `docs/ui/UI_STANDARD.md`：记录用户的新列顺序要求与列表视觉约定。

## 首轮验证（追加修改前的历史结果）

- 仓库根目录误执行 `pnpm exec vitest run src/views/ProductsView.test.ts`，因目录不对未找到 vitest；切换到 frontend 后执行通过，24 项测试通过。
- 首次 `pnpm quality` 因新增模板的 3 个格式警告中止；以 `pnpm exec eslint src/views/ProductsView.vue --fix` 修复后重跑。
- `frontend/` 执行 `pnpm quality`：通过，152 文件 / 708 测试，lint、类型检查、构建通过。构建保留 PDF 模块重复导入及大 chunk 提示，与本次产品列表无关。
- 浏览器首轮因缺少 Chromium 未启动；Node 26 安装出现兼容问题，改用现有 Node 24 执行 `/Applications/ChatGPT.app/Contents/Resources/cua_node/bin/node node_modules/@playwright/test/cli.js install chromium --only-shell` 完成依赖安装，未改项目依赖。系统 Node 22 另有动态库缺失，未用于测试。
- `frontend/` 执行 `pnpm exec playwright test`：最终 4 项通过（登录冒烟 + 1280×800、800×600、360×800 产品列表）；测试用临时 4188 服务随测试退出，未操作持久服务。
- 人工查看三种尺寸截图，修正了被全局样式覆盖的子体缩进；修正后再次执行 `pnpm quality` 和 `pnpm exec playwright test`，结果同上。手机截图保持滚动容器最左侧；操作列可横向滚动访问。
- 截图由 `frontend/e2e/products.smoke.ts` 重现，输出至忽略目录 `frontend/test-results/` 下对应尺寸的 `products-*.png`，不是生产业务数据或线上验收截图。
- 仓库根 `git diff --check`：通过。最终 diff / status 核对：仅本次产品列表及本会话已有文档修改，无他人改动、无暂存。

## 发布与后续

仅本地实现，等待用户确认效果和明确发布指令，再交接 PJM 提交 main、部署及核实线上版本。本次没有更改后端或接口，也不宣称解决此前图片首次加载速度问题。


## 同迭代追加：账号展示与父子层级（2026-11-20）

- 用户追加指出右上角“系统管理员”重复、子体图片未缩进及父子不易区分。
- `frontend/src/layouts/AdminLayout.vue`：账号显示名与角色名去重，保留不同角色及原退出操作；未更改身份或授权。
- `frontend/src/layouts/AdminLayout.test.ts`：补充同名、不同名、多角色去重和旧角色回退用例。
- `frontend/src/views/ProductsView.vue`：子体图片/缺图占位相对父体右移 24px，图片列扩大至 128px 以保留 80px 图片；树形连接线末行收尾。父体浅蓝灰底与分组边界、子体白底，名称下补充“子体”，选中颜色优先。
- `frontend/e2e/products.smoke.ts`：补充实际图片/缺图相对缩进、80px 尺寸、子体标识及父子背景差异检查。
- 状态：本地已修改，待迭代统一验证。本次仅审阅改动和执行 `git diff --check`，未运行应用测试或浏览器；上方历史通过结果不覆盖这些新增改动。
- 交接前统一执行前端 quality、浏览器冒烟和三尺寸视觉验收，覆盖图片列隐藏/缺图、父子层次、选中优先及顶部账号换行；不涉及后端。工作区仍仅包含本会话用户授权的累计修改，无暂存、无他人改动。
