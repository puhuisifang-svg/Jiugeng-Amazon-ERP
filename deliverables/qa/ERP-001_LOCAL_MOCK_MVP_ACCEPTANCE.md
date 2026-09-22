# ERP-001 本地 Mock MVP 端到端验收矩阵

## 1. 文档信息

| 字段 | 内容 |
| --- | --- |
| 目标 | 验收一个可在单机启动、通过浏览器访问并使用合成数据演示核心流程的 MVP |
| 验收基线 | `feature/ERP-001-mock-integration` / `813448d` + 当前未提交实现工作树（动态验收时点） |
| 数据边界 | 仅 `SYNTHETIC` 模拟数据；不得解释为真实业务、真实店铺或 Sprint 正式动态验收 |
| 依据 | PRD、MVP Final Plan、UI 低保真流程、ACCESS_MATRIX、Sprint 0 验收报告及当前前后端实现 |
| 输出性质 | QA 验收设计与只读审计；不替代实现、代码评审或真实数据验收 |

## 2. 第二轮动态验收结论

**结论：PASS（仅限老板收敛后的“本地可运行、合成数据 MVP”范围），P0=0、P1=0。**

本次退出范围收敛为：一键启停；模拟登录和三角色前后端 RBAC；Product + 多 SKU 新增及每 SKU 五店摘要；辅料新增；单仓库存零期初/入库/出库/调整、余额、流水、幂等和负库存；候选状态流转；上新事件确认候选及幂等重放；全链路 `SYNTHETIC` 标记；默认 profile 隔离。第 5 节保留的是更完整的正式产品矩阵，超出上述范围的行不阻塞本轮本地演示 URL。

### 2.1 独立实测结果

| 门禁 | 结果 | 证据摘要 |
| --- | --- | --- |
| 后端 | PASS | `./mvnw clean verify`，23 tests / 0 failure / 0 error / 0 skipped |
| 前端 | PASS | `pnpm run quality`；5 files / 14 tests；lint、typecheck、production build 全通过 |
| 机器契约 | PASS | ImportIssue 正例 4、负例 12；InventoryTransaction 正例 8、负例 6；主校验 6/7/8/11/21/25 |
| 一键启动 | PASS | `scripts/dev/start-local-mvp.sh` 返回 `http://127.0.0.1:5173`；健康与页面探针成功；重复启动明确拒绝 |
| 官方 smoke | PASS | `scripts/dev/smoke-local-mvp.sh` 完整通过 |
| 三角色/RBAC | PASS | 三角色登录均返回合成身份；未登录 Dashboard 401；Selection 库存 403；Selection 产品/辅料写 403；Product 选品流转/Watch 确认 403 |
| Product/SKU/五店 | PASS | 动态创建 1 Product + 2 SKU；每个 SKU 均返回 5 个固定模拟店铺摘要；重复编码 409 |
| 辅料 | PASS | 动态新增合成辅料；Selection 写请求 403；重复编码自动化覆盖 |
| 库存 | PASS | 新辅料 `OPENING=0 → INBOUND 25 → OUTBOUND 10 → ADJUSTMENT -5`，最终余额 10、流水 4；同载荷重放 `replayed=true`；异载荷同请求号 409；超额出库 409 且余额不变 |
| 候选 | PASS | 无原因淘汰 400；`NEW → REVIEWING → APPROVED` 成功；PRODUCT_MATERIAL 写 403 |
| Watch | PASS | 待复核事件首次确认创建唯一候选；二次确认 `replayed=true` 且复用同一 candidateId；PRODUCT_MATERIAL 写 403 |
| Mock 隔离 | PASS | 成功和错误响应显式 `mockData=true/source=SYNTHETIC`；前端错误标记 fail closed；默认 profile 下全部 `/api/**` 返回 404（自动化证据） |
| CORS | PASS | `Origin: http://127.0.0.1:5173` 对受保护 Dashboard 返回 200；此前 403 已整改并复验 |
| 一键关闭 | PASS | `scripts/dev/stop-local-mvp.sh` 后 8080/5173 均无监听；后端 graceful shutdown |

