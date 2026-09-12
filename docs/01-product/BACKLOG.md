# 产品 Backlog

| ID | Epic | 用户故事/需求 | 优先级 | Owner | 依赖 | 验收标准 | 状态 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| ERP-001 | 项目准备 | 梳理产品、物料、BOM 和生产现状流程 | P0 | PM | 产品/生产关键用户 | As-Is/To-Be 由业务 Owner 确认 | Proposed |
| ERP-002 | 数据接入 | 验证美国站 5 店 Private SP-API 授权方案 | P0 | Backend | Amazon 主账号 | 权限、授权、限流和凭据托管方案明确 | Proposed |
| ERP-003 | 产品 | 建立 SPU/SKU/变体与 Amazon 标识映射 | P0 | PM/Backend | 产品样本 | 样本无歧义、可导入、可追溯 | Proposed |
| ERP-004 | 登录与权限 | 实现登录、用户、角色、数据范围和审计 | P0 | Backend/Frontend | 组织与岗位清单 | 越权拦截和关键审计 100% | Proposed |
| ERP-005 | 产品 | 管理产品生命周期、版本、附件和变更 | P0 | PM/Backend | 产品流程 | 已发布版本不可直接覆盖 | Proposed |
| ERP-006 | 物料 | 建立原料、辅料、包材和单位体系 | P0 | PM/Backend | 物料样本 | 编码唯一、分类与换算无歧义 | Proposed |
| ERP-007 | BOM | 支持多层 BOM、版本、替代料和用量展开 | P0 | Backend | 产品/物料主数据 | 代表性 BOM 与人工标准一致 | Proposed |
| ERP-008 | 生产 | 建立计划、工单、领退补料和报工闭环 | P0 | Backend/Frontend | BOM/库存 | 代表工单端到端完成 | Proposed |
| ERP-009 | 质量 | 支持质检、返工、报废和批次追溯 | P0 | PM/Backend | 工序与质检标准 | 代表批次可双向追溯 | Proposed |
| ERP-010 | 选品 | 建立店铺、ASIN、关键词观察清单 | P1 | PM/Backend | 监控样本 | 对象可分组、分派和配置频率 | Proposed |
| ERP-011 | 选品 | 验证对标店铺上新监控的合规数据源 | P0 Spike | Backend/PJM | 样本店铺 | 给出可行性、成本、稳定性和降级方案 | Proposed |
| ERP-012 | 监控 | 建立自有 Listing 与对标 ASIN 快照/变化事件 | P1 | Backend | SP-API/数据源 | 变更有前后值、时间和处理状态 | Proposed |
| ERP-013 | 洞察 | 建立评论、退货和搜索需求分析 | P1 | PM/Backend | Brand Analytics 权限 | 结论可追溯到样本与数据源 | Proposed |

状态：`Proposed` → `Ready` → `In Progress` → `In Review` → `Done`。
