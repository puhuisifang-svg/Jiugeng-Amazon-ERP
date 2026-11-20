# 样品管理 17 项收尾：本地实施与待验记录

> 2026-11-21 统一验证及授权发布交接已完成准备，后续执行状态见 [DEL-042 发布交接](CATALOG_RELEASE_HANDOFF_2026-11-21.md) 和当前状态报告。下文保留此前本地开发阶段记录。

- 范围：用户批准 P1/P2 第 1–17 项；明确不做 P3 第 18–20 项（手动样品转 SKU、数量/借还、扫码盘点）。
- 任务依据：用户直接授权，同一尚未交接的本地累计迭代，无新 ERP 编号。登记号 DEL-041。
- 分支：`codex/project-handover-docs`；HEAD `b0a3d559fd7fd21ed5c0a70bfffed8eacfd4cc20`，不代表本轮未提交内容。
- 状态：本地代码已实现，完整验证待用户要求交接时统一执行。未提交、推送、合并、联系 PJM 发布或部署线上；保留先前产品管理与文档累计改动。

## 逐项实现

| 序号 | 本地实现 | 核心入口 |
| --- | --- | --- |
| 1 | samples.read 在权限目录依赖 products.read；V101 回填已有角色及传递只读依赖、更新授权版本；不授予维护/移除能力 | PermissionCatalog、V101、SamplePermissionDependencyTest |
| 2 | 样品分页使用 ProductLookup.samplePresentations 一次批量补齐图片/当前名称/状态，避免每行请求完整产品详情；受控缩略图复用缓存和懒加载，外链图片懒加载/失败重试 | ProductLookup、ProductLookupService、SampleImage |
| 3 | 登记名称/规格保留快照；当前产品名称和启停/删除状态独立展示。新增关联样品独立复制受控产品图片；历史记录未留图时明确标注当前产品图，下次编辑保存时留存 | SampleController、CatalogImageGateway/Service |
| 4 | 移除改为逻辑删除、保留图片；已移除列表、按原版本恢复；恢复需 samples.write 与 samples.remove，同 SKU/规范化库位冲突由数据库拒绝 | V100、SampleController、SamplesView |
| 5 | 样品详情按需分页读取审计历史；库位、名称、规格、备注、两种图片和移除状态前后对照，不展示内部图片 ID | SampleDetailDialog、sampleHistory |
| 6 | 新增/编辑支持按用户隔离的 Idempotency-Key，事务内锁定用户并保存请求摘要；结果不明时前端锁定输入、会话存储原请求、原编号重试；冲突可加载最新资料对照、显式选择继续使用草稿或采用最新资料 | sample_write_request、SamplesView |
| 7 | 编辑弹窗固定标题/底栏、正文单滚动；库位/规格并排，备注两行；两类上传区 compact 模式，窄屏转单列 | sampleManagement.css、SamplesView |
| 8 | 品名/规格主次层级、来源轻量标识、蓝灰库位、长备注展开、时间弱化；保持原图片尺寸和预览 | 同上 |
| 9 | 品名打开只读详情；双图、登记信息、当前关联产品与历史；按权限进入父体/子体详情，删除关联不生成失效跳转 | SampleDetailDialog |
| 10 | 操作列保留标签打印和更多菜单；编辑、复制、移除进入菜单，危险移除仍明确确认 | RowMoreActions 复用 |
| 11 | 来源/存放照片/独立库位筛选；关键词包含规格和备注；生效标签可逐个移除；保存应用筛选、页码和列表滚动 | samples.ts、SamplesView、SampleController |
| 12 | 已有库位前缀联想、最近使用排序；显式大小写/全角规范提示；重复时可查看已登记样品 | /locations、/duplicates |
| 13 | 保存并继续保留资料、清空库位和存放照片；复制到新库位时服务端独立复制样品图，存放照片不继承 | copyFromId、SamplesView |
| 14 | 跨页勾选，打印前重新读取选中记录；每条份数、逐条超长检测、合并 PDF/多页打印；最多 100 条 / 500 张，每条最多 100 份 | SampleLabelDialog、sampleLabel、sampleLabelPdf |
| 15 | 当前筛选全部结果或跨页勾选范围导出 UTF-8 BOM CSV；服务端查询，最多 10000 条全量或 500 条勾选，超过上限明确拒绝不截断；防 CSV 公式执行 | POST /export、SamplesView |
| 16 | 区分首次加载、刷新中、无匹配、失败和旧结果；失败时禁用旧列表写操作、提供重试；后端版本不兼容时禁用写入，保留旧查询/单条打印 | /capabilities、SamplesView |
| 17 | 无关键词时不生成 CONCAT/LIKE 条件；库位精确/前缀独立条件，按删除标识/父体/库位及时间补索引；批量身份读取，不再 N+1 产品详情 | V100、SampleController、ProductLookupService |

