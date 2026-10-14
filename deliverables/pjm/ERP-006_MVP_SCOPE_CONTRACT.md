# ERP-006 当前 MVP 范围与契约收口

## 目标

落实 DEC-012，消除“当前 MVP 已移除店铺 Listing 映射”与历史六类导入、Listing capability、五店数据门禁仍被当作活跃实现目标之间的冲突。

## 实施范围

- 建立当前 MVP 人类可读和机器可读的唯一活跃范围覆盖层。
- 冻结五类导入、19 个 capability、正式 API 资源族和明确后置实体。
- 更新 PRD、总体架构、产品模型、权限矩阵、测试策略和业务输入门禁。
- 保留 DEL-017、DEL-025 及其复核材料作为历史审计证据，不重写已关闭结论。

## 不在范围

- 正式 API、数据库迁移、IAM、导入 Worker 或任何业务功能实现。
- 删除历史模板、契约、测试夹具或评审记录。
- 重新引入 StoreAccount、StoreListing、五店映射或 SP-API 同步。

## 验收标准

- [当前 MVP 活跃范围契约](../../docs/01-product/MVP_ACTIVE_SCOPE_CONTRACT.md)与机器可读副本一致。
- 当前实现清单只有五类导入，不包含 `STORE_LISTINGS`。
- 当前权限清单只有 19 个 capability，不包含 `listings.*` 或 `stores.write`。
- 当前 API 边界不包含 StoreAccount/StoreListing 资源。
- `DATA-002` 标记为经 DEC-012 批准的当前 MVP 非适用项。
- 历史六类契约明确标记为审计基线，后续实现不得无范围决策直接采用 Listing 部分。
- JSON、文档链接、范围一致性与 `git diff --check` 验证通过。

## 验证命令

```bash
python3 scripts/quality/validate-mvp-scope.py
python3 -m json.tool docs/01-product/mvp-active-scope-contract.json >/dev/null
git diff --check
```

## 状态

- 分支：`feature/ERP-006-mvp-scope-contract`
- 基线：`8744fab144e2a78bd07a7efd9a63cb4ca87e9baa`
- 当前：In Review（实现完成，待 PJM/PM 复核）

## 实施与验证结果

- 已建立人类可读和机器可读活跃范围契约，冻结五类导入、19 个 capability、3 个角色映射和 10 个 API 资源族。
- 已从当前权限、API、导入、业务输入和验收目标中移除 StoreAccount/StoreListing；`WatchStore` 与公司店铺账号边界已明确。
- DEL-017/DEL-025 六类契约及测试文件未改写，只在入口增加历史基线提示。
- `python3 scripts/quality/validate-mvp-scope.py`：PASS。
- `python3 -m json.tool docs/01-product/mvp-active-scope-contract.json`：PASS。
- 历史导入契约 `validate_contract.py`：PASS（6 importTypes、7 statuses、8 phases、11 operations、21 capabilities、25 errors）。
- 历史 AJV 实例测试：PASS（ImportIssue 4/12；库存数量 8/6）。
- 99 个仓库 Markdown 文件本地链接检查：PASS。
- `git diff --check` 与高置信敏感信息模式检查：PASS。
