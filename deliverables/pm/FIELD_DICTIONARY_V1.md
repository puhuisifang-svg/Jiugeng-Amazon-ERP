# MVP 七域字段字典与业务校验基线

| 字段 | 内容 |
| --- | --- |
| 项目 | 九更亚马逊 ERP |
| 交付物 ID | DEL-013 |
| Owner | PM（字段口径）/ Backend（技术约束）/ 业务关键用户（业务确认） |
| 关联 Sprint/任务 | Sprint 0 / S0-PM-01 |
| 版本 | v1.0 |
| 状态 | In Review（字段结构已冻结，待真实脱敏样本与跨角色复核） |
| 日期 | 2026-09-12 |

## 1. 目标与冻结口径

本基线覆盖 Sprint 0 明确的七个 MVP 业务域：①产品、②内部 SKU、③Amazon 目录与 5 店 Listing、④辅料主档及 SKU—辅料关系、⑤单仓辅料库存、⑥选品候选、⑦公开店铺上新监控。

- `P0` 字段均已给出名称、Owner、必填、唯一、枚举和引用校验；当前无未决 P0 字段。
- 编码、枚举和引用规则同时适用于界面新增/编辑与 Excel 导入。
- 系统字段（主键、审计时间、操作人、导入批次、幂等键）由系统生成，不要求业务在 Excel 中填写。
- 20～50 条真实脱敏样本尚未提供，因此 v1.0 是**结构冻结基线**，不是“真实数据已验收”。样本到位后只允许通过变更记录调整，不直接覆盖本文件。

## 2. 通用校验规则

| 规则 ID | 优先级 | 规则 |
| --- | --- | --- |
| COM-001 | P0 | 文本保存前去除首尾空格；必填文本去空格后为空视为未填写。 |
| COM-002 | P0 | `product_code`、`sku_code`、`material_code`：1～64 位，正则 `^[A-Za-z0-9][A-Za-z0-9_-]{0,63}$`，大小写不敏感判重、保留原始展示。 |
| COM-003 | P0 | 业务编码采用停用，不物理删除；已保存编码仅系统管理员可改，且必须记录原因与审计前后值。 |
| COM-004 | P0 | 引用字段必须指向已存在且未停用的记录；同一导入批次按模板规定顺序解析。 |
| COM-005 | P0 | 所有枚举仅接受本字典列出的机器值；Excel 展示值与机器值一致。 |
| COM-006 | P0 | 日期时间采用 ISO 8601；金额/数量使用十进制定点数，不接受科学计数法。 |
| COM-007 | P0 | 导入先全量校验并预览，提交后逐行返回工作表、行号、字段和原因；不得静默跳过。 |
| COM-008 | P0 | 导入采用幂等 Upsert；唯一键冲突只允许更新同一业务记录，不得生成重复记录。 |
| COM-009 | P0 | 图片字段仅存批准对象存储中的 URL/对象引用；不接收内嵌二进制、账号凭据或本机绝对路径。 |
| COM-010 | P0 | 所有关键新增、修改、停用、库存变动、状态流转记录操作人、时间、来源、导入批次及前后值。 |

## 3. 字段 Owner

| Owner | 负责范围 | 最终确认 |
| --- | --- | --- |
| Product Owner | 产品、SKU、变体、Amazon 映射、辅料主档与关系 | 产品/辅料业务关键用户 |
| Inventory Owner | 单仓辅料余额、期初及流水口径 | 仓库/辅料业务关键用户 |
| Selection Owner | 候选、监控店铺、快照与新品事件 | 选品业务关键用户 |
| System Owner | 店铺配置、系统枚举、权限与审计字段 | 系统管理员/PJM |
| Backend | 数据类型、数据库唯一键、事务、幂等与引用约束实现 | Backend + QA |

## 4. D1 产品域（Product）

