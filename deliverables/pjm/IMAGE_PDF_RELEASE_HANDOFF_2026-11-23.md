# ERP-101 与 PDF 累计迭代发布交接

- 项目：九更亚马逊 ERP；交付物 DEL-049；2026-11-23，Asia/Shanghai。
- Owner：Codex → PJM；状态 In Review。用户已授权提交远端 main 并部署，PJM 负责复核、执行和回报。
- 本记录覆盖 DEL-047、DEL-048 开发阶段的“待统一验证 / 无发布授权”描述；不宣称已提交、已合并、已部署或业务验收。

## 授权与工作区

用户原话：“与项目经理交接，把修改内容提交远端main，并部署线上。”授权范围是本轮全部累计修改：ERP-101 图片性能 1–7、PDF 四页签导出源文件名、PDF 收尾 3、5、6、8、9、10、12、15、19。无 CDN，系统图片显示尺寸不变；未选 PDF 功能不追加。

仓库 `/Users/thinkperfect/.openclaw/workspace-jiugeng_pjm/jiugeng-amazon-erp`，分支 `feature/ERP-101-image-performance`，HEAD / 基线 `dfba431424226445db34aa1c937915d85edef9dd`。**实施修改全部未提交、未暂存，没有本轮提交 SHA。** 当前 dirty 工作区均属上述 Codex 累计任务，无无关改动。不得 reset、stash 或覆盖这些未提交文件。

正式发送本交接给 `agent:jiugeng_pjm:main` 后，唯一写入权移交 PJM；Codex 停止编辑、测试、提交和本地服务操作，不等待或轮询线上发布。下一次接手必须重新确认实际锁状态。

## 实现与关键入口

- [DEL-047 图片性能实现](ERP-101_IMAGE_PERFORMANCE_LOCAL_2026-11-23.md)：小尺寸衍生图与历史回填、鉴权后 ETag/304、前端优先队列/缓存/渐进预览、外链受控镜像与变更同步、OSS 持久读缓存、匿名诊断。V107 新增派生记录，不改写原图；敏感与待挂载图片保持 no-store。各领域公开来源适配器，模块边界保留。
- [DEL-048 PDF 收尾实现](PDF_CLOSEOUT_LOCAL_2026-11-23.md)：共享预览与有界渲染、图片编码与内存预算、统一文字绘制、紧凑布局/窄屏折叠、拆分摘要、图层操作、分类错误/成功提示。源文件名保留，多拆分 ZIP 内编号，图片转 PDF 用排序第一张图主名。
- 交接检查修复：外链首次到期判定保留时钟精度，避免刚注册记录被秒级截断跳过；所有刷新分支仅能更新本任务仍持有的 lease，超时旧任务不会清除新任务的锁。新增事务回滚衍生文件清理和过期任务提交保护用例。
- 既有产品/样品测试改为断言受控 Blob 与 externalImageBlob 调用，保留实际认证环境不直接请求外链的行为。新增 `frontend/e2e/pdf-closeout.smoke.ts`，使用浏览器生成的有效 JPEG / 透明 PNG 和合成 PDF。

## 统一验证结果

以下均于 2026-11-23 本地执行。完整门禁后最后的后端 lease 防护只重跑受影响的 ImageDeliveryTest；前端后续仅新增/完善浏览器用例，业务源代码未再变，复用完整 quality 结果。未重复运行无关完整回归。

