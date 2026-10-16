# ERP-035 实施交接

日期：2026-10-16。任务分支 `fix/ERP-035-navigation-layout-labels`；基线 `0287b579280d48114f177650e42340815b1aefdb`。实现提交 SHA 随交接消息提供。

## 交付范围

- `frontend/src/layouts/AdminLayout.vue`：视口高度 Grid，全局菜单、最近页签栏和面包屑保持顶部；只有内容区滚动。窄屏头部换行自适应，切页复位横纵滚动。
- `frontend/src/router/index.ts`：采购管理归商品/产品管理，产品监控归选品/选品管理；店铺监控、上新监控的新名称同步文档标题、页签、面包屑；采购详情增加可返回采购管理的面包屑。
- `PurchasesView.vue`、`PurchaseOrderDetailView.vue`：列表标题和返回入口改为采购管理，采购单业务实体术语不变。
- `WatchStoresView.vue`、`WatchEventsView.vue`：模块加载失败提示同步名称。
- 布局与路由测试、ERP-035 实施计划及本交接记录。
- 未修改路由地址、API、权限、后端、迁移、业务数据；未重启后端。

## 门禁

- `cd frontend && pnpm quality`：PASS，ESLint / 43 个测试文件、159 项测试 / vue-tsc / Vite 生产构建全部通过。
- `python3 scripts/quality/validate-mvp-scope.py`：PASS，5 导入类型、27 capabilities、3 角色映射、18 API 资源族。
- `git diff --check`：PASS；提交范围仅 ERP-035，无无关变更。
- 后端未改，未运行后端全量门禁。

## 发布构建与浏览器证据

生产构建已写入原 `frontend/dist`，沿用既有 Caddy 与隧道。公网入口 `https://andreas-reel-proceedings-problems.trycloudflare.com` 实际加载新构建，最终发布/合并结论由 PJM 独立确认。

通过公网真实页面验证（只读业务操作）：

1. 商品菜单仅产品管理组，含采购管理；原顶级采购菜单移除。采购管理页面、页签、面包屑及文档标题一致。
2. 选品菜单仅选品管理组，含产品监控、候选池、店铺监控、上新监控。实际点击进入三项监控页面，页签与面包屑名称正确。
3. 桌面 1280 × 720 采购列表：内容 scrollTop 从 0 到 87.5，主菜单 / 页签 / 面包屑 top 始终为 0 / 56 / 94，document scrollTop 始终为 0。
4. 窄屏 390 × 720：内容 scrollTop 从 87.5 到 400.5，三栏 top 始终为 0 / 86.5 / 124.5；document scrollTop 为 0、scrollWidth 为 390；列表横向滚动限制在表格。截图确认顶部三栏正常，菜单浮层可点击。
5. 从滚动后的采购页切换店铺监控，内容 scrollTop 为 0。浏览器错误日志为空；响应式覆盖已复位，验收页已关闭。
6. 一次性合成 QA 账号经 PJM 单独授权，仅用于验收；已禁用，全部 session 与本地凭据已清理，安全审计保留；未改任何业务行。

## 风险与边界

- 仅调整界面布局与导航，旧业务 URL 保留，既有权限不放宽，产品监控仍不要求选品角色权限。
- 本地 `127.0.0.1:4173` 登录因既有来源限制返回 403；未改安全配置，改用当前授权公网入口完成验收。
- 原有模拟数据/人工观测提示保留，不将其宣称为真实采集或生产业务数据。
- 交回独占写入窗口供 PJM 精确 SHA 复核、独立门禁与 ff-only 合并；Codex 不操作 main。
