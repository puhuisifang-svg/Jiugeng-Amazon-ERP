# 权限矩阵

| 字段 | 内容 |
| --- | --- |
| 版本 | v1.3 |
| 状态 | Approved（ERP-021-003 新增供应商独立权限） |
| 日期 | 2026-10-15 |

| 资源/能力 | 系统管理员 | 产品/辅料人员 | 选品人员 |
| --- | --- | --- | --- |
| 用户与角色 | 管理 | 无 | 无 |
| 产品数据 | 管理 | 操作 | 查看 |
| 辅料及对应关系 | 管理 | 操作 | 查看 |
| 辅料库存 | 管理 | 操作 | 无 |
| 选品候选池 | 管理 | 查看 | 操作 |
| 店铺上新监控 | 管理 | 查看 | 操作 |
| 首页工作清单 | 管理 | 操作 | 查看；可维护新品待开发 |
| 供应商档案与联系方式 | 管理 | 操作 | 无 |
| 审计日志 | 全局查看 | 查看本人操作 | 查看本人操作 |
| 密钥值 | 默认无；Ops 受控管理 | 无 | 无 |

首期不设置只读角色。权限同时在菜单和后端接口强制校验；后续新增岗位时再扩展角色和数据范围。

## 动作级 Capability 基线

角色用于初始化 capability 集合；前端菜单/按钮/路由使用 `can(capability)`，后端每个接口独立校验同名 capability。本节冻结当前 MVP 的 24 个机器名与三角色映射。机器可读清单见 [当前 MVP 活跃范围契约](../01-product/mvp-active-scope-contract.json)。

| Capability | ADMIN | PRODUCT_MATERIAL | SELECTION |
| --- | --- | --- | --- |
| `system.users.manage` | 是 | 否 | 否 |
| `products.read` | 是 | 是 | 是 |
| `products.write` | 是 | 是 | 否 |
| `materials.read` | 是 | 是 | 是 |
| `materials.write` | 是 | 是 | 否 |
| `inventory.read` | 是 | 是 | 否 |
| `inventory.write` | 是 | 是 | 否 |
| `selection.read` | 是 | 是 | 是 |
| `selection.write` | 是 | 否 | 是 |
| `watch.read` | 是 | 是 | 是 |
| `watch.write` | 是 | 否 | 是 |
| `imports.read` | 是 | 是 | 否 |
| `imports.template.download` | 是 | 是 | 否 |
| `imports.validate` | 是 | 是 | 否 |
| `imports.commit` | 是 | 是 | 否 |
| `imports.cancel` | 是 | 是 | 否 |
| `imports.result.download` | 是 | 是 | 否 |
| `audit.read.all` | 是 | 否 | 否 |
| `audit.read.self` | 是 | 是 | 是 |
| `dashboard.read` | 是 | 是 | 是 |
| `dashboard.shipments.manage` | 是 | 是 | 否 |
| `dashboard.pending-products.manage` | 是 | 是 | 是 |
| `suppliers.read` | 是 | 是 | 否 |
| `suppliers.write` | 是 | 是 | 否 |

## 店铺边界

- DEC-012 已将公司 `StoreAccount`、`StoreListing` 和五店 Listing 映射移出当前 MVP。
- 当前不提供 `/system/stores`、StoreAccount/StoreListing API，也不定义 `listings.read`、`listings.write` 或 `stores.write`。
- `WatchStore` 仅表示一个公开上新监控目标，通过 `watch.read` / `watch.write` 控制；它不保存公司店铺账号、Seller ID 或凭据。
- 未来若恢复公司店铺或 Listing 管理，必须先完成范围、IA、安全和 Secret 管理变更评审。

## 鉴权边界

- 前端隐藏/禁用仅改善体验，不构成安全控制；后端必须按 capability 独立鉴权。
- `audit.read.self` 只允许读取当前用户自身操作事件；普通角色不得修改/删除审计记录。
- 密钥值不授予任何上述业务角色；监控数据源如需 API Key，只保存 SecretRef，由 Ops 受控管理。
