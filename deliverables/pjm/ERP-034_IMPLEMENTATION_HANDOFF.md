# ERP-034 手动添加新品需求实施交接

日期：2026-10-16。分支 `feature/ERP-034-manual-product-development`；PJM 明确批准的例外基线 `fe8a578a3173ca8aca52a915ba1072903170094f`（ERP-033）。实现提交 SHA 随交接消息提供。ERP-033/034 线性链由 PJM 统一复核并处理合并，Codex 未操作 main。

## 变更

- [增量需求与计划](ERP-034_MANUAL_DEVELOPMENT_PLAN.md) 覆盖 ERP-033 的“手动添加后置”限制；保留原候选流程。
- 新品开发列表新增“添加新品需求”，新建页复用完整开发资料表单；仅名称必填，保存为草稿并进入详情。来源标明手动创建，不显示候选评分、ASIN 或虚构链接。
- 后端新增 `POST /api/v1/product-development`，受 products.write 控制；角色权限、后续版本/状态/完成条件不变。
- V26 添加 MANUAL/CANDIDATE 来源约束、手动请求幂等元数据、数据库编号序列；已有记录默认为候选来源。候选唯一约束和外键保持有效。
- 手动编号 `ND-M-00000001` 起；串行锁保证并发编号和创建幂等；请求号、创建者与规范化字段哈希匹配时重放原需求，否则 409。只记录一次创建审计。
- 网络结果不确定时保留提交内容和请求号，重试不会重复建单；确定的校验失败允许原地修改。未保存离开保护沿用现有机制。

主要文件：后端 DevelopmentController/Service/Types、V26、开发与迁移测试；前端 development API、ProductDevelopmentView、路由与组件测试；范围契约/权限矩阵/PRD 补充；真实 MySQL HTTP 门禁。

## 质量门禁

| 门禁 | 结果 |
| --- | --- |
| 后端 `./mvnw clean verify` | PASS，134 tests，无失败/错误/跳过，包含 Modulith 验证 |
| 前端 `pnpm quality` | PASS，43 files / 155 tests，ESLint、vue-tsc、生产构建通过 |
| `python3 scripts/quality/validate-mvp-scope.py` | PASS，契约 v1.5，18 API 资源族 / 27 capabilities |
| `scripts/qa/verify-mysql-profile.sh` | PASS，V26 迁移、手动/候选 HTTP 流程、并发同请求、三角色、独立编号、完成/作废、MySQL 与后端重启持久化；采购和监控回归通过 |
| `scripts/qa/verify-mysql-backup-restore.sh` | PASS，26 migrations，数据库及两类图片恢复一致 |
| 指定真实运行库 `node scripts/qa/verify-development-http.mjs` | PASS，候选与手动完整 HTTP 链路；生成的合成资料已清理 |
| `git diff --check` | PASS；仅 ERP-034 范围变更 |

## 运行环境与页面验证

- 在授权窗口停止原后端，确认 8080 不监听后备份真实库与图片：`.local/backups/erp034/dashboard-20261016T104256Z.tar.gz`；SHA-256 `858b7ae552ae6a4dc7bbf80490cd9c11fc5a2dae42aa340a14db176181459b53`。
- 使用原 `scripts/dev/run-local-mysql-backend.sh` 重启后端，health UP；前端生产构建已更新；既有 Caddy/tunnel 未替换。公网入口仍为 https://andreas-reel-proceedings-problems.trycloudflare.com/product-development 。
- 浏览器使用临时 PRODUCT_MATERIAL 账号，在公网依次验证：列表添加入口→空白新建表单→仅名称保存→独立编号/手动来源/草稿→补充开发员、品名及日期→保存→开始开发→回到列表。无 ASIN/候选依赖，已有候选来源需求仍正常显示。
- 临时账号、业务资料与会话按本轮生成标识清理或停用，保留安全审计。原用户候选和开发资料不修改。测试浏览器页关闭，不改用户原有页面。

## 注意事项与边界

- 手动来源只在创建时由服务端决定，不能转换成候选来源；并不新增 Amazon 采集或多站点功能。
- 相同名称允许代表多个不同需求；只有相同请求号和内容的重试合并。编号不回收，验收清理或事务历史导致空号不影响唯一性。
- 回滚到 ERP-033 应用不安全：其读取逻辑假设来源必有候选，不能读取新的手动行。若出现问题，应停用新增入口并向前修复；保留 V26 和新业务数据，禁止直接恢复旧备份覆盖新增记录。
- 独立手动创建已完成；附件、1688/AI、自定义字段、自动商品/采购建档仍不在范围内。
- 交接后归还独占窗口；正式验收、main 合并和发布结论以 PJM 独立确认记录为准。
