# ERP-085 父体详情完善与返回导航

## 基线与范围

- 用户要求完善产品列表父体详情、修复详情面包屑返回产品管理、参照赛狐调整搜索栏重置按钮。
- 起始分支 ERP-084，工作区干净。fetch 后 origin/main 为 `36f0ef95b918c9ca0921f6a6d99aa83cb9cf4953`；从该基线创建 `feature/ERP-085-product-detail`，依次携入 ERP-078 至 ERP-084 累计本地提交。
- 计划：复用现有 API 和文件组件补齐只读资料及编辑闭环；修正路由元数据；调整重置样式；补行为测试及响应式验收。
- 用户授权本地开发和提交；不推送、合并 main、发布线上或重启持久服务。

## 实现

- ProductDetailView：父体图片预览、基础资料、SKU概览；SKU 结构化尺寸、采购供应商名称与安全链接、报关资料及历史动态属性；辅料汇总名称、备注；按 SKU 查看现有说明书和标签组件；实际调用产品历史接口，展示时间、操作人、操作和变更内容。
- 编辑继续复用 ProductForm，补齐父体 imageAssetId 与 SKU customs 传递，添加取消入口，保存失败保留编辑状态，成功重新读取详情。停用改为系统样式确认弹窗。
- 请求与路由 productId 联动，切换父体清理旧数据，序列校验防止旧响应覆盖新父体；历史请求独立错误与重试。供应商/辅料引用名称按权限读取，引用失败不阻断主体详情。
- 路由新增 documents 分区；所有详情分区面包屑为“商品 / 产品管理 / 产品详情”，产品管理可点击返回，同时保留独立返回入口。
- ProductsView 重置改为 32px 无边框文字按钮，沿用清空全部筛选、回第一页和重新查询行为。已观察用户 Safari 赛狐打开页面的文字重置样式。
- UI 规范增补详情及重置规则。窄屏修复 grid 最小内容宽度导致卡片被宽表撑开的情况。

## 文件

- `frontend/src/views/ProductDetailView.vue`、新增 `ProductDetailView.test.ts`
- `frontend/src/views/ProductsView.vue`、`ProductsView.test.ts`
- `frontend/src/router/index.ts`、`frontend/src/layouts/AdminLayout.test.ts`
- `frontend/src/styles/main.css`、`docs/ui/UI_STANDARD.md`、本记录

## 验证

- `pnpm exec vitest run src/views/ProductDetailView.test.ts src/views/ProductsView.test.ts src/layouts/AdminLayout.test.ts`：53 项通过。
- 最终 `ERP_BUILD_OUT_DIR="$(mktemp -d /tmp/erp085-final-build-XXXXXX)" pnpm quality`：lint、65 文件/280 测试、类型检查、构建全部通过。日志 `/tmp/erp085-final-quality.log`。
- 新回归覆盖图片预览、编辑字段保留/取消/失败、真实历史/失败重试、权限门槛、切换产品失败清空旧内容、停用确认、文件延迟挂载、三个详情路由面包屑返回、重置恢复结果。
- 浏览器使用真实页面组件和合成 API 响应验收，不写业务数据：1280×800 基础详情及产品列表重置；800×600 SKU/变更记录；360×640 返回按钮、页签换行、操作按钮可见。修复后主内容 scrollWidth 345，不超过360视口。
- 面包屑点击实测返回列表，SKU结构化规格与实际历史内容显示正常。临时验收页面与标签页已删除、浏览器尺寸恢复。
- `git diff --check` 通过；无无关修改。

## 边界及交接

没有后端/API 合约变更，因此未运行后端门禁；没有真实业务保存、OSS 上传/下载或线上发布验证。文件功能复用已存在的鉴权组件。历史内容以接口提供的原始操作和描述为准，不推断接口未保存的字段差异。本地提交后由项目经理独立验收，不宣称已部署或发布。
