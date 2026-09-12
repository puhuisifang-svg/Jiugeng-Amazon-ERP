# 项目状态报告

> 更新时间：2026-09-12

## 总体状态

- 状态：项目准备中
- 健康度：绿色
- 当前 Sprint：未启动

## 已完成

- 完成项目规划方案 v0.1。
- 项目成员 Agent 模型统一为 `openai/gpt-5.6-sol`，main 保持原配置。
- 建立项目文档库、成员交付区、治理模板和密钥管理规范。
- 完成本地开发环境审计；结论为部分具备，Java、Node LTS、Docker daemon 和 Docker Registry 网络尚未就绪。

## 进行中

- 等待老板逐项提出并确认项目启动前准备事项。

## 阻塞/待决策

- 项目规划和 MVP 范围尚未批准。
- 业务关键用户、店铺/站点范围、核算口径和部署目标待确认。
- 本地环境 P0：Java 21、Maven、Node.js 26.8.2 与 Docker daemon 已完成配置；既有 MySQL/Redis 容器健康。仍待修复云梯/上游代理至 Docker Hub 的 TLS 链路，并在后端脚手架内生成 Maven Wrapper。
- 待办（老板回到电脑后）：将云梯切换为全局加速，或把 Docker Hub 相关域名强制走可用代理；随后由 PJM 复测 `docker pull`，通过后配置 ERP Compose 数据服务。
- 前端包管理器已确定为 pnpm 12.4.1；项目级官方 npm Registry 配置完成。前端脚手架创建后再固定 Node engine、packageManager 和锁文件。
