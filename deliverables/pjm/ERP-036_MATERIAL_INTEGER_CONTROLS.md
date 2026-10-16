# ERP-036 辅料管理整数数字控件

## 任务信息

- 用户要求：辅料管理菜单下所有数字控件使用整数，不允许输入或提交小数。
- 分支：`fix/ERP-036-material-integer-controls`
- 基线：`42b580228ffd4c3cafa5097a5e7cd0a52125cf14`
- 状态：本地修改、完整门禁、本地部署与冒烟验收完成；按用户要求暂不提交、推送或远端部署。

## 入口审计与实现范围

- 辅料管理顶部“库存操作”和每行“库存”按钮共用库存操作弹窗，数量调整为最小值 1、步长 1，并在调用 API 前拒绝非正整数。
- 辅料列表的安全库存编辑调整为最小值 0、步长 1，并在调用 API 前拒绝负数和小数。
- 历史库存页面虽已被路由重定向，仍同步收紧数量和安全库存控件，避免旧入口恢复后行为不一致。
- 正式库存 HTTP 接口对数量和安全库存增加零位小数约束；模拟库存接口同步拒绝小数。
- 采购收货、期初导入及 SKU—辅料关系不属于本次“辅料管理数字控件”范围，继续保留既有小数契约。

## 验证记录

- `pnpm vitest run src/views/MaterialsView.test.ts src/views/InventoryView.test.ts`：PASS，2 个测试文件、14 项测试。
- `./mvnw -Dtest=InventoryControllerTest,MockErpControllerTest test`：PASS，23 项测试。
- `cd frontend && pnpm quality`：PASS，ESLint、43 个测试文件共 161 项测试、TypeScript 检查和 Vite 生产构建均通过。
- `cd deliverables/backend/sprint-0/jiugeng-erp-backend && ./mvnw clean verify`：PASS，136 项测试和可执行 JAR 打包通过。
- `git diff --check`：PASS。
- 本地部署：保留既有 MySQL、前端、Caddy 与隧道，仅重启 `jiugeng-mvp:0.0` 后端；`http://127.0.0.1:8080/actuator/health` 返回 `UP`，`http://127.0.0.1:5173/materials` 可访问。
- `./scripts/dev/smoke-local-mysql-backend.sh`：PASS，持久化 MySQL 后端健康，mock-only 端点保持隔离。

## 提交与部署约束

- 收到用户明确指令前不创建提交、不推送、不进行远端部署。
- 最终交接需同步分支、完整提交 SHA、变更文件、门禁结果、本地部署结果、风险和遗留范围给项目经理。
