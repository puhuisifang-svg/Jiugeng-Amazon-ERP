# 风险登记册

| ID | 风险 | 概率 | 影响 | Owner | 应对 | 状态 |
| --- | --- | --- | --- | --- | --- | --- |
| R-001 | 需求无限对标成熟 ERP，范围失控 | 高 | 高 | PJM/PM | P0/P1/P2 分级，变更审批 | Open |
| R-002 | 任意公开店铺没有稳定、合规的商品枚举数据源 | 高 | 高 | Backend/PJM | Sprint 0 用 3 店连续验证 7 天；适配器隔离；第三方 API 优先；人工导入降级 | Open / Stage Gate |
| R-003 | SKU/店铺/仓库主数据混乱 | 高 | 高 | PM/Business Owner | 主数据治理、Owner 和变更流程 | Open |
| R-004 | 财务口径不一致 | 高 | 高 | Finance Key User | 财务已移出 MVP，后续立项时重新评估 | Deferred |
| R-005 | 业务关键用户参与不足 | 中 | 高 | PJM | 固定关键用户和每 Sprint UAT | Open |
| R-006 | Token/Key 泄露 | 低 | 极高 | Ops | Secret Store、最小权限、扫描、轮换 | Open |
| R-007 | 云梯/上游代理在 Docker Hub TLS 握手阶段断链，无法拉取公开镜像 | 高 | 高 | PJM/老板 | 老板在电脑前时切换全局加速或将 Docker Hub 域名强制走可用代理，PJM 随后复测拉取 | Open / 待办 |
| R-008 | 人工输入产品/SKU/辅料编码造成重复或格式混乱 | 中 | 高 | PM/Backend | 必填、正则、大小写不敏感唯一约束、导入预校验、管理员修改审计 | Open |
| R-009 | 真实业务样本或试点用户不能按时提供 | 中 | 高 | PJM/Business Owner | Sprint 0 建立清单和截止点；无样本不得退出字段与导入验收 | Open |
