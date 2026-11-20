# 产品与样品累计迭代发布交接（2026-11-21）

用户明确指令：“与项目经理交接，把修改内容提交远端main，并部署线上。（git已有CICD，应该提交就会自动部署）”。接收人：九更项目经理 `jiugeng_pjm`。登记号 DEL-042；此前本地迭代由用户直接授权，未分配新 ERP 编号。

## 写入权与 Git 基线

- 分支 `codex/project-handover-docs`；HEAD / 本次核实远端 main：`b0a3d559fd7fd21ed5c0a70bfffed8eacfd4cc20`。
- 本轮所有累计改动仍未提交、暂存区为空；已有产品、样品、图片及文档修改均属于本会话，确认无其他代理或用户的不相关修改混入。不是干净工作区，不得清理或重置。
- Codex 完成此记录并发送正式移交通知后，停止一切编辑、测试、分支切换和服务操作，项目经理获得唯一写入权；Codex 只读跟踪。不得另建普通工作 worktree 或委派并行写入者。
- PJM 已只读核实：main 严格保护且约束管理员，需 PR 和五项必需检查；`PRODUCTION_DEPLOY_ENABLED=true`。按受保护分支流程合入 main，禁止绕过或 force push。此用户指令已授权本次提交、合并和线上部署，无需重复索要批准。

## 累计发布范围

1. 文档接手体系：README、AGENTS、接手指南、当前状态、历史状态归档、索引/总账/交接模板；基于当前实现纠正旧 MVP 描述，明确验证节奏和本地服务自主重启规则。
2. 产品列表：图片首列、父子行层级与图片缩进、紧凑 UI；消除重复管理员名称；统一跨模块图片预览尺寸和关闭按钮、图片缓存及失败重试。
3. 产品详情/编辑：去除完整资料链接、采购/辅料显示可读名称与关联详情，紧凑关联表单、按需加载、历史停用关系保留、未保存保护、单子体编辑与复制供应商/辅料。
4. 产品收尾 19 项与 16 项 UI：权限依赖、数据库父体分页/筛选/导出、子体库存操作及流水、父体合计、父体删除校验、零库存删除、受权限控制恢复、日志与缺项定位。见 [产品收尾记录](PRODUCT_CLOSEOUT_LOCAL_2026-11-20.md)。
5. 样品 P1/P2 第 1–17 项：独立图片快照、逻辑移除/恢复、历史、幂等重试、复制/连续新增、批量标签、筛选导出、紧凑详情及表单、库位建议、权限联动、批量查询。P3（手动转 SKU、数量/借还、扫码盘点）明确排除。见 [样品收尾记录](SAMPLE_CLOSEOUT_LOCAL_2026-11-21.md)。

关键新增后端：ProductPageService/Controller、ProductInventoryService/Controller、CatalogRestoreService/Controller、V98–V101。关键前端：api/productManagement、ProductInventoryDialog、ProductRestoreDialog、ProductHistoryTable、ProductUiState、ImagePreviewDialog、SampleDetailDialog、SampleImage 与两份模块 CSS；既有 ProductsView/ProductForm/SkuDetailDialog/SamplesView 等同步调整。完整文件清单以交接时 `git status --short` 和最终提交 diff 为准。

## 统一验证与本轮修正

