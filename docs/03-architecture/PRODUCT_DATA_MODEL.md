# 产品与 Amazon 多店铺数据模型

| 字段 | 内容 |
| --- | --- |
| 项目 | 九更亚马逊 ERP |
| 交付物 ID | DEL-006 |
| 版本 | v1.1 |
| 状态 | In Review（S0-DOC-01 补齐六类导入，待 QA 复核） |
| Owner | PM / Backend |
| 日期 | 2026-09-12 |
| 适用范围 | Amazon 美国站、5 个公司店铺 |

## 1. 设计结论

产品模型分为三个层次，禁止混成一张表：

1. **内部产品层**：公司自己的产品款式（SPU）和可销售规格（内部 SKU），是产品、规格和辅料关系的权威来源。
2. **Amazon 目录层**：Amazon Catalog Item，以“站点 + ASIN”唯一；父 ASIN 用于组织变体，子 ASIN/独立 ASIN 对应可售商品。
3. **店铺 Listing 层**：每个 Seller Account 自己的 Seller SKU、Listing、FNSKU、履约方式和状态。同一内部 SKU 可分别映射 5 个店铺的 Listing。

核心原则：

- 一个内部产品（SPU）包含一个或多个内部 SKU。
- 内部 SKU 表示真实可生产、包装和销售的最小规格，例如颜色、尺寸组合。
- Amazon 父子体是渠道结构，不等同于内部 SPU/SKU，必须独立建模。
- ASIN 属于 Amazon 目录；Seller SKU/Listing 属于具体店铺。不同店铺可销售同一 ASIN，但各自拥有独立 Seller SKU、FNSKU、价格、履约和 Listing 状态。
- MVP 通过界面和 Excel 导入维护 Amazon 映射，不接入 SP-API 自动同步。后续引入同步时，Amazon 数据不得直接覆盖内部产品主档；应先保存来源、时间和原始快照，再按明确映射更新渠道字段。

关系图：[打开产品数据模型图](diagrams/PRODUCT_MODEL.html)。

## 2. 核心实体

### 2.1 Product：内部产品/SPU

表示同一产品设计或款式，不直接作为库存和 Amazon 可售单元。

| 字段 | 说明 |
| --- | --- |
| `id` | 内部主键 |
| `product_code` | 公司产品编码，由业务人员手动输入，全局唯一 |
| `name_zh` / `name_en` | 中英文产品名称 |
| `brand` | 品牌 |
| `internal_category` | 公司内部类目 |
| `product_type` | 对应的 Amazon Product Type 候选值 |
| `description` | 内部产品描述 |
| `status` | 草稿、启用、停用 |
| `created_by/at`、`updated_by/at` | 审计字段 |

### 2.2 ProductVariant：内部 SKU

表示可独立配置辅料并映射 Amazon Listing 的具体规格。

| 字段 | 说明 |
| --- | --- |
| `id` / `product_id` | 主键及所属产品 |
| `sku_code` | 内部 SKU 编码，由业务人员手动输入，全局唯一 |
| `variant_name` | 规格显示名 |
| `barcode` | UPC/EAN/GTIN 等商品编码，可空但需唯一性校验 |
| `material` / `specification` | 材质和内部规格 |
| `item_dimensions` | 产品尺寸 |
| `package_dimensions` / `package_weight` | 包装尺寸与重量 |
| `status` | 启用、停用 |

颜色、尺寸、数量等变体轴使用 `VariantAttribute` 键值表维护，例如 `color=Black`、`size=Large`。不把所有类目的变化属性硬编码成固定列。

### 2.3 VariantAttribute：变体属性

| 字段 | 说明 |
| --- | --- |
| `variant_id` | 内部 SKU |
| `attribute_code` | 属性编码，如 `color`、`size`、`item_package_quantity` |
| `attribute_value` | 属性值 |
| `display_order` | 展示顺序 |

同一内部 SKU 下 `attribute_code` 唯一；同一 Product 内不得出现完全相同的变体属性组合。

### 2.4 StoreAccount：Amazon 店铺

首期固定美国站 5 个店铺。

| 字段 | 说明 |
| --- | --- |
| `id` | 内部主键 |
| `store_code` / `store_name` | 公司店铺编码与名称 |
| `seller_id` | Amazon Selling Partner ID |
| `marketplace_id` | 美国站固定为 `ATVPDKIKX0DER` |
| `status` | 启用、停用 |
| `credential_ref` | Secret Store 引用；不保存凭据明文 |

