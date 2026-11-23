# PDF 编辑收尾（2026-11-23，本地累计迭代）

> 最新状态：用户已授权交项目经理提交 main 并部署；统一验证与交接边界见 [DEL-049](IMAGE_PDF_RELEASE_HANDOFF_2026-11-23.md)。以下保留开发阶段实施事实。

## 范围与归属

用户从评估清单明确批准 **3、5、6、8、9、10、12、15、19**，其余未实施。沿用 `feature/ERP-101-image-performance`，基线 `dfba431424226445db34aa1c937915d85edef9dd`，由当前唯一写入者 Codex 追加；保留同迭代图片性能及前一项 PDF 导出命名修改。无新增 PJM 编号，不把 PDF 范围伪称为 ERP-101 原任务内容。全部未提交、未推送、未发布；用户要求交接时再统一验证。

## 已实现映射

| 批准项 | 本地行为与入口 |
| --- | --- |
| 3 预览性能 | `pdfPreviewPool.ts` 按源字节对象共享 PDF.js 文档与 Worker，引用归零销毁；预览渲染并发最多 2。缩略图仅进入视口附近时解析/渲染，长边 360px；主预览长边上限继续为 1400px。渲染后清理页资源及临时画布。 |
| 5 图片内存/体积 | `pdfImageInput.ts` 统一解码方向，照片保持 JPEG 编码，PNG 保持透明通道，排序区只用最长边 512px 的预览图。PDF 保留图片完整像素尺寸；原文件与转换后字节分别累计，各限 100 MiB，最多 50 张、每张 20 MiB；单张限 4000 万、累计限 1 亿像素。失败时撤销本次待加入图片及 URL，已有内容保留。 |
| 6 预览/导出一致 | `pdfTextLayout.ts` 与 `PdfTextLayer.vue` 使用同一 Canvas 字体、PDF 点坐标、基线和行距；预览不再使用 HTML 字体布局，导出前等待字体就绪。保留已有旋转/CropBox 转换；矩形边框按 PDF 点比例显示，画布边界外内容不显示。新增文字仍作为图片图层叠加，不改写源文字。 |
| 8 过期任务/重试 | `PdfPagePreview.vue` 翻页、换文件、离开视口及卸载时取消渲染和排队任务，旧异步结果不能覆盖新页。预览错误就地重试，重新取得失败的文档实例，不要求替换源文件。 |
| 9 紧凑 UI | 沿用共享 `--ui-control-height`，设置栏缩为 240px、减少间距；有文件后上传区收为单行，空态从 520px 缩为 280px，缩略图与参数区保持紧凑。 |
| 10 常用操作可达 | 页签/导出栏吸顶，桌面设置栏可独立滚动，预览区自身滚动保留页码栏。850px 以下设置可折叠，默认收起，预览高度受控；文件选取入口仍可从空态使用。 |
| 12 拆分摘要 | 范围输入立即显示唯一页面数、输出文件数和文件名，无效输入就地提示。分范围输出保留重复页并提示重复数量；提取合并去重且按原页序输出，摘要与导出共用解析结果。 |
| 15 图层操作 | 当前页紧凑图层列表，顶层排在上方；选中、复制、上/下移一层、删除。方向键移动 1pt，Shift＋方向键移动 10pt；图片复制生成独立 Blob URL，删除一份不影响另一份。层级顺序同步用于导出。其他页面图层不出现在本页属性面板。 |
| 19 反馈 | 使用共享 `SuccessToast`，成功不挤动画布；阻断错误保留内联并可关闭。区分空文件、密码 PDF、损坏 PDF、体积/像素超限、表单/签名及内存不足。无效换文件或失败批量导入不会清除已有内容和结果。 |

前一项导出命名继续适用：PDF 单文件沿用源名；图片转 PDF 用当前第一张图片主名；多文件 ZIP 用源主名、包内加唯一序号。

## 变更文件

- 主界面：`frontend/src/views/PdfToolsView.vue`，对应既有 `PdfToolsView.test.ts`。
- 预览：`components/PdfPagePreview.vue`、新增 `PdfPagePreview.test.ts`，新增 `utils/pdfPreviewPool.ts` 及测试。
- 图片输入：新增 `utils/pdfImageInput.ts` 及测试。
- 标注：新增 `components/PdfTextLayer.vue`、`utils/pdfTextLayout.ts` 及测试。
- PDF 错误分类：`utils/pdfTools.ts`、对应测试。以上省略前缀的文件均位于 `frontend/src/`。
- 配套状态、接手指南、索引、交付登记与 UI 规范。无后端或数据库修改；沿用本地持久 Vite 服务接收更新，不重启后端。

## 局部验证与待验

仅为渲染取消/资源生命周期、图片编码/清理及编辑语义这些不确定关键行为运行局部检查，没有完整回归或浏览器矩阵：

- `pnpm exec vitest run src/utils/pdfPreviewPool.test.ts src/utils/pdfImageInput.test.ts src/utils/pdfTextLayout.test.ts src/views/PdfToolsView.test.ts`：4 文件 / 15 项通过；日志 `/tmp/pdf-closeout-tests.log`。
- `pnpm exec vitest run src/components/PdfPagePreview.test.ts src/utils/pdfTools.test.ts -t 'cancels|retries|distinguishes'`：3 项通过、7 项未选中；日志 `/tmp/pdf-closeout-final-targeted.log`。验证旧渲染取消、失败重试与加密/空 PDF 分类；之前同一预览组件的 2 项结果不重复累计。
- `pnpm exec vue-tsc --noEmit` 与变更 TS/Vue 文件的定向 `pnpm exec eslint --fix --max-warnings=0 <文件列表>`：通过。日志 `/tmp/pdf-closeout-types.log`、`/tmp/pdf-closeout-lint.log`。检查发现的测试断言类型与异常 cause 要求已修复。
- `git diff --check` 无输出；159 个变更文档本地链接校验，0 失效。`git status --short` 已复核，均为当前累计迭代的图片性能、PDF 命名及本次收尾，没有其他来源的改动，无暂存文件。
- 交接统一待验：前端完整 quality；共享 PDF 组件的所有使用方；三尺寸工具栏/折叠/滚动；Safari 实际下载；旋转/CropBox PDF 中英文多行标注与导出对照；透明 PNG/JPEG 方向、边界内存及大文件快速翻页；图层复制后删除源图层、层级与导出一致性；此前四页签文件名及 ZIP 内容。

## 边界

- 本轮未加入撤销重做、离开确认、任务进度/导出取消、缩放、页面反选、图片旋转/拖动排序、原稿对照、FBA 逐页适配、草稿/模板/多文件合并等未选功能。
- 导出及 FBA 内容分析仍有主线程工作，本轮优化预览和导入内存，不宣称所有大文件处理无卡顿或完成真实规模性能验证。
- 文字预览与导出共用绘制规则，但不同浏览器可用字体与抗锯齿不同；视觉精度仍需交接时用真实打印样本确认。
- 所有 PDF/图片仍在浏览器处理，不上传业务文件、不持久化草稿。
