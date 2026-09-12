# 风险登记册

| ID | 风险 | 概率 | 影响 | Owner | 应对 | 状态 |
| --- | --- | --- | --- | --- | --- | --- |
| R-001 | 需求无限对标成熟 ERP，范围失控 | 高 | 高 | PJM/PM | P0/P1/P2 分级，变更审批 | Open |
| R-002 | Amazon 授权、限流或接口变化 | 中 | 高 | Backend | 提前验证、限流重试、补数、契约监控 | Open |
| R-003 | SKU/店铺/仓库主数据混乱 | 高 | 高 | PM/Business Owner | 主数据治理、Owner 和变更流程 | Open |
| R-004 | 财务口径不一致 | 高 | 高 | Finance Key User | 先确认公式、版本化、并行对账 | Open |
| R-005 | 业务关键用户参与不足 | 中 | 高 | PJM | 固定关键用户和每 Sprint UAT | Open |
| R-006 | Token/Key 泄露 | 低 | 极高 | Ops | Secret Store、最小权限、扫描、轮换 | Open |
| R-007 | 云梯/上游代理在 Docker Hub TLS 握手阶段断链，无法拉取公开镜像 | 高 | 高 | PJM/老板 | 老板在电脑前时切换全局加速或将 Docker Hub 域名强制走可用代理，PJM 随后复测拉取 | Open / 待办 |