### 2.2 本轮非阻塞边界

- 数据全部驻留于 mock profile 的内存状态，重启恢复种子数据；不是持久化数据库验收。
- 五店仅验收 Product/SKU 下的固定 Listing 摘要，不包含 Listing 独立增改、父子体或单店失败隔离。
- 辅料仅验收主档新增，不包含 SKU—辅料用量/损耗关系。
- 候选仅验收预置候选状态流转，不包含手工新建及 ASIN/URL 查重。
- Watch 仅验收预置事件确认候选，不包含真实采集、基线任务或失败快照保护。
- 登录是本地固定合成角色，不代表生产密码、锁定、Token 生命周期或真实 IAM 安全通过。
- 当前实现仍在共享工作树、未形成新提交；最终交付 URL 应绑定后续固化的精确 commit，不得只引用 `813448d`。

## 3. 严重度与退出规则

- **P0：** 阻止本地启动、登录、主要导航、核心演示链路或导致权限/模拟数据边界失真。任一 P0 未关闭，不提供“已完成”的本地 URL。
- **P1：** 主链路可运行，但关键失败态、约束、重试或证据不足，容易在演示中产生错误结论。P1 应在交付 URL 前关闭；若老板书面接受例外，必须在交付说明中逐项披露。
- **P2/后置：** 不影响本地合成数据演示的生产化、真实数据或增强体验事项。

本地 Mock MVP 通过条件：

1. P0=0，P1=0（或有明确书面豁免）。
2. 从干净终端按一份 Runbook 可启动后端和前端，无需猜测环境变量。
3. 浏览器访问固定 URL，三种模拟角色均可完成各自允许的核心流程；越权行为前后端均被阻断。
4. 所有业务页面、API 响应及错误证据均不会把合成数据表述为真实数据。
5. 自动化门禁通过，QA 按第 8 节完成一次人工烟测和安全关闭。

## 4. 角色验收基线

| 能力 | ADMIN | PRODUCT_MATERIAL | SELECTION |
| --- | --- | --- | --- |
| Dashboard | 全局模拟摘要 | 产品/辅料相关摘要；监控只读摘要 | 选品/监控摘要；无库存摘要 |
| Product/SKU、5 店 Listing | 管理 | 操作 | 只读 |
| 辅料与 SKU—辅料关系 | 管理 | 操作 | 只读 |
| 单仓库存/流水 | 管理 | 操作 | 无菜单；直达 403；API 403 |
| 选品候选 | 管理 | 只读 | 操作 |
| 监控店铺/上新事件 | 管理 | 只读 | 操作 |
| 用户与角色 | 管理 | 无菜单；直达 403；API 403 | 无菜单；直达 403；API 403 |

模拟登录可使用仓库内公开、无秘密含义的角色选择或固定演示账号；不得在代码、文档、日志中保存真实凭据。若使用固定演示账号，密码必须明确标注仅用于本地合成环境。

## 5. 完整产品端到端验收矩阵（本轮收敛范围以第 2 节为准）

