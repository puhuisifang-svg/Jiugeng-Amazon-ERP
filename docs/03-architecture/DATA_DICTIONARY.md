# 数据字典

| 字段 | 内容 |
| --- | --- |
| Owner | PM / Backend |
| 状态 | Draft |

## 数据实体登记

| 实体 | 业务主键 | 来源 | Owner | 保留期 | 敏感级别 | 状态 |
| --- | --- | --- | --- | --- | --- | --- |
| User / Role | 待定义 | 内部 | System Owner | 长期 | 内部 | Draft |
| Product | 产品编码 | 内部 | Product Owner | 长期 | 内部 | Draft |
| Product Variant | 内部 SKU 编码 | 内部 | Product Owner | 长期 | 内部 | Draft |
| Variant Attribute | SKU + 属性编码 | 内部 | Product Owner | 长期 | 内部 | Draft |
| Store Account | 店铺编码 | 内部/Amazon | System Owner | 长期 | 机密 | Draft |
| Amazon Catalog Item | Marketplace + ASIN | Amazon | Product Owner | 长期 | 内部 | Draft |
| Store Listing | 店铺 + Marketplace + Seller SKU | Amazon/内部映射 | Product Owner | 长期 | 内部 | Draft |
| Auxiliary Material | 待定义 | 内部 | Product Owner | 长期 | 内部 | Draft |
| Product Auxiliary Material | 待定义 | 内部 | Product Owner | 长期 | 内部 | Draft |
| Auxiliary Material Inventory | 辅料编码 | 系统计算 | Inventory Owner | 长期 | 内部 | Draft |
| Inventory Transaction | 流水号 | 内部 | Inventory Owner | 长期 | 内部 | Draft |
| Selection Candidate | ASIN + 来源 | 内部/Amazon | Selection Owner | 待定 | 内部 | Draft |
| Watch Store | Amazon 店铺标识 | 内部配置 | Selection Owner | 长期 | 内部 | Draft |
| Store Snapshot / Item | 店铺 + 采集时间 + ASIN | 批准数据源 | Selection Owner | 待定 | 内部 | Draft |
| New Listing Event | 店铺 + ASIN + 首次发现时间 | 系统计算 | Selection Owner | 待定 | 内部 | Draft |

每个字段需记录：名称、类型、是否必填、业务含义、枚举、来源字段、转换规则、示例、敏感级别和变更历史。
