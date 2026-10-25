# ERP-091：ERP-078～ERP-090 累计发布记录

日期：2026-10-25

发布基线：`36f0ef95b918c9ca0921f6a6d99aa83cb9cf4953`

完整功能提交：`99b675c1ce5c7a6e6997a10bcdb081ff8851de99`

交接提交：`5cdae802872e50b3a44a2c947fb7d40938d2a4dd`

最终发布提交：本记录所在提交。

## 复核结论

ERP-078～ERP-090 的 13 项累计修改通过独立复核和发布验收。产品导入与编辑布局、全局紧凑 UI/分页/导航、父体与 SKU 详情、默认启用、结构化 SKU 名称样式，以及首页、今日/待发货、统计和发货图片的名称格式传递均纳入本次发布。

名称样式只保存 UTF-16 文字范围、BLACK/RED 和字重白名单，不保存 HTML、CSS 或脚本；历史发货名称文本快照不改写，仅在当前同名 SKU 仍匹配时附加展示格式。ERP-075 的版本一致性防护继续生效，但不将其描述为历史截图异常的已证实根因。

## Git 与质量门禁

- 功能分支 `fix/ERP-090-shipment-name-format` 已推送远端。
- `main` 以 `ff-only` 从发布基线线性纳入累计提交；禁止 force push，旧版本标签未移动。
- 后端 `./mvnw clean verify`：194 项，0 失败、0 错误、1 个既有 OSS opt-in 联通测试按常规门禁跳过，`BUILD SUCCESS`。
- 前端 `ERP_BUILD_OUT_DIR=/tmp/erp091-build-wAGqLs pnpm quality`：70 个测试文件、303 项测试通过，lint、Vue TypeScript 与生产构建通过。
- 范围契约：5 类导入、27 项能力、3 类角色、18 个 API 资源族通过。
- `git diff --check`、凭据特征与提交归属检查通过；未添加 `Co-authored-by`。
- Flyway 空库、历史升级和重复校验均到 V41。

## 一致性备份

停写备份目录：`.local/backups/erp091`，目录权限 700，文件权限 600；不包含凭据值。

- MySQL V41：`jiugeng_erp-v41-20261025.sql.gz`，SHA-256 `8bb64ba90ae26dce0f0d9ca49e8bf58831dc76f025769c4197a5fb9747a06e3a`
- 工作台图片：`dashboard-images.tar.gz`，SHA-256 `6d2d74779a375440b86a4daf5b3cede43d432e8d21162899710d94af548a8b10`
- 商品图片：`catalog-images.tar.gz`，SHA-256 `ea0f0bd12e39445f5fd6dcdc2a32e093bed98f52786393b7a477c33a69ac1b50`
- 原公网前端：`frontend-before.tar.gz`，SHA-256 `7a49abd64a042ff6700c1f4cbba49d867fc948f9f55446656f272c2fc5537c4f`
- 持久运行配置：`runtime-config.tar.gz`，SHA-256 `88e45a096dbc914e4a841919421dc415eec9407047dbecb44999b87c598344e8`

后端恢复后 Flyway 仍为 V41；产品 6、SKU 9、今日发货 9、待发货 4、商品图片 9、工作台图片 0，与停写前完全一致。

## 部署

- 后端：本机持久化 MySQL 8、OSS provider 原配置，使用 `.local/runtime/run-backend.sh` 运行；未修改认证或 OSS 权限。
- 前端版本化目录：`.local/releases/frontend/99b675c1ce5c7a6e6997a10bcdb081ff8851de99`
- 前端发布树 SHA-256：`f7e11632850465ea1247e002ee9eb73c29e1cf44adbc232a5ad98e1f6d528ddd`
- 入口资源：`assets/index-COdJJ9hV.js`
- Caddy 持久 wrapper 已指向新版本目录并通过 `caddy validate`；Caddy 重启后隧道保持原入口。
- 公网地址：`https://andreas-reel-proceedings-problems.trycloudflare.com`

## 动态验收

- 后端健康 HTTP 200 / `UP`；真实 MySQL schema V41。
- 本地与公网 HTML SHA-256 均为 `51977c7867432134aa4eecf494d9343e466c05e993f10f4633129fe96f103ce8`，入口资源一致。
- HTML 与 API 使用 no-store；哈希静态资源使用 `public, max-age=31536000, immutable`。
- 公网登录、首页、产品列表、新增产品、今日发货和发货统计路由均 HTTPS 200。
- 一次性合成 ADMIN 登录及 `/api/v1/auth/me` 200；公网 API 完成合成产品新增、精确列表读取、SKU 名称局部红色/字重更新、Excel 导出和今日发货读取。
- 产品新增、列表、更新、导出、今日发货接口均 HTTP 200；删除入口 HTTP 204。
- 探针产品已逻辑删除且不再出现在产品列表；一次性账号已禁用、session 归零、本地凭据已清理；5 条 append-only 审计保留。
- 发货图片的局部颜色、字重、换行及垂直居中由本轮独立前端 303 项门禁中的实际 Canvas 回归覆盖；未修改用户发货记录。

## 已知限制

1. 旧后端未曾保存的 SKU 名称样式无法自动恢复；升级时现有 SKU 样式非空数量为 0，用户需要重新设置并保存，不自动猜测颜色。
2. 发货单据继续保留原名称文本快照；同名 SKU 可读取当前展示格式，SKU 改名后安全回退普通文字。因此颜色不是历史冻结快照。
3. OSS 仍为主读、本地/数据库双写备份的过渡方案；本次未变更 Bucket、凭据或权限。
4. 产品/SKU Excel 导出保持纯文本名称；局部颜色和字重用于产品页面、发货页面及发货图片，不宣称 Excel 富文本支持。
5. 旧版本标签保持原指向，本次不新建或移动版本标签，以最终 `main` SHA 作为部署版本。