| 工作目录 | 完整命令 | 结果与边界 |
| --- | --- | --- |
| `deliverables/backend/sprint-0/jiugeng-erp-backend` | `./mvnw --batch-mode --no-transfer-progress clean verify` | 18:52:53 BUILD SUCCESS；440 tests，0 failures / errors，1 skipped（外部 OSS 未配置）。含架构检查、迁移 V107。日志 `/tmp/erp101-handoff-backend-final.log`。 |
| 同上 | `./mvnw --batch-mode --no-transfer-progress -Dtest=ImageDeliveryTest test` | 最终 lease 修改后 18:59:48 BUILD SUCCESS；8 tests，0 failures / errors / skipped。验证回滚只清理派生文件、保留原图，旧任务不能覆盖指针或清除新锁。日志 `/tmp/erp101-handoff-transaction-tests-final.log`。 |
| `frontend` | `pnpm quality` | PASS；167 files / 763 tests，lint、类型检查、生产构建通过。PDF.js 静态/动态导入与超过 500 kB chunk 提示仍存在，不是错误。日志 `/tmp/erp101-handoff-frontend-final.log`。 |
| `frontend` | `pnpm exec playwright test` | 首批 7 个既有 catalog / login / operations 场景通过。随后 PDF 测试用的旧 PNG 字节被浏览器拒绝，测试中断；改为 Canvas 生成的有效图片后定向重跑如下，未隐瞒首次失败。日志 `/tmp/erp101-handoff-browser.log`。 |
| `frontend` | `pnpm exec playwright test e2e/pdf-closeout.smoke.ts e2e/products.smoke.ts` | 6 passed；三尺寸产品与 PDF。日志 `/tmp/erp101-handoff-browser-final.log`。 |
| `frontend` | `pnpm exec eslint e2e/pdf-closeout.smoke.ts --max-warnings=0`；`pnpm exec playwright test e2e/pdf-closeout.smoke.ts` | PDF 用例补充窄屏折叠与截图等待后的最终结果：lint PASS，3 passed。日志 `/tmp/erp101-handoff-pdf-final.log`。 |
| 仓库根 | `caddy validate --config infra/production/Caddyfile.preflight --adapter caddyfile` | Valid configuration。 |
| 仓库根 | `ERP_DOMAIN=erp.example.invalid caddy validate --config infra/production/Caddyfile.live.example --adapter caddyfile` | Valid configuration。 |
| 仓库根 | `ERP_FRONTEND_DIST=frontend/dist caddy validate --config infra/caddy/Caddyfile --adapter caddyfile` | Valid configuration。 |
| 仓库根 | `python3 /tmp/erp101-proxy-cache-check.py` | 独立随机本地端口、合成上游与 Caddy：上游明确 private/no-cache/must-revalidate 仅保留一次；未设置缓存头时缺省 no-store。临时进程均退出。 |
| 仓库根 | `git diff --check`；变更 Markdown 本地链接校验；`git status --short` | 通过；无无关改动或暂存文件。完整改动清单见下。 |

首轮后端全门禁失败为新外链时间精度问题，已修复并完整重跑成功；首轮前端失败为 ProductsView / SamplesView 旧外链断言，已更新并完整重跑成功。最终共覆盖 **13 个独立 Chromium 浏览器场景**（重复 PDF 复测不累计），尺寸 1280×800、800×600、360×800，使用生产构建和合成 API 夹具，不修改真实业务数据。

PDF 浏览器覆盖四页签下载名称、ZIP 内名称与页数、中文多行文字与图层复制顺序、旋转/CropBox、透明 PNG/JPEG、首图重排、FBA 100×100mm 输出、窄屏按钮可见与无横向溢出；人工查看宽/窄屏截图。图片覆盖沿用跨模块功能与产品预览场景。截图在忽略目录 `frontend/test-results/`，本记录保留结果，不把临时日志当唯一证据。

限制：没有 Safari/WebKit 实际下载验证（当前无 WebKit 测试运行时）、物理打印/扫码或生产规模大 PDF 内存压测；外部 OSS 集成探测跳过，线上提速尚无修改后实测。上述不是已验收事实。导入契约未改，不运行导入专用门禁。

## 发布操作交给 PJM

