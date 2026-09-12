# S0-BE-01/02 自测报告

| 字段 | 内容 |
| --- | --- |
| 日期 | 2026-09-12 |
| 环境 | macOS arm64、OpenJDK 21.0.12.1、Maven Wrapper 3.9.16 |
| 工程 | `deliverables/backend/sprint-0/jiugeng-erp-backend` |
| 结果 | PASS（样本依赖的 7 天观察除外） |

## 验证证据

1. `./mvnw clean verify`：BUILD SUCCESS；9 tests，0 failure，0 error，0 skipped。
2. 模块门禁：Spring Modulith `ApplicationModules.verify()` 通过。
3. Flyway：H2 MySQL 模式成功执行 `V1__baseline.sql`；随后在 S0-OPS-01 的 MySQL 8.0 实例（`127.0.0.1:13306/jiugeng_erp`）完成真实集成验证：成功校验 1 个迁移、应用 V1 baseline，Schema 到 v1。
4. Collector 安全性：未批准/需绕过的数据源被拒绝；不完整快照不晋升；重跑新品事件不重复。
5. 启动烟测：提交 `3f2b876` 后，可执行 Jar `jiugeng-erp-backend-0.0.1-SNAPSHOT.jar` 使用固化的 `mysql` profile 和 Ops configtree 无回显注入再次启动成功，Tomcat 监听 18081；Flyway 连接 MySQL 8.0，成功校验 1 个迁移并确认 Schema 当前为 v1、无需新增迁移；Backend 独立请求 `GET /actuator/health` 返回 HTTP 200、`{"status":"UP"}`。实测期间未读取、回显或传递数据库密码。
6. MySQL profile 安全配置：`application-mysql.yml` 仅导入 `ERP_MYSQL_SECRET_DIR` 指向的 configtree，并通过 `mysql_app_password` 属性引用密码；不再接受密码环境变量。测试以临时目录和非敏感测试值真实启动 `mysql` profile 并断言 DataSource 从 configtree 取值，未把值放入命令参数、环境值或日志。
7. JSON Schema：4 个证据 Schema 均通过 `jq empty` 语法检查。
8. 工作树检查：`git diff --check -- deliverables/backend/sprint-0` 通过。
9. 敏感信息扫描：本机未安装 gitleaks；使用 `rg` 对交付目录执行高置信凭据模式及疑似 secret/token/password 明文赋值扫描，命中文件数均为 0。该结果应由 CI 的专用 Secret Scanner 再验证。

## 未完成/阻塞

- 3 个样本店铺链接及数据源审批尚未到位，因此不能启动、也未伪造连续 7 天观察。
- 无 MySQL 8 集成阻塞：S0-OPS-01 Compose 环境中的 Flyway V1 与应用健康检查已实测通过。
- profile 固化 configtree 后已完成真实 MySQL 8 动态复验；Backend 进程随后通过 SIGINT 完成 Tomcat/Hikari 优雅关闭。ERP MySQL 未重建、未轮换，联调结束时保持 `running/healthy`，交由 Ops/PJM 决定后续生命周期。