| ID | 范围 / 角色 | 最小步骤 | 预期结果与证据 | 优先级 | 首轮审计快照 |
| --- | --- | --- | --- | --- | --- |
| LM-BOOT-01 | 全局 | 在全新终端按 Runbook 启动；等待健康检查；启动前端；打开根 URL | `/actuator/health` 为 200/UP；根 URL 可达并进入登录页；端口、Node/pnpm/Java 版本、环境变量写明 | P0 | **未通过**：两进程可分别启动，但前端开发态 API 配置未在启动步骤闭环 |
| LM-BOOT-02 | 全局 | 执行 Runbook 的关闭步骤，再检查端口 | 前后端优雅退出；8080/5173（或文档规定端口）不再监听；无残留 Java/Vite 进程 | P1 | 未覆盖 |
| LM-IAM-01 | 三角色 | 分别模拟登录 ADMIN、PRODUCT_MATERIAL、SELECTION；刷新页面；退出 | 登录进入 Dashboard；当前角色可见；刷新保留本地模拟会话；退出后业务路由不可访问 | P0 | **未通过**：登录页为占位，无会话/退出 |
| LM-IAM-02 | 三角色 | 检查菜单；直接输入受限 URL；直接请求受限 API | 菜单按矩阵裁剪；直达返回 403 页面；API 返回 403，不能只依赖前端隐藏 | P0 | **未通过**：仅有 route meta，无守卫、菜单过滤及后端授权 |
| LM-DASH-01 | 三角色 | 登录后加载 Dashboard；分别核对卡片 | 显示角色允许的摘要，选品角色不出现库存；全部带 `SYNTHETIC` 声明；有生成时间 | P0 | **部分通过**：通用摘要可用，未按角色裁剪 |
| LM-DASH-02 | 任一角色 | 关闭后端后刷新 Dashboard，再恢复服务并重试 | 失败不是空态；显示可理解错误和重试动作；恢复后无需整页重启即可成功 | P1 | **部分通过**：有错误文案，无重试动作/追踪标识 |
| LM-PROD-01 | ADMIN/PRODUCT_MATERIAL | 打开产品列表 → 新建模拟 Product → 至少新增 2 个 SKU → 打开详情 | 编码/属性可见；重复 Product/SKU 编码或重复属性组合被拒；创建结果可立即查询 | P0 | **未通过**：占位页/无 API |
| LM-PROD-02 | SELECTION | 打开产品与 SKU 详情并尝试写操作 | 可查看；不显示写按钮；构造写请求返回 403 | P0 | **未通过** |
| LM-LIST-01 | ADMIN/PRODUCT_MATERIAL | 对一个内部 SKU 查看并编辑 5 个预置店铺映射 | 5 店固定且可区分；每店 Seller SKU/ASIN/FNSKU/履约/状态互不覆盖；刷新后仍保持本次会话结果 | P0 | **未通过**：占位页/无 API |
| LM-LIST-02 | ADMIN/PRODUCT_MATERIAL | 制造同店 Seller SKU 重复或父子体关联错误 | 保存被拒且定位到对应店铺/字段；其他四店数据不被清空 | P1 | **未通过** |
| LM-MAT-01 | ADMIN/PRODUCT_MATERIAL | 新建模拟辅料 → 绑定一个 SKU → 设置用量和损耗率 | 辅料编码唯一；关系挂内部 SKU；用量 `>0`；保存后列表/详情一致 | P0 | **未通过**：占位页/无 API |
| LM-MAT-02 | SELECTION | 查看辅料后尝试新增/编辑 | 只读展示；写按钮隐藏；写 API 403 | P0 | **未通过** |
| LM-INV-01 | ADMIN/PRODUCT_MATERIAL | 对模拟辅料做期初 → 入库 → 出库 → 调整；查看流水 | 固定单仓；余额等于流水重算；流水含类型、前后余额、原因、请求号且只读 | P0 | **未通过**：占位页/无 API |
| LM-INV-02 | ADMIN/PRODUCT_MATERIAL | 重放同请求号；尝试导致负库存；尝试修改/删除流水 | 重放不重复记账；负库存被拒；流水不可改删；错误后余额不变 | P0 | **未通过** |
| LM-INV-03 | SELECTION | 导航菜单、直达库存 URL、调用库存 API | 无库存菜单；页面 403；API 403 | P0 | **未通过** |
| LM-SEL-01 | ADMIN/SELECTION | 手工录入 ASIN/URL 候选 → 待评估 → 评估中 → 通过/淘汰 | 状态流正确；淘汰必须填原因；重复 ASIN/标准 URL 不新增第二条 | P0 | **未通过**：占位页/无 API |
| LM-SEL-02 | PRODUCT_MATERIAL | 查看候选并尝试状态流转 | 可查看但不可操作；写 API 403 | P0 | **未通过** |
| LM-WATCH-01 | ADMIN/SELECTION | 打开模拟监控店铺 → 查看一次基线任务 → 注入下一次完整快照 → 查看新品事件 | 首次成功只建基线；后续新增 ASIN 生成唯一事件；事件证据明确为合成快照 | P0 | **未通过**：占位页；现有 collector 不是可操作链路 |
| LM-WATCH-02 | ADMIN/SELECTION | 将新品事件确认加入候选；对同一事件重放确认 | 新建或关联唯一候选；重放不产生重复事件/候选；来源关系可见 | P0 | **未通过** |
| LM-WATCH-03 | ADMIN/SELECTION | 触发失败、空结果或分页不完整的模拟快照 | 显示明确失败状态；有效基线不变化；可重试或使用模拟人工导入降级 | P1 | **未通过** |
| LM-MOCK-01 | 全局 | 浏览所有页面并检查网络响应 | 顶部或页面显著位置持续显示“本地模拟数据 / SYNTHETIC”；API 统一携带 mock 标记；无真实域名、真实个人信息或真实店铺凭据 | P0 | **部分通过**：Dashboard 有标识，其余页面未实现 |
| LM-MOCK-02 | 全局 | 不启用 `mock` profile 请求全部 mock API；前端收到非 mock 标记响应 | 默认 profile 下 mock API 均 404；前端拒绝渲染缺失/错误的 mock 标记并显示错误 | P0 | **部分通过**：Dashboard 后端隔离有测试；前端未校验响应标记 |
| LM-ERR-01 | 全局 | 对各域触发加载失败、无数据、校验失败、403 和 404 | 失败不伪装为空；输入不丢；可重试；403 与 404 可区分；错误展示稳定且不泄露堆栈/敏感信息 | P1 | **未通过**：仅 Dashboard 网络失败与 NotFound 有最小实现 |
| LM-REG-01 | 全局 | 执行后端测试、前端 quality、契约测试并检查工作树 | 命令全部 0 退出；无高置信秘密；不修改 6 个既有 xlsx；本功能改动范围可审计 | P0 | Dashboard 范围曾通过；完整 MVP 尚无覆盖 |

