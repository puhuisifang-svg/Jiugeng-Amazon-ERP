# ERP-007 正式运行与 MySQL 持久化底座

## 目标

将后端运行模式从“默认内存数据库”收口为显式 profile：正式运行必须使用 MySQL 持久化，合成演示和自动化测试继续使用相互隔离的 H2，不允许混用。

## 范围

- 默认配置不再隐式启用 H2。
- 新增独立 `test` 和 `mock` 数据源配置。
- `mysql` profile 继续从 configtree 读取密码，并增加连接池及 Flyway 安全配置。
- 启动时强制且只能选择 `mysql`、`mock`、`test` 之一。
- 测试验证 Flyway baseline 已应用，并验证 Mock API 不会出现在非 mock profile。
- 提供只连接已健康 ERP MySQL 的本地正式后端运行和冒烟脚本。

## 不在范围

- IAM、产品、辅料、库存、选品或监控业务表和 CRUD。
- 启动、停止、重启或替换项目经理维护的持久服务。
- 数据库备份恢复、发布拓扑和生产 Secret Store 集成。
- 将 H2 测试结果解释为真实 MySQL 动态验收。

## 验收标准

- 无 profile 启动明确失败，不再静默连接内存数据库。
- `mock` 与 `mysql` 同时启用明确失败。
- `test`/`mock` profile 可独立运行 Flyway 和 H2；`mysql` profile 只从 configtree 读取密码。
- 自动化测试能查询 `schema_baseline` 并确认唯一 baseline 行。
- 正式 MySQL profile 健康时，Mock 专用 API 返回 404。
- 后端 `./mvnw clean verify`、脚本语法检查、`git diff --check` 全部通过。

## 验证命令

```bash
cd deliverables/backend/sprint-0/jiugeng-erp-backend
./mvnw clean verify
cd ../../../..
sh -n scripts/dev/run-local-mysql-backend.sh
sh -n scripts/dev/smoke-local-mysql-backend.sh
sh -n scripts/qa/verify-mysql-profile.sh
./scripts/qa/verify-mysql-profile.sh
git diff --check
```

动态脚本使用本地已存在的固定 digest MySQL 镜像、随机临时容器、随机临时命名卷和非冲突端口。它验证 Flyway、健康状态、数据库重启后的非敏感探针数据及 Mock API 隔离，退出时只删除自己创建的临时资源。

## 状态

- 分支：`feature/ERP-007-mysql-persistence-foundation`
- 基线：`8744fab144e2a78bd07a7efd9a63cb4ca87e9baa`
- 当前：In Review（实现完成，待 PJM/Backend 复核）

## 实施与验证结果

- 默认配置已移除 H2；`test`、`mock` 和 `mysql` 数据源配置独立。
- 启动保护要求且只允许一个数据源 profile；无 profile 启动按预期非零退出并输出明确修复提示。
- `mysql` profile 保持 configtree 密码读取，并启用连接池边界、Flyway 禁止 clean 和启动迁移校验。
- 后端 `./mvnw clean verify`：PASS，32 tests，0 failure / 0 error；Spring Modulith 和打包通过。
- 临时 MySQL 8 动态验收：PASS；Flyway baseline 唯一，数据库重启后探针数据保留，二次启动健康。
- 正式 MySQL profile 下 `/api/dashboard/summary`、`/api/products`、`/api/watch/events` 均为 404，未暴露 Mock Controller。
- 三个 shell 脚本语法检查、`git diff --check`：PASS。
- 临时容器和命名卷已清理；现有 `chewy-mysql`、`chewy-redis` 未被停止、重启或修改。

## 保留边界

- 当前迁移仍只有 Sprint 0 `schema_baseline`；领域业务表由后续各领域任务按已批准模型分别增加。
- 本任务不代表 IAM、正式业务 API、业务数据迁移、备份恢复或发布环境已经完成。