## 重要业务与兼容边界

- **旧后端不会安全执行新 UI 的移除语义。** 前端先确认 `/samples/capabilities` 的 closeoutVersion；不支持时禁用新增/编辑/移除/恢复/批量能力，不能移除兼容保护来“修复按钮”。旧列表和单条打印继续可用。
- 两个新增迁移必须随新后端应用：V100 增加删除标识、三元唯一约束、图片快照标识、查询索引与幂等表；V101 回填权限。已有 V58/V81/V85 不修改。
- 已移除登记的照片不释放，以保证恢复完整性；不会恢复在本次升级以前已被硬删除的样品。恢复不自动恢复已删除产品；详情保留关联状态提示。
- 样品受控主图独立复制文件，不复用可被源产品替换/清理的存储键；复制失败会回滚登记，事务回滚时清理复制文件。样品编辑替换图片仍按原机制释放旧图，历史表记录“更换”而不承诺可回看全部旧图。
- 历史样品原来没有图像快照，无法倒推出登记时的图片。明确显示当前产品图，下次编辑保存时才留存；没有伪造历史图。外部 URL 只留存地址，不抓取外部文件，无法承诺外部内容永久不变。
- 手动样品同名/同库位不新增强制唯一规则；请求幂等只防止同一次提交重试导致重复，不阻止合法多个实物登记。
- 复制登记不能改变来源，不实现 P3 的“手动转 SKU”。库位照片默认不复制；样品主图复制为独立资源。
- 样品库存数量和借还未实现；“在库登记”表示未移除的库位记录，不代表可销售库存或准确件数。CSV 是盘点资料清单。
- 新增/修改旧 API 仍接收无请求编号调用，以兼容现有调用方；前端新版本始终发送编号。幂等结果按照原记录 ID 返回当前记录，若记录后来被移除，会明确提示而不会重新创建。
- 历史服务以 1 为第一页，样品 API 以 0 为第一页，Controller 已做转换。不要直接传原 page 导致首页报错。
- 新增 API、历史和导出只读使用 samples.read；维护/移除/恢复分别执行服务端权限检查。权限回填可能使已有会话失效，需要重新登录。
- ProductLookup 是公开模块边界，样品不引用 product 内部类型或直接拼接产品表；图片留存通过 CatalogImageGateway。

## 已进行的必要局部检查

本轮未执行完整回归，以下检查针对新迁移、结果不明重试和图片复制等关键行为：

- 后端 `./mvnw -q -DskipTests test-compile`：通过。
- `./mvnw -q -Dtest=SampleControllerTest#softRemovalRestoreAndSafeRetryUseNewSchema test`：首次发现历史分页基准不一致；修正后 1 个用例通过。隔离 H2 测试库成功应用至 V101，覆盖幂等重试、请求冲突、移除/恢复、恢复重复库位冲突、筛选与历史查询。
- `./mvnw -q '-Dtest=SampleControllerTest#copiesImagesIndependentlyAndReturnsPresentationInOnePage+exportsProtectSpreadsheetCellsAndDoNotTruncateSelection' test`：2 个用例通过，覆盖复制文件独立性、列表图片信息与 CSV 公式保护/缺失勾选拒绝。
- `pnpm exec vitest run src/views/SamplesView.test.ts -t 'same request|old backend|source without|explicitly adopts'`：4 个用例通过，其余 13 个当时跳过；针对原请求重试、旧后端写保护、复制清空库位/存放图、版本冲突草稿保护。后续新增保存并继续用例，留待统一执行。
- `pnpm exec vue-tsc --noEmit`：通过。
- 限定 10 个本轮前端文件 ESLint（含自动格式化）通过。完整命令见下方。
- 后端 `./mvnw -q -DskipTests package`：通过，未运行完整测试。
- 根目录 `git diff --check`：通过；暂存区为空。

