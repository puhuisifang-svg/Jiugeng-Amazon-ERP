# PDF 连续页面工作区（2026-11-24，本地累计迭代）

## 授权与工作区

用户直接要求：拆分默认“提取页面，合为一个 PDF”；取消 12 页一组，整份 PDF 在同一区域勾选；编辑参考已打开的 iLovePDF，解决只能逐页切换的问题。仅本地实现，无本轮提交或发布授权。

基线 / 当前 HEAD：`9954ef8bd15465ed82b545b98411e9cf07e2f892`，开始时 main 工作区干净，`git ls-remote origin refs/heads/main` 与之相同。Git 历史已有上一轮 PR #23 合并；未调查线上发布状态，不把合并当作已部署。

任务标识：用户直接授权的 PDF 连续工作区维护主题，暂无新 ERP 编号。通过既有 OpenClaw 协调入口请求 PJM 任务号和写入权，run `pdf-workspace-20261124-slot` 已接受，但会话以 model error / session ended 结束，没有收到任务分配答复。依据用户直接实施指令及当前无活动写入者的干净工作区继续本地实现，未伪造 PJM 授权或 ERP 编号；本次采用 `codex/pdf-continuous-workspace` 分支作为未编号任务的例外。当前唯一实际写入者 Codex；不重新启动 PJM 并行修改。下次交接须先复核锁、任务编号与分支，不沿用历史写入权。

全部改动未提交、未暂存；仅本轮 PDF 前端、测试和对应文档，无其他来源修改。未切换运行环境，未重启后端、未改数据库/业务记录。现有 Vite 5173 返回 HTTP 200，热更新接收本地前端修改。

## 实现与决策

