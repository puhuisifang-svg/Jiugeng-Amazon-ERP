# S0-FE-01 前端脚手架与管理端壳交付及验证报告

| 字段 | 内容 |
| --- | --- |
| 项目 | 九更亚马逊 ERP |
| 交付物 ID | DEL-022 |
| Owner | Frontend |
| 关联 Sprint/任务 | Sprint 0 / S0-FE-01 |
| 版本 | v0.1 |
| 状态 | Completed（PJM 核验通过） |
| 日期 | 2026-09-12 |

## 1. 交付范围

- `frontend/`：Vue 3 + TypeScript + Vite + pnpm 独立前端工程。
- 精确固定 `packageManager`、pnpm、框架和质量工具小版本；提交 `pnpm-lock.yaml`。
- Node engine 支持 `>=24 <27`，`.nvmrc` 固定本机已验证的 Node 26.8.2；Node 24 LTS 保留为隔离回退方案。
- 项目和前端工程 `.npmrc` 固定 npm 官方 Registry、精确保存、严格 engine 校验。
- `.env.development` / `.env.production` 分离 API 基地址；生产使用同源 `/api`，无真实密钥与 mock 数据。
- 管理端应用壳、可折叠侧栏、顶部全局搜索/帮助/通知/账号占位、首页、登录、403、404 和模块占位。
- 路由 `meta.roles` 固化三角色菜单边界；正式鉴权和业务动作留待 IAM/API 进入 Ready 后实现。
- ESLint、TypeScript 类型检查、Vitest 和 Vite production build 脚本。

Sprint 0 未实现登录、权限执行、API 请求、导入或其他正式业务功能。

## 2. UI IA 增量对齐

在 UI 交付 `DEL-016 v0.1 / Completed` 出现后完成增量复核，当前壳层已对齐：

- `/home`、`/products`、`/listing-mappings`、`/materials`、`/inventory/balances`、`/inventory/ledger`、`/imports`。
- `/selection/candidates`、`/watch/stores`、`/watch/events`、`/system/users`、`/system/audit`。
- 产品新建/详情、候选详情、导入批次、强制改密和 403 等隐藏子路由占位。
- 选品人员无库存入口；普通岗位无用户管理入口；审计页保留三角色可见元数据。

后续若 IA 发生受控变更，按版本增量调整。

## 3. 固定版本

| 工具 | 版本/约束 |
| --- | --- |
| Node.js | 本机验证 `26.8.2`；engine `>=24.0.0 <27.0.0` |
| pnpm | `12.4.1` |
| Vue | `3.5.42` |
| Vue Router | `5.3.1` |
| Vite | `8.3.0` |
| TypeScript | `6.0.3` |
| Vitest | `5.0.0` |
| ESLint | `10.10.0` |

最初尝试 TypeScript 7.0.2 时，`typescript-eslint` 与 `vue-tsc` 明确不兼容；已降至其支持范围内的 6.0.3 并重新生成锁文件。问题与 Node 26 无关，因此未启用 Node 24 隔离回退，也未改动 OpenClaw 运行时。

## 4. Node 26 验证证据

验证目录：`frontend/`。

```text
$ node --version
v26.8.2

$ pnpm --version
12.4.1

$ pnpm install --frozen-lockfile
Lockfile is up to date, resolution step is skipped
Done in 17ms using pnpm v12.4.1

$ pnpm peers check
No peer dependency issues found

$ pnpm run lint
eslint . --max-warnings=0
PASS

$ pnpm run test
Test Files  1 passed (1)
Tests       4 passed (4)

$ pnpm run build
vue-tsc --noEmit
vite v8.3.0 building client environment for production...
33 modules transformed
dist/assets/index-Brj5fCW2.js  92.73 kB / gzip 36.12 kB
build completed in 69ms

$ curl --head http://127.0.0.1:4173/home
HTTP/1.1 200 OK
Content-Type: text/html
```

最终结论：Node 26.8.2 下 install、peer check、lint、test、typecheck、build 和 production preview HTTP 探针全通过。

### 4.1 PJM 独立复核

2026-09-12，PJM 在 Node 26.8.2 / pnpm 12.4.1 环境独立复跑：

- `pnpm install --frozen-lockfile`、`pnpm peers check`：通过。
- `pnpm run lint`、`pnpm run test`（4/4）、`pnpm run typecheck`、`pnpm run build`：通过。
- `pnpm exec vite preview --host 127.0.0.1` 后请求 `/home`：HTTP 200。
- 前端目录敏感信息模式扫描：未发现真实凭据。

核验结论：DEL-022 通过；真实会话、路由守卫、按钮级权限及 API 联调仍按既定边界转入后续 Sprint。

## 5. 验收映射

| 验收项 | 证据 | 结果 |
| --- | --- | --- |
| Vue 3 + TypeScript + Vite + pnpm | `frontend/package.json`、`vite.config.ts` | PASS |
| packageManager / engines / 锁文件 | `package.json`、`.nvmrc`、`pnpm-lock.yaml` | PASS |
| 路由、布局、模块占位 | `src/router/`、`src/layouts/`、`src/views/` | PASS |
| 角色菜单边界 | `src/router/meta.d.ts`、路由 `meta.roles` | PASS（执行鉴权待 IAM） |
| 环境配置分离 | `.env.development`、`.env.production`、`src/config/runtime.ts` | PASS |
| 质量脚本 | `lint`、`test`、`typecheck`、`build`、`quality` | PASS |
| 最小测试 | 路由跳转、IA 路径、导航组、库存角色共 4 项 | PASS |
| Node 26 全链路 | 本报告 §4 | PASS |

## 6. 风险与后续

- 字段字典仍处于跨角色复核期；变更后需同步页面契约和测试，不提前实现页面字段。
- 当前仅有前端角色元数据，不能替代后端接口鉴权；Sprint 1 联调时必须实现会话、守卫、403 和按钮级权限。
- 生产 API 使用同源 `/api`，部署时由反向代理转发；禁止在生产代码中 hardcode 主机或提交 mock 数据。