| 字段 | 业务名称 | 类型/长度 | 必填 | 唯一/引用 | Owner | 校验、枚举与说明 |
| --- | --- | --- | --- | --- | --- | --- |
| `product_code` | 产品/SPU 编码 | string(64) | 是 | 全局唯一（忽略大小写） | Product Owner | COM-002；人工提供，系统不补号。 |
| `name_zh` | 中文名称 | string(200) | 是 | 否 | Product Owner | 去首尾空格；至少 1 个可见字符。 |
| `name_en` | 英文名称 | string(200) | 否 | 否 | Product Owner | 去首尾空格。 |
| `brand` | 品牌 | string(100) | 否 | 否 | Product Owner | 内部维护；不得由 Amazon 目录反向覆盖。 |
| `internal_category` | 内部类目 | string(100) | 是 | 受控词表（待业务样本复核） | Product Owner | v1.0 先按非空文本校验；业务词表作为 P1 配置，不阻塞 P0 结构。 |
| `product_type` | Amazon Product Type 候选 | string(100) | 否 | 否 | Product Owner | 仅为候选值；后续接入 Amazon 定义时按站点校验。 |
| `description` | 内部描述 | string(2000) | 否 | 否 | Product Owner | 纯文本；Amazon 不得覆盖。 |
| `status` | 产品状态 | enum | 是 | 否 | Product Owner | `DRAFT` / `ACTIVE` / `INACTIVE`；默认 `DRAFT`。 |

**实体约束：** Product 可包含 1..N 个 SKU；停用产品不得新增 SKU/Listing，但历史引用保留。

## 5. D2 内部 SKU 域（ProductVariant / VariantAttribute）

| 字段 | 业务名称 | 类型/长度 | 必填 | 唯一/引用 | Owner | 校验、枚举与说明 |
| --- | --- | --- | --- | --- | --- | --- |
| `sku_code` | 内部 SKU 编码 | string(64) | 是 | 全局唯一（忽略大小写） | Product Owner | COM-002；人工提供，系统不补号。 |
| `product_code` | 所属产品编码 | string(64) | 是 | 引用 Product | Product Owner | 必须引用已存在、未停用产品。 |
| `variant_name` | 规格显示名 | string(200) | 是 | 同 Product 内建议唯一 | Product Owner | 去首尾空格。 |
| `barcode` | UPC/EAN/GTIN | string(32) | 否 | 非空时全局唯一 | Product Owner | 仅数字；长度 8/12/13/14；不把 ASIN/SKU 当条码。 |
| `material` | 主材质 | string(100) | 否 | 否 | Product Owner | 自由文本，后续可配置词表。 |
| `specification` | 内部规格 | string(500) | 否 | 否 | Product Owner | 自由文本。 |
| `item_dimensions` | 产品尺寸 | string(100) | 否 | 否 | Product Owner | 显式带单位，例如 `10 x 20 x 5 cm`；不做数值运算。 |
| `package_dimensions` | 包装尺寸 | string(100) | 否 | 否 | Product Owner | 显式带单位。 |
| `package_weight` | 包装重量 | decimal(12,3) | 否 | 否 | Product Owner | `> 0`；单位固定由 `package_weight_unit` 指定。 |
| `package_weight_unit` | 包装重量单位 | enum | 条件必填 | 否 | Product Owner | `G` / `KG` / `OZ` / `LB`；有重量时必填，无重量时为空。 |
| `status` | SKU 状态 | enum | 是 | 否 | Product Owner | `ACTIVE` / `INACTIVE`；默认 `ACTIVE`。 |
| `attribute_code` | 变体属性编码 | string(64) | 条件必填 | SKU 内唯一 | Product Owner | 与 `attribute_value` 成对出现；如 `color`、`size`、`item_package_quantity`。 |
| `attribute_value` | 变体属性值 | string(200) | 条件必填 | 参与组合唯一 | Product Owner | 与 `attribute_code` 成对出现；同 Product 内完整属性组合不得重复。 |
| `display_order` | 属性显示顺序 | integer | 条件必填 | SKU 内唯一 | Product Owner | `>=1`；填写属性时必填。 |

**实体约束：** 同一 SKU 下 `attribute_code` 唯一；属性集合按编码排序并标准化后，同一 Product 内组合唯一。

## 6. D3 Amazon 目录与 5 店 Listing 域

### 6.1 StoreAccount（系统配置，不通过业务模板新建）

