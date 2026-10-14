# 九更 Amazon ERP 后端脚手架

Java 21、Spring Boot 3、Spring Modulith、Flyway。Sprint 0 只定义模块边界、Collector Spike 契约和工程门禁，不实现正式业务功能。

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

健康检查：`GET /actuator/health`。除健康检查外，本脚手架不暴露业务 API。

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
