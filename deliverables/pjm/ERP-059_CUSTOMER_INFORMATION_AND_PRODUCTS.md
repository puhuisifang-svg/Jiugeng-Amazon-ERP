# ERP-059 客户信息与客户商品

日期：2026-10-18。

## 授权、基线与计划

- 用户要求新增“客户 / 客户管理 / 客户信息、客户商品”，维护1688客户信息、不同客户独立供货价、特殊需求及客户产品编码。
- 用户明确授权继续当前分支累计开发，保留 ERP-055～058 未提交修改，只更新本地，不提交、不推送、不正式发布。
- 分支：`fix/ERP-055-shipment-sku-binding`；基线：`f1af0fc94e8bd386688b1c735bf776bba77ca8bf`。
- 计划：独立 customer 模块与数据迁移 → 权限及导航 → 客户与SKU报价表单/列表 → 回归 → 本地数据库迁移与服务验证。

## 实现范围与约定

- 客户信息：名称必填，1688账号、联系人、手机号、微信、收货地址、备注可选；支持新增、编辑、启用/停用、搜索及分页，从客户行可直接查看其商品。
- 客户商品：绑定客户和具体 SKU，不使用父体名称；展示 SKU 图片并沿用点击预览。供货价为人民币、非负金额、最多两位小数，计价单位可填写件/套/箱等。保存客户产品编码、最长2000字特殊需求及启停用状态。
- 同客户+SKU数据库唯一，不同客户可为同SKU设置不同价格。已建档记录不可更换客户/SKU，避免改价时误串客户。包含停用记录在内不允许重复建档。
- 启用报价要求客户、产品和SKU都处于启用状态。客户或SKU停用后保留已有记录，仍允许将报价停用；不物理删除档案。
- 客户与SKU选择器均支持搜索和分页，不依赖静态前100条。列表紧凑固定搜索栏、固定表头和右侧操作列，横向滚动；分页及选择器窄屏换行。
- 新增/编辑采用原生模态dialog，焦点约束在弹窗中；点击遮罩不关闭；脏表单关闭、取消、Esc或应用内导航需确认放弃。默认继续填写，保存中禁止关闭/重复提交，失败保留内容，成功toast。刷新/关闭浏览器不提供持久草稿恢复。
- 新增 `customers.read`、`customers.write`；ADMIN和PRODUCT_MATERIAL拥有读写，SELECTION无权访问，与供应商管理角色范围一致。后端每个端点独立鉴权，不依赖菜单隐藏。
- 两类记录均采用乐观版本锁；追加式审计保留价格/单位/状态/版本前后变化。客户联系方式、地址和自由文本不写入审计；校验失败不使用默认包含拒绝值的日志输出。
- 不自动连接1688，不涉及客户订单、收款、阶梯价、多币种、库存或发货联动；税费/运费约定暂可注明在特殊需求中。

## 变更文件

- 后端 `customer/`：CustomerTypes、Controller、Service、Exception、ExceptionHandler、package-info；经公开 ProductLookup 边界查询SKU，不引用product模块内部类型。
- `V30__customer_catalog.sql`：新增 customer / customer_product，外键、唯一键、金额与状态约束；不修改既有业务数据。
- `iam/IamTypes.java`、`IamControllerTest.java`：权限及合同断言。
- `customer/CustomerControllerTest.java`：四组集成测试，隔离测试库、事务回滚、仅合成数据。
- 前端 `api/customers.ts` / `.test.ts`、`views/CustomerManagementView.vue` / `.test.ts`。
- `auth/capabilities.ts` / `.test.ts`、`router/meta.d.ts`、`router/index.ts` / `.test.ts`、`layouts/AdminLayout.vue` / `.test.ts`：路由、导航与权限。

## 验证

- `pnpm exec eslint src --fix`：通过，仅格式化本轮新页面及相关改动。
- `cd frontend && pnpm quality`：通过，52测试文件、209测试；lint、类型检查及生产构建通过。日志 `/tmp/erp059-frontend.log`。
- `./mvnw -Dtest=CustomerControllerTest,ApplicationModulesTest test`：通过，5测试。
- `cd deliverables/backend/sprint-0/jiugeng-erp-backend && ./mvnw clean verify`：通过，146测试、零失败/错误/跳过，模块边界检查通过。日志 `/tmp/erp059-backend.log`。
- 首轮完整检查发现旧导航和权限集合断言未加入新增项，以及金额输入Vue自动转换为数字的测试预期，更新后全量重跑通过。
- `git diff --check`：通过；无暂存文件，工作区为ERP-055～059本批累计修改，未发现其他任务修改。
- 浏览器人工视觉验收受阻：Mac锁屏，CUA无法打开Safari；已向用户说明待解锁检查。不能宣称已完成真实浏览器验收。

## 本地交接状态

- Vite直接加载源码、生产构建已完成。后端使用既有 `scripts/dev/run-local-mysql-backend.sh` 更新，沿用MySQL，不使用会清空数据的mock模式；无数据库重启或清空操作。
- 更新后 `/actuator/health` 返回 UP，前端客户信息地址返回HTTP 200；MySQL flyway V30 success=1。客户与客户商品均0条（不注入本地测试档案）；今日发货和待发货更新前后均各2条。
- 本轮仍未提交；与ERP-055～058一同等待用户统一指令后交项目经理复核、提交及正式部署。不宣称已合并或正式验收。