| 字段 | 业务名称 | 类型/长度 | 必填 | 唯一/引用 | Owner | 校验、枚举与说明 |
| --- | --- | --- | --- | --- | --- | --- |
| `store_code` | 店铺编码 | string(32) | 是 | 全局唯一 | System Owner | 首期恰好配置 5 个启用店铺；模板仅允许引用。 |
| `store_name` | 店铺名称 | string(100) | 是 | 否 | System Owner | 内部名称。 |
| `seller_id` | Selling Partner ID | string(64) | 是 | 同站点唯一 | System Owner | 机密级别“内部受限”；不得进入示例文件。 |
| `marketplace_id` | 站点 ID | string(32) | 是 | 与 Seller ID 联合唯一 | System Owner | 美国站固定 `ATVPDKIKX0DER`。 |
| `status` | 店铺状态 | enum | 是 | 否 | System Owner | `ACTIVE` / `INACTIVE`。 |
| `credential_ref` | 凭据引用 | SecretRef | 后续同步时必填 | 引用 Secret Store | System Owner | 只保存引用，禁止明文；MVP 人工导入不要求。 |

### 6.2 AmazonCatalogItem / StoreListing

| 字段 | 业务名称 | 类型/长度 | 必填 | 唯一/引用 | Owner | 校验、枚举与说明 |
| --- | --- | --- | --- | --- | --- | --- |
| `store_code` | 店铺编码 | string(32) | 是 | 引用启用 StoreAccount | Product Owner | 必须是首期 5 店之一。 |
| `marketplace_id` | 站点 ID | string(32) | 是 | 与 ASIN 联合引用 Catalog | System Owner | MVP 固定 `ATVPDKIKX0DER`。 |
| `seller_sku` | Seller SKU/MSKU | string(64) | 是 | `store_code + marketplace_id + seller_sku` 唯一 | Product Owner | 去首尾空格；同店内大小写不敏感判重。 |
| `asin` | ASIN | string(10) | 条件必填 | 引用/Upsert Catalog | Product Owner | 10 位大写字母数字；`DRAFT` 可空，其余状态必填。 |
| `listing_role` | Listing 角色 | enum | 是 | 否 | Product Owner | `STANDALONE` / `PARENT` / `CHILD`。 |
| `product_code` | 内部产品编码 | string(64) | 是 | 引用 Product | Product Owner | 必须存在且未停用。 |
| `sku_code` | 内部 SKU 编码 | string(64) | 条件必填 | 引用 ProductVariant | Product Owner | `PARENT` 必须为空；`CHILD`/`STANDALONE` 必填，且 SKU 属于 `product_code`。 |
| `parent_seller_sku` | 同店父体 Seller SKU | string(64) | 条件必填 | 引用同店 PARENT Listing | Product Owner | `CHILD` 必填；其他角色必须为空。 |
| `variation_theme` | Amazon 变体主题 | string(100) | 条件必填 | 父子一致 | Product Owner | `PARENT`/`CHILD` 必填且父子一致；`STANDALONE` 可空。 |
| `fnsku` | FNSKU | string(32) | 条件必填 | 同店非空时唯一 | Product Owner | `FBA` 且非 `DRAFT` 时必填；`FBM` 必须为空。 |
| `fulfillment_channel` | 履约方式 | enum | 条件必填 | 否 | Product Owner | `FBA` / `FBM`；`PARENT` 必须为空，其他角色必填。 |
| `listing_status` | Listing 状态 | enum | 是 | 否 | Product Owner | `DRAFT` / `SYNCING` / `ACTIVE` / `RESTRICTED` / `DELETED`。 |

**实体约束：** `(marketplace_id, asin)` 在 Catalog 唯一且可被多店复用；同一 SKU 在同一店最多一个 `ACTIVE` Listing；父体只关联 Product，子体/独立 Listing 必须关联 SKU；父子必须同店。

## 7. D4 辅料主档与 SKU—辅料关系域

### 7.1 AuxiliaryMaterial

| 字段 | 业务名称 | 类型/长度 | 必填 | 唯一/引用 | Owner | 校验、枚举与说明 |
| --- | --- | --- | --- | --- | --- | --- |
| `material_code` | 辅料编码 | string(64) | 是 | 全局唯一（忽略大小写） | Product Owner | COM-002；人工提供，系统不补号。 |
| `material_name` | 辅料名称 | string(200) | 是 | 否 | Product Owner | 去首尾空格。 |
| `material_type` | 辅料类型 | enum | 是 | 否 | Product Owner | `LABEL` / `BOX` / `BAG` / `MANUAL` / `INSERT` / `OTHER`。 |
| `specification` | 辅料规格 | string(500) | 否 | 否 | Product Owner | 自由文本。 |
| `base_unit` | 基础计量单位 | enum | 是 | 否 | Product Owner | `PCS` / `SET` / `ROLL` / `SHEET` / `M` / `KG`。 |
| `image_url` | 图片 | string(500) | 否 | 否 | Product Owner | HTTPS 或批准对象存储引用；COM-009。 |
| `status` | 辅料状态 | enum | 是 | 否 | Product Owner | `ACTIVE` / `INACTIVE`。 |
| `remark` | 备注 | string(1000) | 否 | 否 | Product Owner | 纯文本。 |

