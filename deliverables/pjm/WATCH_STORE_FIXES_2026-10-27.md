# 上新监控四项修复及本地部署

用户直接授权实现、验收及本地部署，无项目经理审批环节，不另行分配 ERP 任务号。

- 分支：`fix/watch-store-workflow`。
- 应用提交：`32755ff5ed7b14ab8f2f3d9459c18dde9f6f7bac`。
- 本地入口：`http://127.0.0.1:5173/watch/stores`；原 8080 后端、原 MySQL。

## 修复

1. AmazonPublicAdapter 从与 seller 精确对应的搜索范围选项识别店铺名称，WatchCollectionTarget 自动回填 ID 占位名，保留用户自定义名称。部署后已对用户指定店铺实际采集，页面自动显示 JOIE store。
2. 今日上新复用 WatchStoreDetail 商品表格，按列表相同的美国太平洋时区事件集合筛选。图片、标题、价格、评分、搜索、分页、转产品监控一致；零条时不会显示全部在线商品。
3. 操作列新增移除及确认。V50 增加 removed 标志；移除后停止监控并隐藏列表，保留历史与审计。重新添加相同 seller 地址可恢复原记录及历史。后台通过状态及版本阻止在途采集写回已移除目标。
4. 备注列新增可访问的铅笔按钮、独立编辑框与版本校验接口。允许清空，最多1000字；失败保留输入、关闭保护未保存内容。备注接口仅更新备注，不覆盖名称、负责人或监控状态。

同时补全 WatchWorkbench 的业务异常映射，版本冲突返回409、校验失败返回400。

## 变更文件

后端：AmazonPublicAdapter、WatchCollectionTarget、WatchController、WatchExceptionHandler、WatchRepository、WatchService、WatchWorkbench、V50，以及对应三个测试类。
前端：WatchStoresView、WatchStoreDetail、monitoringWorkbench.css、WatchStoreDetail.test.ts、WatchStoresActions.test.ts。

## 验证与部署

- `./mvnw clean verify`：233项，0失败，0错误，1项条件跳过，日志 `/tmp/watch-fix-back.log`。
- `ERP_BUILD_OUT_DIR=/tmp/watch-fix-dist pnpm quality`：83文件、337测试全部通过；lint、typecheck、构建通过，日志 `/tmp/watch-fix-front.log`。
- `git diff --check` 通过；代码提交后工作区干净，无无关变更。
- 原后端停写后备份原库到 `.local/backups/watch-store-fixes/pre-upgrade.sql.gz`，验证gzip完整性；保留原启动脚本用于回退。
- 固定运行产物 `.local/releases/backend/32755ff5ed7b14ab8f2f3d9459c18dde9f6f7bac/backend.jar`；原tmux服务启动，V50迁移成功，健康检查UP。原7条产品、10条变体保留。
- 浏览器真实验证：指定店铺自动名称；今日上新数量4对应4条商品及图片评分；临时合成店铺通过铅笔编辑备注并回显，再通过移除按钮确认后列表消失。原两家店铺均保留。
- 1280×800、800×600、360×800视觉检查；备注框底部按钮可见，今日商品表格内部滚动及分页可用。截图 `/tmp/watch-store-fixes-ui/`。
- 合成测试店铺已软移除且未启动采集；临时QA账号已禁用、会话数0。未修改原用户账号或真实店铺备注。

## 边界

名称在自动采集成功后回填，来源未提供或采集失败时不猜测名称；US公开搜索范围及缺失字段语义沿用原实现。移除保留历史，不做物理删除。此记录不表示合并main或公网静态前端发布。

## 上新列表交互补充（用户直接授权开发及本地部署）

