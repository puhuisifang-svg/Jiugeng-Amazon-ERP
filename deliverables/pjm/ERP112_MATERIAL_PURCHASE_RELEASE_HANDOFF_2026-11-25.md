# ERP-112 官网与辅料、采购累计迭代发布交接

- 交付物：DEL-056；ERP-112 官网与用户直接授权的辅料/采购维护追加。
- Owner：Codex 完成本地实施和统一验证，正式发送交接后由 PJM 独占写入并负责发布。
- 状态：In Review；2026-11-25，Asia/Shanghai。
- 用户最新原话：“与项目经理交接，把修改内容提交远端main，并部署线上”。已授权 PJM 提交、受保护合并 main、部署和发布状态闭合。
- 用户此前明确确认官网任务已停止编辑，允许辅料改动合入本轮；官网及文档不是不明归属的脏文件。

## 工作区

- 仓库：`/Users/thinkperfect/.openclaw/workspace-jiugeng_pjm/jiugeng-amazon-erp`。
- 分支：`feature/ERP-112-corporate-website`。
- 基线完整 SHA：`c410b33a61a60aeb1700ed4501b8ecd9d5e8453f`。
- 实施提交：**未提交**；本地修改和 website/ 新文件一并交给 PJM，Codex 未暂存、提交或推送。PJM 提交前重新检查分支、diff 和归属；不能因本记录直接覆盖后续新文件。
- 不切换工作树、不覆盖官网现有文件；本轮无数据库迁移。当前 git status 仅已授权官网、辅料、采购与交接文档，无无关改动。

## 改动内容与代码入口

1. `website/`：官网独立静态源码、SVG、构建/预览脚本、两项契约测试及 Caddy 示例。官网此前已另行授权部署，当前构建摘要仍为 `ae09c183535a6ecfd9e432ba87cb7c073d3e37b109c4e2846b81451cbe784d5a`；本次只是把当前源码纳入累计提交，不据此声称 ERP 新版本已上线。
2. 后端 materialinventory 下 `CostTypes`、`CostService`、`MaterialManagementController`、`MaterialService`、`MaterialRepository`、`MaterialController`、`MaterialImportService`：成本价按普通字段填写/修改/清空，实际变更追加历史，移除普通编辑对核定原因与确认的依赖；保持金额权限、乐观版本、幂等与原子保存。未使用辅料不再因单纯价格记录锁定单位，修改单位同步成本单位且不舍入旧金额；导入亦同步。删除未使用辅料在同事务清除当前价格、保留审计，解除旧核价外键阻断；库存、采购、产品用量及成本版本引用仍保护删除与单位。
3. `frontend/src/components/MaterialEditor.vue`、`MaterialDetailDialog.vue`、`api/materialManagement.ts`、`utils/productHistory.ts`：简化成本价界面，两位显示且不回写未改的历史六位值；清空作为显式命令，缺省表示不改；显示中文金额变更。旧 material.costPrice 载荷及请求哈希兼容保留。simpleCostVersion 能力检查阻止新前端连旧后端时静默丢价；保存用既有 HTTP 兼容 createRequestId，修复该入口 randomUUID 报错。
4. `MaterialsManagement.vue`、`materialManagement.css`：删除文字红色，包含 Teleport 到 body 的菜单；删除提示区分真实引用与普通成本字段。
5. `ui/purchaseImageExport.ts`：追加采购单备注列，逐明细显示红色加粗文字，手动/自动换行、行高适配；同日不同采购单不混用备注。
6. `PurchasesView.vue`、`DashboardPurchaseReceipts.vue`、`purchaseManagement.css`：采购新增/编辑/再次采购共用表单控件40px同高、字段对齐、数量单位成组、金额右对齐、选择按钮浅底；首页商品图片/品名/收货进度重新布局，长名称可撑高、不截断。现有业务操作与权限保留。
7. 测试：`MaterialCloseoutTest`、`CostControllerTest`、`MaterialEditor.test.ts`、`purchaseImageExport.test.ts`，以及 operations-closeout 浏览器夹具新能力标记。
8. 文档：PROJECT_HANDOVER、STATUS_REPORT、UI_STANDARD、DELIVERABLE_REGISTER 和本文件。状态报告历史“待交接/未授权”由本交接覆盖，保留作为阶段记录。

## 统一验证（2026-11-25）

