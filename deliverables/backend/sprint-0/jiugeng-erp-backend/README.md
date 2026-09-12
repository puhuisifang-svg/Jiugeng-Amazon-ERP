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
