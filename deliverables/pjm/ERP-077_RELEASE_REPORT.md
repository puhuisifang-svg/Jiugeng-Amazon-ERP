# ERP-077：ERP-075 与 ERP-076 发布记录

日期：2026-10-23

基线：`482b8e0d26826a8bd39cb2f12243fcf59641db08`

ERP-076 原子提交：`d4c531de1851c7f2215250bebc177d0e65e7fc94`

ERP-075 原提交：`0e6b637af22c253634f00f67ebad19e8dc35fa97`

组合功能提交：`1355d824348e4deb2118f514b77a35fa508d4a55`

最终发布提交：本记录所在提交。

## 复核结论

ERP-075 页面版本检测、构建目录隔离和 Caddy 缓存策略，以及 ERP-076 私有 OSS 存储、历史资源迁移和本地双写回退方案，通过独立复核与动态验收。ERP-075 是版本一致性防护，不作为此前截图差异的已证实根因。

## 质量门禁

- 后端 `./mvnw clean verify`：188 项测试，187 项执行通过，真实 OSS opt-in 探针在普通门禁中按设计跳过 1 项，`BUILD SUCCESS`。
- Java V4 真实 OSS 探针：1 项执行通过，覆盖上传、读取、匿名 403 和删除清理。
- 前端 `pnpm quality`：63 个测试文件、248 项测试通过；lint、类型检查和临时目录生产构建通过。
- Caddy 候选配置验证通过；构建写入 `/tmp/erp077-build-MhZMux`，未覆盖发布中的旧 `frontend/dist`。
- OSS 迁移工具：5 项测试通过。
- 范围契约、`git diff --check`、敏感信息和提交归属扫描通过。

## 备份与迁移

- 数据库与资源一致性备份：`.local/backups/erp077/dashboard-20261023T054755Z.tar.gz`
- 备份 SHA256：`74bdc4f58c5b92fef25251c5d029ddda329e25f962ced9f3c53200827fae7bf6`
- 备份包含真实 MySQL V39、工作台图片和商品图片；不包含凭据值。
- 原前端静态目录备份：`.local/backups/erp077/frontend-dist-before.tar.gz`
- 原前端备份 SHA256：`82306636cb71bfed3bdc64348c0e1cfaa7f947dc5d106354c60922af37259d0c`
- 持久运行配置备份：`.local/backups/erp077/runtime-config.tar.gz`
- 运行配置备份 SHA256：`99a60aaf1685a1d209473c3d1fcc672fc653f392dd534f41ea615b34c13df841`
- 历史资源 dry-run：22 个源对象、缺失导入文件 0。
- `--apply`：22 个对象逐个 SHA-256 校验通过；同内容对象未覆盖，源文件和数据库未删除或改写。

## 部署配置

- 后端：本机持久化 MySQL 8，Flyway V39，无新增数据库迁移。
- OSS：私有 Bucket `erp-jiugeng`，区域 `cn-hangzhou`，endpoint `https://oss-cn-hangzhou.aliyuncs.com`，前缀 `erp/`。
- `ERP_STORAGE_PROVIDER=oss` 已写入 Git 忽略、权限 600 的 `.local/runtime/erp-backend.env`；凭据只通过 `.local/secrets/oss_credentials` 路径引用。
- STS 只读核验确认当前身份类型为 RAM 用户；未输出账号、ARN、用户名或密钥。
- 后端由 `.local/runtime/run-backend.sh` 启动；进程环境已确认 `provider=oss`。
- 前端版本化目录：`.local/releases/frontend/1355d824348e4deb2118f514b77a35fa508d4a55`。
- Caddy 由 `.local/runtime/run-public-caddy.sh` 使用仓库 `infra/caddy/Caddyfile` 启动。
- 公网入口：`https://andreas-reel-proceedings-problems.trycloudflare.com`

## 动态验收

- 后端健康：HTTP 200 / `UP`；Flyway 校验 39 条迁移，schema V39。
- 新前端入口资源：`assets/index-Bz349aTi.js`；本地和公网一致。
- 登录、首页、客户、发票、报关页面：公网 HTTPS 200。
- HTML/API：`Cache-Control: no-store`；哈希资源：`public, max-age=31536000, immutable`。
- 一次性合成 ADMIN session：认证 200；首页今日发货 API 200。
- 应用上传合成商品图片：HTTP 200；原图和缩略图鉴权读取均为 200。
- 新上传的两个 OSS 对象分别与本地双写副本 SHA-256 一致；匿名访问均为 403。
- 既有外箱标 PDF 鉴权下载 HTTP 200，字节 SHA-256 与数据库历史内容一致。
- 云端两个探针对象、本地两副本、图片业务行、一次性账号和 session 已归零；append-only 审计保留。

## 剩余风险与限制

1. 当前是 OSS 主读、本地/数据库双写备份的过渡方案，尚未减少本地或数据库存储占用。
2. 只确认凭据属于 RAM 用户，尚未通过策略审计证明其权限严格限定为目标 Bucket 的应用前缀；不得宣称最小权限已完全验收。
3. 旧凭据是否已在阿里云侧撤销无法由新凭据或本机文件证明，仍需账号侧复核。
4. 数据库回滚可能留下云端不可达孤儿；本版本不做无依据批量删除。
5. Python 迁移环境为 3.9 / LibreSSL 2.8.3，执行时有 urllib3 兼容警告，但本次 dry-run、22/22 校验和真实 Java V4 探针均成功；后端正式运行使用 Java SDK。
6. 旧版本标签未移动，本次不新增发布标签，以最终 `main` SHA 作为部署版本。
