# ERP-101 图片性能优化（2026-11-23，本地迭代）

> 最新状态：用户已授权交项目经理提交 main 并部署；统一验证与交接边界见 [DEL-049](IMAGE_PDF_RELEASE_HANDOFF_2026-11-23.md)。以下保留开发阶段实施事实。

## 授权、写入权与状态

用户批准方案 1–7，明确系统显示尺寸不变、历史图片需要回填、外链直接转受控存储且来源变化要更新；不做 CDN。本轮只授权本地实现，未授权提交、推送、发布。PJM 已在 2026-11-23 明确分配 ERP-101，并将唯一写入权交给当前 Codex。

分支 `feature/ERP-101-image-performance`；由已同步 `origin/main` 的干净基线 `dfba431424226445db34aa1c937915d85edef9dd` 开始。修改未提交；既有生产发布结果不代表本轮上线。完整门禁、多尺寸浏览器和跨模块业务回归在用户要求交接时统一执行。

## 线上只读测速证据

经用户允许，通过已登录 Safari 页面开启网页开发者功能并读取 Resource Timing；仅记录匿名时间/体积，不保存图片内容、URL、业务 ID 或令牌，不写线上业务数据。测速后关闭调试面板。

- 已有页面历史 7 个受控图片请求：中位 2,381 ms、最大 5,788 ms、响应体合计约 750 KiB。历史记录可能混合路由/预览，不当作一次完整首屏样本。
- 产品列表刷新后独立取得的首个图片请求：3,262 ms，其中首字节等待 2,814 ms、下载 446 ms，约 60 KiB。调试面板缩小了可见区域，此样本不代表整页所有图片或服务器冷缓存。
- 对同一已加载图片连续三次正常 GET：3,419 / 1,325 / 1,305 ms；等待 2,912 / 842 / 831 ms；均 200，约 60 KiB；Cache-Control 实测为 `no-store, no-store`。
- 仓库 Caddy 模板对 `/api/*` 统一加 no-store，后端也加 no-store，和线上重复响应头一致。未登录服务器读取实际 Caddy 配置，不能把此对应关系当作服务器配置逐项审计。
- TTFB 包含网络及服务端等待，尚未据此断言 OSS 或数据库慢。新响应增加 `Server-Timing: catalog_meta, image_read`（不包含前置 IAM 过滤器耗时），后续可进一步分段定位。没有凭空承诺实际提速百分比。

复测入口：`scripts/diagnostics/catalog-image-performance.js` 可在已授权 ERP 页面控制台使用，仅输出匿名汇总。`window.jiugengImageDiagnostics()` 提供前端缓存命中、队列与 Resource Timing 汇总。比较刷新、切页返回、重复请求；保留浏览器正常缓存，注意 fetch 会将 304 合成为 200，网络字节以 transferSize 为准。

## 实施映射

| 用户项 | 本地实现 |
| --- | --- |
| 1 测速 | 上述现场基线、可复用匿名诊断脚本、前端队列指标、后端 Server-Timing/读取来源与后台任务指标。 |
| 2 小图编码，显示大小不变 | 普通图片长边 160px，照片 JPEG 质量 80%；透明图保留 PNG，二维码/联系图/运单使用 320px 无损 PNG。原图保留；CSS 展示大小、父子缩进及统一预览容器不变。使用现有 JPEG/PNG 编码器，不引入 WebP 原生编码依赖。 |
| 3 历史回填 | V107 的 `catalog_image_variant` 保存新缩略图独立路径，默认每 30 秒最多 10 张，租约避免并发重做，失败 1 小时后重试；重启从数据库继续。未生成时用旧缩略图，不阻塞页面。新上传即时生成，原图及旧缩略图不覆盖。 |
| 4 缓存 | 普通已关联图片使用 private/no-cache/must-revalidate、ETag 和 Vary Authorization；先鉴权和查当前变体，再决定 304，命中时不读取文件。敏感图片与待关联上传保持 no-store。前端会话缓存保持 60 秒、32 MiB，条数上限 200；换账号/退出清理，版本换图以新资产/不可变文件键失效。 |
| 5 加载顺序 | 可见图片高于邻近预加载，预加载边距 100px；普通并发最多 5，总并发最多 6，为预览预留位置。大图优先、先显示缓存中的小图，再替换原图；未完成的外链导入会释放下载位置，限次等待和重试。 |
| 6 外链受控及更新 | 外链 URL 保留为来源，展示、预览及产品下载/采购图片导出从受控镜像取图。首次入库和定时同步后台执行，默认每小时条件检查，同一 URL 内容变化后生成新文件并原子切换，至少每日完整核对一次，URL 更换按新来源处理。失败保留旧图、指数退避，不因来源 404 清空图片。旧文件延迟 24 小时清理。 |
| 7 后端读取 | OSS 回源成功后落本地读缓存，默认上限 256 MiB，重启可用；按最近使用淘汰只读副本，不删除权威上传。大原图不挤占全部内存小图缓存；保留同资源合并读取及来源计数。未无依据修改数据库连接池或并发上限。 |

