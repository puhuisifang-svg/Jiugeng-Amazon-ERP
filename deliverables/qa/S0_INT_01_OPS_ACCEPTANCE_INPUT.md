# S0-INT-01：DEL-018/019 Ops P1 验收输入

| 字段 | 内容 |
| --- | --- |
| 项目 | 九更亚马逊 ERP |
| Reviewer | QA |
| 关联任务 | S0-INT-01、S0-OPS-01、S0-OPS-02 |
| 输入交付物 | DEL-018、DEL-019 |
| 状态 | OPS-001 Dynamic Pass / OPS-002 Local Static Pass, Online CI Blocked |
| 日期 | 2026-09-12 |

## 1. PJM 发现与分级

| ID | 发现 | 等级 | 当前结论 |
| --- | --- | --- | --- |
| BUG-S0-OPS-001 | 临时 Docker Compose file-secret 被删除后，MySQL/MinIO 既有容器因 bind source 缺失无法再次启动 | P1 | **Pass / Closed** |
| BUG-S0-OPS-002 | CI 的 `pnpm/action-setup`、`gitleaks/gitleaks-action` 使用 annotated tag object SHA，而非 peeled commit SHA | P1 | **Local Static Pass / GitHub Online Blocked** |

仅限 Ops 本轮验收：P0=0。OPS-001 的 Compose 生命周期门禁已通过；OPS-002 仍需 GitHub 在线 Actions 证据，不得将本地 SHA 通过外推为 CI 整体 Pass。

## 2. BUG-S0-OPS-001：稳定 file-secret 生命周期与持久化复验

### 2.1 静态门禁

1. 默认 secret 目录必须位于 Git 忽略范围；仓库仅保存变量名、路径约定和初始化脚本，不保存值。
2. 初始化脚本创建稳定目录和四个非空文件；目录权限 `0700`、文件权限 `0600`。
3. 重复初始化不得改写已有值；稳定 secret 在 `stop/start`、`down/up` 和验收清理后继续保留。
4. 只有受控轮换流程可替换稳定 secret：临时文件位于受保护临时目录，退出时删除；替换需原子完成，并同步更新已有 MySQL 账号后强制重建相关服务。
5. Runbook 必须明确禁止把稳定 secret 当临时夹具删除，并说明文件丢失后的恢复边界；任何命令、日志、Compose 展开、镜像层和 Git 差异均不得回显值。

### 2.2 动态复验顺序

1. 在隔离环境初始化 stable secrets，记录文件存在性、权限和不可逆摘要；再次初始化后摘要不变。
2. `docker compose config --quiet`，随后启动 MySQL、Redis、MinIO并等待全部 healthy。
3. 写入三类非敏感探针：MySQL 行/标记、Redis AOF 键、MinIO 对象及 SHA-256。
4. 执行容器 `restart`，健康恢复后逐项读取并比对探针。
5. 执行 `docker compose stop` / `start`，再次读取并比对探针；stable secret 文件必须仍存在。
6. 执行 `docker compose down`（禁止 `-v`）/ `up -d`，验证容器重建成功、命名卷未变化、探针与对象哈希保持。
7. 执行受控 `--rotate`，验证服务强制重建后 healthy、MySQL 新 file-secret 可认证、旧认证失效、三类探针仍保持且临时轮换文件已清理。
8. 停止服务但保留命名卷和 stable secrets，再次启动验证可恢复；不得以删除 secret 文件作为验收清理动作。

通过证据至少包含：命令与退出码、服务/健康状态、secret 文件权限与前后摘要、容器/卷清单、三类探针前后值、MinIO 对象哈希、轮换临时目录清理检查，以及无敏感值的日志/扫描结果。

## 3. BUG-S0-OPS-002：GitHub Actions peeled commit SHA 复验

1. 枚举 `.github/workflows/**/*.{yml,yaml}` 中每个 `uses:`；本地 action 与 `docker://` 引用单独说明，其余远程 Action 必须固定为 40 位实际 commit SHA。
2. 对每个注释的 major tag，从 Action 官方仓库解析 tag：annotated tag 必须使用 `refs/tags/<tag>^{}` 的 peeled commit；lightweight tag 使用其直接 commit。Workflow 中 SHA 必须与解析结果精确一致，不能使用 tag object SHA。
3. 至少显式复核 `pnpm/action-setup@v4` 与 `gitleaks/gitleaks-action@v2`；同一规则覆盖 checkout、setup-java、setup-node 等所有 Action，不只修复已发现的两项。
4. YAML 解析、CI dry/static validation 和真实 PR/目标分支运行均须通过；Secret scan、Backend、Frontend、Compose jobs 可追溯到同一提交。
5. 使用安全隔离失败样例证明 secret scan/构建失败能阻断，再清理样例确认恢复；日志与产物不得泄露凭据。

通过证据至少包含：完整 `uses:` 清单、官方仓库 `git ls-remote`/GitHub API 的 tag 与 peeled SHA 映射、逐项比对输出、Workflow 校验结果、CI run URL/commit、各 job 结论及失败门禁演示。

## 4. QA 最终复验结果

### 4.1 BUG-S0-OPS-001

- stable secret 初始化两次退出码均为 0；目录 `0700`、四文件 `0600` 且非空，第二次初始化前后四个 SHA-256 摘要逐项不变；`.local/` 命中 Git ignore。
- MySQL、Redis、MinIO 启动后均 healthy；三类非敏感探针为 MySQL 卷标记、Redis AOF 键与 MinIO 卷对象。MinIO 对象固定 SHA-256 为 `46ffc9e69dda34417e5a94a5bb47928b2c697ef647fd399f834e3274089178c0`。
- 容器 `restart` 后三服务恢复 healthy；Compose `stop/start` 后再次恢复 healthy；三类探针与 MinIO 对象哈希均保持。
- Compose `down`（未带 `-v`）后，三类命名卷均保留；`up -d --pull never` 重建后服务 healthy，三类探针与对象哈希保持。
- `init-local-secrets.sh --rotate` 成功执行，四个 secret 摘要均变化、权限保持 `0600`，服务强制重建后 healthy，三类探针保持。QA 使用受保护临时认证文件验证：新应用凭据认证成功、旧凭据认证明确失败；凭据未进入参数、环境变量或日志。
- 轮换临时目录与 QA 认证临时目录残留均为 0。最终 `stop -> start` 恢复后三服务 healthy、三类探针及对象哈希保持；再次安全停止后三个 ERP 容器均 `exited/0`。
- 三类命名卷、stable secrets 保留；`chewy-mysql`、`chewy-redis` 继续 healthy；高置信敏感扫描及 `git diff --check` 通过。

结论：**BUG-S0-OPS-001 Dynamic Pass / Closed。**

### 4.2 BUG-S0-OPS-002

- 枚举唯一 Workflow 的全部远程 `uses:`；checkout、setup-java、setup-node、pnpm/action-setup、gitleaks-action 均固定 40 位 SHA。
- QA 通过各官方仓库 `git ls-remote` 对比 major tag：checkout/setup-java/setup-node 的 lightweight tag 3/3 匹配；pnpm v4 与 gitleaks v2 的 annotated tag 均匹配 peeled commit，5/5 Pass。
- GitHub 私有远程/Actions 尚无授权，无法提供在线 run URL、同提交 job 结果和失败门禁演示。

结论：**BUG-S0-OPS-002 Local Static Pass；GitHub Online Actions Blocked。** 本地缺陷修订已通过，但在在线 CI 证据到位前不关闭整体 CI 门禁。

S0-INT-01：继续 In Progress。