### 2.5 AmazonCatalogItem：Amazon 目录商品

| 字段 | 说明 |
| --- | --- |
| `marketplace_id` / `asin` | 联合唯一键 |
| `parent_asin` | 父 ASIN，可空 |
| `variation_type` | PARENT、CHILD、NONE |
| `variation_theme` | Amazon 变体主题 |
| `title` / `brand` / `product_type` | Amazon 目录字段 |
| `attributes_json` | Catalog Items 原始属性快照 |
| `source_updated_at` / `synced_at` | 来源更新时间与同步时间 |

Catalog 数据作为 Amazon 外部事实保存，不作为公司内部产品编码来源。

### 2.6 StoreListing：店铺 Listing 映射

这是多店铺设计的核心桥接实体。

| 字段 | 说明 |
| --- | --- |
| `id` / `store_id` | 主键及所属店铺 |
| `product_id` | 所属内部产品 |
| `variant_id` | 对应内部 SKU；Amazon 父体 Listing 可空 |
| `seller_sku` | 店铺 Seller SKU/MSKU |
| `marketplace_id` / `asin` | 对应 Amazon 目录商品；创建过程中 ASIN 可暂空 |
| `listing_role` | STANDALONE、PARENT、CHILD |
| `parent_listing_id` | 同店铺父体 Listing，可空 |
| `fnsku` | FBA 标签编码，可空 |
| `fulfillment_channel` | FBA 或 FBM |
| `listing_status` | 草稿、同步中、有效、受限、删除 |
| `issues_json` | Amazon Listing 问题快照 |
| `last_synced_at` | 最近同步时间 |

唯一性规则：

- `(store_id, marketplace_id, seller_sku)` 唯一。
- `(marketplace_id, asin)` 在 `AmazonCatalogItem` 唯一，但同一 ASIN 可以关联多个店铺 Listing。
- 同一个内部 SKU 在同一店铺默认只允许一个有效 Listing；套装、数量装等必须建立新的内部 SKU，不复用原 SKU。
- 父体 Listing 只能关联 Product，不关联具体 Variant；子体/独立 Listing 必须关联 Variant。
- `parent_listing_id` 必须属于同一店铺，且父子 `variation_theme` 一致。

## 3. 产品与辅料关系

辅料关系挂在 **内部 SKU**，因为不同颜色、尺寸或数量装可能使用不同标签、彩盒、说明书和用量。

`ProductAuxiliaryMaterial` 关键字段：

- `variant_id`
- `auxiliary_material_id`
- `standard_quantity`
- `unit`
- `loss_rate`
- `remark`
- `enabled`

界面提供“应用到全部 SKU”，但系统实际为每个 SKU 生成独立关系，避免隐式继承导致后续变体差异无法追溯。

辅料库存仍按单仓、辅料编码维护，与 Product/SPU、Amazon ASIN 和店铺 Listing 解耦。

## 4. 页面结构

### 4.1 产品列表

- 按产品编码、名称、品牌、类目和状态查询。
- 展开查看 SKU 数量和 5 店 Listing 覆盖情况。
- 支持新增、编辑、停用、导入和导出。

### 4.2 产品详情

采用五个页签：

1. 基础信息：Product/SPU 字段。
2. SKU/规格：Variant 与动态属性。
3. 对应辅料：按 SKU 维护辅料、用量和损耗率。
4. Amazon 映射：按店铺展示 Seller SKU、ASIN、父子体、FNSKU、履约和状态。
5. 变更记录：关键字段修改前后值、人员和时间。

### 4.3 多店铺映射视图

以内部 SKU 为行、5 个店铺为列显示覆盖状态；点击单元格进入该店铺 Listing 详情。该视图只展示映射摘要，不把店铺字段复制进 SKU 主表。

## 5. 数据归属与同步

| 数据 | 权威来源 | 同步规则 |
| --- | --- | --- |
| 产品编码、内部名称、规格、辅料 | 九更 ERP | 人工维护/导入，Amazon 不得覆盖 |
| ASIN、父子关系、目录标题与类目 | Amazon Catalog / 人工导入 | MVP 人工维护或导入；后续可用 SP-API 拉取并留快照 |
| Seller SKU、Listing 状态、问题、履约 | 店铺 Listing / 人工导入 | MVP 按店铺人工维护或导入；自动同步后置 |
| FNSKU | Amazon FBA/内部确认 | 按店铺 Listing 保存 |
| 价格、Amazon 库存 | Amazon 店铺 | 本期仅预留字段，不纳入产品主档维护 |

