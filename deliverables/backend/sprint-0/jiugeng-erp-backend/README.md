# 九更 Amazon ERP 后端脚手架

Java 21、Spring Boot 3、Spring Modulith、Flyway。当前已提供模块边界、Collector Spike 契约、工程门禁和 IAM 认证基础。

## 模块边界

- `iam`：IAM / Audit
- `product`：Product / Amazon Mapping
- `materialinventory`：Material / Inventory
- `selection`：Selection
- `watch`：Watch / Collector Adapter
- `importexport`：Import / Export / File

模块间只允许通过公开应用接口或领域事件协作。`ApplicationModulesTest` 在测试阶段校验无环依赖和内部包访问。

## 命令

```bash
./mvnw clean verify
```

应用必须显式选择一个数据源 profile；不带 profile 启动会失败并给出明确提示：

- `mysql`：正式持久化运行，连接 MySQL 8；
- `mock`：本地合成数据演示，使用隔离的内存 H2；
- `test`：自动化测试，使用隔离的内存 H2。

联调 MySQL 8 时由 Secret Store 注入运行时变量后启用：

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=mysql
```

`mysql` profile 要求 `ERP_MYSQL_SECRET_DIR`、`DATABASE_URL` 和 `DATABASE_USERNAME`；密码只从 configtree 的 `mysql_app_password` 文件读取。真实凭据不得进入参数、配置、日志或仓库。

本地已由 Ops 流程启动并验证 `jiugeng-erp-mysql` 后，可在仓库根目录运行：

```bash
./scripts/dev/run-local-mysql-backend.sh
./scripts/dev/smoke-local-mysql-backend.sh
```

启动脚本只连接已健康的 ERP MySQL，不创建、停止或重启 Compose 服务，也不读取或显示密码值。Flyway 在应用启动时校验并应用迁移；数据库连接或迁移失败会阻止应用进入健康状态。

健康检查：`GET /actuator/health`。

## 正式账号登录

非 `mock` profile（正式运行使用 `mysql`）暴露 `/api/v1/auth/*` 与 `/api/v1/users/*`。系统不会创建固定账号或保存默认密码。空库首次启动时，可由受保护运行环境注入以下配置，系统只在用户表为空时创建管理员：

- `ERP_BOOTSTRAP_ADMIN_LOGIN`
- `ERP_BOOTSTRAP_ADMIN_DISPLAY_NAME`
- `ERP_BOOTSTRAP_ADMIN_PASSWORD`

MySQL profile 会加载 `ERP_MYSQL_SECRET_DIR` 指向的 config tree，推荐把初始密码放在权限受限的 `bootstrap_admin_password` 文件中，避免密码出现在命令参数或仓库。创建完成后应从运行环境移除该引导密码。

认证使用随机不透明 Bearer token，数据库只保存 token 的 SHA-256 摘要；密码使用 BCrypt。初始密码和管理员重置后的密码会强制用户首次登录改密。连续失败默认 5 次锁定；停用、角色变更和密码重置会吊销该用户的全部活动会话。

前端正式构建使用 `VITE_AUTH_MODE=password`；本地开发如需联调正式 IAM，可覆盖同名变量。管理员可创建账号、分配三类角色、启停、解锁和重置密码。

## 密码找回

登录页支持通过已登记邮箱或手机号获取验证码并重置密码。密码长度为 6–128 个字符，且必须同时包含字母和数字。验证码只以 BCrypt 摘要保存，10 分钟有效、最多尝试 5 次且成功后立即失效；请求接口对不存在、停用或受限账号返回相同提示，避免泄露账号是否存在。

管理员应先在账号管理页面为用户登记唯一邮箱或手机号。邮件通道使用 SMTP，需要配置标准 Spring Mail 环境变量 `SPRING_MAIL_HOST`、`SPRING_MAIL_PORT`、`SPRING_MAIL_USERNAME`、`SPRING_MAIL_PROPERTIES_MAIL_SMTP_AUTH`、`SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE` 以及 `ERP_PASSWORD_RESET_EMAIL_FROM`，并把 SMTP 密码写入 configtree 的 `smtp_password` 文件。短信通道使用 HTTPS webhook，需要配置 `ERP_PASSWORD_RESET_SMS_WEBHOOK_URL`，并把 Bearer token 写入 configtree 的 `sms_webhook_token` 文件；请求体固定为 `phone`、`code`、`expiresMinutes`。所有密码和 token 必须由 Secret Store 注入，不得进入参数、配置、日志或仓库。

未配置对应投递通道时，密码找回接口返回服务不可用，不会生成或泄露验证码。正式启用前必须分别用目标 SMTP 与短信服务商完成端到端验收。

## 操作审计

非 `mock` profile 暴露只读接口 `GET /api/v1/audit/events`，支持按操作账号、类别、动作、资源、结果和时间范围筛选并分页。具有 `audit.read.all` 的管理员可查看全部记录；只有 `audit.read.self` 的业务角色由后端强制限定为当前用户记录，不能通过查询参数扩大范围。

审计记录采用追加写入，不提供更新或删除接口。账号创建、角色和状态变更、解锁、密码重置与认证安全事件均记录操作人、时间、目标和结果；可表达状态变化的账号操作同时保存脱敏前后快照。密码、密码哈希、会话 token 和 Secret 值不得写入审计表。

后续正式业务模块通过 IAM 模块公开的 `AuditTrail` 边界，在业务写事务内追加 `BUSINESS` 事件；调用方只可提交经过批准且不含敏感值的前后快照。

## 本地模拟联调

仅供本地 MVP 演示，可启用 `mock` profile：

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=mock
```

模拟登录使用 `POST /api/auth/login`，请求体中的 `role` 可为 `ADMIN`、
`PRODUCT_MATERIAL` 或 `SELECTION`。后续请求通过 `Authorization: Bearer <token>` 传递响应中的
固定演示 token。模拟 token 不是凭据，所有响应均带有 `mockData=true` 与 `source=SYNTHETIC`。
该 profile 提供产品/SKU、辅料、库存余额、选品候选和监控事件的内存只读数据；默认 profile
不注册这些接口。演示角色还可按 capability 创建产品和辅料、登记幂等库存流水、流转候选状态、
确认监控事件并关联候选；全部状态只保存在进程内，应用重启后恢复为固定模拟种子数据。
`mock` 与 `mysql` 不得同时启用，避免合成接口连接持久化数据库。
