# 客户商品相同 HS 历史资料引用（2026-11-24，本地累计迭代）

## 授权与归属

用户直接要求输入 HS Code 时查找历史填入资料，例如 `6307909000`，必须通过弹窗核对、修改并确认后才带入新商品输入框。沿用 Codex 当前唯一写入权与 `codex/pdf-continuous-workspace` 累计分支，基线 `9954ef8bd15465ed82b545b98411e9cf07e2f892`；保留 PDF 全部未提交改动，本次不切分支、不创建并行任务或伪造 ERP 编号。未提交、未暂存、无本轮发布授权。所有 dirty 文件均属本轮用户累计修改，无其他来源文件。

## 实现

- 后端 customer 模块新增 `GET /api/v1/customers/products/customs-history?hsCode=...&excludeId=...&page=0&size=20`，继续使用 `customers.read` 权限，与既有客户商品跨客户读取范围一致。相同 10 位 HS 精确匹配，排除已逻辑删除的客户商品及当前编辑记录，按更新时间、ID 倒序，服务端分页；最大每页 50，前端使用 20。停用历史来源允许参考并明确标记。
- `CustomerController/Service/Types` 返回来源客户、商品、客户 SKU、时间、状态及其已保存报关资料；不返回报价/联系信息，不修改源商品。使用现有 server-serialized `customs_data` 的绑定 REGEXP 检索并兼容历史 JSON 空白，无数据库迁移或索引变更。TEXT 查询需要扫描；尚无生产规模性能实测，不宣称索引检索。
- 新增 `CustomerCustomsFields.vue` 包装共享 `CustomsFields`。仅客户商品入口接入历史引用；手动输入完整 HS 后防抖查询，有记录且当前 HS 字段查询完成才自动弹窗。编辑现有记录初次打开不自动弹窗，也提供“查看同 HS 历史资料”手动入口。无匹配或失败说明状态，可继续手输或重试。
- 弹窗通过 AccessDialog 展示候选来源和更新时间，多来源可选择/分页；品名、当前 HS 定义的申报要素、计量单位是独立可编辑草稿。历史不再适用的字段列出供核对，不静默塞入“其他”。不复制隐藏的重量、国外编码或备注等资料。
- 只有勾选“我已核对并按当前商品修改”并点击“确认带入”才一次更新当前表单；不强迫为了确认而修改原本正确的字段。任何再次编辑会清除核对勾选。取消/关闭不带入历史值，查询失败保留当前输入。确认明确提示替换范围；带入后状态为 DRAFT，保留当前版本，保存客户商品后才持久化。
- 切换 HS、商品、客户、页签、只读状态或卸载会取消待处理引用，旧响应不能打开弹窗或写回新商品。批量新增通过当前客户/商品 key 隔离草稿。原 HS 查询依然只提供字段/单位，不自动填入历史业务值。

## 改动入口

- `frontend/src/components/CustomerCustomsFields.vue` 与对应测试；`CustomsFields.vue` 仅新增手动 HS 输入事件。
- `frontend/src/views/CustomerManagementView.vue` 客户商品报关页签接入包装组件；`frontend/src/api/customers.ts` 增加受保护历史查询。
- 后端 `customer/CustomerController.java`、`CustomerService.java`、`CustomerTypes.java`；测试 `CustomerControllerTest.java`。保持既有公共 CustomsProfile/CustomsProfiles 边界。

## 局部验证（2026-11-24）

前端工作目录 `frontend/`：

- `pnpm exec vitest run src/components/CustomerCustomsFields.test.ts src/components/CustomsFields.test.ts`：2 文件 / 11 项通过，覆盖确认前不带入、取消、编辑独立性、状态/版本不沿用来源、切换后的迟到响应、查询失败重试及只读禁用；原 HS 字段切换/失败逻辑通过。日志 `/tmp/hs-history-ui-tests.log`。
- `pnpm exec eslint src/components/CustomerCustomsFields.vue src/components/CustomerCustomsFields.test.ts src/components/CustomsFields.vue src/views/CustomerManagementView.vue src/api/customers.ts --fix --max-warnings=0`：通过，日志 `/tmp/hs-history-lint.log`。
- `pnpm exec vue-tsc --noEmit`：通过，日志 `/tmp/hs-history-types.log`。

后端工作目录 `deliverables/backend/sprint-0/jiugeng-erp-backend`：

- `./mvnw --batch-mode --no-transfer-progress '-Dtest=CustomerControllerTest#sameHsCustomsHistoryIsPagedExactReadOnlyAndPermissionProtected' test`：1 项集成场景通过；覆盖同 HS、多客户/分页、不把备注中相同数字视为 HS 匹配、排除当前/删除资料、兼容带空白 JSON、未修改版本、不返回价格、401/403、非法 HS 和页大小。日志 `/tmp/hs-history-backend-test.log`。
- `./mvnw --batch-mode --no-transfer-progress -DskipTests package`：BUILD SUCCESS，用于更新本地运行包，不当作完整门禁。日志 `/tmp/hs-history-package.log`。
- 仓库根 `git diff --check` 与变更文档本地链接检查通过；无无关修改。

本次只验证新异步确认与 SQL/权限等关键行为，没有全量 quality / clean verify 或多视口回归；这些按用户约定留到累计迭代交接。尚需真实浏览器核对嵌套弹窗焦点、移动端、候选分页、多个 HS 快速切换、批量商品隔离及确认后保存/报关单引用。

## 本地运行与待交接

