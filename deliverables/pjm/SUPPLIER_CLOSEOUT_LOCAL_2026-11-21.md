# 供应商管理收尾（当前累计迭代追加）

> 2026-11-23 用户已授权提交远端 main 并部署，统一验证与正式交接见 [DEL-046](OPERATIONS_RELEASE_HANDOFF_2026-11-23.md)。下文保留开发阶段快照；移交后写入权与发布归 PJM，Codex 不跟踪线上。

用户明确批准评审第 1–20 项，排除 P3 第 21–23 项（供应商合并、多联系人及合作资料、表现统计）。本任务由用户直接追加到尚未交接的本地迭代，沿用 ERP-100 工作区与 `feature/ERP-100-material-closeout`，不冒用新的 PJM 任务号，不切分支、不提交、不推送。现有辅料改动归本会话 Codex，全部保留；独占写入继续有效。基线 `f87d7c98b9a32a1c73a4441abcfb8caad5f668d3`。

## 实施计划

1. 后端：保存请求幂等、版本冲突、状态/分类影响确认、字段规则修正、关联分页/历史/重复提示/按权限导出、独立复制联系图片。
2. 前端：服务端分页、应用筛选与状态恢复、紧凑列表及编辑、统一只读详情、关闭保护、原请求恢复、冲突对照、快捷复制、连续新增。
3. 复用产品/辅料的供应商详情入口；删除检查复用分页关联，保留权限和历史保护。
4. 必要局部编译/关键行为检查；完整门禁、回归、多视口留至用户要求交接。迁移前备份持久 MySQL、保留旧包和文件，再更新本地服务。

## 当前状态（2026-11-21）

第 1–20 项已本地实现，P3 未实施。辅料与供应商改动保持同一累计迭代，未提交、未推送、未发布。完整质量门禁、业务回归与多视口仍按用户约定在交接节点统一执行。工作区仅包含本会话累计改动及配套文档；没有暂存其他任务文件。

## 批准项与实现映射

| 项 | 实施内容 |
| --- | --- |
| 1 列表分页 | 复用后端分页，默认 20、可选 10/20/50/100，固定底部分页与总数，不再截取前 100 条。 |
| 2 草稿/上传保护 | AccessDialog 未保存确认，上传/保存期间禁止关闭；路由/浏览器离开保护，卸载后旧上传响应不回写表单。 |
| 3 请求幂等 | V104 用户隔离请求表，服务端同用户串行校验请求编号和载荷，原请求重试不重复创建；前端按用户保存待确认请求。 |
| 4 版本冲突 | 展示草稿与最新字段、分类/状态及图片；支持采用最新或保留草稿更新版本，再次核对保存。 |
| 5 筛选一致性 | 保存/停用/启用后重查已应用条件；保留页码并处理末页空白，不符合条件时明确提示。 |
| 6 影响提示 | 停用/减少分类前显示产品、辅料、采购关联及采购草稿影响；新管理接口要求显式确认，保留历史关系。 |
| 7 校验一致性 | 新建固定启用；备注允许正常换行；联系方式至少一项、分类必选、1688 HTTPS 链接提前提示。原电话格式规则不变。 |
| 8 请求与异常 | 列表/详情/关联请求使用响应序号；区分失败与空结果，刷新失败明确旧数据。编辑完整资料失败禁用保存。 |
| 9 统一详情 | SupplierDetailDialog 共用基础、关联、历史；主列表、产品编辑/子体采购关联、辅料详情/编辑入口复用。 |
| 10 紧凑列表 | 图片、名称/分类、联系人/电话、微信/来源、状态、操作组合列；备注按行展开，长名称可换行。 |
| 11 行操作 | 常驻详情/编辑，更多菜单包含复制、停用或启用、删除，继续分别校验维护/删除权限。 |
| 12 紧凑编辑 | 两列资料、紧凑上传、两行备注，固定标题底栏，正文滚动；窄屏单列。 |
| 13 筛选搜索 | 产品/辅料分类、状态、关键词，重置及生效标签；搜索支持联系人并转义 SQL LIKE 的百分号/下划线。 |
| 14 状态恢复 | 按用户保存已应用筛选、页码、每页数量与滚动位置。 |
| 15 快捷联系 | 电话拨号及复制、微信复制、采购来源入口；复制成功轻提示，失败允许手动选择文字。 |
| 16 日常关联入口 | 独立读取权限即可查看供应商详情，关联页签按产品/辅料/采购查看权限过滤；不要求删除权限，不扩大其他模块维护权限。 |
| 17 关联分页 | SQL 分类分页与独立影响计数，删除检查也复用；不再一次加载全部关联。受限关联仅展示数量，不泄露名称/ID。 |
| 18 历史 | 补联系人、备注、采购来源审计，电话/微信继续脱敏；分页显示时间、操作人、中文动作和前后值。历史旧事件不回填伪造字段。 |
| 19 重复/复制/连续新增 | 名称/电话/微信精确匹配提示（最多 10 条，可打开详情）；允许确认后继续。复制不带业务关联、需核对名称和联系方式，受控联系图片独立复制；连续新增只保留分类。 |
| 20 图片/导出 | 联系图片复用受控懒加载和统一预览，外链加载失败可重试；筛选全部或跨页勾选 CSV（选中最多 100，筛选最多 10000），读取权限约束、公式转义。 |