- 范围：ERP-099 上新监控补充修复；分支 `fix/watch-store-interactions`。本次修改未提交，未合并。
- 赛狐参考：在用户已打开的 Safari 上新监控页查看列表、备注铅笔及“修改备注”提示；自动化未取得完整的备注表单，未改动赛狐数据。实现采用列表附近的紧凑编辑框，保留现有 1000 字限制、保存失败保留输入及放弃修改确认。
- `WatchStoresView.vue`：备注编辑框靠近触发按钮并限制在视口内；今日上新下划线；名称新标签打开 Amazon 店铺，详情按钮保留；更新按钮即时排队反馈、旋转图标、禁止重复点击，以及完成/部分成功/失败提示。
- `monitoringWorkbench.css`：仅监控视图的编辑框、数字链接与更新图标样式，复用现有控件、TableViewport 及分页。
- `useCollectionStatus.ts`：按目标跟踪本次手动请求，不将旧任务成功当作本次完成；超过两分钟仍未看到新任务时显示“尚未确认启动”，保留继续轮询及重试。完成回调仅对手动跟踪的任务触发。
- 测试：新增 `useCollectionStatus.test.ts`，更新 `WatchStoresActions.test.ts`。`ERP_BUILD_OUT_DIR=/tmp/watch-interactions-build pnpm --dir frontend quality` 通过：84 个测试文件、344 项测试；lint、typecheck、build 通过。`git diff --check` 通过。无后端修改，无后端重启。
- 本地验收：原 `http://127.0.0.1:5173/watch/stores` 已加载本次代码。1280×800、800×600、360×800 下查看编辑框；窄屏/手机 document.scrollWidth 等于视口宽度，编辑框及保存取消按钮在视口内。取消脏输入弹出确认，放弃后原备注不变。
- 实际手动更新显示排队并禁用按钮，随后本次 Amazon 读取失败，页面明确显示“未识别到店铺商品列表，保留原基线”。此结果验证失败反馈，不能作为本次采集成功证据；采集端解析未在本次范围修改。
- 临时 QA 账号已恢复禁用并清理其会话；仅进行读取及一次用户授权的店铺更新，未改动用户备注，未增加端口。
- 最终工作区变更仅包含本补充修复及此记录，无无关改动。

## 美国站店铺商品覆盖、价格和星级修复

- 范围：ERP-099 用户直接授权的上新监控修复；沿用 `fix/watch-store-interactions`。前一轮列表交互修改为本任务自身未提交成果，完整保留；本轮也未提交，未合并。
- 根因证据：匿名请求默认为 China 收货地区，公开搜索显示 19 个结果、价格为 CNY。原采集器遍历了两页，但覆盖的是国际配送结果，美元解析也无法接收人民币报价。用独立匿名偏好设置美国地区后，搜索可遍历九页。页面结果数动态变化，不以首屏宣称的总数替代真实分页。
- `AmazonPublicAdapter.java`：仅使用采集器自己的临时 CookieManager，通过公开地区选择组件设置美国邮编 10001 和 USD；每页校验地区，拒绝非美元价格和越界请求，沿下一页直到明确结束。没有使用用户登录态或亚马逊卖家 API。兼容旧版分页、主报价/拆分金额/其他卖家最低报价、可访问属性中的星级；不以划线价或优惠券金额冒充当前价格。地区组件临时缺失进入现有有限重试，验证码仍停止。
- `WatchCollectionTarget.java`、`WatchWorkbench.java`、V51：持久化并返回报价类型。其他卖家最低报价在 `WatchStoreDetail.vue` 标注“起”，缺失显示“暂无公开报价/评分”，不填零。无报价或星级时继续保留旧观测，历史未重新出现的商品仍保留，不能凭搜索缺失判下架。
- 测试：`AmazonPublicAdapterTest.java` 增加美国地区初始化、地区/币种/端点校验、主价/拆分价/其他报价、星级及超过 20 条分页；`CollectionServiceTest.java` 验证报价类型持久化与缺失字段保留；`WatchStoreDetail.test.ts` 验证 32 条记录的第二页及缺失评分、报价类型显示。
- 完整门禁：后端 `./mvnw clean verify` 成功，238 项，0 失败，0 错误，1 项既有跳过；前端 `ERP_BUILD_OUT_DIR=/tmp/watch-us-build pnpm --dir frontend quality` 成功，84 文件 / 345 项，lint/typecheck/build 均成功；`git diff --check` 通过。日志 `/tmp/watch-us-back.log`、`/tmp/watch-us-front.log`。
- 本地部署：原 tmux `jiugeng-mvp` 后端 pane `%12`、8080、MySQL 13306 及前端 5173。V51 迁移成功；health UP。最终 jar `.local/releases/backend/watch-us-context-20261027-final/backend.jar`，SHA256 `75563ff7dbe0a4a547bc25b31642a8b819d86a5d1f3e063bb225218c8a93868d`。数据库与旧启动脚本备份 `.local/backups/watch-us-context/`。
- 实际验收：原两家店铺均经页面“更新”成功刷新。JOIE 当前列表保留 131 条，129 条价格、105 条星级；最新公开快照九页、129 个 ASIN（另两条为保留的历史观测）。页面详情显示共 131 条、7 页，实际打开第 7 页看到其他报价“起”和暂无评分。SHUNYITONG 当前保留 30 条，26 条价格、26 条星级。数据随亚马逊页面变化，上述为验收时数量，不代表卖家全部库存的保证。
- UI：1280×800、800×600、360×800 验证；列表在 TableViewport 内滚动，详情及底部分页在视口内，小屏 document.scrollWidth 等于视口宽度。最终重启后列表数据与已更新状态保持。
- 限制：公开搜索本身不保证完整卖家库存；原有 20 页/200 秒边界保留，越界不会覆盖基线。页面未公开或未重新观测到的报价、评分仍可能缺失；今日上新沿用系统首次发现口径，补采发现也计入。未改动其口径、历史事件或用户备注。
- 收尾：临时 QA 已禁用，其会话已清理；验收创建的浏览器页关闭，匿名诊断会话文件删除。工作区仅包含这两轮修复及记录，无其他代理或用户修改被覆盖。

