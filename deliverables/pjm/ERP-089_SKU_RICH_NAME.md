# ERP-089 SKU 名称分段颜色与字重

## 任务与边界

- 用户确认方案：SKU 名称支持选中文字设置黑色/红色、加粗/常规、Enter 换行及展示预览，达到第一行黑色加粗、第二行规格红色加粗的效果。
- 分支：`feature/ERP-089-sku-rich-name`。
- 基线：已 fetch 的 `origin/main`，SHA `36f0ef95b918c9ca0921f6a6d99aa83cb9cf4953`；干净工作区开始，按累计开发要求携带 ERP-078 至 ERP-088 本地提交。
- 本地提交授权沿用用户先前指示；不推送、不合并 main、不部署线上。

## 实现与文件

- `frontend/src/components/ProductNameEditor.vue`：纯文本输入、选区格式工具栏、全选、无选区提示、居中实时预览；键盘/鼠标选区及工具栏操作后恢复选区。输入支持中文及换行，最多 200 UTF-16 单元，与既有 API 限制一致。
- `frontend/src/utils/productName.ts`：格式片段合并，输入、粘贴、删除、替换时调整范围并保留未变文字格式。采用浏览器 UTF-16 选区偏移，不存 HTML。
- `frontend/src/components/ProductName.vue`：共享安全文字渲染，颜色/字重白名单；普通名称与旧整段标红自动兼容。
- `ProductForm.vue`、`ProductsView.vue`、`ProductDetailView.vue`、`SkuDetailDialog.vue`、`api/domain.ts`：编辑、提交、列表和两个详情入口完整传递 nameStyles；保留原名称搜索、SKU 编码、默认状态逻辑。
- `styles/main.css`：紧凑可换行工具栏、预览框，小屏控件不裁切；沿用系统红色。
- 后端 `NameStyleSpan.java`、`ProductNameStyleService.java`、`ProductController.java`、`ProductTypes.java`、`ProductRepository.java`：nameStyles 结构化范围、BLACK/RED 枚举、bold 布尔校验；限制数量、顺序、重叠、边界及 Unicode 代理对。自动适配既有名称首尾 trim。保持原权限、聚合乐观锁及事务；无效格式回滚聚合更新。
- `V41__sku_name_styles.sql`：新增可空 JSON 文本列，旧数据不强制改写。原 V40 的整段标红保留兼容。
- 格式修改追加审计历史，`productHistory.ts` 使用“修改 SKU 名称格式”。UI_STANDARD 同步归档。

## 兼容及明确未改范围

- nameStyles 缺省/null：名称未变时保留旧格式。旧客户端修改整段标红值时以显式旧设置为准。
- 保存格式时绑定对应纯文本快照，旧导入/API 改名但不带格式时不套用失效偏移。
- 格式与纯文本分开保存；不存在名称图片化、任意 HTML/CSS、脚本或远程字体。
- 本次产品管理列表/详情支持分段格式；未改发货、客户商品、外箱标等独立业务的图片/PDF 导出。它们以后可消费同一结构，不宣称现有导出已支持富文本。既有纯文本导出不因新增字段改变。
- 当前使用文本编辑区加实时展示预览，不在 textarea 内绘制局部颜色；最终显示以预览为准。

## 验证

- `pnpm exec vitest run src/components/ProductNameEditor.test.ts src/utils/productName.test.ts src/components/ProductForm.test.ts src/views/ProductsView.test.ts`：4 文件、36 测试通过。
- `pnpm quality`：69 文件、301 测试通过；ESLint、TypeScript、Vite 构建通过，构建输出到临时目录，未替换常驻静态站点。
- `./mvnw clean verify`：193 测试，0 失败、0 错误，1 项既有跳过，BUILD SUCCESS。含迁移、API 往返、权限拒绝、无效格式回滚、旧请求保留、版本冲突、局部取消字重/颜色和 Unicode 边界校验。
- 浏览器真实组件合成验收：1280×800、800×600、360×800；复现黑色第一行/红色第二行，选中第二行改常规/黑色，再改回红色加粗；保存回列表并重新打开仍显示格式。浏览器验收 API 使用内存合成适配器，持久化由后端集成测试覆盖，未改业务数据。
- 手机工具栏自动换行、弹窗内部滚动、底部按钮均可访问。临时页面已删除，浏览器临时页已关闭并恢复视口。
- `git diff --check`：通过；无无关变更。

## 部署交接

- 本次提交 SHA 以本文件所在提交为准，最终答复提供完整 SHA。
- 未重启 `jiugeng-mvp` 持久服务，未执行线上迁移；**现有常驻旧后端不能保存 nameStyles**。本地业务验收前，需项目经理部署此次后端并执行 V40/V41（已应用的迁移不会重复执行），前端版本须配套。
- 重启前确认存储模式并按部署流程处理；内存 mock 数据重启可能重置。合并、部署、发布及验收状态由项目经理独立确认。
