# ERP-090 名称格式全链路修复

## 范围与授权

- 用户要求修复产品列表、发货管理和导出发货图片未体现名称格式的问题。
- 分支 `fix/ERP-090-shipment-name-format`，从已 fetch 的 origin/main `36f0ef95b918c9ca0921f6a6d99aa83cb9cf4953` 起始，按用户累计开发要求携带 ERP-078 至 ERP-089 提交。开始时工作区干净。
- 用户在本轮明确允许升级现有后端；已说明它也服务公网入口，会短暂中断。未授权更换公网静态前端，因此未更改 Caddy 前端发布目录、未推送或合并 main。

## 原因与修改

- 常驻后端启动于旧版本，数据库仍为 V39，缺少 V40/V41 名称格式字段；产品列表虽有展示组件，但保存后拿不到格式。
- ProductLookup / ProductLookupService 新增公开只读名称展示接口；不跨模块使用内部类型，按产品 ID、SKU ID、名称快照完全匹配取得格式。已改名记录保持原名称且不套用失效偏移，已逻辑删除但名称一致的 SKU 可继续提供展示数据。
- DashboardTypes / DashboardRepository / ShipmentDispatchService 返回 namePresentation，覆盖首页、今日发货、待发货及发货统计。纯展示读取，不修改单据名称、数量、客户快照或审计历史。格式跟随当前同名 SKU 设置，不宣称冻结历史颜色；名称不匹配安全回退普通文字。
- dashboard.ts、DashboardShipmentRow、DashboardMaintenanceView、ShipmentStatisticsView 使用共享 ProductName，保留换行、局部颜色和字重；CSS 保持名称独立一块，客户信息另起行。
- shipmentExport.ts 按格式片段计算实际字宽、自动/显式换行并绘制，保持名称块垂直居中；数量/箱数仍加粗，备注仍红色加粗，客户信息不继承局部名称颜色。

## 验证

- `./mvnw clean verify`：194 测试，0 失败，0 错误，1 项既有跳过；包括产品格式保存/读取、发货创建/首页/转待发货格式传递、改名安全回退及原权限/版本回归。
- `pnpm quality`：70 文件、303 测试通过，ESLint / TypeScript / Vite 构建通过（输出临时目录，不替换公网静态站点）。
- 新增 DashboardShipmentRow.test.ts；扩展 shipmentExport.test.ts 验证分段颜色、常规字重、换行及宽度边界。
- 浏览器真实组件 + 合成数据验收 1280×800、800×600、360×800：首页行组件和实际导出函数呈现第一行黑色、第二行红色，图片中数量箱数/备注保持原规则；窄屏横向滚动和导出预览可访问。临时验收页已移除。
- `git diff --check` 通过，无无关修改。

## 已执行的后端升级检查

- 使用已有 `.local/runtime/run-backend.sh` 和 MySQL profile，未改凭据/权限/配置；重启现有 tmux `jiugeng-mvp:0.0` 后端 pane，未影响前端 pane。
- 升级前 MySQL 事务一致备份：`.local/backups/erp090-0YYJag/pre-upgrade.sql.gz`，gzip 检验通过，包含 59 个建表定义。
- SHA-256：`ec0381119915c5cf934c6fec26e38c4488fa728334ac0f8456d4677675bdf369`。
- 后端健康检查 `http://127.0.0.1:8080/actuator/health` 返回 UP；数据库迁移版本 V39 → V41。产品、SKU、今日发货、待发货数量升级前后一致。未删除/重建业务数据。
- 升级后检查现有 SKU 的格式非空数量为 0：之前旧后端没有保存创建页的格式，无法恢复，只能重新设置并保存。不要报告旧记录已经自动变成红色。
- 本地 Vite 前端使用当前代码；公网静态前端仍为原发布版本，本轮没有更换。其他电脑如使用公网静态入口，不能据后端升级推断前端已更新。
- 合并、正式发布和独立验收仍由项目经理确认。当前提交 SHA 见本记录所属提交及最终答复。
