# ERP-041 发货弹窗与最晚日期

- 沿用用户授权的累计本地修复分支 fix/ERP-036-material-integer-controls；基线 42b580228ffd4c3cafa5097a5e7cd0a52125cf14。改动未提交、未推送，等待统一交接。
- 今日发货和待发货新增/编辑使用辅料管理同款 operation-dialog；列表不再常驻表单。关闭、取消、遮罩及 Esc 可退出；保存失败保留内容并在弹窗内显示错误。
- 待发货最晚发货日期使用 date 控件，提交兼容 API 的当日 23:59；编辑从原始时间按上海时区还原日期。
- 后端待发货新增及编辑允许最晚日期大于或等于计划日期，拒绝早于计划日期；今日发货保持当天规则。
- 待发货列表“转到今日发货”直接调用既有转入接口，保留数量、箱数、备注和来源记录；操作期间禁用按钮，已转入记录不再显示按钮。既有后端事务、状态与幂等校验继续生效。
- 文件：DashboardMaintenanceView.vue、DashboardMaintenanceFormal.test.ts、DashboardService.java、DashboardControllerTest.java。
- 验证：pnpm quality PASS（168 测试、Lint、类型检查、构建）；./mvnw clean verify PASS（137 测试）；追加日期下界断言后 ./mvnw -Dtest=DashboardControllerTest test PASS（5 测试）；git diff --check PASS。
- 更新本地前端构建及既有 Vite 源码，重启 jiugeng-mvp:0.0 后端应用。未重启 MySQL。
- 本轮没有新增业务验证记录。浏览器操作遇到用户正在切换页面，未继续干扰；弹窗及转入由组件测试、后端集成测试验证。
- 未发现本批 ERP-036～ERP-041 之外的文件变更。
