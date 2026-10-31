# 样品管理：本地交付

用户直接授权在“商品 / 产品管理”下新增样品管理，从产品列表选SKU并维护库位。本次无新增ERP编号，沿用共享分支 `fix/ERP-099-product-detail-ui`；基准 HEAD `33122a4c08831689db5454320143fb6fc6235089`。所有改动未提交，未push、未合并main。前序未提交文件保持原样，未替其他工作提交；工作区并非干净状态。

## 使用

地址：http://localhost:5173/samples 。点击“新增样品”，按SKU或品名查找并选择产品SKU，填写库位编码后保存。编辑只修改库位；同一SKU可登记多个库位，同一SKU与同一库位不可重复（去除首尾空白，全半角及大小写统一用于去重）。移除仅删除样品库位登记，不删除产品或SKU，不影响库存数量。

列表支持品名、SKU和库位关键词查询，固定底部分页及右侧操作列。沿用products.read / products.write权限；查看账号无法创建、修改或移除。原SKU不存在或不属于指定产品时不可新增。采用版本校验防止覆盖并发修改；失败保留表单输入，未保存关闭有独立确认弹窗。品名与SKU保留登记时快照，不将样品库位变动写回商品或库存。

## 文件

- 后端 sample/SampleController.java、sample/package-info.java：查询、新增、编辑、移除、权限校验、乐观版本、追加审计；通过ProductLookup公共边界验证SKU。
- V58__sample_locations.sql：样品库位表、唯一约束与查询索引。
- SampleControllerTest.java：权限、SKU归属、必填、重复（大小写/全角）、并发版本、分页、通配符转义、删除不影响SKU、审计。
- frontend/src/api/samples.ts、views/SamplesView.vue / SamplesView.test.ts：列表、SKU选择弹窗、库位编辑、确认操作与未保存保护。
- frontend/src/router/index.ts：商品 / 产品管理分组的样品管理菜单、页签及面包屑。

## 验证与部署

- 后端 `./mvnw clean verify`：264项、0失败、0错误、1环境跳过（外部OSS探测未启用），BUILD SUCCESS。
- 前端 `ERP_BUILD_OUT_DIR=/tmp/sample-location-build pnpm --dir frontend quality`：371测试通过，lint、类型检查及构建通过。
- `git diff --check`通过，无暂存文件。仅新增本任务文件及菜单修改，前序用户已授权修改保留。
- 浏览器验证菜单/面包屑、SKU搜索选择、新增库位、编辑库位；1280×800、800×600、360px布局及手机弹窗操作区可见。
- 本地MySQL验证重复库位冲突、25条合成数据分页（20+5）、API移除；长列表分页bottom等于视口bottom。合成样品全部经审计API移除，临时产品/SKU清理，审计记录保留，临时QA账号停用及会话撤销；未改动既有业务记录。
- 部署原端口5173 / 8080，tmux jiugeng-mvp，健康UP。Flyway已完成V58。前端仍为原Vite本地服务，没有更换端口或发布公网构建。
- 数据库一致性备份 `.local/backups/sample-location-20261029/before.sql.gz`，同目录保留上版启动脚本；旧jar保留在production-approved-20261029目录。
- 当前制品 `.local/releases/backend/sample-location-20261029/backend.jar`，SHA256：`1348a5a695e116a9e6bf617796e08284c0946a855b2b32a80a377c0b779ba250`。

当前范围仅为样品SKU与库位登记，不含样品库存数量、借还或盘点流程。
