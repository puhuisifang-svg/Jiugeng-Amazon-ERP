# S0-BE-01/02 自测报告

| 字段 | 内容 |
| --- | --- |
| 日期 | 2026-09-12 |
| 环境 | macOS arm64、OpenJDK 21.0.12.1、Maven Wrapper 3.9.16 |
| 工程 | `deliverables/backend/sprint-0/jiugeng-erp-backend` |
| 结果 | PASS（样本依赖的 7 天观察除外） |

## 验证证据

1. `./mvnw clean verify`：BUILD SUCCESS；7 tests，0 failure，0 error，0 skipped。
2. 模块门禁：Spring Modulith `ApplicationModules.verify()` 通过。
3. Flyway：H2 MySQL 模式成功执行 `V1__baseline.sql`，Schema 到 v1。
4. Collector 安全性：未批准/需绕过的数据源被拒绝；不完整快照不晋升；重跑新品事件不重复。
5. 启动烟测：可执行 Jar 启动成功；`GET /actuator/health` 返回 `{"status":"UP"}`；随后优雅停止。
6. JSON Schema：4 个证据 Schema 均通过 `jq empty` 语法检查。
7. 工作树检查：`git diff --check -- deliverables/backend/sprint-0` 通过。
8. 敏感信息扫描：本机未安装 gitleaks；使用 `rg` 对交付目录执行高置信凭据模式及疑似 secret/token/password 明文赋值扫描，命中文件数均为 0。该结果应由 CI 的专用 Secret Scanner 再验证。

## 未完成/阻塞

- 3 个样本店铺链接及数据源审批尚未到位，因此不能启动、也未伪造连续 7 天观察。
- MySQL 8 集成迁移依赖 S0-OPS-01 Compose Ready；当前已在 H2 MySQL 模式验证基线迁移。