| 工作目录 | 命令 | 结果 |
| --- | --- | --- |
| frontend | `pnpm quality` | 通过：lint、171 文件/802 测试、类型检查、构建；构建有大 chunk 提示，无错误 |
| deliverables/backend/sprint-0/jiugeng-erp-backend | `./mvnw clean verify` | BUILD SUCCESS；456 项，455 通过、0 失败/错误、1 跳过 |
| 仓库根 | `python3 -m unittest discover -s website/tests -v` | 2 项通过 |
| 仓库根 | `python3 website/build.py` | 通过；摘要与已归档官网产物一致 |
| frontend | `pnpm exec playwright test --config /tmp/jiugeng-handoff-playwright.cjs` | operations-closeout 3 场景通过：1280×800、800×600、360×800；覆盖辅料新增/编辑/库存、供应商、新建采购、收货、发货批次 |
| frontend | `node /tmp/jiugeng-purchase-layout.cjs`（上一实现阶段结果复用） | 本地 Vite 与合成 API，1440×900；采购顶部三控件40px且同一Y，首页摘要flex且内容无溢出；截图已目视检查 |
| 仓库根 | `git diff --check` | 通过 |

发送前使用 Python 解析本轮 Markdown 相对链接：152 个本地路径全部存在，缺失0；外站和锚点未作为该检查内容。

- 首次浏览器命令使用默认4188时因官网预览占用而未启动；没有停止官网，改临时4190预览，测试结束自动停止。临时配置只更改端口、测试匹配、输出目录，不改产品设置。
- 后端唯一跳过：OssConnectivityTest，未设置 ERP_OSS_PROBE，未执行真实 OSS 联网探针；其他测试通过。导入契约文件未变更，本轮未重复独立 npm/Python 契约门禁，生产工作流仍会执行。
- 日志和截图持久保留在 `.local/verification/handoff-20261125/`（不入 Git），包含 frontend-quality.log、backend-verify.log、browser.log、browser/ 三尺寸截图及1440截图。临时配置与局部脚本也已复制到该目录，可从 frontend 执行 `pnpm exec playwright test --config ../.local/verification/handoff-20261125/playwright.cjs` 重现同一检查。
- 未运行整个 Playwright 仓库所有场景；本轮按受影响模块验证，Production 工作流仍运行完整浏览器套件。不是实际业务验收，不修改线上业务数据。

## 发布注意事项和剩余边界

- PJM 独立审阅后原子提交，走受保护 PR 合并 main、现有 Production 流程；构建最终 SHA，不复用旧本地 JAR。代码支持前后端能力检查，需同版本发布。
- **保留现有官网/ERP 分流**：根域/www/默认IP为官网，ERP域名及IP:8081为ERP，内部8080/4173不变；不得拿旧 Caddy 模板覆盖当前配置。本轮 ERP receive.sh 发布路径只切换 ERP 应用，不需要重配 DNS/Caddy。官网已上线证据及备份路径见状态报告和 website/README.md。
- 官网有独立产物和 website-version.json，现有 ERP main push 工作流尚无官网独立自动发布。PJM 接管后续官网 CI/CD 与路径隔离，勿把 ERP release.tar.gz 当官网包。本次混合提交确有 ERP 改动，应保留 ERP 完整门禁，不得以官网路径跳过。
- 辅料 HTTP 随机编号只修复当前保存入口；其他页面直接 randomUUID 调用未扩展处理，不宣称全站 HTTP 兼容问题已消除。
- 本地 tmux jiugeng-mvp 保留，MySQL 数据和配置未清理，运行 `.local/releases/backend/material-delete-price-20261125/backend.jar`；前端5173热更新。旧包与启动脚本备份保留；统一验证的新 target 产物不影响当前持久服务。
- 若再遇部署锁，由 PJM 检查实际持锁进程和流水线，不能直接删除锁绕过运行中的部署。Codex 不轮询线上结果；PJM 负责实际 deploy 步骤、ERP目标入口 version.json 和最终发布记录，不能把提交或构建成功等同于上线。

## 接棒

正式交接消息被接受后，PJM 获得共享工作区唯一写入权；Codex 停止修改。PJM 接收当前未提交修改，更新最终提交 SHA、PR 与发布证据；本文件截至交接时只确认本地实现与验证完成。

## PJM release review and corrections (2026-11-25)

