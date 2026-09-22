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
./mvnw spring-boot:run
```

默认使用内存 H2（MySQL 兼容模式）以验证干净环境启动与迁移。联调 MySQL 8 时由 Secret Store 注入运行时变量后启用：

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=mysql
```

真实凭据不得进入参数、配置、日志或仓库；上述 profile 所需变量应由受保护运行环境注入。

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