## Single-page completion and larger-store collection fix

- ERP-099 follow-up directly authorized by the user; branch `fix/watch-store-interactions`. Changes remain uncommitted; existing changes from this task are preserved, with no unrelated changes overwritten.
- `AmazonPublicAdapter.java`: accept an absent pager only on page one when the exact result count matches all valid unique rows. Estimates, mismatched counts, duplicate rows and missing completion evidence still fail without advancing the baseline. Decode gzip/deflate with a 4 MB expanded limit in addition to the existing compressed limit. Remove response-body logging. Increase store bounds to 500 pages / 1800 seconds; completion still requires reaching the actual last page.
- `CollectionService.java`: Amazon store jobs receive a 1980-second lease to cover initialization, traversal and commit. Other jobs retain 300 seconds; lease/version/source fencing is preserved.
- Regression tests cover exact single-page completion and rejection cases, traversal of 120 pages, the 500-page bound, lease duration, gzip/deflate, malformed compressed data and expansion limits.
- Backend full gate `./mvnw clean verify`: PASS, 241 tests, zero failures/errors, one existing skip. Log: `/tmp/watch-pagination-back-final.log`. No frontend source changes this round; previous frontend gate remains 345 passing tests. `git diff --check`: PASS.
- Local deployment uses the original backend 8080 and frontend 5173, tmux `jiugeng-mvp` pane `%12`, with MySQL data retained. Backup: `.local/backups/watch-pagination/`. Release: `.local/releases/backend/watch-pagination-20261027-final/backend.jar`; SHA256 `170b4876987255ebc872853297ac3d6ae35a4d711cee5cb8f1cec0c7f031df9a`. Health UP.
- Remaining bounds: public search does not guarantee complete seller inventory; over 500 pages, persistent timeout or CAPTCHA still preserves the previous baseline. No incomplete observation is promoted as complete.

- Live validation: Liitrton completed and the original UI showed 15 products, 15 prices and 14 ratings; the remaining item had no public rating. Small raindrops also completed with 8 products. Further completed retries: Bedsure Comfy Pet 322, DonSeartSun 647, Jyinneyine 597, MorhaZer 889. These are observed unique products, not a full inventory guarantee.
- A further public-page trace showed the reported result count expanding from 962 / 61 pages to over 4,000 / 282 pages during traversal. The final bounds therefore supersede the initial 100-page candidate. The parser follows actual next links and does not stop at the initial advertised total.
- Temporary QA account disabled and its sessions removed; the agent-created local browser tab closed. No user account, store remarks or existing records were removed.

- At handoff, 6 of the 8 originally failed stores have completed successful local retries. The two larger stores that exceeded the intermediate 100-page candidate are queued/running again under the final 500-page / 1800-second implementation. They are not claimed as successful yet; the persistent original local scheduler owns their continuation.
