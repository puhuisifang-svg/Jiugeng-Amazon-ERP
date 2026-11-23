# DEL-046 累计迭代发布结果（2026-11-23）

## 提交与受保护合并

- 功能提交：`6198abeae0ac997add5d0675d9db383e710cccf3`，116 个路径、6548 行增加、3190 行删除。
- 功能分支最终提交：`d915dc44d374037818bf4913a5cd1c25781882b3`。
- 应用发布 main：`aa5e69b391b230b414f194b97e21fcf5afb19624`。
- [PR #21](https://github.com/puhuisifang-svg/Jiugeng-Amazon-ERP/pull/21)，五项必需检查通过后按正常 merge 流程合并；没有 force push、保护绕过或额外共同作者署名。
- 范围、排除项、业务边界及本地验证见 [正式交接 DEL-046](OPERATIONS_RELEASE_HANDOFF_2026-11-23.md)。

## 验证证据

- 本地有效验证结果按交接复用，没有重跑未失效的完整门禁；变更 Markdown 174 个本地链接核对无缺失。
- 首轮密钥扫描命中 SupplierCloseoutTest 中隔离 H2 测试用户的合成密码。独立确认非真实凭据后，仅添加该历史 finding 的精确 fingerprint 豁免及当前行的用途注释；没有目录级或规则级豁免，没有业务行为变化。新一轮扫描通过。
- [PR CI](https://github.com/puhuisifang-svg/Jiugeng-Amazon-ERP/actions/runs/45122376121) 和 [main CI](https://github.com/puhuisifang-svg/Jiugeng-Amazon-ERP/actions/runs/45122775556) 全部通过。CI 后端 431 项、0 失败/错误、1 外部 OSS 探测跳过，浏览器 10 项通过。
- [Production](https://github.com/puhuisifang-svg/Jiugeng-Amazon-ERP/actions/runs/45122775575) 重新执行门禁、导入契约、精确 SHA 打包和实际 SSH deploy，全部成功。

## 生产证据（Asia/Shanghai）

- 实际 deploy：14:20:08–14:21:56，结论 success，非 skipped。
- 14:21:30：`Database backup completed`。脚本在停止唯一写入服务后执行数据库备份，gzip 完整性验证并保存 SHA-256；本次没有额外核对备份文件大小或做恢复演练，不声称完成配置/图片离机备份。
- 14:21:56：`Deployment verified: aa5e69b391b230b414f194b97e21fcf5afb19624`。按受限接收脚本，此标记要求后端健康 UP，服务器前端版本等于目标 SHA。
- PJM 独立请求用户既有线上入口 `/version.json`，返回 `aa5e69b391b230b414f194b97e21fcf5afb19624`，与合入 main 一致。私有入口地址不写入仓库。
- 本地旧 JAR 未用于发布；包来自 Production 对该 SHA 的构建。没有对本地持久服务进行重启或替换。

## 迁移核验边界

- 新增 V102–V106 已包含在目标提交；生产 mysql 配置启用 Flyway，`validate-on-migrate=true`。构建与成功启动、健康检查为间接证据。
- 当前没有可用服务器管理凭据；部署 SSH 仅接受版本化发布命令，不提供任意查询。未放宽此限制，未直查生产 `flyway_schema_history` 的各版本成功记录。
- CI 中“迁移到 V106”是 H2 测试日志，不是生产迁移日志；本地 MySQL V106 也不替代生产证据。因此本次不能宣称已独立核实生产迁移明细。
- 未执行真实业务写入、全系统人工业务验收、外部 OSS 实测或生产规模压测；保留 DEL-046 的历史收货不补账、旧单位不自动换算等边界。

## 归档

PJM 更新本记录及状态报告；Codex 已完成交接，不需要等待结果。本记录描述上述应用 SHA 的发布事实，后续文档归档提交不改变应用行为。