- 遵循用户长期授权，沿用 tmux `jiugeng-mvp:0.0` / mysql / 8080；停止原进程后更新同一 pane，无竞争实例，不清理数据库或上传文件。本轮无新增迁移，既有 V107 不变。
- 新包 `.local/releases/backend/customs-history-20261124/backend.jar`，SHA-256 `5279e2d32ecc5f4d82bb4192eb93e57f7f8e80256765eb1c4c62b92fd8174404`。
- 原包保留，原启动脚本备份为 `.local/runtime/run-backend.before-customs-history-20261124.sh`，当前 `.local/runtime/run-backend.sh` 指向新包。未改私有运行配置，未把配置或业务数据复制到文档。
- 启动后本地 `/actuator/health` 返回 UP，启动输出确认 mysql 和应用已启动；前端 5173 HTTP 200，接收热更新。未为验证创建真实业务资料。
- 待用户指令后，结合本轮 PDF 变更统一验证并交 PJM 提交 main / 发布；Codex 不跟踪线上发布。

## 同迭代追加：紧凑弹窗与交互（2026-11-24）

用户认可功能，要求优化外观并缩小弹窗。本次仅调整 `CustomerCustomsFields.vue` 及文档，保留确认前不带入、勾选核对、失败保留与取消语义。

- 移除 AccessDialog 的 1000px wide 模式，专用命名空间 `hs-history-dialog` 宽 640px、窄屏减 24px，按内容收缩且不超过视口高度减 32px；头尾固定、正文独立滚动，不改共享弹窗。
- 顶部用小型 HS 标签与一句核对提示；来源从大块背景卡改成紧凑摘要。只有一条结果直接显示来源商品、客户、SKU/时间，省去无意义的下拉选择；多条保留共享单选及服务端分页。
- 桌面两列、标签与输入横排，核对输入使用 36px，作为本弹窗密度例外；“其他”独占一行、文本区默认两行可拉伸。600px 以下变单列，长标签换行、不裁切。
- 核对勾选移入固定底栏，与“暂不带入 / 确认带入”集中；说明缩为一行替换范围与保存语义，不再反复占用正文。历史不匹配字段仍默认折叠且可查看。
- `git diff --check` 通过。此为可逆 UI 微调，没有新增/运行测试或完整回归；原局部结果不当作本次视觉验收。640px 弹窗、多/单来源、长字段、窄屏底栏与嵌套焦点留到交接统一验证。后端不变、不重启，仍未提交、未发布。


## 同迭代追加：参考来源层次与只读说明（2026-11-24）

- 来源区域改为浅底细边框的紧凑卡片，文档图标/来源数量为次级标题，商品名为主体，客户与 SKU 加短标签，更新时间独立弱化；停用来源保留状态标记。单条无需选择，多条继续复用共享单选和分页；保留 640px 弹窗及窄屏换行，不扩大核对表单。
- 明确显示“仅引用，不修改来源”。核对代码路径：历史接口只有 GET/SELECT；`selectSource` 新建草稿及要素对象，`apply` 只发出当前表单更新，保留当前版本；父组件保存使用当前 `editingId` 或当前新增行，不使用来源 ID。修改核对草稿、确认带入及保存当前商品均不回写历史来源。
- 加强已有确认测试：使用真正返回的历史对象快照，断言编辑/确认后来源未变、要素引用独立且没有调用保存接口；此补充断言留到交接统一运行，不将此前通过结果算作新断言验证。
- 本次 `git diff --check` 通过；未跑全量门禁、回归或浏览器矩阵，来源长名称、单/多候选、窄屏和统一视觉验收继续待交接。仅本地未提交，无后端变更或服务重启。


## 截图反馈修复：原生与自定义控件重复显示（2026-11-24）

- 用户截图确认来源选择器出现两套控件。根因：本组件 Teleport 到 body，但共享 `singleSelect.css` 的原生隐藏与按钮样式限定在 `#app`，导致样式失效；之前只做差异检查，未识别真实呈现缺陷。
- 将历史核对弹窗 Teleport 目标改为 `#app`，继续通过原生 dialog 顶层显示，避免嵌套 dialog 的 DOM 层级，并保留统一样式作用域；不修改全局选择器、不改来源读写路径。
- 新增真实 Teleport 挂载回归用例。`cd frontend && pnpm exec vitest run src/components/CustomerCustomsFields.test.ts`：5 项通过，包含此次挂载和上次增加的来源对象不变/没有保存调用断言。
- `cd frontend && pnpm exec eslint src/components/CustomerCustomsFields.vue src/components/CustomerCustomsFields.test.ts --max-warnings=0`：初次有 4 个模板换行警告；补充 `--fix --max-warnings=0` 后通过，仅修正 select/option 换行。
- `cd frontend && node /tmp/hs-source-ui-check.mjs`：通过。临时 Chromium 合成数据组件夹具复用本地 Vite 的实际组件、真实指令/Teleport/AccessDialog 和应用 CSS；断言原生 select 透明且绝对定位移出布局、可见按钮 inline-flex/左对齐、鼠标及键盘切换、返回焦点、390px 无横向溢出、无页面错误。检查结果 `/tmp/hs-source-ui-check.json`，目视截图 `/tmp/hs-source-fixed.png`、`/tmp/hs-source-fixed-mobile.png`。仅拦截合成历史 GET，无业务数据写入。调试夹具期间修正资源拦截范围和边框宽度断言，最终执行通过。
- 根目录 `git diff --check` 通过。此为截图缺陷的定向验证，非完整页面/保存链路/全量回归验收；完整累计门禁仍在交接时执行。现有累计变更归属未变，未提交、未发布。


## 本轮正式交接（2026-11-24）

用户已授权累计修改提交 main 并部署；统一验证、最终未提交文件归属、已验证与未测边界、PJM 操作责任见 [DEL-052](PDF_HS_RELEASE_HANDOFF_2026-11-24.md)。该交接覆盖本文开发阶段的“待验证/无发布授权”状态。正式发送后唯一写入权归 PJM，Codex 不跟踪发布。
