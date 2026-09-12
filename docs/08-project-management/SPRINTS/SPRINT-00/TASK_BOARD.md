# Sprint 0 任务看板

> 更新时间：2026-09-12；状态以 PJM 核验结果为准。

| Task | Owner | 优先级 | 状态 | 关键依赖/阻塞 |
| --- | --- | --- | --- | --- |
| S0-PJM-01 启动治理、看板与风险跟踪 | PJM | P0 | In Progress | 无 |
| S0-PM-01 字段字典与校验口径 | PM | P0 | In Review（结构已自检） | 跨角色复核；真实脱敏样本 |
| S0-PM-02 六类 Excel 模板 | PM | P0 | In Review（6/6 已生成） | Backend/Frontend/QA 契约复核；真实样本 |
| S0-PM-03 业务输入清单 | PM/PJM | P0 | In Review | 老板/业务关键用户输入待到件 |
| S0-UI-01 低保真 IA 与核心流程 | UI | P0 | Completed（PJM 核验通过） | 真实样本到位后做受控复核 |
| S0-BE-01 3 店监控 Spike | Backend/PJM | P0 | In Progress | 3 个公开店铺链接 |
| S0-BE-02 后端工程脚手架 | Backend | P0 | In Progress | Java 21/Maven |
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

## 验收栏

- [ ] PM 字段与模板通过跨角色复核
- [ ] UI 低保真流程通过 PJM 核验
- [ ] 监控 Spike 完成 7 天并通过 Stage Gate 决策
- [ ] 前后端干净构建/测试通过
- [ ] Compose、迁移、健康检查通过
- [ ] 私有远程、CI 和敏感扫描通过
- [ ] QA 验收包完成
- [ ] 老板验收 Sprint 0
