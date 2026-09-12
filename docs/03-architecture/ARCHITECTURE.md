# 总体架构说明

| 字段 | 内容 |
| --- | --- |
| Owner | Backend / Ops |
| 版本 | v0.1 |
| 状态 | Draft |

## 初步架构原则

- 首期采用模块化单体，按领域保持清晰边界。
- Amazon 数据同步与用户请求解耦，使用异步任务和本地业务模型。
- 保留关键 API/报告原始数据，支持补数、重算、对账和审计。
- 财务和库存计算采用可版本化规则。
- 达到明确容量、隔离或团队协作阈值后再拆分服务。

## 首期领域模块

1. IAM：登录、组织、角色、数据范围与审计。
2. Product：SPU/SKU、变体、版本、附件与 Amazon 映射。
3. Material：原料、辅料、包材、单位、替代料与库存流水。
4. BOM/Routing：BOM 版本、工艺路线、工序与质检标准。
5. Production：需求、计划、工单、领退补料、报工、质检与入库。
6. Market Intelligence：观察清单、快照、变化事件、需求主题与机会卡片。
7. Amazon Adapter：5 店授权、Catalog、Listings、Pricing、Notifications、Customer Feedback 与 Brand Analytics 报告。
8. Task/Alert：采集任务、失败重试、告警、分派和处置闭环。

## 初步技术栈

- 前端：Vue 3 + TypeScript
- 后端：Java 21 + Spring Boot 3
- 数据库：MySQL 8
- 缓存/协调：Redis
- 文件：S3 兼容对象存储
- 部署：Docker + CI/CD

## 待产出

- 系统上下文图和容器图
- 领域边界与模块依赖
- Amazon 集成、任务调度与错误补偿设计
- 数据流、权限、安全和审计设计
- 容量、可用性、备份和恢复目标

所有重大架构决定写入 `adr/`。
