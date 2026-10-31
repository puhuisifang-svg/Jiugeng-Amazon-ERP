# 全系统日期时间控件统一

用户直接授权实现/本地部署，无新增ERP任务号。分支fix/ERP-099-product-detail-ui，基准33122a4c08831689db5454320143fb6fc6235089，变更未提交，无main提交。

实际查看Safari已打开的赛狐补货建议开始/结束日期弹层：合并范围框、日历图标、双月面板、8个快捷范围。本系统新增公共DatePicker.vue和DatePicker.test.ts；15个页面/组件接入：AuditView、ShipmentStatisticsView、FinanceBillsView、DashboardMaintenanceView、ProductionDevelopmentView、CustomerOrdersView、WatchStoreDetail、ProductDevelopmentView、ProductMonitoringView、PurchasesView、PurchaseOrderDetailView、OrderInvoicesView、CustomerOrderEditor、SelectionResearchPanel、StoreTimingForm。StoreTimingForm.test.ts改用保存按钮文案定位，避免新增日历按钮导致原序号断言失效。UI_STANDARD升级1.7。

筛选日期对合并范围输入；单日、日期时间、纯时刻也复用控件。保留原模型格式及接口、required/min/max、权限/禁用及查询时机。日期时间保留秒，快捷范围覆盖当天00:00到23:59:59。纯时刻保持HH:mm。原生输入保留键盘编辑与表单校验，鼠标展开统一自绘日历。桌面双月、手机单月翻月，时间范围手机两行，popover顶层避免裁切。取消/Escape不提交草稿，清空同时清除两端，倒序点选自动排序。

验证：ERP_BUILD_OUT_DIR=/tmp/date-picker-build pnpm --dir frontend quality，通过lint、类型检查、96文件389测试、构建。git diff --check通过。真实浏览器1280×800、800×600、360×800，审计日志快捷查询/清空、双月与手机日历、账单范围框、采购编辑弹窗日历检查通过。没有提交真实业务单据。临时验收账号已禁用/软删除并清理会话，原用户已删除的旧验收账号没有恢复。

原本地5173前端已生效，无新增端口、无后端重启。本次仅上述日期控件范围文件/测试/规范/交接记录变化，保留所有前序授权未提交工作。历史审计查询时间仍按浏览器本地时区转换，未改变后端时间语义。
