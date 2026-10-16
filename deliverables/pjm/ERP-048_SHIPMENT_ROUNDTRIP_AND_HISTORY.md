# ERP-048 发货往返转移与历史展示

- 分支：fix/ERP-036-material-integer-controls；基线 42b580228ffd4c3cafa5097a5e7cd0a52125cf14。沿用用户授权的累计本地修复；所有变更未提交、未推送，待最终指令统一同步项目经理。
- 待发货→今日发货→待发货复用来源记录，不再新增第二条待发货；重复往返也复用今日记录，保留状态转移审计。事务、版本检查和幂等机制继续生效。
- 待发货最晚发货日期仅显示日期；历史按钮改为浅灰色。
- 历史对比前后快照展示产品、发货数量、箱数、备注、计划日期、最晚日期及状态变化，例如“发货数量：3 → 5”。
- 今日/待发货删除使用统一系统弹窗，包含产品和日期、删除说明、取消及危险色确认按钮；转待发货弹窗增加“产品名：”标签。
- 变更文件：DashboardRepository.java、DashboardService.java、DashboardControllerTest.java、DashboardMaintenanceView.vue、DashboardMaintenanceFormal.test.ts、main.css，以及本记录。
- 验证：pnpm quality PASS（47 文件、179 测试，Lint、类型检查、生产构建）；./mvnw clean verify PASS（138 测试）；git diff --check PASS。新增连续三次往返不增加记录数的回归断言，以及历史字段变化、删除弹窗确认/取消测试。
- 浏览器检查本地删除弹窗样式并取消，未删除业务记录。前端由本地 Vite 更新，后端使用现有 MySQL profile 重启应用，不重启数据库。
- 限制：本次防止后续转移产生重复；此前已经产生的重复数据不自动合并或删除，避免误处理业务数据。无数据库结构迁移。
- 工作区仅包含 ERP-036 至 ERP-048 的累计修改，无无关修改、无暂存文件。未作远程部署或宣称项目经理验收。
