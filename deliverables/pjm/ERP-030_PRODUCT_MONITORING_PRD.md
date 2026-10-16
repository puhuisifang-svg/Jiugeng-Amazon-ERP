# ERP-030 产品监控 v1.0

状态：2026-10-16 用户授权直接实施；取代仓库外 v0.2 的首期建议。基线 `7ee8e17`，分支 `feature/ERP-030-product-monitoring`。由 Codex 独占开发及部署，PJM 已交接。

## 冻结范围

独立一级“产品监控”，所有有效登录用户均可操作共享目标，不使用 watch 岗位权限。个人关注与列设置按当前用户隔离。没有人员操作日志、修改人历史或业务操作审计；其他模块原有审计不变。

P0：Amazon US；启用目标上限 1000；每批最多 100；分页 20/50/100；每条最多 20 个标签、备注最多 1000 字。目标按站点+ASIN 唯一；新增返回逐行结果并支持请求幂等。清单支持 ASIN 批量精确搜索、标题搜索、负责人/标签/状态/个人关注过滤。切页或筛选清空选择。批量启停、设置本人负责/清除负责人、标签追加/移除、关注均支持；共享修改使用版本号防止覆盖。负责人采用当前用户身份设置，不能伪造其他人员。

详情提供当前商品、人工观测录入、商品变化和历史快照。支持 MANUAL / SYNTHETIC 来源，明确标示，无真实自动采集及自动调度。刷新仅读取。标题、品牌、主图 HTTPS URL、五点、价格 USD、可售状态支持有值、明确未知(null)、未观测(缺少字段)。完整有效快照必须包含全部字段且至少有一项商品信息；部分/失败/乱序不提升基线。首条建立基线，后续多字段变化合为一组；来源切换重建基线以防人工与合成数据交叉误报。暂停后拒绝新快照；在途提交版本过期拒绝。请求键重放返回原结果，不重复写入；同键不同内容冲突。

P1：获批真实数据源、每日调度、昨日对比、趋势、商品比较。P2：探索、多站点、通知、导出。P0 不永久删除、不自动清理历史、不展示未实现页签。48 小时无有效更新提示过期，但不将过期值清零。

## 数据字典及 API

- `monitor_target`：站点、ASIN、启停、当前负责人 ID/名称、标签 JSON、备注、版本、当前基线及来源、最近尝试状态/原因/时间。无操作人历史。
- `monitor_snapshot`：目标、请求键、请求哈希、观测时间、记录时间、来源、完整性、决策、字段 JSON、变化 JSON。无人员操作信息。
- `monitor_request`：新增请求的幂等哈希及结果，不保存操作人。
- `monitor_preference`：用户自己的关注及列配置；用户 ID 从有效会话取得。
- `monitor_guard`：数据库单行锁串行化配额分配及写入，跨进程有效。

API 根 `/api/v1/product-monitoring`，均要求有效登录；mock profile 不开放这些正式接口。

| 方法/路径 | 请求/响应 |
| --- | --- |
| GET / | 筛选 q/searchBy/status/owner/tag/followed/page/size → items,total,activeCount,limit |
| POST / | requestId,asins[],tags[],remark → 每行 CREATED/EXISTS/INVALID/LIMIT |
| GET /{id} | target + 最近快照；完整历史用分页接口 |
| POST /batch | targets[{id,version}],action(ENABLE/PAUSE/ASSIGN_SELF/CLEAR_OWNER/ADD_TAGS/REMOVE_TAGS),tags[] → 逐行成功/冲突；客户端刷新 |
| GET /{id}/snapshots | page,size → 历史快照分页 |
| POST /{id}/snapshots | requestId,version,observedAt,source,quality(COMPLETE/PARTIAL/FAILED),fields,reason → 已保存快照及基线决策 |
| PUT /{id}/follow | followed → 当前用户关注状态 |
| GET/PUT /preferences | columns[] → 当前用户列设置 |

400 校验失败，401 未登录/失效，404 无目标，409 版本/幂等冲突。记录详情不暴露其他用户偏好。金额十进制，缺失不等于零。所有 JSON 内容有界校验；远程图片不由服务器抓取，浏览器 referrerpolicy=no-referrer。

## 验收矩阵

1. 三种岗位均可全流程读写，匿名拒绝；个人关注及列配置隔离。
2. 新增去重、批次限额、并发唯一、请求重放/内容冲突、启用配额原子保护。
3. 首次基线、后续多字段变化、无变化、来源切换、明确未知与未观测、空结果保护。
4. 部分/失败/乱序保留旧基线；暂停和在途版本冲突；历史分页及重启持久化。
5. 前端创建、错误恢复、批量选择/切页清理、录入表单、空态、详情和路由。
6. 后端 clean verify、前端 quality、真实 MySQL 空库和升级验证、重启持久化、备份恢复、动态 HTTP、diff check。

## 数据源 Stage Gate

P0 不采集外网，无凭据依赖。P1 开始前必须确认供应方授权、成本/限额、字段覆盖、观测时区、精度、幂等规则；连续 7 天检查完整率、失败重试、速率限制与历史稳定性。不得以合成数据宣称真实自动监控。

## 部署与回滚

保留部署前数据库和图片联合备份、校验和及应用基线；先在隔离 MySQL 完成恢复演练。V23 仅增加监控表，不改旧业务表。部署使用现有 mysql 启动脚本和 jiugeng-mvp 会话，验证健康、登录/匿名、三岗位动态流程及重启持久化。失败时回到部署前应用提交，保留新表及数据，不执行 destructive down migration。只有数据库损坏才在明确恢复窗口使用已验证备份恢复，避免丢失备份之后旧业务写入。