```bash
pnpm exec eslint src/api/samples.ts src/views/SamplesView.vue src/views/SamplesView.test.ts src/components/SampleDetailDialog.vue src/components/SampleImage.vue src/components/SampleLabelDialog.vue src/utils/sampleLabel.ts src/utils/sampleLabel.test.ts src/utils/sampleLabelPdf.ts src/utils/sampleHistory.ts --fix
```

## 本地运行版本（2026-11-21 已同步）

- 新 JAR 已准备在忽略目录 `.local/releases/backend/samples-closeout-20261121/backend.jar`。
- SHA-256：`28d1cb8be61ea2ea0af5a68d005a6aef0f7c3a9632dd63648f4ee242990ce12d`。
- 用户于 2026-11-21 明确要求重启并修改规则：本地持久服务可按开发需要自主同步/重启，无需逐次确认；线上仍等待明确指令。AGENTS.md、README、接手指南和运行手册已同步。
- 00:41（Asia/Shanghai）完成迁移前 MySQL 备份：`.local/backups/samples-before-v100-v101-20261121-004119.sql`，17,900,258 字节、权限 600。容器使用密码文件配置，读取容器内既有凭据完成备份，未输出凭据或数据。首次尝试未识别密码文件而被拒绝，随后备份成功，确认后才执行重启。
- 旧启动脚本保留于 `.local/runtime/run-backend.before-samples-20261121-004119.sh`，旧产品 JAR 保留；当前 `.local/runtime/run-backend.sh` 指向上述样品 JAR，保留原 mysql profile、连接和存储配置。
- 原 tmux 后端 pane `%6` 与前端 pane `%1` 已顺序重启，继续使用 8080 / 5173；MySQL 容器与持久数据卷未重建。实际查询 `flyway_schema_history` 显示 V100/V101 success=1，后端 `/actuator/health` 返回 HTTP 200、UP，前端 `/samples` 返回 HTTP 200。
- Safari 本地样品页面已加载新版，旧版本不兼容提示消失、新增入口可用；打开新增弹窗确认库位、上传与保存入口显示后关闭，未提交业务写入。此次为必要启动检查，没有执行完整回归或业务验收。

## 统一交接时待验

1. 累计后端 `./mvnw clean verify`、前端 `pnpm quality`；本次未修改导入契约。
2. 本地 MySQL V100/V101 已成功应用；继续验证历史数据/唯一约束、角色回填及会话更新；恢复并发与重复请求并发、图片复制失败回滚及旧资源清理。
3. 手动/关联两种来源、保存并继续、复制、图片替换、同库位冲突、恢复、空/失败/权限组合；会话切换后的待确认请求隔离。
4. 桌面/窄屏/手机：多条长名称、备注、详情嵌套图片预览、焦点返回、固定分页、上传锁定与未保存确认。
5. 标签多份和多页、CJK/长名称、超长定位、实际 70×30 mm 打印及 PDF 尺寸/换页；本轮未声称通过实物打印验收。
6. 导出筛选/跨页选择范围一致，CSV Unicode/引号/换行/公式与数量上限，权限失效时不继续下载。
7. 代表性 MySQL 数据量下 EXPLAIN、页查询/图库请求数、冷/热图片耗时。仅索引与查询路径优化已实现，不宣称已测得速度提升。

## 文件地图

- 前端：`views/SamplesView.vue`、`api/samples.ts`、`components/SampleDetailDialog.vue`、`SampleImage.vue`、`SampleLabelDialog.vue`、`styles/sampleManagement.css`、`utils/sampleHistory.ts`、`sampleLabel.ts`、`sampleLabelPdf.ts` 及样品用例。
- 后端：`sample/SampleController.java`、公开边界 `product/ProductLookup.java` 与实现、`catalogmedia/CatalogImageGateway.java` 与实现、`iam/PermissionCatalog.java`、V100/V101、样品/权限用例。
- 维护入口：状态报告、接手指南、UI 规范和交付物总账均同步指向本记录。

### 最终本地复核（持久服务同步前）

- 最后一次限定文件 ESLint 与 TypeScript 检查均退出 0，`git diff --check` 通过。
- Safari 当前本地 `/samples` 已确认旧服务能力保护提示可见、新增/已移除入口禁用；未执行实际业务写入。新版弹窗与迁移后的页面效果尚未核实，须在本地后端同步后检查。
- 本轮没有切换分支或暂存文件；工作区包含本会话累计授权改动，无不相关文件混入。