- Read-only review found an ABA regression: clearing and recreating a cost reused version 0. PJM now preserves the deleted generation in the locked master version, within the same transaction, and new costs inherit that version. No migration added. Existing price updates still increment their own version.
- Targeted `./mvnw -q -Dtest=CostControllerTest,MaterialCloseoutTest,MaterialControllerTest test`: 31 tests passed, zero failures/errors. Includes stale legacy HTTP price writes rejected after recreation and idempotent aggregate clear retries. Log: `/tmp/del056-cost-regression-final.log`. Two initial assertions assumed version 0; corrected to use returned/current versions. Valid frontend gates remain applicable. CI still runs full gates.
- Independent Website CI builds/tests and retains a commit-SHA artifact with provenance. ERP Production now filters application/deployment paths: website-only and documentation-only changes do not restart ERP; this mixed release still triggers full ERP deployment. Website contract/build passed again; content digest remains identical to the existing live website. All three workflows passed actionlint; 152 local Markdown paths passed.
- Automatic website server delivery remains pending a connected management channel. Current browser tooling cannot connect to the previous cloud-console session; protected credential metadata store is empty. Restricted ERP SSH must not be reused for website delivery or arbitrary management. Access restoration was requested; CI artifacts and matching existing website content are not represented as a newly executed automatic deployment.
- Existing Caddy, HTTPS, domain/IP separation, internal ports, persistent data and local services remain unchanged.

## PJM 发布结果（2026-11-25：已合并，ERP部署受阻）

