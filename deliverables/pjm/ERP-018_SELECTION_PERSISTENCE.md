# ERP-018 选品候选持久化闭环

## 交付范围

- `mysql` / `test` profile 使用正式 `/api/v1/selection/candidates` API，`mock` profile 继续使用隔离的合成数据 API。
- V12 建立候选、标签、追加式评审记录及追加式证据表；迁移不写入任何 `SYNTHETIC` 演示数据。
- 支持 ASIN 或标准 Amazon URL 新增，统一规范为大写 ASIN 与 canonical URL，数据库唯一约束兜底并发查重。
- 支持列表、关键词/状态/负责人筛选、详情、评估资料、固定状态机、淘汰原因、负责人和标签。
- 评审记录与 HTTPS 证据只追加；写操作进入审计，证据 URL 与评审正文不进入审计快照。
- 管理员和选品角色可写；产品辅料角色只读。
- 候选池继续导出当前前端筛选结果，沿用 UTF-8 BOM、RFC 4180 转义及公式注入防护。

## 状态机

- `NEW -> REVIEWING | REJECTED`
- `REVIEWING -> APPROVED | REJECTED`
- `APPROVED`、`REJECTED` 为终态。
- 进入 `REJECTED` 必须填写淘汰原因，其他状态禁止携带淘汰原因。

## 安全与数据边界

- 正式库为空库起步，不把 mock 候选迁入 MySQL。
- ASIN 规范化值使用唯一索引，应用层预检只用于友好提示，数据库约束负责并发最终一致性。
- 证据仅登记有效、无 userinfo 的 HTTPS URL，不抓取外部内容，不产生 SSRF。
- 数据仅逻辑状态流转，不提供候选、note、evidence 物理删除接口。

## 验收门禁

- 后端：`./mvnw clean verify`
- 前端：`pnpm quality`
- MySQL：`scripts/qa/verify-mysql-profile.sh`（V1→V12、重启持久化、mock 隔离）
- Git：`git diff --check`
