# ERP-032 辅料批量删除与分页

- 分支：`fix/ERP-032-material-batch-pagination`
- 基线：`51933d71ee6f28439db9c562ab97ae236ea1bd7a`（保留当前已提交的 ERP-030/031 工作）。
- 用户确认当前无其他修改任务；本次不要求产品经理复核。

## 变更

- 移除辅料页导入入口；新增逐行勾选、本页全选，选中后在原导入位置显示删除按钮，删除前弹窗确认。
- 批量删除需要 `materials.write`；整批校验并事务删除，存在库存、历史流水、产品或采购关联时拒绝整批删除，成功操作保留审计。
- 辅料列表与库存流水使用共享分页组件，可选每页 10/20/50/100 条；筛选重置页码，辅料切页清除选择。
- 库存不足在库存操作弹窗内提示可用量、需求量和处理建议，失败保留输入；服务端并发库存不足也提供明确提示。
- 流水操作列为空时保留同等高度，统一行高。

## 文件范围

- 后端 materialinventory：MaterialController、MaterialService、MaterialRepository、MaterialControllerTest。
- 前端：domain API、MaterialsView 及测试、InventoryLedgerView 及测试、共享 ListPagination 及测试、main.css。

## 验证

- 后端 `./mvnw clean verify`：126 tests passed。
- 前端 `pnpm quality`：lint、140 tests、typecheck、production build 全部通过。
- `git diff --check`：通过。
- 未进行登录后的浏览器视觉验收。

## 边界与发布

- 分页为当前 MVP 全量数据的前端分页，非服务端分页。
- 有历史关联的辅料只能停用，不能物理删除；本次未删除任何业务数据。
- 无数据库迁移；批量删除 API 需要部署新版后端。
- 本文件是实现交接记录，不代表已推送、合并或部署；发布状态以实际执行结果为准。
