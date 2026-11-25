# ERP-104 本轮修复与官网需求发布交接

- 项目：九更亚马逊 ERP
- 交付物：DEL-055；关联官网需求 DEL-054 v0.6
- 日期：2026-11-25，Asia/Shanghai
- 状态：In Review，用户已授权提交 main 并部署；待 PJM 执行
- 写入权：Codex 完成本地验证，正式发送本记录后由 PJM 独占；Codex 停止修改、测试、服务操作，不跟踪线上发布

## 授权与范围

用户原话：“与项目经理交接，把修改内容提交远端main，并部署线上。因为接下来要开始开发官网了。”

本次提交现有 ERP-104 前端修复、测试和文档。官网仍处于需求阶段，页面、Caddy 8081 入口、安全组和防火墙均未实现或变更。本次发布不得把需求文档当成已部署网站，不提前把现有 IP 入口替换为空官网。后续官网开发由 PJM 分配新任务及干净写入槽。

## 精确工作区

- 仓库：`/Users/thinkperfect/.openclaw/workspace-jiugeng_pjm/jiugeng-amazon-erp`
- 分支：`feature/ERP-104-material-ui-fixes`
- HEAD / 基线：`78eea545253685f1967b917d5936e4713e4c050f`
- 实施提交：**未提交、未暂存**；提交、推送、PR/合入 main、CI/CD 与生产核验由 PJM 执行。
- 本轮所有变更属于已授权修复、相应验证或官网需求文档，无无关修改。PJM 接手时重新检查实际 status/HEAD，不沿用历史锁。

## 已完成的应用行为

1. `frontend/src/components/MaterialEditor.vue`：辅料成本价显示两位，合法输入离焦/提交补齐两位；新改价最多两位。BigInt 按十进制显示舍入，未改价时不把历史六位成本覆盖成两位；旧待确认保存按原载荷重试。成本修改确认、原因及后端权限不变。
2. `frontend/src/styles/materialManagement.css`：规格与基础单位控件顶部对齐，单位锁定说明不拉伸规格输入；供应商名称蓝色、备注/采购链接绿色、解除红色。
3. `frontend/src/styles/candidateWorkbench.css`、`frontend/src/views/LaunchWorkbenchView.vue`：白底/透明按钮明确深色默认字色，保留已有实色主按钮覆盖规则。
4. `frontend/src/router/index.ts`：样品面包屑改为“商品 / 样品管理”，删除错误产品管理中间层；路由与权限不变。
5. `frontend/src/components/MaterialEditor.test.ts`：历史精度不回写、输入补齐及确认、超过两位阻断覆盖。
6. `frontend/e2e/operations-closeout.smoke.ts`：三个尺寸的真实构建页面验证编辑成本、按钮颜色、规格40px、桌面两列对齐、备注展开与保存可达；使用合成接口。
7. `frontend/e2e/catalog-closeout.smoke.ts`：三个尺寸实际样品页面验证面包屑，不仅比对路由对象。

没有后端、数据库迁移、依赖或部署脚本变更。本轮不重启本地或生产后端。

## 官网需求交接（尚未开发）

见 [DEL-054 PRD v0.6](../../docs/01-product/JIUGENG_CORPORATE_WEBSITE_PRD_v0.1_DRAFT.md)，文件名保留 v0.1 是稳定链接，正文版本为 v0.6。

- 九更静态企业展示，现阶段用于备案；单页分区，Logo、视觉、一般文案由 Codex 自主设计，真实公司信息不得编造。
- 根域/www与默认HTTP公网IP最终进入官网；ERP正式域名为erp子域。
- 临时ERP8081提供现有完整前端及同源API，内部8080/4173不变。用户明确安全组与防火墙TCP8081对所有外网放行，不设员工IP白名单；登录权限继续保留。
- 8081尚未开通。需先验证临时ERP入口，再切换默认IP官网；不得造成当前ERP唯一入口中断。HTTP明文及备案开放时机限制已记录；未来正式域名可用后撤临时入口。
- 本次仅归档方案。官网开发/入口配置另按下一阶段实施，不能因提交此文档擅自迁移端口、开放防火墙或替换默认站点。

## 验证命令与结果

执行日：2026-11-25。下列 pnpm 命令工作目录为仓库 `frontend/`。

| 命令 | 结果 |
| --- | --- |
| `pnpm quality` | PASS：eslint、171个Vitest文件/795项测试、vue-tsc、生产构建全部通过；日志`/tmp/erp104-quality.log` |
| `pnpm exec eslint e2e/operations-closeout.smoke.ts e2e/catalog-closeout.smoke.ts --max-warnings=0` | PASS；quality后只追加E2E断言和文档，应用代码未变，不重复全量门禁 |
| `pnpm exec playwright test e2e/operations-closeout.smoke.ts e2e/catalog-closeout.smoke.ts` | 初次执行样品/库存3个场景全部PASS；辅料新增断言错误地用button查找combobox，主动中止该轮并修正测试定位，不是应用缺陷 |
| `pnpm exec playwright test e2e/operations-closeout.smoke.ts` | 修正定位后3项PASS；与前次未变化的样品3项合计6个场景。尺寸1280×800、800×600、360×800；日志`/tmp/erp104-e2e-material-recheck.log` |
| 根目录 `git diff --check` | PASS |
| Python本地Markdown链接校验（变更新增行与新增文档） | PASS；检查相对路径存在，不把远程链接或页内锚点当文件 |

