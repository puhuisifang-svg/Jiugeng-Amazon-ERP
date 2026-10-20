# ERP-069 外箱标紧凑双列首行

## 授权与范围

- 延续用户当前分支累计开发、本地部署授权；不提交、推送、合并 main 或远端部署。
- 分支 `fix/ERP-055-shipment-sku-binding`，基准 HEAD `f1af0fc94e8bd386688b1c735bf776bba77ca8bf`；保留 ERP-055～068 全部累计修改。
- 计划：PDF 首行压低并拆分两列 → 导出输入文案明确 → 回归与实际渲染检查 → 本地部署。

## 实现

- `LabelPdf.java`：首行由 12 mm 改为 8 mm，左右列 60% / 40%，分别显示 SKU 名称、合同号或订单号；常规字号 8.5 pt，不加粗突出。正文获得释放的 4 mm 空间，纸张仍为 100×100 mm。
- 首行采用较小垂直内边距；保留自动换行、最小字号及超长拒绝规则，不截断内容。
- `CartonLabelExport.vue`：SKU 名称自动带出并可手动修改，合同号 / 订单号可选手动输入，明确占位提示。
- 沿用已有 productName / contractNo 字段，无数据库迁移；手动值按导出快照保存。历史 PDF 下载仍读取原始字节，不改变既有文件。
- `LabelControllerTest.java`：合成订单号参与 PDF 内容校验与渲染样本。
- `CartonLabelExport.test.ts`：检查手动 SKU 名称及订单号进入预览请求。

## 验证与交接

- `./mvnw clean verify -Dlabel.qa=true`：176 tests 全部通过，BUILD SUCCESS。
- `pnpm quality`：61 files / 240 tests 全部通过，lint / 类型检查 / 构建通过。
- 合成样本 `target/label-qa/sample.pdf` 渲染 PNG 后目视检查：紧凑双列表头、订单号、中文及表格无裁切或重叠；页面尺寸仍由集成测试验证为 100×100 mm。
- `git diff --check` 通过，暂存区为空；最终状态仅包含已授权累计修改及本次记录，无无关新增修改。
- 数据库备份 `.local/backups/erp069-kyto3W/pre-change.sql.gz`，gzip 校验通过、权限 600。
- 2026-10-20 仅重启本地 `jiugeng-mvp:0.0`，运行 `scripts/dev/run-local-mysql-backend.sh`；MySQL 持久化模式，无迁移、清空或业务数据写入。后端健康 `UP`，前端 HTTP 200，Vite 自动更新。未触碰远端部署或公共代理。
- 本次修改保持未提交；等待用户后续指令及项目经理复核。
- 真实 100×100 mm 标签机试打仍由用户确认。
