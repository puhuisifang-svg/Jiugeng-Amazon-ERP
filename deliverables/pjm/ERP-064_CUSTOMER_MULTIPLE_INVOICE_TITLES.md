# ERP-064 客户多发票抬头与新增发票联动

## 授权与状态

- 用户要求：一个客户维护多个发票抬头；新增发票从客户档案选择，唯一抬头自动带出。
- 按用户指示继续当前累计分支 `fix/ERP-055-shipment-sku-binding`；基线 `f1af0fc94e8bd386688b1c735bf776bba77ca8bf`。
- 修改未提交、未暂存、未推送、未合并 main。保留 ERP-055～063 累计修改；没有引入无关修改。
- 已按用户持续授权更新本地服务；未操作远程部署、公网代理或隧道。正式验收和发布仍由项目经理确认。

## 行为与文件

- 客户新增/编辑可添加、移除多组抬头，每组包含抬头、税号、注册地址/电话、开户银行/账号，最多 50 组。
- 完全空白的初始组不保存；部分填写必须有抬头。同一客户的相同抬头与税号不允许重复。不同税号允许相同抬头。
- 新增发票选择客户商品后返回该客户全部抬头；唯一时自动选中，多个时必须选择。切换客户/商品清空旧选择，异步返回有序列保护。
- 新增发票服务端校验抬头与税号属于该客户；其他开票字段由服务端从客户主档复制，不信任客户端提供的银行信息等。客户行锁串行化读取与客户修改。
- 已有发票开票资料保留创建时快照，后续客户编辑/移除抬头不会改变历史；编辑历史发票不会覆盖原抬头，可继续更新业务状态等字段。
- V35 新增 customer.invoice_titles MEDIUMTEXT，存储受校验的 JSON 集合，复用客户 version 乐观锁。NULL 兼容原六个单抬头字段，空数组明确表示已移除全部抬头。原字段保留兼容，集合写入时同步第一组；旧客户端未传集合不会清空集合。
- 不新增权限，沿用 customers.read/write；开票资料、银行等敏感字段不写入审计值。

本次文件：

- 后端 customer/CustomerTypes.java、CustomerService.java、CustomerInvoiceTitles.java（新增）、InvoiceTypes.java、InvoiceService.java、InvoiceController.java。
- 数据库 db/migration/V35__customer_multiple_invoice_titles.sql（新增）。
- 后端测试 customer/CustomerControllerTest.java、InvoiceControllerTest.java。
- 前端 api/customers.ts、api/invoices.ts、views/CustomerManagementView.vue、InvoiceManagementView.vue 及两个对应测试文件。
- 本记录。

## 验证

- 后端 `./mvnw clean verify`：160 tests，0 failures/errors，BUILD SUCCESS；日志 /tmp/erp064-backend.log。
- 前端 `pnpm quality`：58 个文件 / 230 tests，通过 lint、类型检查、测试及构建；日志 /tmp/erp064-frontend.log。
- `git diff --check` 通过；暂存区为空。
- 回归覆盖：原单抬头兼容、多个抬头新增/移除、空/过长/重复/超量校验、版本冲突、无权限拒绝、唯一自动带出、多项手动选择、无抬头阻止保存、切换清空、跨客户/错误税号拒绝、伪造银行信息不采纳、主档移除后历史快照保留。
- Safari 本地页面检查：客户新增弹窗能添加第二组抬头，布局正常，未保存退出有确认；新增发票默认客户/商品读取正常，无抬头时展示维护提示且选择器禁用。没有保存合成客户或发票到本地业务库。单/多抬头持久化与选择路径由隔离自动化测试覆盖。

## 本地部署与数据保护

- 迁移前备份：`.local/backups/erp064-ZKKRXY/pre-migration.sql.gz`，51136 bytes，权限 600，gzip 校验通过。
- 仅重启 tmux jiugeng-mvp:0.0，通过 scripts/dev/run-local-mysql-backend.sh 启动 MySQL 后端。前端5173保持 Vite 热更新。
- Flyway V35 success=1；8080 /actuator/health 返回 UP。
- 部署后客户 2、客户商品 5、发票 0，未删除或新增业务数据。
- 当前默认客户无开票抬头，需用户在客户信息中维护后方可新增发票。
- 后续提交时请项目经理连同累计任务统一复核；本记录不代表正式发布或验收完成。
