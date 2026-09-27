# ERP-002 本地 Mock MVP 增强批次

## 目标

在 `ERP-001` 已通过的本地合成数据 MVP 上，继续补齐正式产品矩阵中不依赖外部输入的可操作闭环。所有数据继续显式标记为 `SYNTHETIC`，不替代真实业务或生产验收。

## 批次范围

1. 候选手工新增、ASIN/Amazon URL 归一化和查重。
2. 五店 Listing 独立编辑、单店校验和失败隔离。
3. SKU—辅料用量及损耗率关系。
4. 模拟监控基线、后续快照和失败快照保护。

## 当前完成：候选手工新增

- `ADMIN`、`SELECTION` 可用 ASIN 或标准 `amazon.com/dp/` URL 新增候选。
- URL 统一归一化为大写 ASIN 和标准链接。
- ASIN 与等价 URL 按同一业务键查重，重复请求返回 HTTP 409。
- 非 Amazon URL 或无法识别的输入返回稳定业务错误。
- `PRODUCT_MATERIAL` 写请求仍由后端 capability 返回 HTTP 403。
- 前端新增操作表单、初始评分和标准链接展示，响应继续执行 `SYNTHETIC` fail-closed 校验。

## 验证证据

- Backend：24 tests，0 failure / 0 error。
- Frontend：5 files / 15 tests；lint、typecheck、production build 通过。
- 动态 HTTP：标准 Amazon URL 新增返回 200；等价 ASIN 重复返回 409；前端根页面返回 200。

## 边界

- 当前仍为内存合成数据，后端重启后恢复种子状态。
- 不访问 Amazon、不抓取页面，也不把标准链接解析解释为真实数据源验收。
- 下一项按顺序推进五店 Listing 独立编辑与单店失败隔离。
