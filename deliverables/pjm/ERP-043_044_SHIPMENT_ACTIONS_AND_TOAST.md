# ERP-043 / ERP-044 发货操作与全局成功提示

- 分支：`fix/ERP-036-material-integer-controls`；基线 `42b580228ffd4c3cafa5097a5e7cd0a52125cf14`。用户授权继续累计本地修复，自定义任务编号；未提交、未推送，最终提交/部署与项目经理同步等待用户指令。

## ERP-043

- 按今日发货列表理解用户的“新增发货列表”：移除该列表历史按钮；归档入口改为删除，确认后沿用软删除，保留审计，不物理删除数据。默认仅显示有效记录；状态筛选仍可查看历史移出记录。
- 今日发货新增“转为待发货”，保留产品、数量、箱数、备注、日期和已有图片。转移后可在待发货编辑计划日期。
- 后端新增 `POST /api/v1/dashboard-maintenance/today-shipments/{id}/transfer-to-pending`，权限为 `dashboard.shipments.manage`；原记录移出与待发货创建在同一事务执行，乐观锁、幂等请求、双向审计保证失败不生成半条记录。历史图片引用一并转移。
- 待发货管理按钮改为“新增待发货”。
- 文件：DashboardMaintenanceView.vue、api/dashboard.ts、DashboardController.java、DashboardService.java、DashboardRepository.java 及前后端测试。

## ERP-044

- 新增 SuccessToast.vue / SuccessToast.test.ts，成功提示固定浮层，不参与布局，4 秒自动消失，可手动关闭，使用 status/polite 朗读。支持浏览器顶层 popover，避免被原生模态框覆盖；不支持时使用固定定位。
- 全局替换 21 个页面/组件的成功反馈，包括创建/保存/删除/转移、采购、导入、产品/辅料/供应商、SKU 文件、选品/监控、用户/设置、找回密码等。
- 错误、字段校验、加载状态、重试指引、合成数据告知和业务状态标签保留，不作为短暂成功提示隐藏。
- 对连续重复操作补充旧消息清空，保证同文案再次操作能重新显示。

## 验证与本地更新

- `pnpm quality` PASS：47 个测试文件，175 项测试；Lint、类型检查、生产构建通过。
- `./mvnw clean verify` PASS：138 项测试，无失败。新增转回待发货的权限、版本冲突无副作用、成功数据保留、幂等重放及重复转移拒绝测试。
- `git diff --check` PASS；MySQL 本地 smoke PASS。
- 本地 Vite 已加载新代码；重启 `jiugeng-mvp:0.0` 后端，沿用 MySQL 配置，没有重启数据库。Safari 已确认待发货页面显示“新增待发货”。没有通过浏览器修改用户业务记录。
- 未逐个页面人工验收全部 toast；统一组件及既有回归测试通过。
- 当前工作树仅本批 ERP-036～044 修改，无无关改动、无暂存文件。项目经理尚未验收，未合并或远端部署。
