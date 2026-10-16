# ERP-030 产品监控实现与部署交接

日期：2026-10-16。用户明确授权无需等待评审，完成开发与部署后通知。

## 版本与范围

- 分支：`feature/ERP-030-product-monitoring`。
- 已部署代码：`daafcd67609c516c76fa89253d6f7989ed9ae61e`。
- 功能提交：`9c58c193a54bee556354751a7ed1460443ccdc48`；最终样式、验收脚本修正：`daafcd67609c516c76fa89253d6f7989ed9ae61e`。
- 包含其他协作者合入的采购更新（合并提交 `7d91d4ccf7a8a23b0048f14defa85b7476cf103b`），未覆盖或撤销；合入后重新执行前后端完整门禁。
- 未由 Codex 合并到 main 或推送；项目经理独立验收、合并状态另行记录。

独立一级菜单，所有有效登录角色均可使用。支持美国站 ASIN 批量添加、查询筛选、分页、批量暂停/恢复、负责人和标签、个人关注与列偏好、详情、人工/合成观测以及商品字段变化历史。不新增人员操作追溯，也不改动已有身份安全审计。

## 主要文件

- `deliverables/pjm/ERP-030_PRODUCT_MONITORING_PRD.md`：冻结范围、数据与接口契约。
- 后端 `src/main/java/com/jiugeng/erp/monitoring/`：登录鉴权、幂等、并发版本检查、配额与基线规则。
- 后端 `src/main/resources/db/migration/V23__product_monitoring.sql`：独立持久化表。
- 后端 `src/test/java/com/jiugeng/erp/monitoring/MonitoringControllerTest.java`：8 个集成测试。
- `frontend/src/api/monitoring.ts`、`frontend/src/views/ProductMonitoringView.vue` 及测试：列表、详情、观测和偏好。
- 前端路由、AdminLayout 及相关测试：独立导航和全角色入口。
- MVP scope 契约、访问矩阵及 `scripts/quality/validate-mvp-scope.py`：范围同步。
- `scripts/qa/verify-monitoring-http.mjs`、`MonitoringPasswordHash.java`、`verify-mysql-profile.sh`：真实 MySQL/HTTP 验收与重启持久化测试。

## 门禁与动态验证

| 命令/验证 | 结果 |
| --- | --- |
| 后端目录 `./mvnw clean verify` | PASS，121 tests，0 failures/errors/skipped |
| frontend 目录 `pnpm quality` | PASS，40 files / 127 tests，类型、lint、构建通过 |
| `python3 scripts/quality/validate-mvp-scope.py` | PASS，5 imports / 27 capabilities / 3 roles / 17 API families |
| `./scripts/qa/verify-mysql-profile.sh` | PASS，三角色完整 HTTP 流程、MySQL 与服务重启、mock 隔离 |
| `./scripts/qa/verify-mysql-backup-restore.sh` | PASS，隔离环境数据库与两类图片恢复、计数/哈希、旧备份兼容 |
| 已部署 8080 环境 HTTP 验收及 `--verify-restart` | PASS，重启后目标、4 条观测记录及基线保持 |
| 浏览器验收 | 5173 列表/详情/观测弹窗；公网 HTTPS 登录并进入产品监控成功 |
| `git diff --check` | PASS |

临时日志位于 `/private/tmp/erp030-backend-final.log`、`erp030-frontend-final.log`、`erp030-mysql-gate.log`、`erp030-backup-drill.log`（非永久制品）。

## 部署与回滚

部署到既有本地 MySQL 环境与 `jiugeng-mvp` 服务，8080 健康检查 UP，V23 已成功执行。前端已构建，既有静态服务读取本仓库 `frontend/dist`，开发入口 5173 也指向本仓库。公网临时入口为既有 Cloudflare 隧道下 `/product-monitoring`；隧道重建后地址可能变化。

停写备份：`.local/backups/erp030/dashboard-20261016T085236Z.tar.gz`。

SHA256：`92622952c1d62283fff60f5dbdef38b4b273a32305c7a8bed8e24a1575ce6593`，备份 schema 版本 V22。隔离恢复门禁使用独立合成数据，并非将这份实际备份还原覆盖当前库。

回滚优先回退本功能应用代码、保留新增表及后续数据，不能直接覆盖部署后的数据库。若必须数据库恢复，先停止写入、另存当前库和图片、明确恢复目标与数据损失窗口后再执行既有恢复流程。采购更新需保留，不能整体回退到旧采购版本。

## 清理与限制

- 已清理本次生成的合成监控目标、观测和幂等记录；验收账号已禁用、会话与偏好已清理，保留必要身份安全记录。用户数据未作为清理对象。
- 已停止本任务自动跟进；交接前工作区无无关修改。本交接文件为最后文档提交，不改变已部署代码。
- P0 仅人工/合成观测，尚未接入真实亚马逊自动采集、定时任务、趋势图或竞品对比，不将合成数据表述为生产数据。
- 自动采集须在数据源合法授权、稳定性与成本门禁明确后另行实施。公网地址是现有临时隧道，不是高可用正式托管。
