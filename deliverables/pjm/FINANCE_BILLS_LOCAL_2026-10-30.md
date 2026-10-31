# 财务 / 账单管理：本地交付

用户直接授权开发并部署本地；未新分配 ERP 任务号。沿用 `fix/ERP-099-product-detail-ui`，基准 HEAD `33122a4c08831689db5454320143fb6fc6235089`。改动未提交，未推送、未合并 main。

## 参考与口径

已通过用户 Safari 中打开的腾讯文档查看月度收支记账明细和年度汇总。原表按月分收入/支出，字段为日期、明细、收支账户、金额、图片、备注，按账户及月份汇总，统一人民币。仅参考结构；未修改表格内容、未导入真实历史记录、未将其金额或业务名称写入代码/测试。

实施顺序：独立记账店铺与持久化模型 → 收支 CRUD/作废与汇总 → 凭证和导出 → 菜单、紧凑列表、表单与详情 → 全量检查和原环境部署。

## 使用方式

入口 `/finance/bills`，菜单“财务 → 账单管理”，默认仅管理员有 finance.read / finance.write。

1. 店铺管理新增自己的运营店铺；可改名、备注、启停。与竞品监控店铺分离。
2. 选择店铺及日期，点击搜索。当前月/当前年快捷日期为截至当天；收支类型、状态、明细/分类/账户/备注关键词均参与筛选。
3. 新增收入/支出，日期、分类、收支明细、账户、金额必填。金额为人民币 CNY，正数，最多两位小数；分类和账户可自由输入，支持历史账户/常用分类建议。
4. 可附 PNG/JPG/PDF 凭证，每笔最多5个、每个5MB。保存失败保留输入；部分凭证上传失败会保留已保存账单 ID，重试不会重复新增账单。
5. 详情查看凭证缩略图/放大预览、下载和变更记录。编辑使用乐观锁，已保存账单不能跨店铺移动。作废保留原账单、凭证及审计，并从有效汇总排除，可筛选已作废记录。
6. 收入/支出/结余卡、账户汇总、月度汇总按整个筛选结果统计，不受当前分页影响。结余标注收入减支出，不代表会计利润。导出当前筛选下全部明细为带 BOM 的 CSV（最多10000条，转义公式注入）。

## 变更文件

- 后端新增 `finance/FinanceController.java`、`FinanceAttachments.java`、`package-info.java`，迁移 `V59__finance_ledger.sql`（店铺、账单、凭证三表）。后台分页、固定精度金额、乐观锁、受控凭证读取、业务审计。无跨域内部 Java 类型依赖。
- 后端 `iam/IamTypes.java` 为 ADMIN 增加财务读写；`IamControllerTest.java` 同步权限契约。新增 `finance/FinanceControllerTest.java`。
- 前端 `api/finance.ts`、`views/FinanceBillsView.vue` 与测试、`components/FinanceVoucher.vue`。路由、权限类型和 AdminLayout 增加财务菜单，路由分组测试同步。
- 复用 UI_STANDARD 的 TableViewport、ListPagination、统一确认、成功提示、未保存保护。宽表横向滚动，操作列固定，分页停靠应用底部，弹窗仅正文滚动、固定保存区。

## 验证

- `ERP_BUILD_OUT_DIR=/tmp/finance-build pnpm --dir frontend quality`：94测试文件、377测试通过；lint/typecheck/build通过。一次重跑遇到 worker 启动超时，最终完整重跑通过。
- 后端 `./mvnw clean verify`：270测试，0失败/错误，1环境探针跳过，BUILD SUCCESS。包括金额精度、多店铺隔离、分页、日期/金额校验、权限、版本冲突、作废后汇总、审计、凭证归属和内容类型、导出。
- `git diff --check` 通过。
- 原本地 MySQL 实测两个合成店铺独立，25笔主账本汇总收入24.24/支出12.34，另店铺9999.99不混入；月份分组正确；凭证上传及原图读取成功。
- 浏览器实际新增店铺和账单、切换店铺后汇总正确、凭证缩略图及弹窗、导出CSV落盘25行。1280×800、800×600、360×640检查列表/弹窗，窄屏横向内容不撑开页面，分页底部等于视口底部，手机新增/详情底部按钮可见。
- 测试店铺、26笔测试账单及凭证已按精确ID清理；审计保留。临时QA账号已禁用，会话已清理。未操作原有业务数据。

## 本地部署

- 后端 release `.local/releases/backend/finance-ledger-20261029/backend.jar`，沿用 `jiugeng-mvp:backend` / 8080；前端沿用Vite / 5173。
- V59迁移成功，health UP。迁移前数据库备份 `.local/backups/finance-ledger-20261029/before.sql.gz`（受限权限），旧启动脚本保存在 release 下 `previous-run-backend.sh`。
- 未改外网静态发布目录或隧道，未发布 main。当前历史表格不会自动同步；不含银行/亚马逊自动对账、多币种换算或原表批量导入。
- 已检查最终diff和status，原有候选/开发、样品、供应商/图片等未提交工作完整保留。本次未暂存、覆盖或提交其他任务文件。
