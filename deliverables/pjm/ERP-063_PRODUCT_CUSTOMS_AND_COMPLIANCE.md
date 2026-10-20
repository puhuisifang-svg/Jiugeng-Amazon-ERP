# ERP-063 产品报关资料与合规报关管理

日期：2026-10-19。

## 授权、基线与计划

- 用户要求：产品管理／新增产品增加报关资料，维护出口欧美相关品名、申报要素；新增合规／报关管理并联动。
- 沿用用户授权的当前分支累计开发：`fix/ERP-055-shipment-sku-binding`，基线 `f1af0fc94e8bd386688b1c735bf776bba77ca8bf`。保留 ERP-055～062 的未提交修改；本任务仍未提交、未暂存、未推送、未合并。
- 计划：核对官方资料与SKU表单 → 共用资料模型及同事务保存 → 新增产品页签、合规列表维护 → 联动/冲突/权限回归 → 全量门禁 → 备份、本地部署和页面检查。
- 范围是SKU级产品报关主资料，不是逐票报关单、海关提交接口、货代系统、税率计算或认证审核。事先已向用户说明。

## 功能与实现

- 产品新增/编辑的每个SKU新增“报关资料”页签；维护中英文申报品名、材质/成分及含量、用途、品牌、规格型号、原产国、申报单位、单件净重/毛重kg、中国商品编码、美国HTS、欧盟CN/TARIC、英国编码、申报要素、目的地合规/证明资料说明及备注。
- 不猜测商品编码、原产国、申报品名或申报要素。按目的地分开保存编码；要素使用逐行“要素名：内容”文本，支持不同商品要求，不提供自动要素字典或自动归类。
- 资料状态：未维护、草稿/待核对、资料已核对。草稿允许空字段；已核对要求内部基本字段齐全。已核对只是人工标记，页面明确说明不代表海关审核通过、已申报或合规认证。
- 合规顶级菜单下新增“报关管理”；以SKU列出全部产品，包含未维护和未启用SKU，支持关键字、资料状态筛选和分页。固定表头、搜索栏和右侧操作列，横向滚动适配；完整字段通过维护/查看弹窗呈现。
- 两处共用 `product_variant.customs_data`，无复制表或后台同步延迟；刷新/重新进入页面读取最新数据。独立 `customs_version` 防止另一入口的旧表单覆盖新资料；冲突保留输入并提示刷新。
- 新增/更新产品接口将产品、SKU、默认客户商品及报关资料放进同一事务；报关资料校验失败整单回滚。未传报关资料的旧客户端及批量修改不清空已有内容。复制SKU不复制原SKU的报关归类和核对状态。
- 本次报关资料权限复用 `products.read/write`：ADMIN及PRODUCT_MATERIAL可维护，SELECTION只读；服务端权限与校验生效。
- 编码仅做长度/数字格式检查，原产国仅做两位大写代码格式检查，不声称验证真实归类、代码存在性或法律适用性；净重/毛重必须为正，毛重不能小于净重，字段限制长度。
- 报关管理弹窗遮罩不关闭、未保存关闭或离开需确认，继续填写保留输入，失败保留内容，保存中禁止重复提交。成功使用toast。
- 审计追加 `PRODUCT_CUSTOMS_UPDATE`，记录SKU、操作者、前后版本和资料状态；不复制自由文本到审计正文。原产品保存审计保持。

## 文件

- 后端新增：`product/CustomsProfile.java`、`ProductCustomsService.java`、`ProductCustomsController.java`；迁移 `V34__product_customs_profile.sql`。
- 后端修改：`ProductController.java`（原子聚合保存）、`ProductTypes.java`/`ProductRepository.java`（返回SKU报关资料）、`ProductExceptionHandler.java`（新接口校验/错误映射）。
- 前端新增：`api/customs.ts`、`components/CustomsFields.vue`、`views/CustomsManagementView.vue`；修改domain类型、ProductForm、ProductsView、路由和AdminLayout。
- 测试：ProductControllerTest增加联动、并发、整单回滚、草稿/核对校验和权限；ProductForm、CustomsManagementView、customs API测试及路由组断言。

## 官方参考与边界

- [美国CBP新进口商说明](https://www.help.cbp.gov/s/article/Article-1894?language=en_US)：商品完整描述、原产地、材质和用途是归类判断相关资料；系统据此提供通用字段，不代替归类决定。
- [欧盟Combined Nomenclature](https://taxation-customs.ec.europa.eu/customs/common-customs-tariff-cct/tariff-classification-goods/combined-nomenclature_en)与[TARIC](https://taxation-customs.ec.europa.eu/online-services/online-services-and-databases-customs/eu-customs-tariff-taric_en)：分别维护目的地编码，CN为8位，TARIC为10位。
- [英国商品编码指导](https://www.gov.uk/guidance/finding-commodity-codes-for-imports-or-exports)：不同国家的细分归类不能假定通用。页面英国字段用于目的地进口10位编码，不声称覆盖全部英国申报场景。
- [海关业务办理指南](https://online.customs.gov.cn/static/pages/guides/000629020000/000629020000.html)：提及10位海关商品编码；系统保留10位基础编码及独立申报要素说明，不覆盖检验检疫扩展编码和特定监管业务。
- 来源核对日期2026-10-19；没有内置税率、保证合规、自动认证判断或自动海关提交。

## 测试与本地部署

- `./mvnw clean verify`：158测试通过，零失败/错误/跳过，模块边界检查通过。日志 `/tmp/erp063-backend.log`。
- `pnpm quality`：58文件／228测试通过；lint、类型检查、构建通过。日志 `/tmp/erp063-frontend.log`。
- `git diff --check`：通过；已检查最终差异，无暂存文件、无既有累计任务及本任务范围以外改动。
- 更新前停止本地后端并备份MySQL至 `.local/backups/erp063-IrASgR/pre-migration.sql.gz`，51069字节、权限600、gzip完整性检查通过，Git忽略。不重启/清空MySQL。
- 通过 `scripts/dev/run-local-mysql-backend.sh` 更新 `jiugeng-mvp:0.0`；V34 success=1，健康检查UP；Vite加载新代码、构建完成，未操作公共代理、隧道或远程环境。
- 只读核对：3产品、4SKU、5客户商品、0发票不变；报关资料已填写0条。没有自动回填或保存真实商品申报信息。
- Safari实际检查：新增产品中报关资料页签及字段可见、布局可滚动；合规菜单报关管理可进入，自动列出现有4个SKU且状态为未维护，固定操作列及横向滚动可见。未保存本地测试记录。保存和两处互读、回滚、冲突由隔离数据库API集成测试验证。

## 交接与后续

- 修改留在当前分支且未提交，等待用户统一指令后交项目经理复核及提交main、正式部署；不宣称已获项目经理验收或正式发布。
- 后续若需证明附件上传、各目的国独立多版本、按HS自动生成要素、逐票商业发票/装箱单或海关系统对接，需要另行确认；本轮没有擅自扩展。
