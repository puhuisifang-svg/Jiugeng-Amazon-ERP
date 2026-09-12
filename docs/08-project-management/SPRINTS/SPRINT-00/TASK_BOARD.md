# Sprint 0 任务看板

> 更新时间：2026-09-12；状态以 PJM 核验结果为准。

| Task | Owner | 优先级 | 状态 | 关键依赖/阻塞 |
| --- | --- | --- | --- | --- |
| S0-PJM-01 启动治理、看板与风险跟踪 | PJM | P0 | In Progress | 无 |
| S0-PM-01 字段字典与校验口径 | PM | P0 | In Review（结构已自检） | 跨角色复核；真实脱敏样本 |
| S0-PM-02 六类 Excel 模板 | PM | P0 | In Review（6/6 已生成） | Backend/Frontend/QA 契约复核；真实样本 |
| S0-PM-03 业务输入清单 | PM/PJM | P0 | In Review | 老板/业务关键用户输入待到件 |
| S0-UI-01 低保真 IA 与核心流程 | UI | P0 | Completed（PJM 核验通过） | 真实样本到位后做受控复核 |
| S0-BE-01 3 店监控 Spike | Backend/PJM | P0 | Blocked（框架/Runbook 静态通过） | 3 个公开店铺链接与数据源审批；首个有效快照后连续 7 天 |
| S0-BE-02 后端工程脚手架 | Backend | P0 | Completed（PJM 核验通过） | `4e4aacb` 已完成真实 MySQL 8 Flyway V1、Jar 启动与 health=UP；后续业务实现转 S0-INT-01 |
| S0-FE-01 前端脚手架与管理端壳 | Frontend | P0 | Completed（PJM 核验通过） | IAM/API Ready 后实现真实会话、路由守卫与按钮权限 |
| S0-QA-01 测试计划与追踪矩阵 | QA | P0 | Completed（PJM 静态核验通过） | 动态验收转 S0-INT-01 |
| S0-OPS-01 Docker Hub 与 Compose | Ops | P0 | In Progress | 可用代理出口 |
| S0-OPS-02 GitHub 私有远程与 CI | Ops/PJM | P0 | In Progress | GitHub 安全授权 |
| S0-INT-01 集成验证与验收包 | QA/PJM/全员 | P0 | In Progress（先做结构契约复核） | 动态验收仍待前置证据 |

## 输入待办

| 输入 | 提供方 | 状态 | 影响 |
| --- | --- | --- | --- |
| 20～50 条脱敏产品/SKU/辅料/期初库存样本 | 老板/业务关键用户 | Waiting | 字段与模板退出 |
| 3 个 Amazon 美国站公开对标店铺链接 | 老板/选品关键用户 | Waiting | 监控 Spike 7 天计时 |
| 首期用户和试点人员清单 | 老板/业务关键用户 | Waiting | IAM 与 UAT 准备 |
| GitHub 安全授权 | 老板/管理员 | Waiting | 私有远程与 CI |
| Docker Hub 可用代理出口 | 老板/Ops | Investigating | Compose 镜像拉取 |

## 静态评审整改