- 后端目录 `./mvnw --batch-mode --no-transfer-progress clean verify`：通过，412 项、0 失败、0 错误、1 跳过。跳过的是 `OssConnectivityTest.privateV4UploadDownloadAndCleanup`，未设置 `ERP_OSS_PROBE`；不宣称本次验证了外部 OSS 连通性。日志 `/tmp/jiugeng-release-backend.log`。
- 前端目录 `pnpm quality`：通过，156 文件 / 750 测试通过，ESLint、TypeScript、生产构建通过。日志 `/tmp/jiugeng-release-frontend.log`。保留现有 PDF chunk 大小与混合导入警告，非构建错误。
- 前端目录 `pnpm exec playwright test`：7 项全部通过，包含既有应用启动冒烟、产品列表与图片预览三尺寸、库存/样品弹窗三尺寸。尺寸 1280×800、800×600、360×800；纯合成 API fixture，不写真实业务数据。日志 `/tmp/jiugeng-release-browser.log`，截图在忽略目录 `frontend/test-results/`，已抽查手机样品表单、窄屏库存和桌面样品详情。
- `pnpm exec eslint e2e/catalog-closeout.smoke.ts --max-warnings=0`、`pnpm exec vue-tsc --noEmit` 局部补充通过；最终完整 quality 覆盖随后表单标签修正。
- 根目录 `git diff --check` 与维护文档相对路径检查通过；导入契约和生产工具未修改，本地未重复运行其门禁，PR/Production 自带相应检查。
- 统一验证首次发现并修复：历史供应商保留逻辑意外放宽辅料分类校验，现限定到产品关系，辅料校验维持原规则；更新旧权限/分页/预览/UI 测试预期与选择器、原生 DOM 可见性测试挂载；删除用例使用数据库实际支持的 ACTIVE/DISABLED 子体状态（V5 不支持 DRAFT），仍验证无状态豁免的父体删除检查。
- 浏览器实查补齐样品表单两种上传的可见标题（样品图片/存放照片），避免 compact 模式两块上传无法区分；名称必填标识保持同一行。修正后完整前端门禁及全部浏览器用例重跑通过。

## 数据库与本地运行事实

- 新增生产待应用迁移 V98/V99/V100/V101，分别为产品权限依赖、产品库存、样品逻辑移除/快照/幂等表、样品权限依赖。权限迁移会更新授权版本，已有账号可能需要重新登录。
- 本地 mysql 已实际应用至 V101；2026-11-21 00:41 的备份 `.local/backups/samples-before-v100-v101-20261121-004119.sql`（600 权限），旧启动脚本和 JAR 均保留。备份不是生产备份。
- 当前本地后端仍运行 `.local/releases/backend/samples-closeout-20261121/backend.jar`，SHA-256 `28d1cb8be61ea2ea0af5a68d005a6aef0f7c3a9632dd63648f4ee242990ce12d`；它早于本次统一验证时的 SupplierService 限定修正。不能把运行 JAR 当作最终提交构建。最终线上包必须来自 CI 测试的精确 SHA。
- 本地前端 Vite 热更新；用户长期授权按开发需要自主重启本地持久服务。MySQL/上传文件不因应用重启清空，不得将此授权扩大为清库。
- 发布脚本停服后备份生产数据库与配置，迁移后失败不会自动回退数据库；需按现有流程保留备份并核实 V101、健康与运行版本，禁止盲目回退 schema。

## 仍需明确的边界

- 自动化覆盖核心权限、校验、失败、恢复、幂等、分页与导出等场景；未声称穷尽全系统业务验收或高并发压测。
- 代表性生产数据量的 EXPLAIN、冷/热图片耗时与提速百分比未测；实际标签打印机与全部特殊长名称打印未人工验收。仅保留优化实现，不编造性能数值。
- 历史样品未留存图片的记录不会伪造旧图；外部图片 URL 不保证外站内容永久不变；升级前硬删除的样品不可恢复。样品在库登记不等于可销售库存数量。
- 没有修改生产凭据、发布门禁、分支保护、域名/网络或用户业务数据。

## 项目经理执行与回报

1. 核对当前 status/diff 和上述门禁，必要修正需对应检查；已有通过结果在代码未变时可复用。
2. 提交全部累计授权改动（可按可审阅单元原子提交），推送本分支并建 PR，等待所有必需检查，通过后合入 main；记录完整功能 SHA、PR URL、最终 main SHA。
3. 跟踪 main 对应 Production 工作流，必须确认实际 restricted SSH deploy 步骤成功，不能把 skipped 的绿色运行视为部署完成。保留既有服务器端门禁；出现阻断反馈具体失败，不自行绕过保护。
4. 核实用户实际线上入口 `/version.json` 与 main SHA 一致，核对后端健康、迁移版本/能力与必要只读页面；生产变更历史记录到状态报告及发布报告。
5. 最终回报 PR、精确 main SHA、CI/Production 运行链接、实际部署及入口版本证据、数据库迁移/备份、剩余风险与工作区状态。不要只回复计划。

本文件是获授权的交接，尚非已合并或已部署声明；PJM 独立核验后更新最终状态。