- `frontend/src/views/PdfToolsView.vue`：拆分默认 merge，提取模式全部页码同一缩略图网格；取消上一组/下一组。原范围输入、全选/清空、选中统计、其他拆分方式、导出命名与页序保持。既有 50 MB / 500 页文件限制不变。
- 新增 `frontend/src/components/PdfContinuousDocument.vue`：编辑采用左侧可隐藏缩略图导航、中间连续页面画布、顶部常驻文字/图片/高亮/矩形工具及页码跳转、10%–200% 缩放和适合宽度。100% 表示适合工作区宽度，不是物理打印比例。滚动同步当前页，短末页有边界处理；跳转仅改变内部滚动，不拉动整个应用。
- 编辑属性/图层移至右侧紧凑 224px 栏；为连续画布保留空间，属于既有 240px 设置栏的局部密度调整。850px 以下仍可折叠设置，导航和编辑按钮可换行；其他页签保留原布局。
- 页尺寸在导入时读取 CropBox 和旋转；不同页的标注位置、字号和键盘微调按各自尺寸计算。拖动与缩放手柄读取所属页面的实际矩形，不再依赖单个全局 stage；缩放只改变显示比例，保留导出坐标。
- `frontend/src/components/PdfPagePreview.vue`：增加可选 lazy / size。完整编辑页和缩略图均在可视附近加载，沿用共享 PDF 文档和两路渲染；离屏释放 lease 并清零画布，保留比例占位，返回时重绘。避免取消分组后累积所有页位图。其他既有非 lazy 预览行为不变。
- 参考来源：[iLovePDF 编辑页面](https://www.ilovepdf.com/edit-pdf)。只读观察已打开页面的布局，没有操作/上传用户文件，没有将页面中的业务内容写入仓库。本轮借鉴连续编辑工作流，不复制品牌，也不增加原 PDF 文字直接改写能力。

## 同迭代追加：选中图层跨页复制

用户补充要求第一张添加的文字可从右侧复制到第二页。本次沿用 Codex 已有工作区和分支：右侧明确标出“第 N 页图层”，选中任一图层后显示紧凑“复制到 [目标页] / 复制到该页”。默认下一页，末页默认上一页；当前页选项不可选，单页文件不显示跨页入口，原当前页“复制”保留。

`duplicateLayer(targetPage)` 复用文字、图片和形状复制；新副本独立 ID，保留文字/字号/颜色及页面相对位置和尺寸，图片持有独立 Blob URL。成功后解除忙碌锁再定位目标页并选中新副本，可直接继续编辑；修改或删除副本不影响源页。失败保留原图层且不跳页。不同尺寸页面使用相对位置，并非保持距纸边毫米数。

新增跨页文字内容/样式/位置、副本独立修改与删除、单页入口边界用例，**本次未运行**；上一阶段 16 项通过不覆盖此次追加改动。`git diff --check` 通过；按用户约定将新用例、异尺寸/图片独立副本、实际滚动定位与导出核对纳入本轮交接统一验证，不为这项追加启动完整测试或浏览器回归。仍未提交、未发布。

## 同迭代追加：原图保真、快捷缩放、删除与 FBA 全页预览

用户要求图片转 PDF 默认跟随图片尺寸且不压缩；编辑支持 Ctrl＋滚轮、修复文字无法删除；FBA 预览全部页面。沿用当前分支/唯一写入者，无新发布授权。

- `PdfToolsView.vue`：图片纸张默认 original，选项为“跟随图片尺寸（保留原图像素）”；提示说明 96 DPI 只换算页面物理尺寸，A4 可选页面或打印时适合纸张。`imagesToPdf` 原本用每像素 0.75pt（72/96），不重采样图片，这部分算法无需修改。
- `pdfImageInput.ts`：排查发现旧流程会将 JPEG 用质量 .92 重新编码，确有有损压缩。现在普通 JPEG/PNG 将原字节交给 PDF 嵌入；EXIF 旋转/镜像照片才按完整解码像素转无损 PNG，以保留方向而不再进行 JPEG 有损重压缩。小预览仍可缩小/压缩，仅用于界面，导出从不用缩略图。原有体积/像素限制继续生效，超限提示而非静默降质。
- `PdfContinuousDocument.vue`：Ctrl 或 Command＋滚轮只缩放文档画布，阻止该区域的浏览器整页缩放；普通滚轮保持滚动，范围为 10%–200%。缩放保留光标附近内容位置。工具按钮沿用同一缩放逻辑；readonly 模式显示预览语义。
- 文字删除：原 pointerdown 阻止了默认聚焦，导致点击文字后键盘删除无法到达图层；现显式聚焦所选图层，页面工作区统一处理 Delete / Backspace，忽略输入框、文本域、下拉及可编辑内容，避免删字误删整个图层。增加顶部“删除选中”按钮，右侧原移除入口保留；删除同时清理拖动监听。
- FBA 复用连续文档组件展示所有原页/转换页，转换后占位尺寸统一 100×100mm；有缩略图导航、滚动与缩放，保留按需渲染/离屏释放，不一次渲染全部页面。转换后的导出算法及文件名不变。

本次对用户报告的删除/缩放错误和原图保真做必要局部检查，未运行全量门禁或浏览器矩阵。以下命令均在 `frontend/`：

- `pnpm exec vitest run src/utils/pdfImageInput.test.ts src/components/PdfContinuousDocument.test.ts src/views/PdfToolsView.test.ts`：图片输入 3 项通过；另 3 项最初因测试事件构造器及等待异步 PDF 完成的写法失败，已修正夹具。
- `pnpm exec vitest run src/components/PdfContinuousDocument.test.ts src/views/PdfToolsView.test.ts`：最终 2 文件 / 18 项通过，合并未受修改影响的图片输入 3 项，共 21 个独立用例通过。覆盖普通 JPEG/PNG 字节不变、EXIF 无损规范方向、Ctrl/Command wheel 与普通滚动区分、点击聚焦及键盘/按钮删除、输入文字时不误删、默认纸张及 14 页 FBA 连续预览，并覆盖上一追加的跨页复制独立性。日志 `/tmp/pdf-print-update-tests.log`、`/tmp/pdf-print-update-tests-final.log`。
- `pnpm exec eslint src/utils/pdfImageInput.ts src/utils/pdfImageInput.test.ts src/components/PdfContinuousDocument.vue src/components/PdfContinuousDocument.test.ts src/views/PdfToolsView.vue src/views/PdfToolsView.test.ts e2e/pdf-closeout.smoke.ts --max-warnings=0`：通过，日志 `/tmp/pdf-print-update-lint-final.log`。
- `pnpm exec vue-tsc --noEmit`：通过，日志 `/tmp/pdf-print-update-types.log`；最终仅补删除时停止拖动及修正测试事件/等待方式，完整类型门禁仍在交接统一执行。
- `git diff --check` 通过；所有修改仍属本轮。已更新 FBA 浏览器用例为多个预览节点，但未执行，保留交接待验。

本节更新了上节跨页复制“未运行测试”的阶段状态。真实浏览器 Ctrl 滚轮/触屏行为、各类 EXIF 图片方向与实际 A4 打印/标签扫码仍留交接检查，不能把局部用例当作实物打印验收。96 DPI 不减少像素，但打印清晰度仍由原图像素数与最终打印尺寸决定，不保证低分辨率图片放大后清晰。

## 局部检查

本轮未到用户要求的项目经理交接节点，只对新的页面归属、导航和资源释放这些关键行为进行局部检查；没有完整 quality、全量回归或多视口矩阵。

所有 pnpm 命令工作目录为 `frontend/`：

- `pnpm exec vitest run src/views/PdfToolsView.test.ts src/components/PdfPagePreview.test.ts`：2 文件 / 15 项通过。覆盖 25 页跨旧分组界线勾选、跨页标注、缩放坐标不变、已有图层操作、页签隔离与 lazy 释放重绘。日志 `/tmp/pdf-continuous-tests.log`。
- `pnpm exec vitest run src/components/PdfContinuousDocument.test.ts`：1 项通过；覆盖滚动同步不反向跳动、短末页、缩略图定位及忙碌锁定。日志 `/tmp/pdf-continuous-navigation-tests.log`。
- `pnpm exec eslint src/components/PdfContinuousDocument.vue src/components/PdfPagePreview.vue src/components/PdfPagePreview.test.ts src/views/PdfToolsView.vue src/views/PdfToolsView.test.ts e2e/pdf-closeout.smoke.ts --max-warnings=0`：通过。首轮提示可选 size 的默认值，已补齐；日志 `/tmp/pdf-continuous-lint.log`。
- `pnpm exec eslint src/components/PdfContinuousDocument.vue src/components/PdfContinuousDocument.test.ts --max-warnings=0`：导航补充后通过，日志 `/tmp/pdf-continuous-navigation-lint.log`。
- `pnpm exec vue-tsc --noEmit`：通过。首轮 exactOptionalPropertyTypes 不接受可选 size 的显式 undefined，已修正类型后重跑；日志 `/tmp/pdf-continuous-types.log`。后加导航测试由 Vitest/lint 覆盖，完整交接门禁会再纳入类型检查。
- 仓库根 `git diff --check` 与变更 Markdown 本地链接检查：通过；改动归属复核无无关文件。

`frontend/e2e/pdf-closeout.smoke.ts` 已更新默认拆分方式、多个预览/画布的定位，**本轮尚未执行**。不能沿用上一轮浏览器结果宣称当前布局已通过验收。

## 交接时统一待验

1. 前端 `pnpm quality`、更新后的 PDF 浏览器用例与三尺寸布局；尤其长工具栏换行、右侧属性滚动、窄屏折叠和导出入口。
2. 大文件快速滚动/缩略图跳页、缩放后定位、不同页宽高/旋转/CropBox、多个页的文字/图片/矩形拖动和导出对照；确认不发生页归属错位或长期内存增长。
3. 源文件名、ZIP、透明图片、FBA 与使用共享 PdfPagePreview 的其他入口回归。Safari 下载/触屏拖动仍待实际浏览器验证。
4. 等用户另行指令再交 PJM 提交 main / 部署；本地保持未提交，Codex 不查询线上发布。

## 同迭代追加：最小缩放 10%

用户反馈 50% 仍过大，已将共享连续文档组件的缩放下限及缩小按钮禁用边界统一改为 10%，编辑与 FBA 全页预览同时适用；Ctrl/Command＋滚轮使用相同边界。最大值仍为 200%。本次只运行 `git diff --check`，没有重复完整测试；10% 下限、滚轮/按钮一致性与恢复适合宽度纳入交接待验。此前 21 项通过不代表本次边界已验证。未提交、未发布。


## 本轮正式交接（2026-11-24）

用户已授权累计修改提交 main 并部署；统一验证、最终未提交文件归属、已验证与未测边界、PJM 操作责任见 [DEL-052](PDF_HS_RELEASE_HANDOFF_2026-11-24.md)。该交接覆盖本文开发阶段的“待验证/无发布授权”状态。正式发送后唯一写入权归 PJM，Codex 不跟踪发布。