## 6. 初审缺口处置

本节记录首轮审计时的完整产品缺口。与第 2 节收敛范围重合的 P0/P1 已关闭；Listing 独立编辑、SKU—辅料关系、候选新建、真实监控基线和完整全局错误态已明确后置，不计入本轮本地合成 MVP 的 P0/P1。

### 首轮 P0 清单（与第 2 节相交项已关闭）

1. **可重复启动闭环：** 提供单一 Runbook 或脚本，明确 Java 21、Node 24–26、pnpm 12.4.1、后端 mock profile、前端 API Base URL、端口冲突检查、健康等待和关闭方法。
2. **模拟登录与双层 RBAC：** 实现三角色会话、退出、菜单过滤、路由守卫和 API capability 校验；至少覆盖库存、选品写操作、用户管理三条越权反例。
3. **核心业务页仍是占位：** Product/SKU、5 店 Listing、辅料、单仓库存、选品候选、监控店铺/上新事件必须有可操作的模拟 API 与页面，完成第 5 节定义的最小 happy path。
4. **库存关键不变量：** 余额/流水一致、请求幂等、负库存拦截、流水不可修改删除必须由自动化测试证明，不能只靠页面演示。
5. **上新到选品闭环：** 合成基线、后续快照、唯一事件、人工确认、唯一候选需贯通；禁止把静态数量卡片当作流程完成。
6. **模拟数据隔离：** 所有 mock Controller 仅在 `mock` profile 注册；每个业务响应携带明确标记；前端对缺失或非 `SYNTHETIC` 响应 fail closed；所有页面持续可见模拟数据横幅。
7. **自动化回归不足：** 为三角色矩阵、核心域 happy path、关键业务约束、profile 隔离和启动健康增加测试，保持前端 quality 与后端 verify 全绿。

### 首轮 P1 清单（本轮范围外项已后置）

1. Dashboard 和核心列表提供可见重试，不把失败展示为业务空态。
2. 5 店映射错误必须隔离到单店；父子体/唯一性错误应定位到字段。
3. 上新模拟任务覆盖失败、空结果、分页不完整且不污染有效基线。
4. 各核心页面至少覆盖 loading、业务空态、筛选无结果、请求失败、403 和校验错误；服务端错误不泄露堆栈。
5. Runbook 提供安全关闭和端口释放验证；连续启动两次给出明确端口冲突信息。

