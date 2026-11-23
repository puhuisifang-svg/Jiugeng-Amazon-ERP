# ERP-100 辅料管理收尾

> 2026-11-23 用户已授权提交远端 main 并部署，统一验证与正式交接见 [DEL-046](OPERATIONS_RELEASE_HANDOFF_2026-11-23.md)。下文保留开发阶段快照；移交后写入权与发布归 PJM，Codex 不跟踪线上。

用户 2026-11-21 授权评审清单 1–22；排除 23（采购建议/报价管理）、24（库位扫码盘点）。

PJM 明确移交唯一写入权给 Codex，基线 `f87d7c98b9a32a1c73a4441abcfb8caad5f668d3`，分支 `feature/ERP-100-material-closeout`。当前仅本地开发，不提交、不推送、不发布；完整门禁在用户要求交接时统一执行。交接后 Codex 不轮询/等待发布，PJM 独立负责后续流程。该用户约定覆盖基线文档中旧的发布跟踪要求。

## 实施计划

1. 后端：单位引用保护、版本锁、资料/供应商/成本原子保存、用户隔离请求幂等、历史供应商、权限回填、完整审计。
2. 查询：SQL 分页、批量余额成本、条件筛选/导出、详情与影响预检、分页关系/流水/历史。
3. 前端：紧凑详情/编辑/库存、未保存与待确认保护、安全库存、复制连续新增、状态恢复、图片重试与六位成本精度。
4. 计量：测量单位最多四位小数，计数单位整数；包装换算显式输入，最终按基础单位记账，不变更历史单位。
5. 维护关键行为用例；必要编译/局部检查用于确认跨层接口，完整质量/回归/多视口留待交接。

## 当前状态（2026-11-21）

1–22 已本地实现，保持未提交；完整质量门禁、业务回归与多视口验收待用户要求交接时统一执行。23、24 未开发。当前唯一写入权仍为 Codex，工作区变更均属于本任务及配套文档，不得并行切分支、提交或修改。

## 实施映射

| 项 | 当前实现 |
| --- | --- |
| 1 基础单位 | 已有产品/采购引用、库存账或成本的辅料锁定单位；普通编辑与 Java 导入提交均校验，关联新增和库存操作锁定主档。包装换算不修改主档单位。 |
| 2 库存重试 | 保存原始请求编号与精确内容，异常结果锁定表单；按用户/辅料隔离恢复入口，重试原操作。 |
| 3 聚合保存 | 主资料、供应商、可选核定成本单事务保存；失败整体回滚，新增/编辑按用户请求编号幂等。 |
| 4 加载保护 | 完整编辑资料加载成功前禁止保存；加载失败保留明确错误与重试，不能把未知关系当空数组覆盖。 |
| 5 历史供应商 | 已停用或不再属于辅料类别的既有关联可以保留；新增关联仍须启用且类别有效。复制时明确列出未带入的历史供应商。 |
| 6 并发保护 | 新聚合编辑、状态切换与批量删除校验主档版本；冲突显示草稿/最新资料，包括图片、成本和供应商，用户选择后再次保存。 |
| 7 关闭保护 | AccessDialog 未保存确认，保存/上传期间锁定关闭；路由离开及浏览器关闭保护，待确认请求可稍后恢复。 |
| 8 权限闭环 | 辅料读取依赖供应商/库存读取，维护依赖供应商维护；删除单独 materials.delete，库存入出库/成本保持独立授权。V103 为既有辅料维护角色回填其原删除能力。采购角色可独立勾选，不再要求辅料维护权限。 |
| 9 库存语义 | 未建账、已建账零库存、加载失败分别展示；读取失败不可据此记账，刷新失败标注旧列表结果。 |
| 10 查询性能 | 列表 SQL 分页，当前页一次关联库存/成本；供应商延迟分页搜索，详情供应商一次联表，流水/历史/产品关系分别分页。 |
| 11 只读详情 | 紧凑基础资料、供应商、关联产品、变更记录页签；根据权限提供编辑与流水入口。 |
| 12 库存 UI | 辅料摘要、四种操作、精度校验、预计结存、原因、流水在一个紧凑弹窗内；空流水不撑满屏。 |
| 13 列表 | 图片/品名/规格/成本/库存分层，低库存文字提示、数字对齐、更多操作，固定表头与底部分页。沿用原 48px 列表图。 |
| 14 供应商 | 紧凑行内主供/联系方式/采购链接/备注，名称可打开完整只读档案。 |
| 15 安全库存 | 按需编辑，可为未建账辅料创建零余额与阈值，遵守原独立库存权限。 |
| 16 库存到采购 | 每辅料独立流水；按最新余额及安全阈值差额带入采购，数量可人工确认修改。没有自动采购建议、报价或在途计算。 |
| 17 审计 | 主档全字段快照、供应商前后关联与独立图片复制审计；变更记录中文标签与前后值。库存流水仍按原追加规则。 |
| 18 删除/停用 | 显示产品、采购、流水与余额影响；存在业务/成本记录禁止删除，批量全量版本及引用校验后才删除，任一失败整体不删。 |
| 19 筛选导出 | 类型/状态/库存/供应商/图片/成本筛选，条件标签；当前筛选或勾选 CSV，按权限输出字段；按用户记忆筛选/分页/滚动。 |
| 20 复制连续新增 | 复制资料及有效供应商，受控图片独立存储，成本需显式选择并核定；连续新增清空身份、图片、供应商及成本，保留类型/单位。 |
| 21 精度图片 | 成本保留六位小数，不因编辑其他字段改变价格；受控图与外链失败均可重试，统一预览。 |
| 22 数量换算 | 测量单位最多四位小数，计数单位整数；包装数×基础单位系数显式输入，精确字符串提交，换算写入原因。 |

