# 供应商删除与产品图片下载修复

任务：用户直接授权修复及原本地环境部署，未新分配 ERP 任务号。分支 `fix/ERP-099-product-detail-ui`，基准 HEAD `33122a4c08831689db5454320143fb6fc6235089`。本次改动未提交，未推送、未合并 main。

## 变更

- SupplierController / SupplierService / SupplierRepository：增加带 version 的 DELETE，校验 suppliers.write，乐观锁；外键保护已有产品、辅料、采购单关联，返回可读冲突提示；成功后释放二维码关联并追加 SUPPLIER_DELETE 审计。
- frontend/src/views/SuppliersView.vue、api/domain.ts：操作列删除按钮、统一确认、忙碌禁用、成功提示与错误保留。
- frontend/src/views/ProductsView.vue、api/catalog.ts：预览增加保存图片，受控图片使用带鉴权的原图请求；下载名称用产品名和实际媒体格式，过滤非法文件名字符。临时 blob 地址在原生右键保存时仍可能被浏览器命名 unknown，应使用新的保存图片按钮。外链图片受来源服务器 CORS 限制，失败显示错误，不绕过浏览器安全限制。
- 回归：SupplierControllerTest、SuppliersView.test.ts、catalog.test.ts。

## 验证及部署

- `ERP_BUILD_OUT_DIR=/tmp/supplier-image-build pnpm --dir frontend quality`：93 文件、373 测试通过，lint/typecheck/build 通过。
- 后端 `./mvnw clean verify`：266 测试，0 失败、0 错误、1 环境探针跳过；BUILD SUCCESS。
- `git diff --check` 通过。
- 浏览器 1280×800、800×600、360×640 检查供应商固定操作列及图片预览，保存按钮可见。实点保存图片后 Downloads 中生成产品名称 .png 文件，PNG 文件签名正确。浏览器工具 download 事件等待超时，以实际落盘文件核验结果。
- 部署 `.local/releases/backend/supplier-image-20261029/backend.jar`，沿用 jiugeng-mvp backend/8080 与 frontend/5173；健康 UP，前端 HTTP 200。无数据库迁移；原 launcher 副本留在 release 目录。未发布外网静态包。
- 本地合成供应商 API 创建→删除 204→查询 404 验证通过，测试记录已清理，审计保留。临时 QA 账号已禁用、会话已清理。现有用户供应商未修改。
- 已检查最终 diff/status：工作区原有候选开发、样品管理、详情 UI 等未提交改动保留；本次仅上述文件及交付记录增加修改，没有暂存或覆盖其他工作。
