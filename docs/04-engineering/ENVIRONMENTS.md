# 环境说明

> 本机当前状态与缺口见 [本地开发环境审计报告](LOCAL_ENVIRONMENT_AUDIT.md)。

| 环境 | 用途 | 数据要求 | 部署方式 | Owner | 状态 |
| --- | --- | --- | --- | --- | --- |
| Local | 本地开发 | 伪造/脱敏数据 | 待定 | Dev | Draft |
| Dev | 集成开发 | 伪造/脱敏数据 | CI/CD | Ops | Draft |
| Test | QA 与数据对账 | 脱敏样本 | CI/CD | Ops/QA | Draft |
| Staging | 上线前验证 | 受控生产镜像数据 | CI/CD | Ops | Draft |
| Production | 正式内部使用 | 生产数据 | 受控发布 | Ops | Draft |

## 环境原则

- 不共享生产凭据和非生产凭据。
- 非生产环境原则上不得使用真实个人信息。
- 配置与密钥分离，密钥由 Secret Store 或平台变量注入。
- 生产变更必须有发布单、备份/回滚方案和审计记录。
