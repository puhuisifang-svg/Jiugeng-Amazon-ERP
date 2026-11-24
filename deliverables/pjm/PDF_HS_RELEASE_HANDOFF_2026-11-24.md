# PDF 连续工作区与客户商品 HS 历史引用发布交接（DEL-052）

- 项目：九更亚马逊 ERP
- Owner：Codex → PJM；正式发送后唯一写入权归 PJM
- 状态：v1.0 / In Review；本地统一验证完成，提交/合并/部署由 PJM 执行，未宣称已上线
- 日期：2026-11-24，Asia/Shanghai
- 审批：用户已明确授权提交远端 main 并部署；PJM 负责复核与发布结果

## 授权与精确工作区

用户原话：“与项目经理交接，把修改内容提交远端main，并部署线上。”范围为本轮累计 PDF 连续页面、复制、原图转换、缩放/删除、FBA 全页预览，以及客户商品同 HS 历史引用及紧凑弹窗/来源选择器修复。

- 工作区：`/Users/thinkperfect/.openclaw/workspace-jiugeng_pjm/jiugeng-amazon-erp`
- 分支：`codex/pdf-continuous-workspace`
- HEAD / 基线：`9954ef8bd15465ed82b545b98411e9cf07e2f892`。本次交接前 `git ls-remote origin refs/heads/main` 仍返回同一 SHA。
- **全部累计修改未提交、未暂存，没有本轮实施提交 SHA。** 下列 dirty 文件均属本轮，未发现无关改动。
- 任务标识：用户直接授权维护主题，尚无新 ERP 编号。开始时 PJM 编号请求因模型错误未获答复；分支例外及开始时干净工作区证据见 [DEL-050](PDF_CONTINUOUS_WORKSPACE_LOCAL_2026-11-24.md)。请 PJM 接管后补充任务归档，不能伪造已分配编号。
- 正式发送本记录给 `agent:jiugeng_pjm:main` 后，唯一写入权移交 PJM。Codex 停止编辑/测试/本地服务操作，不提交、不追踪 CI/CD 或线上发布。不得 reset/stash/clean 丢弃当前未提交代码。下一次接手重新核对实际状态。

## 已完成与代码入口

1. **PDF 连续工作区**：[DEL-050](PDF_CONTINUOUS_WORKSPACE_LOCAL_2026-11-24.md)。`PdfToolsView.vue` 默认提取合并，整份 PDF 同一网格勾选；新 `PdfContinuousDocument.vue` 左缩略图、中间连续画布、右侧属性栏、页码联动。所有页面保留比例，`PdfPagePreview.vue` 按可视区域渲染并释放离屏画布。
2. **PDF 跨页与快捷操作**：文字/图片/形状独立复制到目标页，复制后定位选中；保留相对位置和样式。Ctrl/Command＋滚轮仅缩放文档，10%–200%，适合宽度可复位；点击文字后 Delete/Backspace 与显式删除按钮可用，编辑输入时不截获删除键。
3. **图像与 FBA**：图片转 PDF 默认跟随原像素尺寸，96 DPI 仅换算纸张，既有每边 2mm 默认边距仍保留；普通 JPEG/PNG 原字节嵌入，EXIF 方向修正按全像素转无损 PNG，小预览不用于导出。FBA 原文件/转换结果均可连续访问全部页。
4. **客户商品 HS 历史**：[DEL-051](CUSTOMER_HS_HISTORY_LOCAL_2026-11-24.md)。customer 模块新增 `GET /customers/products/customs-history`，10 位精确匹配、分页、只读权限、排除当前/已删除来源。`CustomerCustomsFields.vue` 维护独立核对草稿和过期响应隔离；来源可选择/分页，只有核对确认才更新当前表单，保存只提交当前商品。历史来源不回写，当前版本保留，历史已核对状态不继承。
5. **历史核对 UI**：640px 内容高度、紧凑两列/窄屏单列、固定底栏；来源商品/客户/SKU/更新时间分层，明确“仅引用，不修改来源”。截图反馈的双控件已修复：Teleport 挂到 `#app`，使共享选择器原生隐藏和主题规则生效，不改全局控件样式。