1. 接管唯一写入权后复核 HEAD、完整 diff 和所有未跟踪新文件，按范围原子提交，走受保护 PR 流程合入远端 main；不要直推绕过保护。由 main 现有 CI/CD 构建精确提交并部署，最终 SHA 由 PJM 补入状态报告。
2. **必须同步生产实际 Caddy 配置。** 此前 API 统一 `header Cache-Control no-store` 会覆盖后端缓存策略；本次模板改成 `header ?Cache-Control no-store`，仅作缺省值。JAR 部署不会自动保证服务器 Caddy 文件更新。PJM 备份实际配置、按模板更新、validate 后 reload，并核实业务接口仍默认 no-store、普通图片由后端决定，不能全站启用公共缓存。
3. V107 随最终 CI 构建迁移，发布前由既有流程备份数据库并保留上一版本。已有本地 MySQL V107 成功不是生产迁移证据；禁止清空数据库/上传目录。
4. 本地服务仍为 DEL-047 记录的持久运行包，未为本次交接重复重启。该旧 JAR 早于最终时间精度、lease 防护和 mock 配置补充，**不能作为发布包**；以精确 Git SHA 重新构建。
5. PJM 独立负责 CI/CD、实际 deploy、健康/入口版本核验及最终用户回报，并更新状态/发布记录。Codex 不跟踪上线；本交接没有任何已上线承诺。

本地备份/运行包路径与校验值见 DEL-047；所有临时测试进程已结束，持续业务服务与数据保留。无额外用户输入阻塞移交。

## 完整改动归属清单

以下文件均属于图片性能、PDF 累计功能、配套测试或文档；状态为未提交。`??` 包含本轮应纳入提交的新文件，不能漏掉。

