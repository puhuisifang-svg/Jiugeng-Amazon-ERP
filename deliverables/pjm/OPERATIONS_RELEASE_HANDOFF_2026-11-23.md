# 辅料、供应商、采购累计迭代发布交接（2026-11-23）

登记 DEL-046。用户明确指令：“与项目经理交接，把修改内容提交远端main，并部署线上。”接收人：九更项目经理 `jiugeng_pjm`。此指令已授权本轮提交、合并和部署，不需要重复请求批准；继续遵守受保护 main 的 PR/CI 流程，不绕过门禁。

## 写入权与基线

- 任务：ERP-100 辅料管理及用户同迭代追加的供应商、采购与样品 UI；追加内容未另冒用任务编号。
- 分支：`feature/ERP-100-material-closeout`；HEAD：`f87d7c98b9a32a1c73a4441abcfb8caad5f668d3`。
- 修改尚未提交、暂存区为空，没有本轮提交 SHA。当前 status 包含同一 Codex 累计修改与验证修复，未发现其他任务或其他代理修改。保留整个工作区，不 reset/stash/clean。
- 正式发送此交接后，Codex 停止编辑、测试、切分支和服务操作，唯一写入权移交 PJM。提交/合并、CI/CD、部署与发布状态确认由 PJM 独立完成；Codex 不轮询、不等待线上发布。

## 发布范围与文件地图

| 范围 | 核心文件与行为 |
| --- | --- |
| 辅料 1–22 | `MaterialManagementController`、`MaterialQuantity`、Inventory/Material 服务与 V102/V103；`MaterialsManagement`、`MaterialEditor/DetailDialog/InventoryDialog`、`api/materialManagement`、`materialManagement.css`。聚合保存、原请求/版本保护、单位锁定与数量精度、SQL 分页、库存/安全库存、复制导出、紧凑关联资料。见 [DEL-043](ERP-100_MATERIAL_CLOSEOUT.md)。 |
| 供应商 1–20 | `SupplierManagementController`、Supplier 服务与 V104；`SuppliersView`、`SupplierEditor/DetailDialog/Relations/ImpactSummary`、`api/supplierManagement`、`supplierManagement.css`。分页、影响确认、原请求、冲突、复制、联系图片、历史、CSV；产品与辅料入口共用详情。见 [DEL-044](SUPPLIER_CLOSEOUT_LOCAL_2026-11-21.md)。 |
| 采购 1–22、24 | `PurchaseManagementController`、`PurchaseCloseout`、Purchase 服务/仓储、`ProductPurchaseInventory`、V105/V106；`PurchasesView`、`PurchaseOrderDetailView`、`DashboardPurchaseReceipts`、共用 ReceiveFlow/Picker/Shipments/RelatedItem、`purchasePending`、金额/导出与 CSS。新产品收货入库存、多批次/运单、快照、幂等、分页筛选、复制导出。见 [DEL-045](PURCHASE_CLOSEOUT_LOCAL_2026-11-22.md)。 |
| 样品操作 UI | `main.css`、`sampleManagement.css` 复用产品更多按钮样式；`RowMoreActions` 防止菜单聚焦造成自动滚动和即时关闭。 |
| 交接维护 | AGENTS/README 明确发布归 PJM；状态报告、接手指南、UI 规范、索引与总账；定向用例与三尺寸 e2e。 |

明确排除：辅料 P3 第 23/24；供应商 P3 第 21–23；采购第 23（收货纠错/退货）、25（结算/表现统计）。这轮不扩展这些范围。

## 统一验证结果

