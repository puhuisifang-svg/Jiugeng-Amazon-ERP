# ERP-076：私有对象存储接入（进行中）

日期：2026-10-20。分支：`feature/ERP-076-oss-storage`。
基础 SHA：`482b8e0d26826a8bd39cb2f12243fcf59641db08`。

## 授权与前置工作

用户要求资源文件接入已创建的阿里云 OSS，并明确同意先把该 Bucket 改为私有。
已在控制台保存，保存结束后只读状态显示私有选中、公共读写未选中；未操作其他 Bucket。
ERP-075 已独立提交至 `0e6b637af22c253634f00f67ebad19e8dc35fa97`，未合入本分支，未部署。
本任务开始时工作区干净；本任务修改均未提交。不得将本记录理解为线上切换完成。

## 实施计划

1. 统一后端对象存储接口，提供本地与 OSS 实现，通过运行时配置切换，配置中不硬编码真实 Bucket、凭证。
2. 保留图片解码、缩略图、文件大小/类型校验；商品及发货图片、SKU 说明书/标签、发货附件、外箱标 PDF 和导入文件逐项接入。
3. 下载继续走现有鉴权接口；不开放匿名读写，不把永久密钥或公共文件地址交给前端。
4. 历史文件按源存储类型复制并校验字节摘要，以现有不可变 ID 映射对象路径，不改动业务记录；原件保留，迁移可重入。不能用重启或清空数据库代替迁移。
5. 测试配置缺失、无权限、上传失败、对象丢失、路径越界、旧资源读取及迁移重试；运行后端完整门禁，再进行真实 OSS 小样本上传/下载核验。

## 已检查的入口

- `catalogmedia/LocalCatalogImageStore.java`：商品图片及缩略图使用本地文件。
- `dashboard/LocalDashboardImageStore.java`：首页图片及缩略图使用本地文件。
- `product/SkuDocumentController.java`：SKU 附件使用数据库 content。
- `dashboard/ShipmentDocumentController.java`：发货标签使用数据库 content。
- `cartonlabel/LabelService.java`：生成的 PDF 原存数据库。
- `importexport/LocalImportFileStore.java`：导入源文件及结果文件原存本地暂存目录。

## 已实现与验证

- 新增 `objectstorage/PrivateObjectStorage.java`、`ObjectStorageConfiguration.java`；官方 Java SDK 3.18.4，HTTPS/V4，超时和大小限制，异常脱敏、路径校验，单独凭证文件且要求 owner-only 权限。
- 六类资源入口均接入；`app.storage.provider` 默认 local，显式 oss 才启用。OSS 主读，只有 NoSuchKey 可回退原存储，权限/网络错误不回退。新上传先写 OSS，同时保留本地/数据库备份，暂不收缩原存储空间。
- 下载权限、URL、文件类型/尺寸校验不变；不开放 OSS 公共地址，不向前端发送凭证。数据库写入失败可能留下不可达对象，本轮不自动批量清理此类云端孤儿，避免误删。
- 新增迁移工具 `scripts/ops/migrate-oss-resources.py`：默认只读检查；`--apply` 复制并逐个 SHA-256 校验，禁止覆盖冲突对象，不删除源文件、不修改数据库。应在发布时再增量运行一次以覆盖开发期间新增文件。
- 实际预检：22 个源对象，缺失导入文件 0；实际复制：22 个对象校验通过，原件全部保留。
- 新凭证已复制到 Git 忽略的本地 secrets 目录，权限 600；真实密钥和 Bucket 参数不进入提交文件。此前凭证泄露事件已告知用户并要求撤销旧密钥；无法仅凭新文件证明旧密钥已撤销。
- `./mvnw clean verify`（启用真实 OSS 探针）：188 tests PASS，包括 V4 上传/下载/删除、默认本地、配置错误、权限错误、路径越界、失败脱敏，以及现有业务回归。
- 最后一轮真实探针还验证了对象匿名 GET 返回 403；`git diff --check` 通过，待提交文件的凭证值泄漏扫描为 0。
- `scripts/ops/test_migrate_oss_resources.py`：5 tests PASS，覆盖重入、冲突不覆盖、越界、符号链接和凭证权限。
- 前端与导入契约未改动，未运行其门禁。迁移工具的临时 Python 3.9 SDK 环境有 LibreSSL 警告；正式后端使用 Java SDK，发布迁移推荐 Python 3.11+ / OpenSSL 环境。

## 发布配置（由项目经理验证并切换）

设置 `ERP_STORAGE_PROVIDER=oss`、`ERP_OSS_ENDPOINT`（HTTPS 区域 endpoint）、`ERP_OSS_REGION`、
`ERP_OSS_BUCKET`、`ERP_OSS_CREDENTIALS_FILE`（指向本机 secrets 文件），`ERP_OSS_PREFIX` 默认 `erp/`。
不得把密钥放入命令行参数。若需回退，可设 provider=local；本轮原文件/数据库备份仍保留。

迁移另需 `ERP_CATALOG_IMAGE_DIR`、`ERP_DASHBOARD_IMAGE_DIR`、`ERP_IMPORT_STAGING_DIR`、
`ERP_MIGRATION_DB_PASSWORD_FILE`、`ERP_MIGRATION_DB_USER`、`ERP_MIGRATION_DB_NAME`；
数据库端口通过 `ERP_MIGRATION_DB_PORT` 设置，默认 13306，仅连接本机。
工具依赖 oss2、pymysql；先 dry-run，再 --apply。它不启动应用、不执行数据库迁移、不提供 HTTP 管理接口。

## 安全前置条件与交接

凭证真实对象操作已验证；对应 RAM 身份及最小权限仍需发布审核，不应仅凭操作成功断言未使用主账号密钥。
建议仅授权目标 Bucket 的应用前缀 GetObject、PutObject、DeleteObject 及对象 ACL 所需操作；不授予跨 Bucket 或管理账号权限。
当前运行服务未重启，线上文件仍沿用原有存储。尚未合入 main、提交、部署或完成线上验收。
没有无关任务改动；工作区 dirty lock 属于 ERP-076，后续不得并发开展第二个修改任务。
