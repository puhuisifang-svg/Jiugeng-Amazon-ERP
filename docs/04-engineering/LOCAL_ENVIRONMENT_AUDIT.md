# 本地开发环境审计报告

| 字段 | 内容 |
| --- | --- |
| 项目 | 九更亚马逊 ERP |
| 交付物 ID | DEL-003 |
| Owner | PJM |
| 检测日期 | 2026-09-12 |
| 版本 | v1.5 |
| 状态 | Completed |
| 总体结论 | **未完全具备，存在启动阻塞项** |

## 1. 执行摘要

本机硬件资源、基础命令行工具和主要开发网络出口满足需要。Node.js 已从停止支持的 25.9.0 升级至 26.8.2，并已验证 OpenClaw Gateway 与 `node:sqlite` 正常；Java 21 LTS 与 Maven 已安装并通过编译、运行和中央仓库下载验证。Docker Desktop 与 daemon 已启动并验证正常，现有 MySQL/Redis 容器健康。当前剩余阻塞为项目脚手架及 Maven Wrapper 尚未生成、Docker Hub TLS 链路异常，因此环境**仍不能直接进入完整容器化开发**。

建议采用容器优先的本地环境：MySQL、Redis、对象存储等依赖由 Docker Compose 启动，不要求开发机安装原生服务。

## 2. 检测基线

- Git
- Java 21
- Maven Wrapper（推荐）或兼容 Maven
- Node.js LTS 与选定包管理器
- Docker Desktop / Docker Engine 与 Docker Compose
- MySQL 8、Redis、S3 兼容对象存储（建议容器化）
- 可访问依赖仓库和 Amazon SP-API 端点的网络

Node.js 官方建议生产应用使用 LTS；截至本次检测，Node.js 24 为 LTS，Node.js 26 为 Current。本机使用 Node.js 26.8.2 以满足 OpenClaw 运行时要求；ERP 是否沿用该版本，需在脚手架阶段完成 Vue、Vite、测试及构建依赖兼容性验证后固定。Spring Boot 官方当前版本最低支持 Java 17；本项目仍建议固定 Java 21 LTS。

## 3. 检测结果

### 3.1 系统与资源

| 检查项 | 当前状态 | 结论 |
| --- | --- | --- |
| 操作系统 | macOS 26.3.1 (arm64) | 通过 |
| CPU | 14 核 | 通过 |
| 内存 | 36 GiB | 通过 |
| 工作区磁盘 | 约 369 GiB 可用 | 通过 |
| 文件描述符上限 | 1,048,575 | 通过 |
| 工作区写权限 | 可写 | 通过 |

### 3.2 基础工具

| 工具 | 当前状态 | 结论 |
| --- | --- | --- |
| Git | 2.49.0 | 通过 |
| curl | 8.7.1 | 通过 |
| jq | 1.7.1 | 通过 |
| Homebrew | 5.1.5 | 通过 |
| Xcode CLI Tools | 已安装 | 通过 |
| Git 仓库 | 工作区已初始化，分支 `master` | 条件通过 |
| Git 远程仓库 | 未配置 | 正式协作前需补充 |

### 3.3 Java 后端工具链

| 工具 | 当前状态 | 结论 |
| --- | --- | --- |
| Java Runtime | OpenJDK 21.0.12.1（arm64） | 通过 |
| javac | 21.0.12.1 | 通过 |
| Maven | 3.9.16 | 通过；实际运行于 Java 21 |
| Maven Wrapper | 尚未生成 | 后端脚手架创建时固定并提交 |
| Gradle | 未安装 | 若采用 Maven 则不需要 |
| `JAVA_HOME` | 已在 `~/.zprofile` 固定至 Homebrew `openjdk@21` | 通过 |
| JDK 系统发现 | 已通过用户级 `JavaVirtualMachines` 注册 | 通过 |

Java 验证包含 `java -version`、`javac -version`、`/usr/libexec/java_home -V`、UTF-8 源码编译与 JVM 运行；实测架构为 `aarch64`、默认编码为 UTF-8。

Homebrew Maven 依赖同时安装了 OpenJDK 26，但项目 Shell 已通过 `JAVA_HOME` 和 PATH 固定使用 OpenJDK 21；`mvn --version` 已确认 Java Runtime 为 21.0.12.1。项目级 Wrapper 将在后端 `pom.xml` 创建时生成，不提前创建空工程。

### 3.4 前端工具链

| 工具 | 当前状态 | 结论 |
| --- | --- | --- |
| Node.js | 26.8.2 | **条件通过：OpenClaw 已验证，ERP 依赖待验证** |
| npm | 11.19.1 | 通过 |
| pnpm | 12.4.1 | 通过；项目选定包管理器 |
| Yarn / Corepack | 未安装 | 不需要 |
| Node 版本管理器 | 未发现 | 建议项 |

OpenClaw Gateway 已于升级后重启，实际加载 `/opt/homebrew/Cellar/node/26.8.2/bin/node`，连通性、事件循环和 Telegram 深度探针均通过；`node:sqlite` 中文 TEXT 往返测试通过。旧 Node 25.9.0 keg 暂时保留作为短期回退点。