- 后端工程 `./mvnw --batch-mode --no-transfer-progress clean verify`：通过，430 项，0 失败/错误，1 跳过（未配置外部 OSS 探测）。日志 `/tmp/jiugeng-20261123-backend-final.log`。随后只增加采购无效复制参数回归用例，执行 `./mvnw --batch-mode --no-transfer-progress -Dtest=PurchaseControllerTest test`：26 项通过；生产代码与已通过全量结果一致。日志 `/tmp/jiugeng-20261123-purchase-extra.log`。
- frontend `pnpm quality`：通过，163 文件、748 项测试，ESLint/TypeScript/生产构建均通过。日志 `/tmp/jiugeng-20261123-frontend-passed.log`；保留 PDF 模块混合导入与 chunk 大小警告，非失败。
- 浏览器矩阵：frontend `pnpm exec playwright test` 10 项全部通过，日志 `/tmp/jiugeng-20261123-browser-passed.log`。`e2e/operations-closeout.smoke.ts` 覆盖辅料编辑/库存、供应商编辑、采购编辑/收货/多运单；`catalog-closeout` 增加样品更多菜单与 Escape 焦点恢复；既有产品列表、图片与启动冒烟保留。尺寸 1280×800、800×600、360×800，使用合成 API fixture，不写业务库。
- 最后菜单定位/滚动修复后 `pnpm run build`（含 vue-tsc）通过；`pnpm exec eslint src/components/RowMoreActions.vue src/components/RowMoreActions.test.ts e2e/catalog-closeout.smoke.ts e2e/operations-closeout.smoke.ts --max-warnings=0` 与 `pnpm exec vitest run src/components/RowMoreActions.test.ts` 3 项通过，复用其余未受影响的完整 quality 结果。
- 根目录 `git diff --check`、本轮新增文档入口链接与最终工作区检查通过，暂存区为空。导入契约目录和生产脚本未变，本地未重复跑其独立门禁；后端完整门禁包含真实导入控制器集成用例。

统一验证发现并修正：

1. 计数单位整数规则未同步期初库存导入预检查，导致小数数量预检通过而提交失败。现在预检查同时核对期初量和安全库存；旧正向用例改用 KG 验证小数，新增 PCS 小数拒绝用例，保留事务不写入断言。
2. 角色迁移回归预期补入已批准的 `materials.delete` 拆分授权，不放宽其他权限断言。
3. 首页采购测试改为新分页接口 fixture；不恢复已替换的全量订单加载。
4. 手工采购复制缺少明细或包含 null 时返回字段校验错误，避免空指针 500。
5. 样品更多菜单在 800px 低高度视口聚焦时自动滚动导致关闭；先完成视口内定位再聚焦并采用 preventScroll；滚动事件仅在触发按钮实际移动时关闭，菜单内部及已完成定位的排队事件不误关。保留外部点击与 Escape 焦点恢复。

## 数据与运行边界

- 发布新增迁移 V102–V106；权限回填会更新授权版本，原会话可能需重新登录。按现有生产发布脚本备份并迁移，不绕过已有门禁，不能用本地备份代替生产备份。
- 本地 mysql 已应用 V105/V106，最后核实成功；运行包仍为 `.local/releases/backend/purchases-closeout-20261122-192928/backend.jar`，SHA-256 `f88b109b6283abed404915b43b2a85959d607ea2790b0d207810e09335526d33`。此包早于本次期初导入/采购空明细修复，不能作为最终线上包；部署包应来自 CI 已验证的精确 SHA。
- 本地迁移前备份 `.local/backups/purchases-before-v105-20261122-192928.sql`，18,524,094 字节、600 权限；上一脚本及更早辅料/供应商包仍保留。禁止提交备份、凭据、上传文件或忽略目录。
- 产品只对新登记收货入账，历史产品收货不自动补库存；新单件数与旧单位留存不做自动换算。
- 已有批次/收货的采购不可编辑明细或删除；批次本轮仅新增、查看和关联收货，不提供修改数量/撤销/退货。
- 受控图片独立复制；外链只保存 URL，不保证外站内容永久不变。停用档案保留历史资料，新增/增加采购仍校验当前供货关系。
- 历史计数单位若已有不合规小数库存，不在迁移中自动改账；依库存校验核对处理，不能静默取整。
- 未修改生产凭据、分支保护、发布脚本或真实业务资料。自动化不等于穷尽人工业务验收；无真实规模压测或外部 OSS 实测结论。

## PJM 执行要求

1. 核对 status/diff、上述门禁及待验边界；复用有效结果，后续修改只重跑受影响检查。
2. 原子提交累计授权内容，推送当前分支并按保护规则建 PR，所有必需检查通过后合入远端 main；记录提交 SHA、PR URL、main SHA。
3. 由 PJM 跟踪 main 对应自动 Production 发布，确认实际部署步骤与版本、健康、迁移；如自动部署被跳过，按已有授权及流程处理，不将绿色但 skipped 当成已部署。
4. PJM 直接向用户回报提交与线上结果，并更新状态报告。Codex 正式移交后不继续追踪发布。

这是执行交接记录，不是已合并、已部署或业务验收完成声明。
