# ERP-054：ERP-036～053 汇总交接及 JG-ERP-V1.0 封版请求

日期：2026-10-16。接收人：九更项目经理 `jiugeng_pjm`。

## 用户最新授权（覆盖此前暂不提交的限制）

用户原话：“bug修改告一段落，把所有修改记录让项目经理复核，并让他提交main，并部署。然后告知项目经理当前所有功能可以封版为JG-ERP-V1.0”。

请项目经理接管共享工作区，复核全部变更与记录，运行最终门禁，按项目流程提交、合入并推送 main、部署并验收，完成后将当前功能集封版为 **JG-ERP-V1.0**，记录版本标识、准确提交 SHA、部署环境与验收结果并向用户反馈。请勿只给计划而不执行；如遇权限、验收或部署阻塞，请明确回报，不绕过质量门禁。封版是用户确认的范围意向，不代表独立验收、合并或部署已经完成。

## 共享目录及写入权交接

- 仓库：`/Users/thinkperfect/.openclaw/workspace-jiugeng_pjm/jiugeng-amazon-erp`
- 当前分支：`fix/ERP-036-material-integer-controls`
- 基础/当前 HEAD：`42b580228ffd4c3cafa5097a5e7cd0a52125cf14`
- ERP-036～053 全部累计修改尚未提交、未暂存，无本批之外的变更。新增源代码及记录目前为 untracked，复核时务必同时检查，不能只看 git diff。
- Codex 在发送本交接后停止修改、测试写入及服务操作，释放本批写入权给项目经理。请勿清理或覆盖该 dirty worktree，它正是待发布成果。

## 完整修改记录索引

以下均位于 `deliverables/pjm/`，请逐份复核，后续记录覆盖此前同一功能的旧行为：

| 任务 | 记录文件 | 主要内容 |
| --- | --- | --- |
| ERP-036 | ERP-036_MATERIAL_INTEGER_CONTROLS.md | 辅料数字整数校验及入口检查 |
| ERP-037～039 | ERP-037_039_LOCAL_BUGFIX_RECORD.md | 供应商链接/电话、产品图片预览、发货登录与日期修复 |
| ERP-040 | ERP-040_SHIPMENT_FORM_FIX.md | 产品图片自动带出、备注单行、保存修复 |
| ERP-041 | ERP-041_SHIPMENT_DIALOG_AND_DEADLINE.md | 新增编辑弹窗、最晚日期及转今日发货 |
| ERP-042 | ERP-042_STICKY_LIST_LAYOUT.md | 全局固定表头、紧凑固定搜索区 |
| ERP-043～044 | ERP-043_044_SHIPMENT_ACTIONS_AND_TOAST.md | 删除/转待发货、统一成功 toast |
| ERP-045 | ERP-045_SHIPMENT_LIST_FIELDS_AND_TRANSFER_CONFIRMATION.md | 日期合并、备注/日期列、搜索提示、转移确认及原因 |
| ERP-046 | ERP-046_TODAY_SHIPMENT_BUTTON_LABEL.md | 新增今日发货文案 |
| ERP-047 | ERP-047_SHIPMENT_FILTERS_DIALOG_AND_HISTORY_DELETE.md | 搜索重置、错误 toast、弹窗样式、历史删除 |
| ERP-048 | ERP-048_SHIPMENT_ROUNDTRIP_AND_HISTORY.md | 往返不重复、历史字段变化、日期与删除弹窗 |
| ERP-049 | ERP-049_SHIPMENT_HOME_DOCUMENTS_AND_PREVIEW.md | 图片预览、首页表格、标签上传下载及产品说明书 |
| ERP-050 | ERP-050_SHIPPING_DISPATCH_AND_STATISTICS.md | 发货菜单、确认发货、不可变台账、统计 |
| ERP-051 | ERP-051_SHIPMENT_SELECT_ALL_AND_IMAGE_EXPORT.md | 全选/半选、批量发货、图片导出、最晚日期字号 |
| ERP-052 | ERP-052_SHIPMENT_EXPORT_ALIGNMENT.md | 复选框对齐、仅选中导出/绿色按钮、文字垂直居中 |
| ERP-053 | ERP-053_SHIPMENT_EXPORT_TITLE.md | 导出标题同排日期、日期标红 |

具体文件清单及各轮测试见对应记录；总变更覆盖 frontend 下 API、视图、公共组件、样式与测试，以及后端 dashboard、库存整数校验、mockdata、IAM、ProductLookup 公共接口和测试。请核对 `git status --short`、tracked diff 与全部 untracked 文件。

## 已有门禁证据（请项目经理最终独立重跑）

- 最新前端：ERP-053 执行 `cd frontend && pnpm quality` PASS，50 个测试文件、192 项测试；类型检查、lint、生产构建均通过。日志 `/tmp/erp053-frontend.log`。
- 最新后端改动：ERP-050 执行 `cd deliverables/backend/sprint-0/jiugeng-erp-backend && ./mvnw clean verify` PASS，141 项测试；ERP-051～053 无后端改动。
- 各轮 `git diff --check` PASS；交接检查暂存区为空，无无关修改。
- 本地前端 Vite 5173 已更新，后端 MySQL profile 8080 已在 ERP-050 更新；持久服务由 `jiugeng-mvp` tmux 管理。既有本地部署不等于正式发布验收。
- 只做有限浏览器视觉验证，并非逐页人工验收；未代用户确认实际发货、清理历史业务数据或对外发送清单。

## 部署与封版注意事项

1. 必须包含 V27 SQL `dashboard_shipment_document` 迁移及 V28 Java `shipment_dispatch` 迁移。V28 兼容 H2/MySQL 约束语法；本地 MySQL 已应用。按既有运维流程做好数据库/附件保护，不清空业务记录。
2. 往返转移修复防止新增重复，但不会自动合并/删除旧重复数据。
3. 发货表示系统内确认实际发货，不调用承运商、不扣库存、不按 SKU 分摊。台账从显式确认开始，不从历史归档/删除推断回填。已发货无撤销入口，未来冲正需另立范围。
4. 首页全选范围为当前展示的最多 10 条；批量发货逐条执行非整批事务，失败保留未完成项。导出仅勾选项，无选中禁用；外链图片遵守 CORS，缺图明确提示。
5. 复核文件上传权限、内容校验、下载安全、转移幂等/版本冲突、发货终态与快照、全局列表滚动，以及最新导出标题/红色日期。
6. 当前仓库定义仍是内部 ERP 的本地 synthetic/mock MVP，不得因封版声称已对接真实生产数据或完成未实现的外部业务能力。

请完成后回报：复核结论、准确提交 SHA、main 推送状态、部署环境/健康与冒烟结果、JG-ERP-V1.0 版本标识及剩余限制。此前记录中的“等待用户指令”已由本次用户授权解除。
