# ERP-070 外箱标首行去边框

- 用户要求第一行边框不用加黑，按无边框双列实现；其余表格边框、首行高度和文字位置不变。
- 分支 `fix/ERP-055-shipment-sku-binding`；基准 HEAD `f1af0fc94e8bd386688b1c735bf776bba77ca8bf`。延续已授权累计修改，仅本地部署，不提交或远端发布。
- 变更：`LabelPdf.java` 首行不绘制边框；`LabelControllerTest.java` 校验 PDF 仅保留正文、标题、页脚的 10 个边框。
- 计划：实现 → 后端全量门禁及 PDF 渲染检查 → 本地更新；历史已保存 PDF 不重写。
- `./mvnw clean verify -Dlabel.qa=true`：176 tests 全部通过，BUILD SUCCESS。前端未改动，不重复运行前端门禁。
- 渲染 `target/label-qa/sample.pdf` 为 PNG 并目视检查：首行无外框/竖线，文字清晰、位置不变，正文边框完整。
- 本地数据库备份 `.local/backups/erp070-owI9YB/pre-change.sql.gz`，权限 600、gzip 校验通过。仅重启 `jiugeng-mvp:0.0`，沿用 MySQL 持久化模式，无数据库迁移或业务数据改写。
- 修改保持未提交、暂存区为空，保留全部先前授权累计修改，无无关新增修改。未推送、合并 main 或远端发布；真实标签机试打未执行。
- 本地重启后健康检查 `UP`；`git diff --check` 通过。
