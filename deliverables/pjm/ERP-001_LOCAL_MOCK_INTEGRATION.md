# ERP-001 本地模拟数据联调

## 状态

- 分支：`feature/ERP-001-mock-integration`
- 结论：本地最小前后端调用链已通过
- 数据性质：`SYNTHETIC` 模拟数据

## 交付范围

- 后端仅在 Spring `mock` profile 下开放 `GET /api/dashboard/summary`。
- 返回待处理导入、库存预警、待复核新品三项模拟摘要，并显式携带 `mockData=true` 与 `source=SYNTHETIC`。
- 前端首页调用该接口，展示加载态、错误态和模拟数据声明。
- 默认 profile 不注册 mock Controller，防止模拟接口进入非 mock 运行环境。

## 本地启动

后端：

```bash
cd deliverables/backend/sprint-0/jiugeng-erp-backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=mock
```

前端：

```bash
cd frontend
pnpm dev
```

## 验证结果

- Backend：`./mvnw test` 通过。
- Frontend：`pnpm run quality` 通过（lint、测试、typecheck、build）。
- 运行验证：mock profile 启动后，接口返回 HTTP 200、正确 CORS 响应头及显式模拟数据标记。
- 服务验证后已优雅停止。

## 验收边界

本交付仅证明本地模拟数据下的前后端调用链可运行，不替代以下门禁：

- 真实脱敏业务样本导入；
- 真实 API、数据库事务、IAM 与 Worker；
- 3 店连续 7 天监控；
- GitHub 在线 CI；
- Sprint 0 正式验收。
