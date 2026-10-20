# ERP-072 外箱标保存反馈与单页配置

## 授权与计划

- 用户要求模板保存成功反馈，以及客户信息中配置仅导出一页。
- 当前分支 `fix/ERP-055-shipment-sku-binding`，基准 HEAD `f1af0fc94e8bd386688b1c735bf776bba77ca8bf`。延续累计开发、本地部署，不提交/推送/合并 main/远端发布。
- 计划：系统 toast → 客户外箱标模板页数设置 → 预览/导出/历史页数一致 → 测试和 PDF 渲染 → 本地更新。

## 实现

- `CustomerLabelTemplates.vue`：保存成功使用系统 `SuccessToast`（支持弹窗顶层，不占版面），失败保留错误提示；保存后即保留返回 ID，刷新列表失败不误报保存失败、不重复新增模板。
- 客户信息 → 外箱标模板 → 导出页数：按箱数导出 / 仅导出 1 页（每条发货记录）。配置按客户模板保存，多模板各自独立。
- `api/cartonLabels.ts`、`LabelTypes.java`：配置增加 singlePage，默认 false，旧 JSON 缺省时保持按箱数；无数据库迁移。
- `LabelPdf.java`、`LabelService.java`：按模板计算实际页数，统一渲染、500 页上限及导出历史页数。仅减少重复页，不改标签上的总数量/总箱数。批量不同发货记录分别出标签，不合并内容。
- `CartonLabelExport.vue`：切换模板后实时刷新页数和预览状态。
- 已保存 PDF 原始字节不改写；配置进入新导出快照。
- 测试：`LabelControllerTest.java`、`CustomerLabelTemplates.test.ts`、`CartonLabelExport.test.ts`。

## 验证与交接

- `./mvnw clean verify -Dlabel.qa=true`：178 tests 全部通过，BUILD SUCCESS。
- `pnpm quality`：61 files / 243 tests 全部通过，lint / 类型检查 / 构建通过。
- 回归覆盖：成功 toast、保存失败不报成功、保存后刷新失败保留 ID、单页配置保存并回读、超过 500 箱但只出 1 页、单页和按箱数模板混合导出、历史页数准确、旧配置缺省兼容、切换模板实时重算页数并清除旧预览。
- PDF 技能渲染检查：`target/label-qa/single-page.png`，单页中保留真实合成数量/箱数，中文、无边框首行及表格无裁切；测试校验实际 PDF 页数。
- 本地备份 `.local/backups/erp072-bgnRvA/pre-change.sql.gz`，权限 600、gzip 校验通过；仅重启 `jiugeng-mvp:0.0`，执行 `scripts/dev/run-local-mysql-backend.sh`，Vite 自动更新。无数据库迁移、无数据清空。
- `git diff --check` 通过，暂存区为空；无无关新增修改，保留先前累计变更。
- 本次及之前累计修改保持未提交；没有替用户更改真实客户模板。
- 本地更新后后端健康 `UP`，前端 HTTP 200；未远端部署，尚未项目经理验收或实体标签机试打。
