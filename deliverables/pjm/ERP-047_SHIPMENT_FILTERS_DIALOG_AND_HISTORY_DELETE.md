# ERP-047 发货筛选、弹窗与历史删除

- 分支：fix/ERP-036-material-integer-controls；基线 42b580228ffd4c3cafa5097a5e7cd0a52125cf14。沿用用户授权的累计本地修复，未提交、未推送。
- 日期范围校验改用错误色 toast，连续提交可重复显示；无效范围不请求列表、不占用页面提示空间。
- 切换今日/待发货管理时重置关键词、日期、状态、页码、结果及弹窗。加载请求增加序号保护，旧页延迟响应不能覆盖新页。
- 转待发货原因弹窗采用统一 operation-form：规范输入框、圆角卡片、标题与关闭按钮、产品说明区、底部操作区；保留原因必填和失败反馈。
- 日期合并单元格改为垂直居中。
- 待发货支持删除待处理及已转入历史记录：软删除保留审计，默认列表排除已删除记录；通过归档状态仍可查看。删除来源历史不影响对应今日发货记录，版本冲突仍被拒绝。
- 文件：DashboardMaintenanceView.vue、SuccessToast.vue、main.css、DashboardRepository.java、DashboardMaintenanceFormal.test.ts、DashboardControllerTest.java。
- 验证：pnpm quality PASS（178 测试、Lint、类型检查、构建）；后续 CSS 调整再次 pnpm build；./mvnw clean verify PASS（138 测试）；git diff --check PASS。
- 浏览器确认统一弹窗样式、日期居中，随后取消弹窗；未操作业务数据。筛选重置、日期 toast、历史删除由前后端回归覆盖。
- 更新本地前端并重启本地 MySQL profile 后端，不重启数据库。无无关工作树修改，无暂存文件；待用户最终指令后提交及同步项目经理。