- 累计实施提交：`6a156daf2e6db4a089364d2efc74d90c3b61750e`，46 个文件；[PR #30](https://github.com/puhuisifang-svg/Jiugeng-Amazon-ERP/pull/30) 按五项必需检查保护合并，未绕过保护。
- 应用 main：`fc8891cbbd6db4e3eccd6e74c26a6453d54a9d80`。
- [PR CI](https://github.com/puhuisifang-svg/Jiugeng-Amazon-ERP/actions/runs/45640020675) 与 [main CI](https://github.com/puhuisifang-svg/Jiugeng-Amazon-ERP/actions/runs/45640729491) 成功；完整后端457项、0失败/错误、1外部OSS条件跳过，前端171文件802测试，浏览器17项通过。
- [ERP Production](https://github.com/puhuisifang-svg/Jiugeng-Amazon-ERP/actions/runs/45640730028) 已完成完整门禁及精确SHA打包；实际SSH deploy于15:08:20 UTC开始、15:33:14 UTC因作业30分钟时限被取消（非人工取消）。未见备份完成或Deployment verified标记，也未见锁冲突标记，不能据此断言服务器端进程已结束。
- [独立官网 CI](https://github.com/puhuisifang-svg/Jiugeng-Amazon-ERP/actions/runs/45640729577) 成功，产物 provenance 对应上述应用 main。官网HTTPS的8个源码文件与该CI产物逐文件一致；website-version.json 摘要 `ae09c183535a6ecfd9e432ba87cb7c073d3e37b109c4e2846b81451cbe784d5a`。这确认当前官网内容与提交构建一致，不等同于新自动服务器投递已经执行。
- 官网HTTPS及www重定向通过，官网API路径404、ERP匿名认证API401，保留既有分流；未改Caddy、DNS、安全组或内部端口。官网自动投递仍待恢复管理通道。
- 本轮无迁移，未重启本地持久服务；发布归档为纯文档，不触发ERP Production，线上应用SHA可以与后续纯文档main不同。

- 15:34 UTC 后独立读取 ERP HTTPS `/version.json`：HTTP200，仍为旧应用 `365892ccb4c5d5eb151420bd0cb7c66c739f8586`，目标 `fc8891cbbd6db4e3eccd6e74c26a6453d54a9d80` 尚未确认上线；本次不能宣称生产备份、后端健康或最终版本切换通过。官网HTTPS仍为上述摘要，未受此次ERP发布影响。
- 当前阻断：缺少已连接的主机管理通道，无法检查实际部署接收/传输进程及锁持有者；未盲目重跑、删锁或绕过保护。恢复管理访问后，先检查实际进程、锁及当前版本，再决定安全续跑/恢复，用户发布授权持续有效。
- 证据：GitHub run/job终态及超时注解；本地 `/tmp/del056-production-monitor-log.txt`、`/tmp/del056-production-monitor-state.json`（不提交日志或私有配置）。本归档仅记录受阻事实，不代表发布闭环。

## DEL-056 服务器恢复回执（2026-11-25，PJM接回操作槽）

以下为具有原生Safari CUA通道的协助会话回执，PJM未冒称亲自执行云助手操作：

- 只读复核 `t-bj0746xm0iklvr4`（23:54:52 CST）：进程链和starttime匹配，临时包21,823,488字节、两次大小/mtime不变且停滞超过20分钟，旧应用健康。
- 定向清理 `t-bj0746xqoqxf1fk`（23:56:19，ExitCode0）：即时核对PID身份/父子关系/starttime、head stdout包及旧current；备份残包与进程证据，再观察5秒无增长，TERM已确认head及仍存的原会话。结果 `VERIFIED_SESSION_EXITED`、`LOCK_AVAILABLE`、`RECOVERY_CLEANUP_PASS`。未unlink锁、未停应用。
- 备份根目录 `/srv/jiugeng/recovery/del056-20261125-2356/`：残包、processes-before.txt、scripts/receive.sh.previous、helper-previously-absent.txt；旧receive.sh SHA-256 `50f5ad123fd0170378f6ef17dc486bc63964e284f50e52a5409f2bca2c211943`。
- GitHub raw首次下载发生curl35连接重置，尚未安装；改由云助手传递源码，双哈希一致后安装。最终 `t-bj0746xygtkkh6o`（23:58:44，ExitCode0）helper先、receiver后原子替换，均root:root 0755，来自提交 `2fb8852d3a8b59c61d7461d3bd57bffbd5b7ac72`。
- 安装哈希：receive.sh `547ace3a3145a6413dc3641634c82bdc7fc5b02a314df45b52a1f9c35f82082a`；receive_stream.py `c9de4b7935b7ffc9fbdd7e57d248f378f876fb2eb3bd974abbe7902a03131f66`。bash语法、Python AST、15字节EOF和0.2秒idle探针通过，结果 `INSTALLED_HELPER_PROBES_PASS`、`SCRIPTS_INSTALLED_PASS`、`LOCK_AVAILABLE_AFTER_INSTALL`。证据 install-direct-result.txt 位于上述备份根目录。
- 安装后jiugeng/caddy active、health UP，current仍为旧应用 `365892ccb4c5d5eb151420bd0cb7c66c739f8586`；未改Caddy、SSH/sudo、端口、业务数据、应用包或本地仓库。
- 操作槽明确归还后，PJM核对干净分支及五项必需检查，通过 [PR #32](https://github.com/puhuisifang-svg/Jiugeng-Amazon-ERP/pull/32) 受保护合并，应用目标main为 `14c67cf04576215a6ae7f95b5c28a467d0ef8fd2`。[恢复Production](https://github.com/puhuisifang-svg/Jiugeng-Amazon-ERP/actions/runs/45650240074) 自动启动，最终结果待核验。
- 超时补丁避免永久接收残留，不等于解决底层跨境传输。若再次失败，应按进度字节和实际速率选择经校验的稳定产物通道，不循环重试或盲目延长时限。上述回执覆盖此前“未清理/未安装”的阶段描述。

## 接收启动窗口修正（2026-11-26）

合并PR32后的main CI在TERM清理测试中出现5秒等待超时（PR和Production前置工具测试曾通过）。本地40次启动窗口复现出现2次相同失败：TERM早于接收子进程exec完成时可能被忽略。修复清理逻辑，在TERM后对同一个只负责临时文件接收的直接子进程补KILL并wait，不涉及应用服务；100次启动窗口测试及完整9项发布工具测试通过。此为真实修复，不靠重复运行原失败门禁放行。服务器目前仍是此前安装的2fb8852版本，新增清理修正尚未同步。

PJM尝试在浏览器门禁阶段取消恢复Production，但GitHub取消接口报500/无有效返回，未确认取消；重新读取时流程已进入实际deploy。未再次中断传输、未启动并行部署。最终结果另记。

## DEL-056 应用上线核验（2026-11-26 00:06 CST）

[恢复Production 45650240074](https://github.com/puhuisifang-svg/Jiugeng-Amazon-ERP/actions/runs/45650240074) 最终success，实际deploy非skipped。接收82,836,154字节，耗时66秒，15/30/45/60秒分别累计18,939,904 / 37,584,896 / 56,262,656 / 74,514,432字节，未触发接收超时。00:05:28数据库备份完成，00:05:55 `Deployment verified: 14c67cf04576215a6ae7f95b5c28a467d0ef8fd2`，部署脚本健康与内部版本验证通过。PJM独立读取ERP HTTPS `/version.json` HTTP200并匹配同SHA；官网HTTP200，内容摘要仍ae09c183535a6ecfd9e432ba87cb7c073d3e37b109c4e2846b81451cbe784d5a。无需新增迁移，未改分流。应用发布已完成，不代表底层网络故障根因已确定。

仍待闭合：main CI45650240148的启动瞬间TERM清理测试失败已定位、修复并推送e0b799eaed9ce03247314ee7ebd26271dff330c6（100次启动测试及9项工具测试通过），但尚未合并或安装主机。新receive.sh SHA256 f28d124dac213958056baff493bfcd53dce0079ab49711edea91b9caaac01102，helper不变。已请求CUA协助会话在无接收进程/锁空闲后备份同步；其操作期间PJM不执行远端写入或部署。创建后续PR时GitHub GraphQL返回内部错误，REST无有效返回，尚未确认PR创建，不能绕过分支保护。官网自动服务器投递仍为独立未闭合项。