本轮不改数据库迁移、部署配置或外部存储配置；历史图片性能/Caddy 变更已在基线中，不重复提交旧迭代内容。没有增加原 PDF 文字直接改写能力。

## 统一验证结果

本次已到用户要求的迭代交接节点。以下覆盖最终应用代码；完整门禁之后只完善了浏览器测试及文档，应用代码未再改变，复用有效结果。

| 工作目录 | 精确命令 | 结果 |
| --- | --- | --- |
| `frontend` | `pnpm quality` | PASS：169 个文件 / 778 项测试；lint、类型检查、生产构建均通过。日志 `/tmp/erp-20261124-frontend-quality.log`。 |
| `deliverables/backend/sprint-0/jiugeng-erp-backend` | `./mvnw clean verify` | BUILD SUCCESS，18:56:24；443 项，0 失败/错误，1 跳过：外部 OSS 探测缺少 `ERP_OSS_PROBE`。含模块架构和既有迁移。日志 `/tmp/erp-20261124-backend-verify.log`。 |
| `frontend` | `pnpm exec eslint e2e/pdf-closeout.smoke.ts e2e/customs-history.smoke.ts --max-warnings=0` | PASS，覆盖最后的浏览器测试变更。 |
| `frontend` | `pnpm exec playwright test e2e/pdf-closeout.smoke.ts e2e/customs-history.smoke.ts` | HS 三尺寸 3 项通过；PDF 旧第一页可见断言不适用于跨页复制后的第二页，首轮 3 项失败，后续修正并重跑。日志 `/tmp/erp-20261124-browser.log`。 |
| `frontend` | `pnpm exec playwright test e2e/pdf-closeout.smoke.ts` | 最终 4 项通过：三尺寸 PDF 完整流程、15 页选择与离屏释放。日志 `/tmp/erp-20261124-pdf-browser-corrected.log`。 |
| `frontend` | `pnpm exec playwright test e2e/pdf-closeout.smoke.ts --grep 'PDF tools preserve' --output /tmp/erp-20261124-final-screens` | 增加截图前等待当前页渲染后，3 项通过；已目视桌面/手机截图。日志 `/tmp/erp-20261124-pdf-screens.log`。 |
| 仓库根 | `git diff --check`、变更 Markdown 本地路径/链接检查、`git status --short` | 通过；无暂存或无关修改。 |

共 **7 个独立 Chromium 浏览器场景**（重复复测不计数），使用生产构建、合成 PDF/图片/API 夹具，不写入真实业务数据。三尺寸为 1280×800、800×600、360×800。

- HS：从真实客户商品页打开嵌套核对弹窗；原生选择器正确隐藏；多来源切换与第 2 页、取消保留原值、查询失败重试、修改后重新核对、未确认不带入、未保存不发写请求、最终唯一 PUT 指向当前商品，状态 DRAFT/版本 7 保留。可见按钮、无横向溢出和页面错误均检查。此前定向挂载/来源对象隔离用例已纳入完整门禁。
- PDF：默认提取合并、四类下载源文件名、ZIP 页数、旋转/CropBox、中文标注、跨页独立编辑/删除与导出页图层、Ctrl 滚轮、10% 下限和复位、图片默认 original 与 96 DPI＋边距纸张尺寸、透明 PNG/JPEG、FBA 每页 100×100mm、窄屏操作可达。15 页文件不分组，首尾可达，离屏页画布释放为 0。
- 测试调试记录：PDF 先修正了“第一页面必须可见”的旧断言，后修正新增夹具对既有 2mm 边距的遗漏与误用 checkbox 定位（实际为 aria-pressed 选择按钮）。这些是测试假设修正，未靠改业务行为让检查通过。最终应用代码复用完整门禁；截图补等渲染仅重跑受影响的 3 个场景。
- 无导入契约或发布脚本变更，不执行其专用门禁；没有额外启动整个不相关模块的浏览器回归。

