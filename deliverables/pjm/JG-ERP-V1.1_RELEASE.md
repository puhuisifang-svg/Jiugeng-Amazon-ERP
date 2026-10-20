# JG-ERP-V1.1 发布记录

日期：2026-10-20

范围：ERP-055～ERP-074

基线：`JG-ERP-V1.0` / `f1af0fc94e8bd386688b1c735bf776bba77ca8bf`

功能提交：`c46ab4ddad89c26a5e67e3ffe4d06f6af4379ca8`

## 复核结论

ERP-055～073 累计变更通过独立复核，可作为向后兼容功能版本发布为 `JG-ERP-V1.1`。旧标签 `JG-ERP-V1.0` 保持不变。

发布包含发货 SKU 绑定及交互修正、客户与客户商品、发票登记、产品报关资料、父体图片、产品/SKU/客户商品逻辑删除、客户外箱标模板与 PDF 导出，以及配套权限、测试和迁移。ERP-056 是已完成的本地数据操作记录，本次未重放清理动作。

## 独立质量门禁

- 前端 `pnpm quality`：61 个测试文件、243 项测试通过；lint、Vue TypeScript、Vite 生产构建通过。
- 后端 `./mvnw clean verify -Dlabel.qa=true`：178 项测试通过，`BUILD SUCCESS`。
- Flyway：39 条迁移从空库完整执行并验证；V29～V39 共 11 个新增迁移文件齐全。
- 范围契约：`validate-mvp-scope.py` 通过（5 类导入、27 项能力、3 类角色映射、18 个 API 资源族）。
- `git diff --check`、未跟踪产物盘点及敏感信息检查通过。
- PDFBox 3.0.8、Noto Sans SC 字体及 OFL 许可证已打入后端 JAR；字体 SHA256 为 `b1a3beb24731fe84695e98d61d76cbeaaec7d6f29744c90aa56da0bf70b1d030`。
- 隔离 MySQL 备份恢复演练通过：V39、数据库记录、工作台图片、商品图片计数和文件哈希一致；兼容旧版无商品图片目录的备份。

## 发布与数据保护

- 环境：本机真实 MySQL 8、`jiugeng-mvp` tmux 前后端、Caddy 与 Cloudflare Tunnel。
- 公网入口：`https://andreas-reel-proceedings-problems.trycloudflare.com`
- 发布前一致性备份：`.local/backups/erp074/dashboard-20261020T073230Z.tar.gz`（Git 忽略、权限 600）。
- 备份 SHA256：`f1af5cc4fc5cc5468a787f0581b65c1edb81238816cabe64b811be7bddc0fdf8`。
- 备份清单：Flyway V39；工作台图片 0；商品图片 9 个有效资源；数据库、两类附件分别记录 SHA256。
- 运行库外箱标状态：2 个模板均为 `singlePage=true`，4 批历史导出保留；未重放 ERP-073 更新，未改写历史 PDF。
- 后端使用既有 `scripts/dev/run-local-mysql-backend.sh` 恢复，Flyway 验证 39 条迁移并确认 schema V39；前端由现有 Vite 服务加载生产构建对应源码。

## 健康与冒烟

- 后端健康：`HTTP 200`，`{"status":"UP"}`。
- 本地前端首页、客户、发票、报关页面：HTTP 200。
- 公网登录、客户、发票、报关页面：HTTPS 200。
- 公网未登录访问客户、发票、报关 API：HTTP 401。
- 后端、前端、Caddy、Cloudflare Tunnel 进程均保持运行。

## 剩余限制

1. 发票是内部登记，不是税务开票接口；报关资料是维护，不是海关申报。
2. 发货确认不调用承运商、不扣库存、不按 SKU 分摊；已发货暂无撤销入口。
3. 产品、SKU、客户商品逻辑删除后无页面恢复入口；编码和历史身份保留，新业务入口排除删除档案。
4. 外箱标单页含义是每条发货记录一页；历史 PDF 保留原始字节，新导出使用当前模板；实体标签机试打未执行。
5. 首页批量发货逐条执行，不是整批事务；外链图片导出仍受浏览器 CORS 约束。
6. 系统仍是内部 ERP 的本地 synthetic/mock MVP，未对接外部生产数据、税务、海关或承运商系统。
