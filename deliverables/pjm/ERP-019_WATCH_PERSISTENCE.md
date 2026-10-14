# ERP-019 店铺监控持久化与候选来源闭环

## 已交付

- V13 持久化监控店铺、运行、快照、有效基线和上新事件；正式库不写入 synthetic seed。
- 正式 `/api/v1/watch` API 支持登记获批来源、人工提交 ASIN 快照、店铺/事件查询和确认候选。
- 首个非空完整快照仅建立基线；后续完整快照按 ASIN 差集生成事件。
- 失败、空结果、数量不符或分页不完整的快照保留原有效基线。
- `store + marketplace + ASIN` 确定事件键，数据库唯一约束保证事件不重复。
- `requestId` 同载荷安全重放，异载荷返回 409；人工快照最多 1000 行并规范化 ASIN。
- 事件确认幂等，候选 ASIN 唯一约束保证只关联一个候选；候选保留 store/run/event 来源链路。
- mock profile 保持原合成功能，正式 API 与 mock API 双向隔离。
- 写操作审计不记录来源 URL、快照原文或其他潜在敏感内容。

## 合规边界与外部待办

- 当前仅提供无网络访问的 `ManualSnapshotCollectorAdapter`，通过既有 `CollectorAdapter` 端口接入。
- **WATCH-001 仍为外部待办**：需业务方提供获批公开店铺链接、采集授权证据及 7 天数据源。
- 未获得 WATCH-001 前不实现网页抓取、不绕过登录/CAPTCHA/限流/反自动化措施，也不伪造 7 天销量、BSR 或评论趋势。
- 后续批准的数据源适配器必须继续经过 `CollectorPolicyGuard` 和同一完整性/基线保护流程。

## 验收

- 后端全量、前端 quality。
- 临时 MySQL V1→V13、重启后 store/run/baseline/event/candidate 关联保持。
- mock 端点在 mysql profile 下保持隔离。