## 代码地图与兼容边界

- 前端：`views/SuppliersView.vue`、`api/supplierManagement.ts`、`styles/supplierManagement.css`；共享 `SupplierEditor`、`SupplierDetailDialog`、`SupplierRelations`、`SupplierImpactSummary`、`SupplierContactValue`、`SupplierImage`、`SupplierDeleteDialog`。
- `MaterialSupplierDetail` 保留兼容壳，内部使用统一详情；`ProductForm`/`SkuDetailDialog` 增加供应商名称详情入口。辅料主页面支持 `viewMaterial` 只读深链，不把关联查看强制变成编辑。
- 后端：`supplier/SupplierManagementController` 提供 `/api/v1/suppliers/manage` 能力、保存、状态、影响、关联分页、历史、重复查询及导出；原 `SupplierRepository` 搜索及分页复用，`SupplierService` 修正备注与审计。
- `CatalogImageGateway.copyForSupplier` 为公共图片边界，独立复制存储，事务回滚清理复制文件；不会与来源供应商共享可释放的图片资产。
- 旧供应商 API 保留兼容。幂等与影响确认保护适用于新管理保存/状态接口；不声称全部旧调用已要求请求编号或确认字段。旧删除仍有版本及数据库引用保护。
- 列表通过能力接口禁用旧后端写入。mock 模式只显示演示不可维护说明，真实管理使用 password/MySQL。
- 不新增供应商合并、多联系人、交期/结算资料或表现评分。

## 已执行的必要局部检查

| 命令 / 范围 | 结果 |
| --- | --- |
| 后端 `./mvnw --batch-mode --no-transfer-progress -Dtest=SupplierCloseoutTest,SupplierControllerTest test` | 14 项通过：新增 5 项及既有 9 项；覆盖原请求/载荷、版本、影响确认、备注换行、分页/搜索/CSV、权限及独立图片复制。 |
| 后端 `./mvnw --batch-mode --no-transfer-progress -DskipTests package` | 成功，用于更新本地运行包，非完整门禁。 |
| 前端 `pnpm exec vitest run src/views/SuppliersView.test.ts src/components/SupplierEditor.test.ts src/components/SupplierDeleteDialog.test.ts` | 3 文件 / 11 项通过；覆盖加载保护、未保存确认、原请求重试、冲突、影响确认、列表刷新、旧后端禁写、受限引用/失败禁删与历史解除确认。 |
| 前端 `pnpm exec vue-tsc --noEmit` | 通过。 |
| 前端 `pnpm exec eslint <下列本轮 Vue/TS 文件> --fix` | 通过，仅格式化本轮文件。 |
| `git diff --check` 及文档链接检查 | 通过。 |

ESLint 精确文件参数（相对 frontend）：`src/views/SuppliersView.vue src/views/SuppliersView.test.ts src/components/SupplierEditor.vue src/components/SupplierEditor.test.ts src/components/SupplierDetailDialog.vue src/components/SupplierDeleteDialog.vue src/components/SupplierDeleteDialog.test.ts src/components/SupplierRelations.vue src/components/SupplierImpactSummary.vue src/components/SupplierImage.vue src/components/SupplierContactValue.vue src/components/MaterialSupplierDetail.vue src/components/ProductForm.vue src/components/SkuDetailDialog.vue src/views/MaterialsManagement.vue src/utils/productHistory.ts src/api/supplierManagement.ts`。

Safari 本地供应商页已成功读取列表、显示分页及可用新增入口；打开空白新增弹窗检查实际布局后关闭，无业务写入。未执行完整多视口、全部角色矩阵、全链路采购/产品/辅料回归或规模性能测量。

## 本地服务与数据

- V104 前备份：`.local/backups/suppliers-before-v104-20261121-215003.sql`，18,289,166 字节，权限 600，保存在忽略目录；没有读取/提交备份内容。
- 保留旧包与脚本：`.local/runtime/run-backend.before-suppliers-20261121-215003.sh`。
- 最新包：`.local/releases/backend/suppliers-closeout-20261121-215003/backend.jar`；SHA-256 `d046a163dac9e02b9bad36e17610fe8c9f3a6b8b075fbf66bc2cf8f0b9072003`。
- 沿用 tmux `jiugeng-mvp` 原后端 pane、既有 MySQL 与上传目录；V104 success=1，后端 `/actuator/health` 返回 UP。前端 Vite 热更新，新能力接口已由实际页面验证。

## 交接时统一验证

按用户指令在交接节点统一执行后端 `./mvnw clean verify`、前端 `pnpm quality`，累计覆盖辅料与供应商变更。重点追加：产品/辅料供应商详情入口、旧兼容 API、采购草稿与分类变化、复制/恢复请求、每页条数和返回位置、不同权限角色以及桌面/窄屏/手机布局。局部通过不等于整体验收。

保持当前未提交状态，等待用户追加反馈或明确交接指令。由 PJM 处理后续提交/合并/部署，Codex 不轮询、不等待发布结果。