### 7.2 ProductAuxiliaryMaterial

| 字段 | 业务名称 | 类型/长度 | 必填 | 唯一/引用 | Owner | 校验、枚举与说明 |
| --- | --- | --- | --- | --- | --- | --- |
| `sku_code` | 内部 SKU 编码 | string(64) | 是 | 引用启用 SKU | Product Owner | 不得使用 ASIN/Seller SKU。 |
| `material_code` | 辅料编码 | string(64) | 是 | 引用启用辅料 | Product Owner | 与 SKU 组成唯一键。 |
| `standard_quantity` | 标准用量 | decimal(12,4) | 是 | 否 | Product Owner | `>0`。 |
| `unit` | 用量单位 | enum | 是 | 否 | Product Owner | 必须等于辅料 `base_unit`；MVP 不做换算。 |
| `loss_rate` | 损耗率 | decimal(7,4) | 是 | 否 | Product Owner | 取值 `0..1`，例如 2% 填 `0.02`。 |
| `enabled` | 是否启用 | boolean | 是 | 否 | Product Owner | `TRUE` / `FALSE`。 |
| `remark` | 备注 | string(1000) | 否 | 否 | Product Owner | “应用到全部 SKU”后仍生成每个 SKU 的独立关系。 |

**实体约束：** `sku_code + material_code` 唯一；不支持多层 BOM、替代料、版本和成本。

## 8. D5 单仓辅料库存域

### 8.1 AuxiliaryMaterialInventory / Initial Balance

| 字段 | 业务名称 | 类型/长度 | 必填 | 唯一/引用 | Owner | 校验、枚举与说明 |
| --- | --- | --- | --- | --- | --- | --- |
| `warehouse_code` | 仓库编码 | string(32) | 是 | 引用固定单仓 | Inventory Owner | MVP 仅允许系统配置的唯一启用仓库。 |
| `material_code` | 辅料编码 | string(64) | 是 | 单仓内唯一/引用辅料 | Inventory Owner | 必须存在且未停用。 |
| `opening_quantity` | 期初数量 | decimal(18,4) | 是 | 否 | Inventory Owner | `>=0`；单位为辅料基础单位。 |
| `safety_stock` | 安全库存 | decimal(18,4) | 是 | 否 | Inventory Owner | `>=0`；余额低于该值触发提示。 |
| `as_of_date` | 期初日期 | date | 是 | 否 | Inventory Owner | 不得晚于导入提交日期。 |
| `opening_reference` | 期初依据号 | string(100) | 是 | 单仓内唯一 | Inventory Owner | 脱敏盘点表/迁移批次标识；不得包含敏感原文。 |
| `remark` | 备注 | string(1000) | 否 | 否 | Inventory Owner | 纯文本。 |

### 8.2 InventoryTransaction（系统记账字段）

| 字段 | 业务名称 | 类型/长度 | 必填 | 唯一/引用 | Owner | 校验、枚举与说明 |
| --- | --- | --- | --- | --- | --- | --- |
| `transaction_no` | 流水号 | string(64) | 是 | 全局唯一 | Backend | 系统生成，不由 Excel 提供。 |
| `material_code` | 辅料编码 | string(64) | 是 | 引用辅料 | Inventory Owner | 必须与余额记录一致。 |
| `transaction_type` | 变动类型 | enum | 是 | 否 | Inventory Owner | `OPENING` / `INBOUND` / `OUTBOUND` / `ADJUST_IN` / `ADJUST_OUT` / `REVERSAL`。 |
| `quantity` | 变动数量 | decimal(18,4) | 是 | 否 | Inventory Owner | 存正数，方向由类型决定；`>0`。 |
| `business_reference` | 业务依据号 | string(100) | 是 | 类型+依据号幂等唯一 | Inventory Owner | 重复请求不得重复记账。 |
| `reason` | 原因 | string(500) | 条件必填 | 否 | Inventory Owner | 调整/冲销必填。 |
| `occurred_at` | 发生时间 | datetime | 是 | 否 | Inventory Owner | ISO 8601。 |

