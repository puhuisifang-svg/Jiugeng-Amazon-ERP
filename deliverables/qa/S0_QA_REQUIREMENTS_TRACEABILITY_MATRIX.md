# Sprint 0 需求追踪矩阵

| 字段 | 内容 |
| --- | --- |
| 项目 | 九更亚马逊 ERP |
| 交付物 ID | DEL-014 |
| Owner | QA |
| 关联 Sprint/任务 | Sprint 0 / S0-QA-01 |
| 版本 | v0.1 |
| 状态 | Completed（PJM 核验通过） |
| 日期 | 2026-09-12 |

> 状态说明：`Designed` 表示用例和判定已设计但尚未取得动态执行证据；交付后更新为 `Pass / Fail / Blocked / N/A-Approved`。

| 追踪 ID | 基线需求/规则 | 测试 ID | 验证要点 | 责任交付 | 证据 | 状态 |
| --- | --- | --- | --- | --- | --- | --- |
| RTM-001 | S0-PM-01；七业务域字段冻结 | FLD-001 | Product/SKU、Listing、辅料、辅料关系、库存、选品、监控均有字段、类型、必填、枚举、Owner、示例 | PM | 字段字典审查记录 | Designed |
| RTM-002 | S0-PM-02；PRD FR-IMP-001 | FLD-002、FLD-003 | 6 类逻辑模板齐全；表头、类型、必填、枚举、唯一、引用与字典一致 | PM/Backend/Frontend | 模板 diff、样例导入 | Designed |
| RTM-003 | PRD FR-PROD-002 | FLD-004 | 5 店 Seller SKU/ASIN/FNSKU/履约/状态独立，父体仅挂 Product，子体/独立挂 SKU | PM/Backend | 五店样例、DB/API 核对 | Designed |
| RTM-004 | Approved 编码规则 §1～4 | COD-001～007 | 三类编码手工输入、必填、1～64 位、正则、trim、大小写不敏感唯一、停用不复用、修改权限/审计 | PM/Backend/Frontend | UI/API/导入结果、约束测试、审计 | Designed |
| RTM-005 | 权限矩阵 v1.0 | RBAC-001～007 | 三角色对 7 类资源的菜单和 API 能力与管理/操作/查看/无一致 | UI/Frontend/Backend | 角色矩阵执行表、API 自动化 | Designed |
| RTM-006 | PRD FR-IAM-001；权限矩阵 | RBAC-008、RBAC-009 | 直接 URL/API 越权 100% 拦截；普通角色仅看本人审计；任何角色默认不可见密钥值 | Frontend/Backend/Ops | 403/等效响应、审计查询、脱敏截图 | Designed |
| RTM-007 | PRD FR-IMP-001；架构 §4 | IMP-001、IMP-002 | 上传→校验→预览期间业务表零写入；确认后才提交 | Backend/Frontend | DB 前后快照、任务状态 | Designed |
| RTM-008 | PRD FR-IMP-001 | IMP-003～005、IMP-013 | 失败精确到工作表、行号、字段、原因；Issue scope 的机器 Schema 强制对应定位字段；混合错误无静默丢数 | PM/Backend/Frontend | OpenAPI Schema 正反例、错误报告、行数对账 | Designed |
| RTM-009 | 产品数据模型 §5～6；架构 §4 | IMP-006、IMP-007、IMP-009～010、IMP-014～018 | 前五类幂等 Upsert；期初首次/重放/冲突/零值/不可编辑/调整冲销/余额重算及整批回滚符合一次性 OPENING 语义 | Backend | API 并发测试、DB 余额/流水/约束、审计、故障注入与前后 hash | Designed |
| RTM-010 | 验收标准：1,000 行 ≤5 分钟 | IMP-008 | 确认提交至结果文件生成 ≤5 分钟，无丢行 | Backend/Frontend | 计时日志、结果对账 | Designed |
| RTM-011 | PRD FR-WATCH-001 | WAT-001、WAT-002 | 首次完整成功仅建基线；完整性判定覆盖分页/数量/字段/重复等约定 | Backend | 原始证据、hash、任务与基线记录 | Designed |
| RTM-012 | PRD FR-WATCH-001；架构 §4 | WAT-003～005 | 失败、空结果、分页不完整快照保留失败证据但不得提升或覆盖有效基线 | Backend | 故障注入前后基线 ID/hash | Designed |
| RTM-013 | PRD FR-WATCH-001；ERP-110 | WAT-006～008 | 完整差异生成新增事件；同店+ASIN 唯一；重跑/并发不重复；跨店同 ASIN 互不串扰 | Backend | 事件表约束、任务结果、并发测试 | Designed |
| RTM-014 | S0-BE-01；Sprint 退出条件 | WAT-009 | 3 店连续 7 天：合规、覆盖、完整性、稳定性、成本、人工降级结论齐全 | Backend/PJM | 每日报告、Stage Gate 报告 | Designed |
| RTM-015 | S0-FE-01；开发规范 | BLD-001、BLD-002 | 全新目录按锁文件安装，lint/test/build 全通过；仅 pnpm 锁；Node/packageManager 固定 | Frontend | 命令、版本、退出码、产物 | Designed |
| RTM-016 | S0-BE-02；开发规范 | BLD-003、BLD-004 | Java 21 + Maven Wrapper 在全新目录 test/package 通过；Flyway 基线可执行 | Backend | Wrapper/Java 版本、测试、迁移日志 | Designed |
| RTM-017 | S0-OPS-01；架构 §6 | CMP-001～003 | Web/API/MySQL/Redis/对象存储定义匹配；依赖健康；迁移和应用占位可启动 | Ops/Backend/Frontend | Compose config/ps、健康与启动日志 | Designed |
| RTM-018 | S0-OPS-01 | CMP-004、CMP-006～008 | stable gitignored file-secret 在 restart、stop/start、down/up 和受控轮换中不丢失；三类命名卷探针及对象 hash 在容器重建/轮换后保持 | Ops | 权限/摘要、健康状态、前后查询、卷清单、对象 hash、轮换与临时清理证据 | Designed |
| RTM-019 | Secret 规范；环境原则 | CMP-005～007 | 样例仅非敏感配置/SecretRef；stable secret 生命周期受控；日志、镜像层、Compose 展开和 Git 无真实凭据 | Ops | 配置审查、生命周期记录、扫描报告 | Designed |
| RTM-020 | S0-OPS-02；架构 §2 | CI-001～004、CI-007 | PR/推送触发前后端构建测试及敏感扫描；任一故意失败能阻断；所有远程 Action 固定为官方 tag 对应 peeled commit SHA | Ops/Frontend/Backend | CI run、失败控制实验、分支规则、完整 uses 与官方 tag/SHA 映射 | Designed |
| RTM-021 | Secret 规范；开发完成定义 | CI-005～007 | CI 最小权限；仓库工作树/历史/构建产物/日志无真实凭据；Action 不引用可漂移 tag 或 annotated tag object SHA | Ops/PJM/全员 | Workflow 权限、扫描报告、SHA 解析证据 | Designed |
| RTM-022 | S0-UI-01；Sprint 退出条件 | UI-001～005 | 主流程、空态、错误、权限受限、导入状态以及三角色可见性齐全 | UI/QA | 原型走查记录 | Designed |
| RTM-023 | Sprint 0 退出条件 1～6 | GATE-001～006 | 每项均有 Owner、证据、结论；P0=0，P1 无未批准遗留 | QA/PJM/全员 | 验收清单和报告 | Designed |

## 覆盖汇总

| 维度 | 已识别需求 | 已设计覆盖 | 动态通过 | 当前结论 |
| --- | ---: | ---: | ---: | --- |
| S0-QA-01 指定专项 | 10 | 10 | 0 | 设计覆盖 100%，待交付执行 |
| Sprint 0 退出条件 | 6 | 6 | 0 | 全部已映射，尚不可验收 |
| 当前 P0 Backlog（ERP-101～110，与 Sprint 0 有关部分） | 10 | 10 | 0 | 静态可追踪；Backlog 尚为 Draft，动态通过率尚未统计 |

注意：`Designed` 不计入 P0 需求“通过测试”的比例；没有执行证据时动态通过率仍为 0，禁止把设计完成写成验收通过。
