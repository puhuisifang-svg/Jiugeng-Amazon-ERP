# 试点候选版部署、回滚与故障排查

## 适用范围

本手册适用于 ERP-010～ERP-020 的单实例试点候选版。当前范围是 MySQL 单库、单仓、五类受控导入和人工店铺快照；它不是高可用或生产容量承诺。

## 部署前门禁

1. 工作树必须干净，发布提交必须是已验收的 `main` SHA。
2. 通过 `scripts/qa/verify-pilot-candidate.sh`。该命令串行执行后端、前端、导入契约、活跃范围契约和临时 MySQL V1→V13/重启/mock 隔离。
3. 通过 `scripts/qa/verify-mysql-backup-restore.sh` 和 `scripts/qa/verify-performance-baseline.sh`。
4. 在受保护通道配置数据库、Bootstrap Admin、SMTP/SMS（如启用）等 Secret。不得写入仓库、命令参数或日志。
5. 确认 staging 位于受限持久目录，空间充足；默认保留 7 天，活跃批次文件不参与清理。

## 备份

推荐使用权限为 `0600` 的 MySQL defaults 文件，由 Secret Store 或宿主机受保护配置生成：

```sh
ERP_MYSQL_CLIENT_CONFIG_FILE=/protected/mysql-client.cnf \
ERP_MYSQL_DATABASE=jiugeng_erp \
ERP_MYSQL_BACKUP_DIR=/protected/backups \
scripts/ops/mysql-backup.sh
```

脚本使用一致性事务、压缩输出和 `0600` 文件权限。备份路径不应位于仓库。不要把密码放入 CLI 参数。

## 部署步骤

1. 记录当前应用 SHA、数据库 Flyway 版本和备份文件校验和。
2. 完成数据库备份，并在隔离临时库做恢复抽检。
3. 设置 `mysql` profile、数据库 URL/用户名、configtree Secret 目录和持久 staging 目录。
4. 启动后端，确认 Flyway 到 V13、`/actuator/health` 为 `UP`。
5. 启动前端 password 模式，验证登录、改密和路由加载。
6. 用 ADMIN、PRODUCT_MATERIAL、SELECTION 三角色执行 ERP-020 验收矩阵中的授权/拒绝路径。
7. 检查应用日志不包含 Secret、验证码、密码哈希或原始导入本地路径。

## 回滚

- **仅应用回滚**：数据库结构与旧版本兼容时，停新版本、部署已知良好 SHA，再执行健康和只读冒烟。
- **数据库已迁移**：Flyway 迁移是前向迁移，禁止手工删除 schema history 或执行未审查的 down SQL。恢复备份到新的空数据库，核对版本与关键表计数，再通过连接切换完成回滚。
- **恢复命令**：目标库必须为空且必须显式确认数据库名。

```sh
ERP_MYSQL_CLIENT_CONFIG_FILE=/protected/mysql-client.cnf \
ERP_MYSQL_DATABASE=jiugeng_erp_restore \
ERP_MYSQL_BACKUP_FILE=/protected/backups/backup.sql.gz \
ERP_MYSQL_RESTORE_CONFIRM=jiugeng_erp_restore \
scripts/ops/mysql-restore.sh
```

## 故障排查

- **Flyway 失败**：停止发布，保留日志和原库；检查版本顺序、权限、字符集。不要 repair，除非已完成人工根因审查。
- **健康状态 DOWN**：分别检查数据库连接、磁盘、邮件健康开关和端口占用；未配置 SMTP 时邮件通道必须禁用。
- **导入卡在活跃状态**：不要删除对应 staging 文件；先保全批次、issue、preview 和审计记录，再决定重校验或取消。
- **存储空间不足**：确认清理任务只处理超过保留期的终态文件。符号链接、未知文件名和活跃批次文件必须保留并告警。
- **库存不一致**：停止写入，用不可变流水重算余额；禁止直接修改流水或重复导入期初。
- **监控无数据**：当前只支持人工完整快照。WATCH-001 未批准前禁止启动网页抓取或绕过访问限制。

## 运维决策待办

- 由业务与运维批准 RPO/RTO、备份频率、保留周期、异地副本和恢复演练频率。
- 由业务完成真实数据 UAT 并签字；自动化和 synthetic 技术测试不等同于 UAT。
- 批准 WATCH-001 的公开店铺链接、7 天指标数据源及合规边界后，方可接入真实 CollectorAdapter。