```text
 M deliverables/backend/sprint-0/jiugeng-erp-backend/pom.xml
 M deliverables/backend/sprint-0/jiugeng-erp-backend/src/main/java/com/jiugeng/erp/catalogmedia/CatalogImageController.java
 M deliverables/backend/sprint-0/jiugeng-erp-backend/src/main/java/com/jiugeng/erp/catalogmedia/CatalogImageService.java
 M deliverables/backend/sprint-0/jiugeng-erp-backend/src/main/java/com/jiugeng/erp/catalogmedia/CatalogImageStore.java
 M deliverables/backend/sprint-0/jiugeng-erp-backend/src/main/java/com/jiugeng/erp/catalogmedia/LocalCatalogImageStore.java
 M deliverables/backend/sprint-0/jiugeng-erp-backend/src/main/resources/application-mock.yml
 M deliverables/backend/sprint-0/jiugeng-erp-backend/src/main/resources/application-test.yml
 M deliverables/backend/sprint-0/jiugeng-erp-backend/src/test/java/com/jiugeng/erp/catalogmedia/LocalCatalogImageStoreTest.java
 M docs/04-engineering/PROJECT_HANDOVER.md
 M docs/08-project-management/STATUS_REPORT.md
 M docs/DELIVERABLE_REGISTER.md
 M docs/INDEX.md
 M docs/ui/UI_STANDARD.md
 M frontend/src/api/catalog.ts
 M frontend/src/components/AuthenticatedCatalogImage.test.ts
 M frontend/src/components/AuthenticatedCatalogImage.vue
 M frontend/src/components/CustomerProductImage.vue
 M frontend/src/components/CustomerSkuPicker.vue
 M frontend/src/components/MaterialImage.vue
 M frontend/src/components/ParentProductDetailDialog.vue
 M frontend/src/components/PdfPagePreview.vue
 M frontend/src/components/ProductForm.vue
 M frontend/src/components/ProductImagePreview.vue
 M frontend/src/components/ProductMonitorDetail.vue
 M frontend/src/components/PurchaseItemImage.vue
 M frontend/src/components/SampleImage.vue
 M frontend/src/components/ShipmentProductImage.vue
 M frontend/src/components/SupplierImage.vue
 M frontend/src/ui/purchaseImageExport.ts
 M frontend/src/utils/catalogImageCache.test.ts
 M frontend/src/utils/catalogImageCache.ts
 M frontend/src/utils/pdfTools.test.ts
 M frontend/src/utils/pdfTools.ts
 M frontend/src/views/CandidateWorkbenchView.vue
 M frontend/src/views/DevelopmentWorkbenchView.vue
 M frontend/src/views/LaunchWorkbenchView.vue
 M frontend/src/views/PdfToolsView.test.ts
 M frontend/src/views/PdfToolsView.vue
 M frontend/src/views/ProductDetailView.vue
 M frontend/src/views/ProductsView.test.ts
 M frontend/src/views/ProductsView.vue
 M frontend/src/views/SamplesView.test.ts
 M infra/caddy/Caddyfile
 M infra/production/Caddyfile.live.example
 M infra/production/Caddyfile.preflight
 M infra/production/README.md
?? deliverables/backend/sprint-0/jiugeng-erp-backend/src/main/java/com/jiugeng/erp/catalogmedia/CatalogImageVariants.java
?? deliverables/backend/sprint-0/jiugeng-erp-backend/src/main/java/com/jiugeng/erp/catalogmedia/ExternalImageController.java
?? deliverables/backend/sprint-0/jiugeng-erp-backend/src/main/java/com/jiugeng/erp/catalogmedia/ExternalImageFetcher.java
?? deliverables/backend/sprint-0/jiugeng-erp-backend/src/main/java/com/jiugeng/erp/catalogmedia/ExternalImageService.java
?? deliverables/backend/sprint-0/jiugeng-erp-backend/src/main/java/com/jiugeng/erp/catalogmedia/ExternalImageSource.java
?? deliverables/backend/sprint-0/jiugeng-erp-backend/src/main/java/com/jiugeng/erp/catalogmedia/ImageHttpResponse.java
?? deliverables/backend/sprint-0/jiugeng-erp-backend/src/main/java/com/jiugeng/erp/catalogmedia/JdbcExternalImageSource.java
?? deliverables/backend/sprint-0/jiugeng-erp-backend/src/main/java/com/jiugeng/erp/catalogmedia/JsonExternalImageSource.java
?? deliverables/backend/sprint-0/jiugeng-erp-backend/src/main/java/com/jiugeng/erp/development/DevelopmentExternalImages.java
?? deliverables/backend/sprint-0/jiugeng-erp-backend/src/main/java/com/jiugeng/erp/materialinventory/MaterialinventoryExternalImages.java
?? deliverables/backend/sprint-0/jiugeng-erp-backend/src/main/java/com/jiugeng/erp/monitoring/MonitoringExternalImages.java
?? deliverables/backend/sprint-0/jiugeng-erp-backend/src/main/java/com/jiugeng/erp/product/ProductExternalImages.java
?? deliverables/backend/sprint-0/jiugeng-erp-backend/src/main/java/com/jiugeng/erp/purchase/PurchaseExternalImages.java
?? deliverables/backend/sprint-0/jiugeng-erp-backend/src/main/java/com/jiugeng/erp/sample/SampleExternalImages.java
?? deliverables/backend/sprint-0/jiugeng-erp-backend/src/main/java/com/jiugeng/erp/selection/SelectionExternalImages.java
?? deliverables/backend/sprint-0/jiugeng-erp-backend/src/main/java/com/jiugeng/erp/supplier/SupplierExternalImages.java
?? deliverables/backend/sprint-0/jiugeng-erp-backend/src/main/java/com/jiugeng/erp/watch/WatchExternalImages.java
?? deliverables/backend/sprint-0/jiugeng-erp-backend/src/main/resources/db/migration/V107__image_delivery.sql
?? deliverables/backend/sprint-0/jiugeng-erp-backend/src/test/java/com/jiugeng/erp/catalogmedia/ExternalImageFetcherTest.java
?? deliverables/backend/sprint-0/jiugeng-erp-backend/src/test/java/com/jiugeng/erp/catalogmedia/ImageDeliveryTest.java
?? deliverables/pjm/ERP-101_IMAGE_PERFORMANCE_LOCAL_2026-11-23.md
?? deliverables/pjm/IMAGE_PDF_RELEASE_HANDOFF_2026-11-23.md
?? deliverables/pjm/PDF_CLOSEOUT_LOCAL_2026-11-23.md
?? frontend/e2e/pdf-closeout.smoke.ts
?? frontend/src/components/PdfPagePreview.test.ts
?? frontend/src/components/PdfTextLayer.vue
?? frontend/src/utils/pdfImageInput.test.ts
?? frontend/src/utils/pdfImageInput.ts
?? frontend/src/utils/pdfPreviewPool.test.ts
?? frontend/src/utils/pdfPreviewPool.ts
?? frontend/src/utils/pdfTextLayout.test.ts
?? frontend/src/utils/pdfTextLayout.ts
?? scripts/diagnostics/catalog-image-performance.js
```
