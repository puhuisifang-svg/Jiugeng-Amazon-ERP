# S0-OPS-01 Docker Hub 链路与 Compose 交付

| 字段 | 内容 |
| --- | --- |
| 日期 | 2026-09-12 |
| Owner | Ops |
| 状态 | In Review |

## 结论

- Docker daemon 与 `desktop-linux` context 正常。
- Docker Hub 相关域名经当前代理仍在 TLS 阶段失败；未关闭 TLS 校验，也未配置不安全 Registry。
- `quay.io` TLS 校验成功，采用 MinIO 官方 Quay 镜像；MySQL 8 与 Redis 7.2 使用已存在的本机镜像。三项镜像均固定 digest。
- ERP Compose 使用独立容器、网络、命名卷和回环地址端口，不修改既有 `chewy-mysql`、`chewy-redis`。

## 脱敏诊断证据

2026-09-12 复核结果（仅记录公开域名、错误类型、HTTP/TLS 状态，不包含代理凭据）：

| 目标 | 结果 |
| --- | --- |
| `registry-1.docker.io:443` | TLS `SSL_ERROR_SYSCALL`，HTTP 000 |
| `auth.docker.io:443` | TLS `SSL_ERROR_SYSCALL`，HTTP 000 |
| `production.cloudflare.docker.com:443` | TLS 连接超时，HTTP 000 |
| `quay.io:443` | HTTP 200，证书校验成功 |
| `ghcr.io:443` | HTTP 301，证书校验成功 |

Docker daemon 代理为 Docker Desktop 内部代理端点；未记录本机代理规则、账号或 Token。使用空的临时 Docker CLI 配置后，MinIO 官方 Quay 镜像拉取成功，说明 CLI 凭据助手与 Docker Hub TLS 是两个独立问题。Docker Hub 故障仍需上游代理管理员修复对应域名路由。

## 配置与安全

- 配置文件：`compose.yaml`、`.env.example`。
- 密码/对象存储身份只通过 Docker Compose file secrets 注入。默认落在 gitignored 的 `.local/secrets/`，目录权限 `0700`、文件权限 `0600`；仓库不保存值。
- Redis 只用于 Local Compose，绑定 `127.0.0.1` 且位于独立网络；后续共享 Dev/Test 环境必须启用 ACL/TLS 或使用托管 Redis。
- 默认本机端口：MySQL `13306`、Redis `16379`、MinIO API `19000`、MinIO Console `19001`，避免与其他项目冲突。

## 启停与验证

1. 首次执行 `./scripts/ops/init-local-secrets.sh`。脚本只创建缺失文件，不显示值；可用 `ERP_LOCAL_SECRET_DIR` 指向其他受保护目录。
2. 执行 `docker compose up -d`。
3. 执行 `./scripts/ops/compose-health.sh`。
4. 执行 `./scripts/ops/verify-persistence.sh`，验证三类命名卷在容器重启后保留标记数据。
5. 轮换执行 `./scripts/ops/init-local-secrets.sh --rotate`：先使用受保护临时文件在线更新 MySQL root/应用账号，再原子替换四个文件并强制重建服务；临时文件退出时删除，值不进入参数或日志。
6. 停止服务使用 `docker compose stop`；删除容器使用 `docker compose down`。除非已有审批与备份，不得附加 `--volumes`。

## 2026-09-12 实测证据

- `docker compose config --quiet`：通过。
- `jiugeng-erp-mysql`、`jiugeng-erp-redis`、`jiugeng-erp-minio`：全部达到 `healthy`。
- 稳定密钥目录与四个文件权限分别验证为 `0700`、`0600`，且 `.local/` 已被 Git 忽略。
- 在三类命名卷分别写入非敏感标记后验证：容器 restart、Compose stop/start、Compose down/up（重建）后 MySQL 卷标记、Redis AOF 键、MinIO 卷标记均保留。
- 完整轮换并强制重建后，三服务恢复 `healthy`，三类持久化标记仍保留；MySQL 应用账号使用轮换后 file secret 的无回显认证查询成功。
- MySQL 配置 `30s` 停止宽限期；复测 `docker compose stop mysql` 后退出码为 `0`。
- 从临时密钥方案迁移时，旧 MySQL 测试卷已无法认证。确认其仅含 Ops 测试标记后，精确删除并重建 `jiugeng-erp-mysql-data`；未删除 Redis/MinIO 卷，未触碰任何 chewy 资源。
- 重启前后 `chewy-mysql`、`chewy-redis` 的容器 ID 与启动时间摘要一致：未修改。
- 验证完成后 ERP 三个容器已停止；命名卷与稳定本地 file secrets 保留，可重复启动。轮换临时文件已销毁，未写入仓库或日志。

## 风险与后续

- Docker Hub TLS 仍为外部网络门禁；代理规则修复后应重新验证三个 Docker Hub 域名并执行公开镜像拉取。
- 固定 digest 后的 MinIO 版本升级需走依赖评审与持久化回归。