**实体约束：** `现存 = 期初 + 入库 - 出库 ± 调整`；默认禁止负库存；流水不可编辑/删除，纠错通过反向流水；余额与流水在同一事务中原子更新。

## 9. D6 选品候选域

| 字段 | 业务名称 | 类型/长度 | 必填 | 唯一/引用 | Owner | 校验、枚举与说明 |
| --- | --- | --- | --- | --- | --- | --- |
| `candidate_code` | 候选编号 | string(64) | 是 | 全局唯一 | Selection Owner | 系统生成；导入/界面展示但不可改。 |
| `marketplace_id` | 站点 ID | string(32) | 是 | 否 | Selection Owner | MVP 固定美国站。 |
| `asin` | ASIN | string(10) | 条件必填 | 美国站优先查重 | Selection Owner | 有 ASIN 时按 `marketplace_id + asin` 查重。 |
| `normalized_url` | 标准化商品 URL | string(1000) | 条件必填 | ASIN 为空时暂存查重 | Selection Owner | ASIN 与 URL 至少一个；去追踪参数后标准化。 |
| `title` | 标题 | string(500) | 是 | 否 | Selection Owner | 去首尾空格。 |
| `image_url` | 图片 | string(500) | 否 | 否 | Selection Owner | HTTPS/批准对象存储引用。 |
| `category` | 类目 | string(200) | 否 | 否 | Selection Owner | 来源类目或人工分类。 |
| `price` | 发现价格 | decimal(18,2) | 否 | 否 | Selection Owner | `>=0`；与币种成对。 |
| `currency` | 币种 | enum | 条件必填 | 否 | Selection Owner | MVP `USD`；有价格时必填。 |
| `rating` | 评分 | decimal(2,1) | 否 | 否 | Selection Owner | `0..5`。 |
| `review_count` | 评论数 | integer | 否 | 否 | Selection Owner | `>=0`。 |
| `source_type` | 来源类型 | enum | 是 | 否 | Selection Owner | `MANUAL` / `WATCH_EVENT`。 |
| `source_store_id` | 来源监控店铺 | id | 条件必填 | 引用 WatchStore | Selection Owner | `WATCH_EVENT` 必填；`MANUAL` 可空。 |
| `source_event_id` | 来源新品事件 | id | 条件必填 | 引用 NewListingEvent | Selection Owner | `WATCH_EVENT` 必填。 |
| `discovered_at` | 发现时间 | datetime | 是 | 否 | Selection Owner | ISO 8601。 |
| `tags` | 标签 | string(500) | 否 | 否 | Selection Owner | 逗号分隔仅为导入交换格式，系统内结构化保存。 |
| `owner_user_id` | 负责人 | id | 否 | 引用启用用户 | Selection Owner | 仅选品角色或管理员。 |
| `selection_reason` | 选品理由 | string(2000) | 否 | 否 | Selection Owner | 纯文本。 |
| `status` | 候选状态 | enum | 是 | 否 | Selection Owner | `PENDING` / `EVALUATING` / `APPROVED` / `REJECTED`。 |
| `rejection_reason` | 淘汰原因 | string(1000) | 条件必填 | 否 | Selection Owner | 状态为 `REJECTED` 时必填，其他状态为空。 |

## 10. D7 公开店铺上新监控域

### 10.1 WatchStore / Snapshot / Item / Event

