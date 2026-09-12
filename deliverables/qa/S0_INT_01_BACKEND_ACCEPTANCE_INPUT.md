# S0-INT-01：DEL-020/021 后端验收输入接收记录

| 字段 | 内容 |
| --- | --- |
| 项目 | 九更亚马逊 ERP |
| Reviewer | QA |
| 关联任务 | S0-INT-01 |
| 输入交付物 | DEL-020 v0.1、DEL-021 v0.1 |
| 状态 | DEL-021 Pass / DEL-020 Static Pass, 7-Day Blocked |
| 日期 | 2026-09-12 |

## 1. 分项结论

| 交付物 | 范围 | 结论 | 不外推范围 |
| --- | --- | --- | --- |
| DEL-021 | Java 21 / Spring Boot 3 模块化单体脚手架 | **PASS / 纳入 S0-INT-01 动态证据** | H2 MySQL 模式通过不等于 MySQL 8 Compose 联调通过 |
| DEL-020 | CollectorAdapter 合规 Spike 框架、Schema、Runbook、模板和框架内安全测试 | **STATIC PASS** | 3 店连续 7 天仍 **BLOCKED**，DEL-020 不得整体 Pass |

本轮 P0=0；未发现新增 P1/P2。

## 2. DEL-021 动态证据

| 验收项 | PJM 实测/仓库证据 | QA 接收结论 |
| --- | --- | --- |
| 干净构建 | `./mvnw clean verify`：BUILD SUCCESS | Pass |
| 自动化测试 | 7 tests / 0 failure / 0 error / 0 skipped | Pass；QA 汇总 4 个 Surefire XML 与报告一致 |
| 模块边界 | Spring Modulith `ApplicationModules.verify()` 通过 | Pass |
| 数据库迁移 | Flyway `V1__baseline.sql` 在 H2 MySQL 模式执行到 Schema v1 | Pass（脚手架基线）；MySQL 8 联调另验 |
| 可运行 Jar | Jar 启动成功，`GET /actuator/health` 返回 `{"status":"UP"}`，随后优雅停止 | Pass；PJM 实测记录纳入 |
| 构建产物 | `target/jiugeng-erp-backend-0.0.1-SNAPSHOT.jar` 存在且非空 | Pass |
| 自测报告 | `deliverables/backend/sprint-0/TEST_REPORT.md` 已跟踪并记录环境、命令和结果 | Pass |

## 3. DEL-020 静态与框架内证据

| 验收项 | 实际 | 结论 |
| --- | --- | --- |
| JSON Schema | 4/4 文件存在且 `jq empty` 通过 | Pass |
| Collector 策略测试 | 未批准/需绕过数据源拒绝；不完整快照不晋升；重跑新品事件不重复 | Pass（框架内自动化） |
| 合规边界 | Runbook 明确遇登录/验证码/访问拒绝停止，不规避控制 | Pass |
| 快照保护 | 仅 COMPLETE 快照建立/更新基线；失败/不完整保留证据、不更新基线 | Pass（静态/框架内） |
| 7 天观察 | 无 3 个批准店铺链接和数据源审批，未启动 | **Blocked** |

## 4. 敏感信息与完整性检查

- PJM 高置信敏感扫描：通过。
- QA 对 `deliverables/backend/sprint-0` 再执行高置信私钥、AWS/GitHub/Slack/JWT 模式扫描：0 命中。
- 4 个 Schema 语法、4 个 Surefire XML 汇总、Jar 存在性及 `git diff --check -- deliverables/backend/sprint-0`：通过。
- 未安装 gitleaks；CI 专用 Secret Scanner 结果仍应作为最终安全门禁证据。

## 5. 保留阻塞与下一步

1. S0-OPS-01 Compose Ready 后，在真实 MySQL 8 执行 Flyway、应用启动及健康检查；H2 结果不得替代该项。
2. 收到并批准 3 个公开店铺链接/数据源后，从首个完整有效快照起连续观察 7 个自然日。
3. 完成 7 天成功率、覆盖、完整性、合规、成本、误报和人工降级结论前，DEL-020 维持 Blocked，不给整体 Pass。

## 6. 证据路径

- Backend 自测：`deliverables/backend/sprint-0/TEST_REPORT.md`
- 后端工程：`deliverables/backend/sprint-0/jiugeng-erp-backend/`
- Collector 框架：`deliverables/backend/sprint-0/collector-spike/`
- Schema：`deliverables/backend/sprint-0/collector-spike/schemas/`
- 7 天 Runbook：`deliverables/backend/sprint-0/collector-spike/RUNBOOK.md`
