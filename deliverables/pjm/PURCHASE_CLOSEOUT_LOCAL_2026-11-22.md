# 采购管理收尾：本地实施与待验记录

> 2026-11-23 用户已授权提交远端 main 并部署，统一验证与正式交接见 [DEL-046](OPERATIONS_RELEASE_HANDOFF_2026-11-23.md)。下文保留开发阶段快照；移交后写入权与发布归 PJM，Codex 不跟踪线上。

日期：2026-11-22。状态：本地已实现，待累计迭代统一验收，未提交、未推送、未发布。

## 授权与工作区

用户批准清单第 1–22 项及 P3 第 24 项；明确排除第 23 项（收货纠错/退货）和第 25 项（结算/供应商表现统计）。继续累计辅料、供应商改动；Codex 为唯一写入者，没有新建或冒用 PJM 任务编号。分支 `feature/ERP-100-material-closeout`，基线 `f87d7c98b9a32a1c73a4441abcfb8caad5f668d3`，无本轮提交 SHA。当前工作区包含同一用户授权累计迭代的辅料、供应商、采购及文档修改，不覆盖前两模块。发布与发布状态交 PJM，本轮不联系 PJM、不轮询线上。

## 实施映射

| 项 | 本地实现 |
| --- | --- |
| 1 | 产品数量整数；辅料计数单位整数、测量单位最多四位；日常物品最多四位。收货和库存共用单位口径。 |
| 2 | 新登记产品收货与产品入库同事务，库存流水关联收货；不为历史产品收货补账。 |
| 3 | 保存请求按用户/请求号去重，参数摘要不符拒绝；建单与提交原子完成。 |
| 4 | 编辑、收货、物流的未保存保护，上传/保存锁定，关闭及离开确认。 |
| 5 | 首页与详情共用 PurchaseReceiveFlow；结果不明保留原请求，按用户隔离存储，刷新后可恢复。 |
| 6 | 保存冲突展示最新资料与草稿，显式选择采用最新或保留草稿；物流/发货批次刷新版本后重试。 |
| 7 | 单价完整四位；行金额用十进制整数运算舍入；成本超出精度时提示人工核对，不静默截断。 |
| 8 | 下单留存供应商名称、品名、规格、单位、受控图片独立副本；外部图片仅保存 URL。旧订单明确标注未留图。 |
| 9 | 旧单不被停用档案阻断原约定收货；新增/增加数量仍校验供应商启用、分类及关联；单位变化提示并阻止错误入库。 |
| 10 | 采购查看补齐商品/辅料/供应商/库存查看依赖；既有角色 V106 回填。建单/草稿下单要求 submit，已下单编辑要求 write，不附带手动库存写权限。 |
| 11 | 列表 SQL 分页、当前页批量明细；首页待收采购同样服务端分页。 |
| 12 | 紧凑列表，前两项摘要及展开；48px 列表缩略图，数量与单位同列，次级资料用文字链接。 |
| 13 | 详情/收货常驻，复制/编辑等更多菜单；批量勾选后逐单登记，避免混合单位汇总。 |
| 14 | 交期逾期只针对未收齐状态；已收项数与逐项数量分开表达。 |
| 15 | 状态、关键词、供应商、创建/交期范围、逾期/缺交期/缺物流筛选；应用条件标签与会话记忆。 |
| 16 | 新增/编辑共用紧凑弹窗，固定标题底栏、两列字段、表格明细，失败保留草稿。 |
| 17 | 供应商/项目搜索分页选择，价格标明同币种历史或标准成本来源，单位不符不带入。 |
| 18 | 共用供应商、SKU、辅料详情及只读库存入口；历史留存名称不显示内部 UUID。 |
| 19 | 收货/历史按需分页，中文前后值；收货可查看库存流水并显示对应物流、运单号。 |
| 20 | 原物流资料支持图片、复制、加载重试及只读展示；保留版本冲突保护。 |
| 21 | 复制采购重新核对供应商与项目；清空单据身份、收货、物流、旧交期；受控图片独立留存。 |
| 22 | CSV 按筛选或选择导出、公式转义；图片导出数量限制、ZIP/分页和取消；异步过期响应不覆盖当前查询。 |
| 24 | 多个发货批次/运单、发货日期、包裹数及分项数量；收货关联批次并校验批次剩余。 |

## 代码定位与关键边界

