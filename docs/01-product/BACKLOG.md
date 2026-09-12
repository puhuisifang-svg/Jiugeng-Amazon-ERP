# 产品 Backlog

| ID | Epic | 用户故事/需求 | 优先级 | Owner | 依赖 | 验收标准 | 状态 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| ERP-101 | 项目准备 | 确认产品、辅料字段和真实导入样本 | P0 | PM | 产品关键用户 | 字段字典和导入模板由业务确认 | Proposed |
| ERP-102 | 技术验证 | 验证 3 个公开店铺上新数据源 | P0 Spike | Backend/PJM | 样本店铺链接 | 给出覆盖率、稳定性、合规、成本和降级结论 | Proposed |
| ERP-103 | 登录与权限 | 实现登录、账号、四类角色和审计 | P0 | Backend/Frontend | 用户与角色清单 | 越权拦截和关键审计 100% | Proposed |
| ERP-104 | 产品 | 维护产品主档、SKU 和可选 Amazon 标识 | P0 | PM/Backend | 产品样本 | 编码唯一，可增改查、导入导出和追溯 | Proposed |
| ERP-105 | 辅料 | 维护辅料主档 | P0 | PM/Backend | 辅料样本 | 编码唯一，分类和单位无歧义 | Proposed |
| ERP-106 | 产品辅料 | 维护单层产品—辅料关系、用量和损耗率 | P0 | Backend/Frontend | 产品与辅料 | 真实样本关系和用量准确 | Proposed |
| ERP-107 | 辅料库存 | 维护仓库、期初、入库、出库、调整、余额和流水 | P0 | Backend/Frontend | 辅料主档 | 余额与流水一致，负库存拦截 | Proposed |
| ERP-108 | 选品 | 建立选品候选池和评估状态流转 | P0 | PM/Frontend/Backend | 选品字段 | 手工/监控候选统一查重与追溯 | Proposed |
| ERP-109 | 店铺监控 | 配置公开店铺并生成可审计快照 | P0 | Backend | 已批准数据源 | 基线完整，失败不污染有效快照 | Proposed |
| ERP-110 | 上新识别 | 对比快照并生成新品事件 | P0 | Backend | ERP-109 | 新增 ASIN 24 小时内发现且不重复 | Proposed |
| ERP-111 | 试点 | 完成权限、数据、库存、监控回归和用户试用 | P0 | QA/PJM | ERP-103～110 | P0 缺陷清零，老板验收 | Proposed |

状态：`Proposed` → `Ready` → `In Progress` → `In Review` → `Done`。
