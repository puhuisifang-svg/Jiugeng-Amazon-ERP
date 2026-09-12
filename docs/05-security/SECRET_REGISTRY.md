# 密钥登记表

> 本文档只登记元数据和 SecretRef/变量名，严禁填写真实 Token、Key、密码或私钥。

| Secret ID | 逻辑名称 | 用途 | 环境 | Owner | 存放系统 | 引用名 | 允许访问主机 | 轮换/到期 | 状态 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| SEC-001 | AMAZON_SPAPI_LWA_CLIENT_SECRET | SP-API LWA 客户端认证 | Dev/Test/Prod 分离 | Backend/Ops | 待确认 | 待创建 | Amazon 官方域名，待精确确认 | 待定 | Planned |
| SEC-002 | AMAZON_SPAPI_REFRESH_TOKEN | 店铺授权刷新令牌 | 按环境/店铺隔离 | Ops | 待确认 | 待创建 | Amazon 官方域名，待精确确认 | 事件触发 | Planned |
| SEC-003 | DATABASE_PASSWORD | 数据库服务账号密码 | 各环境隔离 | Ops | 待确认 | 待创建 | 数据库内网地址 | ≤90 天 | Planned |
| SEC-004 | REDIS_PASSWORD | Redis 认证 | 各环境隔离 | Ops | 待确认 | 待创建 | Redis 内网地址 | ≤90 天 | Planned |
| SEC-005 | OBJECT_STORAGE_SECRET | 原始报告/文件对象存储 | 各环境隔离 | Ops | 待确认 | 待创建 | 对象存储域名 | ≤90 天 | Planned |
| SEC-006 | JWT_SIGNING_KEY | 内部会话签名 | 各环境隔离 | Backend/Ops | 待确认 | 待创建 | 无外部访问 | ≤90 天 | Planned |

## 登记要求

- Secret ID 创建后保持稳定。
- `存放系统` 只能写平台名称，不写凭据内容。
- `引用名` 只能写 SecretRef 或运行时变量名。
- `允许访问主机` 必须使用精确域名/内网目标，禁止无理由使用通配范围。
- 新增、轮换、吊销均需记录日期和执行人。
