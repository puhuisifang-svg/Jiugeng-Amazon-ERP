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
./mvnw spring-boot:run
```

默认使用内存 H2（MySQL 兼容模式）以验证干净环境启动与迁移。联调 MySQL 8 时由 Secret Store 注入运行时变量后启用：

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=mysql
```

真实凭据不得进入参数、配置、日志或仓库；上述 profile 所需变量应由受保护运行环境注入。

健康检查：`GET /actuator/health`。

## 正式账号登录

默认和 `mysql` profile 暴露 `/api/v1/auth/*` 与 `/api/v1/users/*`。系统不会创建固定账号或保存默认密码。空库首次启动时，可由受保护运行环境注入以下配置，系统只在用户表为空时创建管理员：

- `ERP_BOOTSTRAP_ADMIN_LOGIN`
- `ERP_BOOTSTRAP_ADMIN_DISPLAY_NAME`
- `ERP_BOOTSTRAP_ADMIN_PASSWORD`

MySQL profile 会加载 `ERP_MYSQL_SECRET_DIR` 指向的 config tree，推荐把初始密码放在权限受限的 `bootstrap_admin_password` 文件中，避免密码出现在命令参数或仓库。创建完成后应从运行环境移除该引导密码。

认证使用随机不透明 Bearer token，数据库只保存 token 的 SHA-256 摘要；密码使用 BCrypt。初始密码和管理员重置后的密码会强制用户首次登录改密。连续失败默认 5 次锁定；停用、角色变更和密码重置会吊销该用户的全部活动会话。

前端正式构建使用 `VITE_AUTH_MODE=password`；本地开发如需联调正式 IAM，可覆盖同名变量。管理员可创建账号、分配三类角色、启停、解锁和重置密码。

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