辅料1280px与360px实际截图已目视确认，按钮可读、底栏可达；图片为合成资料，位于本地忽略的`frontend/test-results/`。可复现证据是纳入仓库的E2E断言，不依赖临时截图作为唯一记录。旧局部7项与1100px组件检查记录见状态报告；本次完整结果覆盖它们。

执行记录补充：曾在仓库根目录误执行一次pnpm exec eslint，返回eslint不可找到；随后在正确frontend目录完成上述检查。初次浏览器运行因测试定位修正中止，未将该整次命令表述为通过。非阻断警告为现有大于500KB的构建chunk、Vitest环境耗时提示和Node废弃/颜色设置提示，无相关依赖或分包改动。

后端、导入契约、发布脚本均未改，本轮未运行其全量门禁。PJM按既有受保护PR与CI/CD执行发布流程，不需要Codex等待线上完成。

## 文档变更与剩余事项

- 新增本交接与官网PRD；更新`docs/INDEX.md`、`docs/DELIVERABLE_REGISTER.md`、`docs/08-project-management/STATUS_REPORT.md`、`docs/04-engineering/PROJECT_HANDOVER.md`与`docs/ui/UI_STANDARD.md`。
- 官网公司主体/真实联系方式及备案类型尚待核实；生产DNS、8081占用、证书与入口配置尚未检查。
- 用户“样品管理 2”没有完整内容，不属于漏做事项，不猜测实现。
- Chromium已验证相关三个尺寸；本轮未新增Safari/Edge独立验收，不声称全浏览器验收。
- 验证用preview进程随Playwright退出；原本地持久服务保持。工作区由PJM接管后，Codex不得抢先开官网分支。

## PJM 下一步

1. 独立核对本分支、基线、完整diff与验证范围；按原子提交将前端修复/测试与文档归档提交到远端，经受保护PR合入main并部署现有ERP。
2. 记录最终提交SHA、PR、CI/CD和实际发布结果。用户已授权本次发布，Codex不轮询或等待发布状态。
3. 收尾归档并释放干净工作区后，分配官网开发任务；以PRD v0.6为基线，勿把旧版端口迁移方案重新执行。

## 发送前工作区清单

全部为本轮授权范围，未暂存、未提交：

```text
 M docs/04-engineering/PROJECT_HANDOVER.md
 M docs/08-project-management/STATUS_REPORT.md
 M docs/DELIVERABLE_REGISTER.md
 M docs/INDEX.md
 M docs/ui/UI_STANDARD.md
 M frontend/e2e/catalog-closeout.smoke.ts
 M frontend/e2e/operations-closeout.smoke.ts
 M frontend/src/components/MaterialEditor.test.ts
 M frontend/src/components/MaterialEditor.vue
 M frontend/src/router/index.ts
 M frontend/src/styles/candidateWorkbench.css
 M frontend/src/styles/materialManagement.css
 M frontend/src/views/LaunchWorkbenchView.vue
?? deliverables/pjm/ERP-104_RELEASE_HANDOFF_2026-11-25.md
?? docs/01-product/JIUGENG_CORPORATE_WEBSITE_PRD_v0.1_DRAFT.md
```

## PJM 发布核验与下一阶段（2026-11-25）

ERP-104 的15个文件原子提交为 `2cf21d9c6f9b5a19eaf1ec7376a539a2acef1e4e`，经[PR #28](https://github.com/puhuisifang-svg/Jiugeng-Amazon-ERP/pull/28)五项必需检查后受保护合并。应用 main 为 `365892ccb4c5d5eb151420bd0cb7c66c739f8586`。

[PR CI](https://github.com/puhuisifang-svg/Jiugeng-Amazon-ERP/actions/runs/45563211636)、[main CI](https://github.com/puhuisifang-svg/Jiugeng-Amazon-ERP/actions/runs/45563565759)与[Production](https://github.com/puhuisifang-svg/Jiugeng-Amazon-ERP/actions/runs/45563565678)成功。PR CI 后端449项零失败/错误、1项OSS条件跳过，前端171文件/795项及浏览器17项通过。未重复运行有效本地门禁。

实际SSH deploy非跳过：北京时间14:56:18归档校验OK，14:56:22生产数据库备份完成，14:56:49部署接收器验证后端健康UP及完整版本。PJM独立读取真实入口`/version.json`为上述应用main，HTTP200，首页200。健康为接收器日志证据，未直查生产数据库或执行恢复演练。本轮无新迁移，未改Caddy、DNS、安全组、8081或默认IP入口；未重启本地服务。

本节是已上线应用的证据归档，不声称包含本节的文档提交已部署。仅归档一次；归档PR、最终main及其Production终态由PJM在`/tmp/jiugeng-pjm-release-progress.md`和最终报告维护，不为文档自身SHA递归创建PR。下方“未提交/待执行”为历史交接快照。

下一阶段分配 **ERP-112｜九更单页静态官网本地开发**（ERP-105～111已被历史Backlog占用，不复用）。以DEL-054正文v0.6为需求基线，独立`website/`目录、静态产物和文档；Logo、视觉和一般文案可自主设计，公司主体及联系方式不编造。开发与本地验证可进行，**不授权本阶段提交、推送、部署、开放端口或切换生产入口**。正式唯一写入槽以PJM收尾消息为准；从届时干净最新main创建`feature/ERP-112-corporate-website`，不沿用历史写入锁。未来上线先验证完整ERP临时8081入口，再切默认IP官网，内部8080/4173和ERP数据保持不变。