## 边界、风险与运行状态

- PDF.js 静态/动态导入与超过 500kB chunk 的构建提示仍在，非构建失败。本轮没有引入新依赖。
- 没有 Safari/WebKit 下载、物理 A4 打印/标签扫码、全部 EXIF 方向组合或 500 页生产规模内存压测结果；15 页懒渲染不等同极限压测。低像素原图放大打印的清晰度不能由不压缩保证。
- HS 当前在已有 TEXT JSON 上使用绑定 REGEXP 查询；没有新增索引，数据量增长后的扫描性能需按实际量评估。符合既有客户读取权限，不新增跨客户数据隔离模型。
- 本地后端为 DEL-051 的 mysql 持久运行包，原运行包与脚本已保留；本次验证未重启或迁移生产/本地业务库。无需为交接重复更新本地运行包。
- 临时 Playwright 4188 预览和测试进程已结束，持久 `jiugeng-mvp` 服务继续保留。临时截图/日志不纳入 Git，命令、结论与限制已写入本记录。
- **实现与本地验证完成；合并、部署、业务验收未核实。** 没有已知阻断提交的问题，没有需用户再次确认的发布权限。

## PJM 下一步

1. 接管共享工作区后复核分支/基线/完整 diff（含未跟踪文件），按范围原子提交，走受保护 main 的 PR/合并流程；不要直推绕过保护或遗漏新增组件/测试/文档。
2. 使用精确提交触发现有 CI/CD，发布前保留既有备份与上一版本；由 CI 构建前后端，不把开发运行 JAR 当作正式提交包。本轮没有新增数据库迁移或代理配置步骤。
3. PJM 独立负责 CI/CD、实际部署、健康与版本核实，并将提交 SHA、PR、main SHA 与发布证据补入状态报告，直接向用户报告。Codex 按用户约定不轮询、不等待线上发布。

## 完整改动归属

下列全部属于本轮 PDF、HS 引用、配套验证和交接文档；均未提交。不得遗漏 `??` 新文件。

```text
 M deliverables/backend/sprint-0/jiugeng-erp-backend/src/main/java/com/jiugeng/erp/customer/CustomerController.java
 M deliverables/backend/sprint-0/jiugeng-erp-backend/src/main/java/com/jiugeng/erp/customer/CustomerService.java
 M deliverables/backend/sprint-0/jiugeng-erp-backend/src/main/java/com/jiugeng/erp/customer/CustomerTypes.java
 M deliverables/backend/sprint-0/jiugeng-erp-backend/src/test/java/com/jiugeng/erp/customer/CustomerControllerTest.java
 M docs/04-engineering/PROJECT_HANDOVER.md
 M docs/08-project-management/STATUS_REPORT.md
 M docs/DELIVERABLE_REGISTER.md
 M docs/INDEX.md
 M docs/ui/UI_STANDARD.md
 M frontend/e2e/pdf-closeout.smoke.ts
 M frontend/src/api/customers.ts
 M frontend/src/components/CustomsFields.vue
 M frontend/src/components/PdfPagePreview.test.ts
 M frontend/src/components/PdfPagePreview.vue
 M frontend/src/utils/pdfImageInput.test.ts
 M frontend/src/utils/pdfImageInput.ts
 M frontend/src/views/CustomerManagementView.vue
 M frontend/src/views/PdfToolsView.test.ts
 M frontend/src/views/PdfToolsView.vue
?? deliverables/pjm/CUSTOMER_HS_HISTORY_LOCAL_2026-11-24.md
?? deliverables/pjm/PDF_CONTINUOUS_WORKSPACE_LOCAL_2026-11-24.md
?? deliverables/pjm/PDF_HS_RELEASE_HANDOFF_2026-11-24.md
?? frontend/e2e/customs-history.smoke.ts
?? frontend/src/components/CustomerCustomsFields.test.ts
?? frontend/src/components/CustomerCustomsFields.vue
?? frontend/src/components/PdfContinuousDocument.test.ts
?? frontend/src/components/PdfContinuousDocument.vue
```