后端集中在 `purchase/PurchaseManagementController`、`PurchaseCloseout`、`PurchaseService/Repository/Types/Controller`；公开跨模块接口 `product/ProductPurchaseInventory`、`materialinventory/InventoryInboundGateway`、`catalogmedia/CatalogImageGateway.copyForPurchase`。供应商影响摘要增加未收采购提示。V105 添加快照、保存去重、发货批次及收货批次关联；V106 补全既有角色依赖。

前端集中在 `PurchasesView`、`PurchaseOrderDetailView`、`DashboardPurchaseReceipts`、`api/purchaseManagement.ts`；共用 `PurchasePicker`、`PurchaseReceiveFlow`、`PurchaseShipmentsDialog`、`PurchaseRelatedItem`；保留 `PurchaseLogisticsDialog` 兼容历史单一物流。`ui/purchasePending.ts` 保存原请求，`purchaseAmounts.ts` 处理金额；样式限定 `.purchase-ui`。

- 旧 API 保留兼容；新的保存请求恢复通过 `/manage/save` 使用，不能宣称旧客户端全部具有新幂等协议。
- 产品新单单位为“件”，旧单原单位留存，不进行套/件自动换算。新收货才入产品库存，禁止迁移补历史库存。
- 已发货或已收货订单不能改采购明细/删除；已有批次时收货必须选择批次。发货批次本轮提供新增、查看和收货关联，不提供已登记批次数量修改、撤销、退货或收货纠错。
- CSV 上限 2000 单/20000 行，图片选单上限 100 单/1000 行；超过限制明确报错，不静默截断。
- 角色依赖是查看能力，不是附带库存调整、成本写入或其他业务管理权限。

## 已执行局部验证

以下均为本轮增量检查，不代表完整迭代验收：

- 后端目录：`./mvnw --batch-mode --no-transfer-progress -Dtest=PurchaseControllerTest,SupplierCloseoutTest test`：30 项通过（采购 25、供应商 5）。最后调整停用供应商增量校验后重跑 `-Dtest=PurchaseControllerTest test`：25 项通过。覆盖产品库存同事务、原请求、版本、精度、停用档案收货、发货超量/超收、分页搜索/CSV、权限及独立快照图片。
- 前端目录：`pnpm exec vitest run src/views/PurchasesView.test.ts src/views/PurchaseOrderDetailView.test.ts src/components/PurchaseReceiveFlow.test.ts src/components/PurchaseShipmentsDialog.test.ts src/components/DashboardPurchaseReceipts.test.ts src/components/PurchaseLogisticsDialog.test.ts src/ui/purchaseAmounts.test.ts src/ui/purchaseImageExport.test.ts`：8 文件/28 项通过。随后补收货运单显示用例，重跑前两文件：13 项通过，现有采购局部用例合计 29 项。
- `pnpm exec vue-tsc --noEmit`、受影响文件定向 ESLint：通过；后端 `./mvnw --batch-mode --no-transfer-progress -DskipTests package`：通过。
- Safari 本地只读核对：现有采购记录读取、新建空白弹窗、明细表头与数量/单位对齐、关闭按钮及 Escape；未提交业务测试数据。页面检查发现的表头列错位已修正。不是完整多视口验收。
- `git diff --check`：通过；文档新增入口链接检查通过。最后表头对齐修正后再次通过 PurchasesView 定向 ESLint 和 vue-tsc。未执行全量后端 `clean verify`、前端 `pnpm quality`、全业务回归、性能基准和多视口矩阵；依用户约定在交接节点统一执行。

## 当前本地运行证据

2026-11-22 19:29 后已在原 tmux `jiugeng-mvp` 后端 pane 更新运行包，前端 Vite 热更新。MySQL 与上传文件保留；健康接口 `UP`；Flyway V105/V106 均 `success=1`。

- 迁移前 MySQL 备份：`.local/backups/purchases-before-v105-20261122-192928.sql`，18,524,094 字节，权限 600，禁止提交该文件。
- 上一启动脚本：`.local/runtime/run-backend.before-purchases-20261122-192928.sh`。
- 当前包：`.local/releases/backend/purchases-closeout-20261122-192928/backend.jar`。
- SHA-256：`f88b109b6283abed404915b43b2a85959d607ea2790b0d207810e09335526d33`。
- 上述运行记录取代 DEL-044 的供应商阶段运行包；后者仍保留历史备份证据。不代表线上版本。

## 后续接手

用户要求交接时，先核对整个累计工作区归属与最终 diff，统一执行受影响完整门禁、权限/并发/异常路径、多视口及业务回归，更新本记录。PJM 接收后再按其流程提交/合并/部署；Codex 不跟踪线上发布。第 23/25 项继续排除，除非用户另行授权。