MVP 导入采用幂等 Upsert，并记录 `source`、导入批次和操作人。后续自动同步应补充 `source_updated_at`、`synced_at` 和请求追踪 ID；单个店铺失败不得影响其他店铺，也不得删除内部产品。

## 6. 导入模板

与 Approved PRD、Sprint 0 字段字典及 Excel 模板一致，拆分为六类导入文件，避免一张宽表重复主档信息：

1. `products`：产品/SPU；对应 `01_products_v1.0.xlsx`。
2. `variants`：内部 SKU 与动态变体属性；对应 `02_variants_v1.0.xlsx`，文件内先处理 `variants`，再处理 `variant_attributes`。
3. `store_listings`：店铺—Seller SKU—ASIN—内部 SKU 映射；对应 `03_store_listings_v1.0.xlsx`。
4. `auxiliary_materials`：辅料主档；对应 `04_auxiliary_materials_v1.0.xlsx`。
5. `product_auxiliary_materials`：SKU—辅料关系；对应 `05_sku_auxiliary_materials_v1.0.xlsx`。
6. `auxiliary_material_opening_inventory`：单仓辅料期初库存；对应 `06_initial_inventory_v1.0.xlsx`。

推荐串行导入顺序为 `products → variants → store_listings → auxiliary_materials → product_auxiliary_materials → auxiliary_material_opening_inventory`。其中 `store_listings` 仅依赖产品与 SKU，可与辅料链路并行；`product_auxiliary_materials` 必须在 SKU 和辅料主档之后；`auxiliary_material_opening_inventory` 必须在辅料主档及固定单仓配置之后。Listing 同一文件内父体行必须先于引用它的子体行。

六类导入均采用校验、预览、提交三阶段的幂等 Upsert；任一失败行必须返回工作表、行号、字段和原因，不得静默跳过。辅料期初库存提交后生成不可编辑的 `OPENING` 库存流水，并以“固定仓库 + 辅料编码”保证唯一；重复提交不得重复记账。

## 7. MVP 验收标准

- 一个产品可维护多个 SKU，且同一产品内变体组合不重复。
- 一个内部 SKU 可正确映射至 5 个店铺的独立 Listing。
- 同一 ASIN 可被多个店铺 Listing 复用，Seller SKU/FNSKU/状态互不覆盖。
- Amazon 父体、子体和独立 Listing 关系可表达并通过一致性校验。
- 每个 SKU 可维护独立辅料清单；“应用到全部 SKU”后关系可分别调整。
- 六类导入文件均能完成真实脱敏样本导入：产品、SKU/动态属性、5 店 Listing、辅料主档、SKU—辅料关系和单仓辅料期初库存；必填、唯一、枚举、引用及条件校验失败时逐行返回工作表、行号、字段和原因。
- 辅料主档可独立导入并按辅料编码全局查重；期初库存按“固定仓库 + 辅料编码”唯一，提交后生成不可编辑且幂等的 `OPENING` 流水，余额与流水重算一致。
- Amazon 同步失败不覆盖内部主档，所有映射和关键修改可追溯。

## 8. 官方依据

- [Manage Product Listings with SP-API](https://developer-docs.amazon.com/sp-api/docs/manage-product-listings-guide)：区分 Product Type Definitions、Listings Items 和 Catalog Items；Listing 以卖家 SKU 管理。
- [Listings Items API](https://developer-docs.amazon.com/sp-api/reference/listings-items-v2021-08-01)：按 Selling Partner、SKU 和 marketplace 查询/维护 Listing。
- [Catalog Items API](https://developer-docs.amazon.com/sp-api/reference/catalog-items-v2022-04-01)：查询 ASIN 目录数据及 variations。
- [Product Type Definitions API](https://developer-docs.amazon.com/sp-api/reference/product-type-definitions-v2020-09-01)：按 Product Type 和 marketplace 获取属性及条件要求。

内部编码提案见[产品与辅料编码规则](../01-product/PRODUCT_CODE_STANDARD.md)。

## 9. 变更记录

| 版本 | 日期 | 变更 | 任务 |
| --- | --- | --- | --- |
| v1.0 | 2026-09-12 | 首次 Approved 基线。 | - |
| v1.1 | 2026-09-12 | 将导入范围由 4 类纠正为 6 类，补齐 `auxiliary_materials` 与 `auxiliary_material_opening_inventory`，同步导入顺序及验收标准。 | S0-DOC-01 |
