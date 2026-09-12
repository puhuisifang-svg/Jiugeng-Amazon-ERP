# 权限矩阵

| 字段 | 内容 |
| --- | --- |
| 版本 | v1.1 |
| 状态 | In Review（S0-CONTRACT-02 capability 名称冻结，真实 IAM 实现后移） |
| 日期 | 2026-09-12 |

| 资源/能力 | 系统管理员 | 产品/辅料人员 | 选品人员 |
| --- | --- | --- | --- |
| 用户与角色 | 管理 | 无 | 无 |
| 产品数据 | 管理 | 操作 | 查看 |
| 辅料及对应关系 | 管理 | 操作 | 查看 |
| 辅料库存 | 管理 | 操作 | 无 |
| 选品候选池 | 管理 | 查看 | 操作 |
| 店铺上新监控 | 管理 | 查看 | 操作 |
| 审计日志 | 全局查看 | 查看本人操作 | 查看本人操作 |
| 密钥值 | 默认无；Ops 受控管理 | 无 | 无 |

首期不设置只读角色。权限同时在菜单和后端接口强制校验；后续新增岗位时再扩展角色和数据范围。

## 动作级 Capability 基线

角色用于初始化 capability 集合；前端菜单/按钮/路由使用 `can(capability)`，后端每个接口独立校验同名 capability。真实账号、会话、Token、角色配置 UI 和鉴权实现后移，本节只冻结机器名与三角色映射。

| Capability | ADMIN | PRODUCT_MATERIAL | SELECTION |
| --- | --- | --- | --- |
| `system.users.manage` | 是 | 否 | 否 |
| `products.read` | 是 | 是 | 是 |
| `products.write` | 是 | 是 | 否 |
| `listings.read` | 是 | 是 | 是 |
| `listings.write` | 是 | 是 | 否 |
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

## StoreAccount 决策

- MVP 的 5 个公司 `StoreAccount` 由系统在受控配置/初始化中预置，不提供业务用户新增、编辑、停用能力。
- MVP 不新增 `/system/stores` 页面，也不向三角色发放 `stores.write` capability。
- Listing 导入仅可引用系统中已预置且启用的 5 个 `store_code`；引用不存在或停用店铺是阻断错误。
- 未来若开放店铺管理，必须走范围、IA、安全和 Secret 管理变更评审，不得仅通过前端加路由实现。

## 鉴权边界

- 前端隐藏/禁用仅改善体验，不构成安全控制；后端必须按 capability 独立鉴权。
- `audit.read.self` 只允许读取当前用户自身操作事件；普通角色不得修改/删除审计记录。
- 密钥值不授予任何上述业务角色；StoreAccount 凭据只保存 SecretRef，由 Ops/系统受控流程管理。
