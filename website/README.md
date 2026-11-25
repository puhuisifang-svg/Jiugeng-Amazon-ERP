# 九更官网 · ERP-112

[需求基线 DEL-054](../docs/01-product/JIUGENG_CORPORATE_WEBSITE_PRD_v0.1_DRAFT.md)。主体为厦门九更网络科技有限公司。合作区展示经用户确认的公开邮箱与手机号，支持邮件、拨号和复制。首页文案集中在 `src/index.html`。品牌标识和海港场景均为原创本地 SVG；资源和导航可从域名或 IP 根路径加载。

## 本地使用

在仓库根目录运行，使用 Python 3.9 或以上，无需安装依赖。

```sh
python3 website/build.py
python3 website/preview.py
```

打开 [官网本机预览](http://127.0.0.1:4188/)。端口占用时使用 `--port 4189`。保存源文件后重新构建并刷新浏览器；在预览终端按 Ctrl+C 停止。双击生成的 `website/dist/index.html` 也能查看首页。契约检查命令为 `python3 -m unittest discover -s website/tests -v`。

## 产物与发布边界

`website/dist/` 是完整静态站点，`website-version.json` 记录内容摘要。未来由 PJM 把完整产物复制到官网专用版本目录，记录版本后切换官网目录指向；回退时切回上一个官网目录。资源文件使用固定文件名，部署时为 HTML 和静态资源配置重新验证缓存。未知路径应返回 HTTP 404 和本站 `404.html`。域名证书、IP 入口、ERP 替代入口验证及切换顺序见 DEL-054 第 7、11、12 节。

PJM 发布接手更新：官网独立 `Website CI` 执行契约、构建并保存带提交 SHA 的独立 artifact。ERP Production 已按应用/发布配置路径过滤；仅官网或发布记录变更不会触发 ERP 部署，混合 ERP 变更仍执行完整发布。官网自动服务器部署尚未接通专用管理通道，CI artifact 不是线上部署；不得借用只允许 ERP 发布的 SSH 命令投递官网包。预览服务器仅绑定本机，用于开发。公开时机与备案字段由业务确认。

## ERP-112 首次部署

首次部署与 IP 切换已于 2026-11-25 17:42 完成：默认 HTTP IP 为官网，HTTP IP:8081 为原 ERP；安全组放行所有 IPv4 来源的 TCP8081。官网内容摘要 `ae09c183535a6ecfd9e432ba87cb7c073d3e37b109c4e2846b81451cbe784d5a` 已从生产版本文件核对。原内部服务、ERP 数据和应用版本不变。自动 CI/CD 由 PJM 接管，当前源码尚未提交。

同日21:54域名上线验收通过：[官网](https://jiugengerp.cn/)、[ERP](https://erp.jiugengerp.cn/)；www通过301跳根域，保留路径及查询参数。三域HTTP均转HTTPS，Caddy管理证书与自动续期，原IP/8081继续保留。域名变更前备份在服务器 `/srv/jiugeng-website/backups/domains-20261125-182024/`，其中 `Caddyfile.original` 为仅IP入口、`Caddyfile.domains` 为当前域名/IP整合配置、`verification-https.txt` 为TLS与路由验收结果。域名模板需与现网完整配置整合，不得直接覆盖全局和探针规则。备案号页脚等待可核实的实际编号。

实际完整配置备份在服务器 `/srv/jiugeng-website/backups/20261125-173820/`：`Caddyfile.original` 为切换前配置，`Caddyfile.phase1` 为两处 ERP 入口，`Caddyfile.phase2` 为当前官网/ERP 分流。需要回退时，先校验选定备份，再恢复 `/etc/caddy/Caddyfile` 并平滑 reload；恢复 original 后还需按实际安排撤掉8081安全组规则，恢复 phase1 则保留8081。不要把这些服务器备份或实际生产标识提交到仓库。

2026-11-25 用户授权 Codex 负责首次部署与服务器配置，PJM 负责后续自动 CI/CD。版本证据和当前进度见[状态报告](../docs/08-project-management/STATUS_REPORT.md)。模板中的示例 IP 仅供替换；生产使用前对照实际 Caddy 版本、现网配置、域名解析、备案接入状态、系统服务和目录权限。

1. 经已认证的管理通道保存当前 Caddy 配置及其导入文件、现用站点指向、DNS 和防火墙规则。记录原 ERP 入口、内部探针结果与版本。
2. 将校验过的官网 zip 解压到新目录 `/srv/jiugeng-website/releases/<内容摘要>/`，核对产物的 `website-version.json` 和本地包摘要，原子切换官网专用 `current` 符号链接。先用回环监听测试 [官网配置](deploy/website.caddy)。
3. 如需过渡期入口，先以[8081 配置](deploy/erp-bridge.caddy.example)为基础复用实际 ERP 站点的完整前端/鉴权 API 路由，配置已约定的安全组和主机防火墙。若正式 ERP 子域已具备 HTTPS 条件，则可以先验证正式入口。用员工网络检查登录、文件读写与深链接，核对与原 ERP 版本一致，再安排旧入口切换。
4. 按已核实的开放时机接入 [IP 配置](deploy/ip.caddy.example)和[域名配置](deploy/domains.caddy.example)。仅替换对应的旧入口，整合现有全局 Caddy 配置和未知 Host 兜底。每阶段先对完整配置执行 `caddy validate --config /etc/caddy/Caddyfile`，再平滑 reload。从外网验证 www 跳转保留路径和查询、根域证书、IP 静态资源、版本与真实 404，同时复核 ERP 及回环探针。
5. 切换异常时恢复该阶段备份的 Caddy 配置、站点指向及受影响的 DNS/防火墙记录，通过校验后 reload 并复验原入口。首次部署以旧入口备份为回退依据。正式 ERP HTTPS 验证成功并完成用户迁移后撤掉临时 8081 入口与对应放行规则。
