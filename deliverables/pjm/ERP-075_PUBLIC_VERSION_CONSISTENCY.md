# ERP-075：页面版本检测与缓存配置

日期：2026-10-20。分支：`fix/ERP-075-public-version-consistency`。
基础 SHA：`482b8e0d26826a8bd39cb2f12243fcf59641db08`。

## 范围和调查结论

用户授权先完成并提交未提交改动，再进行 OSS 开发。本任务不包括 OSS，不包含 main 合并或线上发布。
调查时公网和本地构建的入口、关键页面资源一致；用户截图已有最新导出入口，未勾选记录时按钮禁用。
未证实“其他电脑功能不同”由旧版本缓存引起。本次是版本更新防护，不应声明已修复截图所示问题的根因。

## 修改

- `frontend/src/App.vue`：挂载生产环境版本提示。
- `frontend/src/components/ReleaseUpdateNotice.vue`：定期及恢复焦点时检测入口变化，动态资源加载失败时提示刷新；不自动刷新，刷新前确认，避免丢失未保存内容；卸载时清理监听。
- `frontend/src/ui/releaseVersion.ts`：同源入口比较，HTML 请求禁用缓存；HTTP 错误或非 HTML 不误判版本更新。
- 对应两个测试文件：新增 5 个测试。
- `frontend/vite.config.ts`：支持 `ERP_BUILD_OUT_DIR`，默认仍为 dist，允许验证构建不覆盖当前服务目录。
- `infra/caddy/Caddyfile`：候选部署配置，HTML/API 禁止缓存，带哈希资源长期缓存；尚未替换运行中的配置。

## 验证

- `ERP_BUILD_OUT_DIR="$(mktemp -d /tmp/erp075-build-XXXXXX)" pnpm quality`：通过，63 个测试文件、248 个测试，lint、类型检查、生产构建均通过。
- `ERP_FRONTEND_DIST="$PWD/frontend/dist" caddy validate --config infra/caddy/Caddyfile`：通过。
- `git diff --check`：通过。
- 没有改动后端，未运行后端门禁。没有重启服务、替换线上 dist 或应用 Caddy 配置。

## 交接与限制

最终提交 SHA 以本记录所在提交为准。提交仅包含上述文件及本记录；不存在其他任务的未提交改动。
项目经理后续发布前需独立审核 SHA，并验证实际 HTTP 缓存响应头及更新提示；本地验证不等于发布验收。
版本检测每分钟请求一次小型 HTML；离线时保持页面，不强制更新。配置默认绑定本机，仍依赖现有反向隧道。
OSS 接入另立 ERP-076，不能将本任务提交描述为 OSS 已接入。