## 代码定位与兼容边界

- `frontend/src/views/MaterialsView.vue` 分流真实认证页面 `MaterialsManagement.vue` 与保留的 `MaterialsMockView.vue`；旧 mock 用例保留在 `MaterialsMockView.test.ts`。
- `frontend/src/api/materialManagement.ts` 对接 `/api/v1/materials/manage`；`MaterialEditor`、`MaterialDetailDialog`、`MaterialInventoryDialog`、`MaterialSupplierDetail`、`MaterialImage` 与 `styles/materialManagement.css` 管理界面。`ui/materialQuantity.ts` 提供精确换算与校验。
- 后端 `materialinventory/MaterialManagementController` 为新聚合/分页入口；`MaterialService/Repository/ImportService` 保护单位，`InventoryService/Repository` 实施计量与分页流水。`supplier/SupplierAssignmentGateway` 与 `catalogmedia/CatalogImageGateway` 保持跨模块公共边界。
- `PurchasesView.vue` 接收可选辅料数量参数；采购创建和审批流程未替换。
- V102 添加主档版本、幂等表和查询索引；V103 补全权限依赖。权限版本变更可能要求旧登录会话重新登录。
- 旧选择器及兼容 API 保留；严格客户端版本校验适用于新管理接口，旧主档 PUT 保持兼容，不能声称所有旧调用已要求 version。
- 本轮不提供采购推荐/报价管理，也不提供库位/扫码/盘点任务。数量换算只是单次人工记账工具。

## 已执行的必要局部验证

均为本地独立测试数据；未向持久业务库提交测试业务。

| 命令（在对应项目目录） | 结果 |
| --- | --- |
| 后端 `./mvnw --batch-mode --no-transfer-progress -Dtest=MaterialCloseoutTest,SupplierControllerTest test` | 15 项通过；含聚合回滚、原请求重试、版本冲突、单位/精度、权限导出、历史供应商、图片独立复制与批量删除保护。 |
| 后端 `./mvnw --batch-mode --no-transfer-progress -DskipTests package` | 成功；用于本地运行包，不是完整质量门禁。 |
| 前端 `pnpm exec vitest run src/views/MaterialsView.test.ts src/components/MaterialEditor.test.ts src/components/MaterialInventoryDialog.test.ts src/ui/materialQuantity.test.ts` | 4 文件 / 13 项通过；含加载写保护、稳定原请求、六位价格不误改、复制历史关系说明、采购选择和未知库存。 |
| 前端 `pnpm exec vue-tsc --noEmit` | 通过。 |
| 前端 `pnpm exec eslint <本任务改动的 Vue/TS 文件> --fix` | 通过；只格式化本任务文件。精确文件清单见下方。 |
| `git diff --check` | 通过。 |

ESLint 文件清单（均相对 frontend）：`src/api/materialManagement.ts src/components/MaterialEditor.vue src/components/MaterialEditor.test.ts src/components/MaterialInventoryDialog.vue src/components/MaterialDetailDialog.vue src/components/MaterialImage.vue src/components/MaterialSupplierDetail.vue src/components/SupplierContactLinks.vue src/views/MaterialsManagement.vue src/views/MaterialsView.vue src/views/MaterialsView.test.ts src/views/MaterialsMockView.vue src/views/MaterialsMockView.test.ts src/views/PurchasesView.vue src/ui/materialQuantity.ts src/ui/materialQuantity.test.ts src/components/MaterialInventoryDialog.test.ts src/utils/productHistory.ts`。

完整 `./mvnw clean verify`、`pnpm quality`、浏览器多视口、采购往返/权限角色矩阵和规模性能测量未执行，统一留至用户要求交接。不得将上述局部通过当作完整业务验收。Java 导入保护有变更，交接时纳入后端导入回归；导入契约文件本轮未改。

## 本地运行记录

- 迁移前备份：`.local/backups/materials-before-v102-v103-20261121-210336.sql`，18,287,396 字节，权限 600；保留原运行脚本与旧 JAR。备份包含本地业务数据，仅留在被忽略的本地目录，不进入提交。
- 本地 MySQL 已成功应用 V102/V103（success 均为 1）；重启没有清空 MySQL 或上传文件。
- 最新运行包：`.local/releases/backend/materials-closeout-20261121-211055/backend.jar`，SHA-256 `0d3c9ee33bc708598187b1490d5bb989fe5e22081698ed28657ca14746c12529`。
- 运行入口仍为 `.local/runtime/run-backend.sh`，tmux `jiugeng-mvp` 原 backend pane；Vite 原服务热更新前端。最终健康检查 `/actuator/health` 返回 UP，前端 `/materials` HTTP 200；运行脚本路径修正后已重新启动成功。
- Safari 本地辅料页已显示新列表、真实读取结果与可用新增入口；只做界面读取，未创建/修改业务记录。修正了共享样式导致关键词输入框过窄的问题。完整视觉验收仍待交接。

## 下一步与归属

等待用户本地体验或追加同迭代修改。收到交接指令后统一验证累计差异、更新本文证据并交 PJM；提交、合并、部署及发布状态归 PJM。Codex 不轮询 CI/CD、不等待或跟踪线上发布。没有新提交 SHA：HEAD 仍是基线，修改保持未暂存，当前工作区无其他任务变更。

## 同迭代供应商追加（2026-11-21）

用户随后批准供应商 1–20 项，范围和新运行包见 [DEL-044](SUPPLIER_CLOSEOUT_LOCAL_2026-11-21.md)。辅料变更全部保留，供应商改动共用部分图片/关系/详情文件，交接时必须按累计差异统一验证；本文此前运行包是阶段记录，当前本地已同步 V104。
