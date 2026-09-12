# Sprint 0 执行计划

| 字段 | 内容 |
| --- | --- |
| Sprint | Sprint 0 |
| 状态 | In Progress |
| 启动日期 | 2026-09-12 |
| 参考周期 | 1 周；监控 Spike 自首个有效快照起连续观察 7 天 |
| Sponsor / 验收人 | 老板 |
| Owner | Jiugeng_PJM |
| 范围基线 | MVP 最终规划 v1.0 |

## 1. Sprint 目标

1. 冻结产品、SKU、5 店 Listing、辅料关系、单仓库存、选品和监控的字段与 Excel 模板。
2. 完成登录及核心业务的低保真流程，消除研发对页面状态和交互的歧义。
3. 对 3 个 Amazon 美国站公开店铺启动连续 7 天的数据源 Spike，形成合规、覆盖率、稳定性、成本与降级结论。
4. 建立可构建、可测试、可启动的前后端脚手架和本地 Compose 环境，完成私有远程与 CI 基础收口。

## 2. 前置输入

- 20～50 条脱敏产品/SKU、辅料关系及期初库存样本。
- 3 个公开 Amazon 美国站对标店铺链接，建议覆盖小、中、大三种商品规模。
- 首期用户与试点人员清单。
- GitHub 安全授权；所有凭据仅进入 Secret Store 或受保护变量。

缺失输入不阻塞无依赖任务启动，但会阻塞对应任务验收与 Sprint 退出。

## 3. 范围

- 字段字典、字段 Owner、样本数据清单、Excel 导入模板和校验口径。
- 登录、产品/SKU、Listing 映射、辅料、库存、选品、监控的低保真流程与异常状态。
- `CollectorAdapter` Spike、样本店铺快照、完整性判定、差异识别与 7 天观察报告。
- Vue 3 + TypeScript + Vite + pnpm 前端脚手架。
- Java 21 + Spring Boot 3 + Maven Wrapper 模块化单体后端脚手架。
- MySQL 8、Redis、S3 兼容存储的 Docker Compose，健康检查、非敏感配置样例和 CI 基线。
- Sprint 0 测试计划、验收用例、构建与环境证据。

## 4. 不在范围

- 正式业务功能实现和生产数据迁移。
- 5 个公司店铺 SP-API 自动同步。
- 生产、采购、成品库存、财务、广告、多层 BOM、多仓、批次/效期。
- 绕过登录、验证码、访问控制或反爬机制的采集方案。

## 5. 任务

| Task | Owner | 依赖 | 交付物 | 验收标准 | 状态 |
| --- | --- | --- | --- | --- | --- |
| S0-PJM-01 启动治理、看板、依赖与风险跟踪 | PJM | 无 | Sprint 计划、看板、日报/决策记录 | 范围、Owner、依赖和验收口径可追溯 | In Progress |
| S0-PM-01 冻结字段字典和业务校验 | PM | 业务样本 | 字段字典 v1、字段 Owner、校验规则 | 覆盖 MVP 七个业务域，无未决 P0 字段 | In Progress |
| S0-PM-02 产出 6 类 Excel 导入模板与样例 | PM | S0-PM-01 | 产品/SKU、Listing、辅料、辅料关系、期初库存模板 | 必填、唯一性、引用关系和错误示例明确 | In Progress |
| S0-PM-03 建立业务输入清单 | PM/PJM | 老板/业务关键用户 | 样本、3 店链接、首期用户清单 | 输入有 Owner、状态和截止点 | In Progress |
| S0-UI-01 低保真信息架构与核心流程 | UI | PRD v1.0 | 登录及核心模块原型 | 主流程、空态、错误、权限受限和导入状态完整 | In Progress |
| S0-BE-01 监控数据源 Spike 与 7 天观察 | Backend/PJM | 3 个样本店铺 | 适配器原型、原始证据、每日结果、结论报告 | 说明合规、覆盖、完整性、稳定性、成本及人工降级 | In Progress |
| S0-BE-02 后端模块化单体脚手架 | Backend | Java 21/Maven | Spring Boot 工程、Maven Wrapper、模块边界、Flyway 基线、测试 | Wrapper 构建和测试通过；无真实密钥 | In Progress |
| S0-FE-01 前端脚手架与管理端壳 | Frontend | UI 信息架构 | Vue/TS/Vite 工程、路由、布局、质量脚本 | Node 兼容性、安装、lint、test、build 全通过 | In Progress |
| S0-QA-01 Sprint 0 测试与验收设计 | QA | 计划/PRD | 测试计划、需求追踪矩阵、验收清单 | 覆盖字段、模板、权限、监控失败保护和工程门禁 | In Progress |
| S0-OPS-01 Docker Hub 链路与 Compose 环境 | Ops | 可用代理出口 | TLS 诊断、Compose、健康检查、配置说明 | MySQL/Redis/对象存储健康，重启后数据持久 | In Progress |
| S0-OPS-02 GitHub 私有远程与 CI 基线 | Ops/PJM | GitHub 安全授权 | 私有 remote、保护策略建议、CI | main 推送成功；构建/测试/敏感扫描可运行 | In Progress |
| S0-INT-01 集成验证与 Sprint 验收包 | QA/PJM/全员 | 上述任务 | 验收报告、风险结论、Sprint 1 Ready 清单 | 所有退出条件有证据，遗留有 Owner 和批准 | Pending |

## 6. 依赖与并行策略

- PM、UI、Backend Spike、工程脚手架、QA 设计与 Ops 环境并行启动。
- PM 字段基线完成后，Backend/Frontend/QA 做一次契约复核。
- Ops Compose Ready 后，Backend 执行数据库迁移和启动验证，Frontend 执行联调占位验证。
- Spike 必须从首个通过完整性校验的有效快照开始计算连续 7 天；失败或不完整快照不得覆盖基线。

## 7. 风险与门禁

| 风险 | 应对 | Owner |
| --- | --- | --- |
| 样本或店铺链接迟交 | 建立输入清单；缺失项不得虚假验收 | PJM/PM |
| 公开店铺数据源不稳定或不合规 | 第三方 API 优先；适配器隔离；人工导入降级 | Backend/PJM |
| Docker Hub TLS 仍失败 | 保留诊断证据，评估批准的镜像源或本机依赖替代，不降低安全要求 | Ops |
| 脚手架提前实现业务导致返工 | Sprint 0 只建边界、契约与工程门禁 | Backend/Frontend |
| Secret 泄露 | 只使用 SecretRef/受保护变量，提交前扫描 | Ops/全员 |

## 8. Sprint 退出条件

1. 字段字典与 6 类导入模板由 PM 自检、Backend/Frontend/QA 复核，PJM 核验。
2. 低保真原型覆盖核心流程、异常状态和三角色可见性。
3. 3 店 Spike 完成连续 7 天观察，并给出“自动化路线 / 降级人工导入”的明确 Stage Gate 建议。
4. 前后端均可从干净环境安装、构建和测试；Maven Wrapper 与 pnpm 锁文件已提交。
5. Compose 依赖健康，迁移可执行；私有远程和 CI 基线可用，仓库无真实凭据。
6. QA 验收清单完成，P0 阻塞清零或经老板明确批准降级；由老板验收后才能进入 Sprint 1。

## 9. 汇报规则

- 各 Owner 仅向 PJM 汇报；PJM 汇总对老板负责。
- 每次汇报必须包含：状态、交付物路径、验证证据、风险/阻塞、下一步。
- 完成不等于验收；PJM 核验证据后更新看板。