ERP 工程可先评估共用 Node.js 26.8.2；在 Vue、Vite、测试、构建和 CI 全部验证通过前，不将其标记为正式项目基线。验证通过后应提交版本约束文件和 `packageManager` 字段。

pnpm 12.4.1 已安装，实测可在 Node.js 26.8.2 下运行、解析 Vue 元数据并访问官方 npm Registry。用户全局配置当前使用 `npmmirror`，未作修改；项目根目录通过 `.npmrc` 固定官方 `https://registry.npmjs.org/`，同时启用精确版本保存与严格 Node engine 检查。前端脚手架创建时再生成并提交 `package.json` 与 `pnpm-lock.yaml`。

### 3.5 容器与数据服务

| 工具/服务 | 当前状态 | 结论 |
| --- | --- | --- |
| Docker CLI | 29.4.0 | 已安装 |
| Docker Desktop | 4.69.0 | 已安装 |
| Docker daemon | 已运行，Server 29.4.0、LinuxKit aarch64 | 通过 |
| Docker Compose | 5.1.1 | 已安装 |
| Docker context | `desktop-linux` | 正常配置 |
| 既有容器 | `chewy-mysql`、`chewy-redis` 自动恢复且健康 | 保留未修改 |
| ERP MySQL / Redis / MinIO | 尚未建立项目 Compose | 待后续配置 |
| kubectl | 1.34.1 | 已安装；MVP 暂不要求 Kubernetes |
| Helm | 未安装 | MVP 非必需 |

### 3.6 端口

`3306`、`6379`、`8080`、`5173`、`9000` 当前均无监听进程。端口可用不代表服务已安装。

### 3.7 网络

| 目标 | 结果 | 结论 |
| --- | --- | --- |
| GitHub API | HTTP 200 | 通过 |
| npm Registry | HTTP 200 | 通过 |
| Maven Central | HTTP 200，且 Maven 实际解析并下载依赖成功 | 通过 |
| Amazon SP-API 北美端点 | HTTP 200 | 通过 |
| Docker Registry | daemon 拉取返回 TLS 链路 `EOF` | **阻塞镜像拉取** |

系统 HTTP、HTTPS 和 SOCKS 代理指向本机 `127.0.0.1:7897`。Docker Desktop 已继承代理并在 daemon 内显示内部 HTTP/HTTPS 代理。实际诊断显示：本机代理可接受到 Docker Hub 的 HTTP CONNECT 并返回 200，但远端 TLS ClientHello 后连接立即断开；Docker daemon 匿名拉取 `alpine:3.22` 同样返回 `EOF`。使用临时空 Docker 凭据配置仍可复现，已排除凭据助手和登录态为主因。问题位于云梯/上游代理对 Docker Hub 域名的路由或出口链路，不是 Docker daemon 故障。

## 4. 缺口与优先级

### P0：进入开发前必须完成

1. 前端脚手架创建后，完成 Vue、Vite、测试与构建全链路验证，再正式固定 ERP 的 Node.js 版本；若 Node.js 26.8.2 不通过，使用隔离的 Node.js 24 LTS，不回退 OpenClaw 运行时。
2. 调整云梯分流：让 `registry-1.docker.io`、`auth.docker.io` 与 Docker CDN 域名走可用代理出口，并完成公开镜像拉取。
3. 后端脚手架创建时生成并提交 Maven Wrapper；前端脚手架创建时固定 `packageManager`、`engines` 并生成 pnpm 锁文件。

### P1：团队协作前完成

1. 创建并配置项目私有 Git 远程仓库。
2. 建立 Docker Compose，拉起 MySQL 8、Redis 和对象存储。
3. 提交非敏感环境配置说明；真实值使用受保护的 Secret Store。
4. 增加一键环境检查和健康检查。
5. 固定 Java、Node、数据库和镜像版本。

### P2：工程底座完成后验证

1. 前端安装、构建、测试和本地启动。
2. 后端编译、单测、数据库迁移和本地启动。
3. 前后端联调、持久化和重启恢复。
4. Amazon Sandbox/受控店铺授权链路验证。

## 5. 环境验收命令

```bash
java -version
javac -version
./mvnw -version
node --version
npm --version
pnpm --version
docker version
docker compose version
docker compose up -d
docker compose ps
```

还需验证项目构建、单测、数据库迁移、健康检查和公开镜像拉取。命令中不得包含真实凭据。

## 6. 当前结论

**环境准备度：部分具备，不允许标记为 Ready。**

硬件和基础网络没有明显瓶颈；Node.js/OpenClaw 运行时升级、Java 21 LTS、Maven 与 Docker daemon 配置均已完成并验证。Docker Hub TLS/代理链路仍是容器化环境的 P0 阻塞；修复并完成公开镜像拉取后可进入本地 Compose 环境建设。

## 7. 参考

- [Node.js Releases](https://nodejs.org/en/about/previous-releases)：生产应用应使用 Active LTS 或 Maintenance LTS；Node.js 25 已 EOL，Node.js 24 为 LTS。
- [Spring Boot System Requirements](https://docs.spring.io/spring-boot/system-requirements.html)：当前 Spring Boot 版本至少需要 Java 17，并给出 Maven/Gradle 支持范围。