## 7. 可后置项（不阻塞本地 Mock MVP）

- 真实账号生命周期：首次改密、失败锁定、管理员解锁/重置、真实审计持久化。
- MySQL/Redis/MinIO 正式数据持久化、真实事务并发与备份恢复；Mock MVP 可在重启后重置合成数据，但必须明示。
- 六类 Excel 的真实文件上传/异步 Worker；现有静态模板和机器契约仍单独保留，不能以模拟页面宣称真实导入通过。
- 真实 20–50 条脱敏样本、真实 5 店名称/Listing、真实首期用户清单。
- 真实 Amazon 数据源、3 店连续 7 天观察、生产调度、SP-API 或第三方 API 集成。
- GitHub 远程、在线 Actions、Docker Hub 出口与公网部署。
- 高保真品牌视觉、移动端、SSO、多租户、多仓、多层 BOM、采购/生产/财务/广告等明确非 MVP 范围。

## 8. 建议验收命令

以下命令在仓库根目录执行；不得把凭据放进命令行或环境输出。

### 8.1 静态与自动化门禁

```bash
git status --short

cd deliverables/backend/sprint-0/jiugeng-erp-backend
./mvnw clean verify

cd ../../../../frontend
pnpm install --frozen-lockfile
pnpm run quality

cd ../deliverables/backend/sprint-0/import-contract/tests
npm ci --ignore-scripts
npm test
python3 validate_contract.py
```

### 8.2 本地一键启停

```bash
./scripts/dev/start-local-mvp.sh
./scripts/dev/smoke-local-mvp.sh
./scripts/dev/stop-local-mvp.sh
```

基础探针：

```bash
curl --fail http://127.0.0.1:8080/actuator/health
curl --fail -H 'Authorization: Bearer mock-session-admin' http://127.0.0.1:8080/api/dashboard/summary
curl --fail http://127.0.0.1:5173/
```

默认 profile 隔离需在单独进程启动后验证：

```bash
curl --output /dev/null --silent --write-out '%{http_code}\n' http://127.0.0.1:8080/api/dashboard/summary
```

预期为 `404`；不得同时运行 mock profile 实例占用同一端口。

## 9. 建议人工烟测步骤

1. 按 Runbook 从全新终端启动，确认健康探针后访问前端根 URL。
2. 以 ADMIN 登录，核对全菜单和全局 `SYNTHETIC` 横幅。
3. 建 Product + 2 SKU，为一个 SKU 维护 5 店映射，确认各店字段不串写。
4. 建辅料并绑定 SKU，依次做期初、入库、出库、调整，人工重算余额；重放一次请求并尝试负库存。
5. 注入一组模拟基线和后续快照，确认只产生一个上新事件；复核进入候选池并完成状态流转。
6. 切换 PRODUCT_MATERIAL，确认产品/辅料/库存可写，选品/监控只读，用户管理不可见且直达 403。
7. 切换 SELECTION，确认产品/辅料只读、库存和用户管理不可见且直达/API 均 403，候选/监控可操作。
8. 停止后端验证各页面失败态和重试；恢复后端后验证恢复成功。
9. 检查浏览器 Network：业务响应均为本机来源且携带 mock 标记，无真实店铺、真实个人数据或密钥。
10. 退出登录后尝试返回受保护 URL；应回登录而非继续显示业务数据。
11. 按关闭步骤停止服务，确认端口释放，再重复完整启动一次。

## 10. QA 最终交付证据

完成实测时至少保留：

- 精确 Git commit、分支、工作树状态和工具链版本。
- 后端 `clean verify`、前端 `quality`、机器契约测试的通过摘要。
- 三角色菜单/403/API 403 证据。
- Product/SKU → 5 店 Listing、辅料 → 库存、上新事件 → 候选池三条闭环证据。
- mock profile 200 与默认 profile 404 的成对证据。
- 浏览器可访问 URL、启动时间、停止后的端口释放结果。
- 明确声明：该 URL 仅为本地合成数据 MVP，不代表真实样本、生产 IAM、真实数据库事务或公开店铺 7 天监控通过。