## 模块与文件入口

- 后端 `catalogmedia`：`CatalogImageVariants`、`ImageHttpResponse`、`ExternalImageController/Service/Fetcher`、`ExternalImageSource` 与 JDBC/JSON 适配基类；`CatalogImageController/Service/Store` 及 `LocalCatalogImageStore`。
- 各领域通过公开 `ExternalImageSource` 提供自己已存在的图片引用和权限：产品、辅料、供应商、样品、采购、监控、店铺上新、候选及开发；无跨模块内部类型依赖。新增/编辑草稿须对应维护权限，临时授权绑定当前账号及该维护权限；普通读者不能将任意 URL 当作无鉴权图片代理。
- 前端 `AuthenticatedCatalogImage` 统一受控/外链加载；`catalogImageCache` 管优先级、取消、缓存及诊断；产品、辅料、供应商、样品、采购、客户商品/发货商品、监控/候选/开发相关图片入口接入。原文件上传 Blob、PDF 页面、财务附件等独立二进制附件流程保持各自用途，不抓取本地 Blob/data URL。
- `catalogApi.externalImageBlob` 先解析受控镜像，再请求二进制；图片未准备好时服务端返回 503/Retry-After，前端限次等待后提供重试。
- 生产及本地 Caddy 模板的 API Cache-Control 改为缺省 `?Cache-Control no-store`，尊重后端明确给出的私有缓存头；其他 API 默认仍不可缓存。[Caddy 官方默认响应头语义](https://caddyserver.com/docs/caddyfile/directives/header)。

## 边界及交接注意

- 外链下载限制 HTTP/HTTPS、80/443、无 URL 用户凭据；连接时通过受控 DNS 解析器限定公开 IPv4，每次跳转重新校验，拒绝内网/回环/元数据地址；不发送 ERP 凭据、Cookie 或浏览器 Referer。最多 3 次跳转、20 秒总读取预算、5 MiB/4096px/1600 万像素限制，解码失败保留旧图。IPv6-only 来源暂不支持。
- 外链同步是来源镜像，旧外链快照原本仅保存 URL，现在也会跟随该 URL 更新；原先已经独立复制为受控资产的业务快照不会被镜像任务改写。来源变更检查有调度和网络延迟，不承诺瞬时同步。
- 后台网络队列最多 2 个工作线程、24 个排队任务，任务失败可重试；镜像地址仍保存在受控数据库以供同步，不把业务 URL、令牌或图片写入诊断文档。测试及 mock profile 禁止自动后台网络任务；mock 前端保留原模拟外链显示。
- 本轮未接 CDN、未改生产带宽、未扩大公共访问权限。
- **发布时必须核对并应用实际 Caddy API 缓存配置。仅部署应用 JAR 不能消除旧代理强制的 no-store。** 三个仓库模板已修改，线上实际配置变更、Caddy 校验/重载由后续获发布授权的 PJM 完成；本轮没有改线上配置。

## 局部检查与待验

- 后端 `./mvnw --batch-mode --no-transfer-progress -Dtest=ImageDeliveryTest,ExternalImageFetcherTest,LocalCatalogImageStoreTest test`：14 项通过，日志 `/tmp/erp101-image-tests.log`。包含条件缓存鉴权、变体回填不改原图、敏感图无损、源更新/失败保留、来源 SQL、SSRF 基础拒绝、磁盘缓存重启和容量淘汰。首次检查暴露的领域软删除字段误用和测试认证上下文已修正。
- 合成 640×480 渐变 PNG：旧 320px 缩略图 196,344 字节，新缩略图 2,916 字节；这是合成用例，不当作线上所有图片的压缩率承诺。
- 前端 `pnpm exec vitest run src/utils/catalogImageCache.test.ts src/components/AuthenticatedCatalogImage.test.ts`：2 文件 / 11 项通过，日志 `/tmp/erp101-ui-tests.log`。覆盖会话隔离、合并/取消、优先级及预览预留、渐进显示、外链转受控和 URL 变化。
- 前端 `pnpm exec vue-tsc --noEmit` 通过，日志 `/tmp/erp101-types.log`；对本轮变更的 TS/Vue 文件执行 `pnpm exec eslint --fix --max-warnings=0 <变更文件列表>` 通过，日志 `/tmp/erp101-eslint.log`。
- 后端 `./mvnw --batch-mode --no-transfer-progress -DskipTests package` 本地运行包构建通过，日志 `/tmp/erp101-package.log`；它不是完整门禁。局部检查后补充的事务回滚文件清理及租约提交保护须随交接回归覆盖。
- 收尾检查：`git diff --check` 无输出；对全部变更 Markdown 的 155 个本地链接进行存在性检查，0 失效；`node --check scripts/diagnostics/catalog-image-performance.js` 通过。`git status --short` 已复核，均为 ERP-101 代码、测试及配套文档，无无关改动、无暂存文件。
- 待交接统一执行：受影响完整前后端门禁、跨模块原有用例/导出、三尺寸图片与预览、外链慢源/重定向/取消/透明图与二维码视觉、Caddy 配置校验和实际 HTTP 缓存链路。无真实规模压测或优化后线上速度结论。

## 本地运行同步

- 迁移前备份：`.local/backups/images-before-v107-20261123-150925.sql`，18,583,661 字节，权限 0600；原数据库、上传文件和凭据均保留。
- 当前运行包：`.local/releases/backend/images-erp101-20261123-151025/backend.jar`，SHA-256 `74f4182ed7e82e06608d324a2eb6c6c6a497acda1dab1c4a2c6bfc4e0abc9869`。旧启动脚本保留在 `.local/runtime/run-backend.before-images-20261123-151025.sh`，旧运行包未删除。
- 沿用 tmux `jiugeng-mvp`、原 mysql profile 与私有配置。后端旧 pane 结束后使用 `tmux respawn-pane` 启动更新包，无并行实例；前端原持久 Vite 服务通过 HMR 接收改动。
- `/tmp/erp101-local-backend.log`：15:11:51 成功应用 V107，15:11:52 启动完成；`curl -fsS http://127.0.0.1:8080/actuator/health` 返回 `{"status":"UP"}`。自动回填/镜像在本地后台分批执行，不代表已全部完成。
- 打包后补充了 `application-mock.yml` 关闭维护任务及前端 mock 兼容；当前 mysql 运行包不受该 mock 配置差异影响。正式交接需从最终树重新构建。
- 本地备份与运行配置属于忽略目录，禁止提交。无生产业务写入、配置变更或发布操作。


## 同迭代用户追加：PDF 导出命名（2026-11-23）

本轮后续用户要求 PDF 编辑四页签导出沿用源文件名，沿用当前分支及 Codex 写入权，不创建第二个并行任务。`PdfToolsView.vue`：单 PDF 输出保留原文件名，图片转 PDF 使用当前第一张图片主名，拆分 ZIP 用源主名且包内文件加唯一序号。详细约定见状态报告新增节。仅本地实现，`git diff --check` 通过；交接时统一核对四页签、图片重排/移除和多文件 ZIP 命名。本节追加文件属于当前迭代，前述 ERP-101 局部测试不代表已经验证此追加功能。