| 字段 | 业务名称 | 类型/长度 | 必填 | 唯一/引用 | Owner | 校验、枚举与说明 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `watch_store_code` | 监控店铺编码 | string(64) | 是 | 全局唯一 | Selection Owner | 内部编码，不等同公司 5 店 `store_code`。 |
| `store_url` | 公开店铺 URL | string(1000) | 是 | 标准化后唯一 | Selection Owner | 仅 Amazon 美国站公开店铺；不得需要登录/验证码绕过。 |
| `data_source_type` | 数据源类型 | enum | 是 | 否 | Selection Owner | `APPROVED_API` / `MANUAL_IMPORT`；未通过评审不得标记 API。 |
| `status` | 监控店铺状态 | enum | 是 | 否 | Selection Owner | `DRAFT` / `ACTIVE` / `PAUSED` / `DISABLED`。 |
| `snapshot_id` | 快照编号 | string(64) | 是 | 全局唯一 | Backend | 系统生成。 |
| `collected_at` | 采集时间 | datetime | 是 | 否 | Selection Owner | ISO 8601。 |
| `snapshot_status` | 快照状态 | enum | 是 | 否 | Selection Owner | `SUCCESS_COMPLETE` / `SUCCESS_INCOMPLETE` / `EMPTY` / `FAILED`。 |
| `completeness_evidence` | 完整性证据 | json/string | 是 | 否 | Backend | 页数、条目数、分页结束标志、源请求追踪；不存密钥。 |
| `asin` | 快照商品 ASIN | string(10) | 是 | `snapshot_id + asin` 唯一 | Selection Owner | 10 位大写字母数字。 |
| `source_item_url` | 来源商品 URL | string(1000) | 否 | 否 | Selection Owner | 公开 URL。 |
| `event_id` | 新品事件编号 | string(64) | 是 | 全局唯一 | Backend | 系统生成。 |
| `first_seen_at` | 首次发现时间 | datetime | 是 | 否 | Selection Owner | 来源于首个完整成功快照。 |
| `event_status` | 事件状态 | enum | 是 | 否 | Selection Owner | `PENDING_REVIEW` / `CONFIRMED_NEW` / `FALSE_POSITIVE` / `ADDED_TO_CANDIDATE`。 |

**实体约束：** 首个 `SUCCESS_COMPLETE` 快照只建立基线；仅后续完整成功快照参与差异；`FAILED`/`EMPTY`/`SUCCESS_INCOMPLETE` 不得覆盖基线；`watch_store_code + marketplace_id + asin` 只产生一次首发事件；监控事件转候选时保留引用。

## 11. 跨域导入顺序与数据库兜底

```text
Product → SKU/Variant → Auxiliary Material → SKU—Material Relation
        ↘ Store Listing
Auxiliary Material → Initial Inventory
```

| 约束 | 应用层 | 数据库 |
| --- | --- | --- |
| 三类业务编码全局唯一 | 预校验并定位行 | 大小写不敏感唯一索引 |
| Listing 同店 Seller SKU 唯一 | 预校验/Upsert | `(store_id, marketplace_id, normalized_seller_sku)` 唯一 |
| SKU 变体组合唯一 | 规范化属性集合比对 | 组合哈希唯一索引（Product 范围） |
| SKU—辅料唯一 | 批次内和库内查重 | `(variant_id, auxiliary_material_id)` 唯一 |
| 期初库存唯一 | 一辅料一条期初 | `(warehouse_id, auxiliary_material_id)` 唯一 |
| 库存不为负 | 提交前校验 | 事务锁/乐观锁 + Check/业务约束 |
| 新品事件不重复 | 差异计算去重 | `(watch_store_id, marketplace_id, asin)` 唯一 |

## 12. 验证过程与结果

- 已逐条对照 PRD v1.0、产品数据模型 v1.0、产品/辅料编码规则 v1.0 和 Sprint 0 计划。
- 六类 Excel 模板字段均可回溯到本字典，生成脚本执行结构化校验。
- P0 未决字段：0；P1 待业务词表项：内部类目、部分自由文本规格的后续受控词表。
- 未执行真实样本导入：20～50 条脱敏样本未提供。

## 13. 已知限制与风险

1. 未获得真实脱敏样本，长度、历史编码和单位词表仍可能触发受控变更。
2. 5 个公司店铺的真实 `store_code` 与固定单仓 `warehouse_code` 尚未确认；模板采用占位提示，不把测试值当业务主数据。
3. 选品与监控域不在本轮六类 Excel 模板范围内，仅冻结字段口径。

## 14. 评审记录

| 评审人 | 结论 | 日期 | 意见 |
| --- | --- | --- | --- |
| PM 自检 | 通过 | 2026-09-12 | 七域、Owner、必填/唯一/枚举/引用规则已覆盖。 |
| Backend/Frontend/QA/PJM | 待评审 | - | 待契约与模板复核。 |