| ID | 问题 | Owner | 严重级别 | 状态 | 关闭条件 |
| --- | --- | --- | --- | --- | --- |
| S0-DOC-01 | `PRODUCT_DATA_MODEL.md` 导入模板仅列 4 类，与 Approved PRD/Sprint 0 的 6 类要求不一致 | PM/Backend | P1 | Closed | v1.1 已补齐六类、依赖顺序及验收表述；QA/PJM 复核通过 |
| S0-DOC-02 | `TEST_STRATEGY.md` 沿用订单、财务、采购、FBA、利润等后置范围，缺少当前 MVP 专项 | QA | P1 | Closed | v0.2 已隔离后置范围并补齐人工编码、三角色、选品去重、监控基线失败保护；QA/PJM 复核通过 |
| S0-DOC-03 | PM 字段字典与 UI 交付物编号冲突，PM 字段字典未单独登记总账 | PJM/PM | P1 | Closed | DEL-013～017 唯一归属及总账登记检索通过 |
| BUG-S0-OPS-001 | 临时 file-secret 删除后 MySQL/MinIO 容器因 bind source 缺失无法再次启动 | Ops | P1 | In Review（Ops 整改已交付） | 稳定 gitignored/0600 本地 secret 生命周期及 restart、stop/start、down/up、轮换重建证据已到；待 QA/PJM 复验关闭 |
| BUG-S0-OPS-002 | pnpm 与 gitleaks Action 固定到 annotated tag object，而非实际 commit SHA | Ops | P1 | In Review（Ops 整改已交付） | pnpm/gitleaks 已改为官方 peeled commit SHA，其他 Actions 为官方 lightweight v4 commit；待 QA 与在线 CI 证据关闭 |
| S0-CONTRACT-01 | DEL-017 缺少可联调的异步导入契约（FE-CONTRACT-01～06） | PM/Backend | P1 | Closed | `106a0e2 + 4ebd2be + 21870a8` 经 Backend/Frontend/PJM/QA 复核通过；运行实现转 S0-INT-01 |
| S0-CONTRACT-02 | 三角色已有页面级矩阵但缺动作级 capability 机器码 | PM/Backend | P1 | Closed | 21 个 capability 与三角色机器映射经 Frontend/PJM/QA 复核通过；真实鉴权实现后移 |
| BE-CONTRACT-P0-01 | DEL-013 通用 Upsert 与期初库存不可覆盖的一次性 `OPENING` 语义冲突 | PM/Backend | P0 | Closed | `106a0e2` 已明确前五类 Upsert/期初一次性幂等命令；Backend/PJM/QA 回归通过 |
| BE-CONTRACT-P0-02 | `opening_quantity >= 0` 与所有库存流水 `quantity > 0` 冲突 | PM/Backend | P0 | Closed | `106a0e2` 已冻结 `OPENING >=0`、其他流水 `>0`；Backend/PJM/QA 回归通过 |
| BE-CONTRACT-P1-01 | DEL-013 依赖图误表达辅料主档依赖 SKU | PM | P1 | Closed | Product 与 AuxiliaryMaterial 两条根链已对齐 DEL-017 DAG；PJM/QA 回归通过 |
| BE-CONTRACT-P1-02 | Listing 负向样例缺重复 FNSKU、同店同 SKU 双 ACTIVE、跨店父体、variation theme 不一致 | PM/QA | P1 | Closed | 4 类合成错误样例已写入脚本与实际 xlsx；PJM/QA 验证 4/4 |
| BE-CONTRACT-P1-03 | `display_order` 缺 Excel 数值校验 | PM | P1 | Closed | `display_order >= 1` whole DataValidation 已落入实际 xlsx；总数 17，PJM/QA 验证通过 |
| QA-CONTRACT-P1-01 | `ImportIssue` 的 SHEET/ROW/FIELD scope 定位字段约束不完整 | Backend/QA | P1 | Closed | `21870a8` Draft 2020-12 合法 4/4、非法 6/6 实例测试经 PJM/QA 独立复验通过 |
| FE-OPENAPI-P1-01 | 上传 202 响应未在机器 schema 固定 `status=QUEUED` | Backend | P1 | Closed | `4ebd2be` 使用专用 QUEUED/WAITING const schema；Frontend/PJM 隔离复验通过 |
| FE-OPENAPI-P1-02 | operation 未绑定可达错误码，缺 500、模板 404 与通用 400 机器映射 | Backend | P1 | Closed | 10/10 operation code/status/response 双向可达且含技术 500；Frontend/PJM 隔离复验通过 |
| FE-OPENAPI-P1-03 | diff 动态字段允许 decimal number/string 双编码，业务键过宽 | Backend | P1 | Closed | tagged canonical scalar、7 类业务键及共享 resource 枚举已收窄；Frontend/PJM 隔离复验通过 |

## 受控范围决定

- `StoreAccount` 在 MVP 由系统预置 5 个公司店铺，不新增 `/system/stores` 管理路由；后续如需界面维护，必须走范围与 IA 变更。

## 验收栏

- [ ] PM 字段与模板通过跨角色复核
- [ ] UI 低保真流程通过 PJM 核验
- [ ] 监控 Spike 完成 7 天并通过 Stage Gate 决策
- [x] 前后端干净构建/测试通过
- [ ] Compose、迁移、健康检查通过
- [ ] 私有远程、CI 和敏感扫描通过
- [ ] QA 验收包完成
- [ ] 老板验收 Sprint 0
