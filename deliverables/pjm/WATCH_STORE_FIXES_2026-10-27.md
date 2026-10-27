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

## Repeated products across advancing search pages

- ERP-099 follow-up, explicitly authorized for local deployment. User policy: subsequent changes deploy locally first; do not commit or merge into main until the user explicitly instructs it.
- Branch `fix/ERP-099-store-pagination-repeat`; clean starting base `159d5b3e6b0dbced1c51e56b049bc1707836c429` (origin/main). This change remains uncommitted on the fix branch.
- Live reproduction: pages 170 and 171 contained the same 16 unique ASINs, while the selected page advanced to 171 and the result range advanced from 2705–2720 to 2721–2736. The old ASIN-set-based loop detector rejected this legitimate page advance and discarded the entire collection.
- `AmazonPublicAdapter.java`: permit repeated product sets only with matching selected/requested page and an advancing result range. Continue to require an actual terminal page. Reject backward next links even if tracking parameters change, mismatched page numbers, nonadvancing ranges and repetitions without progress evidence. Merge duplicate product observations without clearing earlier prices/ratings when later rows omit them.
- `AmazonPublicAdapterTest.java`: regressions for forward pages with identical products, deduplication and missing-field preservation, mismatched page numbers, nonadvancing ranges and tracking-parameter URL loops. Existing host/seller/size/time/page/terminal checks remain.
- Gates: targeted `./mvnw -Dtest=AmazonPublicAdapterTest test` passed 19 tests; full `./mvnw clean verify` passed 243 tests, zero failures/errors, one existing skip. Logs `/tmp/watch-repeat-unit.log`, `/tmp/watch-repeat-back.log`. `git diff --check` passed. No frontend source changes or performance optimization.
- Local deployment: original tmux `jiugeng-mvp` pane `%12`, backend 8080 and frontend 5173. No active collection jobs were interrupted. MySQL retained; pre-deployment backup and previous launcher in `.local/backups/watch-repeat-20261027/`. Jar `.local/releases/backend/watch-repeat-20261027/backend.jar`, SHA256 `4b21d8a9f156f04ec7ea54daa490056391eba4aa5b8459ea1e9a88bc822bb26c`; health UP.

- Final live acceptance: HOOBRO US completed 284 public result pages, 2,658 unique products, 2,606 observed prices and 912 observed ratings. `pages_complete=1`, decision `PROMOTED`, collection job `SUCCEEDED`. The original localhost UI showed the resolved store name, updated state and detail total 2,658 with visible prices and ratings. Missing public fields remain unknown rather than invented.
- Captured failing-page regression independently confirmed range end 2736 for requested/selected page 171 after previous end 2720. Temporary captured HTML removed after verification. Temporary QA disabled and its sessions cleared; agent-created browser tab closed.
- Final worktree contains only the adapter, its regression tests and this handoff record. No unrelated changes, commits, pushes or main modifications. Public search coverage remains distinct from guaranteed seller inventory; existing 500-page / 1800-second bounds remain unchanged.

## Store workflow and detail UI revision

- ERP-099 continuation directly authorized by the user; branch `fix/ERP-099-store-pagination-repeat`. All changes remain uncommitted. No push, merge or main modification; the preceding pagination fix is preserved.
- `WatchStoresView.vue`: remove the global new-record link and technical More panel; move selected-only refresh beside assignment, disable without selection and report queued/skipped/failed counts. Replace More with per-store Settings.
- `WatchStoreDetail.vue`, `monitoringWorkbench.css`: redesigned identity/summary header, four fixed tabs (online, suspected offline, new records, settings), shared product table, fixed pagination and settings save footer. New records default to today in America/Los_Angeles and support historical date ranges. Preserve baseline exclusion, missing-price/rating semantics and unsaved-edit guards.
- New `StoreScheduleDialog.vue` and `StoreTimingForm.vue`: independent store schedules, interval 1–30 days, start time, Beijing or US Pacific time zone, next-run display, optimistic conflict handling and save feedback. Basic store settings and timing save independently.
- `CollectionService.java`, `CollectionController.java`, V52 migration: persist timing and its independent version; manual requests preserve recurring next-run time; retain collection leases and source fencing. `WatchWorkbench.java` adds permission-checked store-scoped events.
- Regression coverage: `CollectionServiceTest`, `WatchControllerTest`, `WatchStoresActions.test.ts`, `WatchStoreDetail.test.ts`, new `StoreTimingForm.test.ts`. Backend full `./mvnw clean verify`: 247 tests, zero failures/errors, one existing skip. Final frontend `ERP_BUILD_OUT_DIR=/tmp/watch-workflow-build pnpm --dir frontend quality`: 85 files / 349 tests passed, lint/typecheck/build passed. Logs: `/tmp/watch-workflow-back.log`, `/tmp/watch-workflow-front-final.log`. `git diff --check` passed.
- Deployed to the original local backend 8080 and frontend 5173, tmux `jiugeng-mvp`. V52 applied; health UP. Release `.local/releases/backend/watch-workflow-20261027/backend.jar`, SHA256 `5d05be855ae5055cde3ab9b09fac1749f78c0344203cd7e116fc90054d81d4b7`. Pre-upgrade backup `.local/backups/watch-workflow-20261027/`. No new ports or active collection interruption.
- Live UI verification: no-selection refresh disabled; selecting one store queues only that store and collection succeeded. Detail shows the four tabs and today's actual events. Desktop table fills available space with fixed pagination; 360×800 settings retain all tabs and visible footer actions. A disabled synthetic fixture successfully saved a 3-day, 09:30 Beijing schedule and persisted version 1; no fixture collection ran. Fixture soft-removed, QA account disabled, QA sessions cleared and temporary browser tab closed after validation.
- Scheduling is daily/multi-day at one time per store; queued execution may start after its scheduled time when earlier jobs are running. Existing real-store timing was not changed during UI verification. Public-source limits and baseline protections remain unchanged; no performance optimization included.
- Final worktree contains only these authorized changes, the prior pagination fix and this handoff record; no unrelated changes overwritten or staged.

### Follow-up: selected update and explicit history search

- ERP-099 continuation on `fix/ERP-099-store-pagination-repeat`, still uncommitted; no main changes. Preserve previous authorized work.
- `WatchStoresView.vue`: rename to 更新所选, remove row update action and top-level schedule settings entrance; detail settings remain available. `WatchStoreDetail.vue` and scoped styles: move history keyword input next to dates, remove Today shortcut, apply date/keyword drafts only on Search. Other product tabs retain existing filtering.
- Updated action/detail regression tests verify removed actions and deferred filtering. Full frontend gate `ERP_BUILD_OUT_DIR=/tmp/watch-actions-build pnpm --dir frontend quality` passed: 85 files, 349 tests, lint/typecheck/build. `git diff --check` passed. Backend unchanged this follow-up.
- Original localhost:5173 Vite service serves the update. Browser verified visible controls, two results remaining before Search and zero after Search, plus 1280×800, 800×600 and 360×800 layouts. No new port or backend restart. QA disabled and sessions cleared; temporary browser tab closed. No unrelated files staged or overwritten.

## Competitor product workbench revision

- ERP-099 continuation, branch `fix/ERP-099-store-pagination-repeat`; user directly authorized implementation and original local deployment. Uncommitted; no push or main changes. Prior authorized dirty changes preserved; no unrelated changes staged or overwritten.
- Reference: inspected the user's open Sellfox competitor-monitoring page in Safari, including its actual fixed identity columns, switches and change summaries. No Sellfox records modified.
- `WatchStoresView.vue` and `monitoringWorkbench.css`: selected update becomes green with compact button styling. `ProductMonitoringView.vue`: remove followed filter/star/actions, refresh and More; selected-only Update next to Add; switches confirm stopping; row action becomes Remove. Replace list with the requested 16 business columns plus selection/action columns, fixed selection/state/image/title columns and horizontal scrolling. Latest changes use readable field summaries; unknown observations remain explicit.
- `MonitorInlineEditor.vue`, `api/monitoring.ts`: pencil editors for manual 30-day sales and remark, save feedback, failure preservation, unsaved guard and version fencing. Values are independent of collected fields. New component and view tests cover editing errors, selection controls and stop-confirm cancellation.
- `MonitoringController/Service/Types`, V53: validate manual sales as optional integer 0–999999999 and remarks up to 1000 characters. Soft removal disables targets and excludes them from the list; re-add restores the same ID and history. Existing target-enabled/version fences prevent removed targets from collection promotion. Latest change summaries include prior historical changes when the new cached field is absent.
- `AmazonPublicAdapter.java`: parse publicly observed rating/count, main/subcategory ranks, first available date, product dimensions and weight, including combined dimensions/weight rows. Additional fields are optional for existing snapshot contracts. Parser regression and API integration tests cover field mapping, permissions, invalid input, optimistic conflicts, manual values surviving snapshots and remove/re-add history.
- Gates: full backend `./mvnw clean verify` (final via root wrapper with `-f .../pom.xml`) passed 249 tests, zero failures/errors, one existing skip; `/tmp/product-monitor-back-final2.log`. Final frontend `ERP_BUILD_OUT_DIR=/tmp/product-monitor-build pnpm --dir frontend quality` passed 86 files / 351 tests, lint/typecheck/build; `/tmp/product-monitor-front-final4.log`. `git diff --check` passed.
- Local deployment: original 8080 backend / 5173 frontend, tmux backend pane `%12`; no new ports. V53 applied successfully; health UP. Release `.local/releases/backend/product-monitor-20261027-final/backend.jar`, SHA256 `67e6eb501afad3f633d0e0f18d9b880c03b0a1d21d9c3f8d0f2a617bd632156a`. Database/launcher backup `.local/backups/product-monitor-20261027/`. No active jobs interrupted at restarts.
- Browser acceptance: actual five products submitted through selected update, all completed their current partial public observations; available extra fields appeared. Verified green selection state, horizontal fixed identity columns, stop confirmation cancelled without mutation, manual fixture sales 123 and remark persisted, then fixture soft-removed through UI. Original five user products preserved. Desktop 1280×800, 800×600 and 360×800 checked; compact mobile fixed column widths use a 52px contained thumbnail to retain all requested fixed columns within the narrow viewport (intentional mobile density exception). Sidebar/header and footer remain available; wide data needs horizontal scrolling.
- Temporary QA disabled and sessions cleared; fixture remains soft-removed with no collection schedule; browser override reset and temporary tab closed. Public fields can remain unobserved when absent from the page; collected values appear on subsequent successful observations. Manual sales are user-entered, not inferred Amazon sales. No unrelated performance optimization.

### Product pencil reuse and horizontal access repair

- ERP-099 continuation on `fix/ERP-099-store-pagination-repeat`; uncommitted, no main changes. Scope is frontend only; all earlier authorized work preserved.
- `MonitoringPencil.vue` and `useAnchoredPopover.ts` are shared by store remarks and product manual editors. `MonitorInlineEditor.vue` now uses the same `mw-remark-popover` layout, transparent backdrop, nearby anchor positioning, compact title/body/footer and remark counter. Validation, save errors and unsaved-close protection remain.
- `HorizontalTableViewport.vue` adds always-visible slider and left/right buttons synchronized with the actual table scroll position. `TableViewport.vue` accepts an optional reserved-bottom space; existing callers retain their default behavior. At short window heights the product viewport can shrink below its former 180px minimum so controls cannot be covered by pagination.
- Product list remains horizontally scrollable through all 16 business fields and actions. Desktop identity columns stay fixed. At <=600px the whole table scrolls, superseding the preceding compact-fixed-column exception which left too little space for other fields; product thumbnails retain full size. Product styles use one scroll owner, and the pager remains docked.
- Full frontend gate `ERP_BUILD_OUT_DIR=/tmp/product-scroll-build pnpm --dir frontend quality`: 87 files, 352 passing tests, lint/typecheck/build passed. Regression verifies slider/table synchronization and both endpoints; editor regression checks reused popover class. `git diff --check` passed. No backend changes/restart required.
- Browser validation at 1280×800, 800×600 and 360×800: slider reaches far-right remark/action columns; desktop identity stays fixed, mobile can view the entire table. At 800×600, controls bottom=532, pagination top=551: no overlap. Product remark opens near its pencil using the store layout; store remark visually regression checked. Existing local Vite 5173 serves final changes. Temporary QA disabled, sessions cleared, browser override reset and temporary browser closed. No store/product records altered during this UI-only acceptance.

### Dedicated product collection status

- ERP-099 follow-up on `fix/ERP-099-store-pagination-repeat`, uncommitted; no main changes. Only product view/styles and progress regression added this round; earlier changes preserved.
- `ProductMonitoringView.vue`: add explicit 采集状态 immediately after title/ASIN, separate 可售状态, reuse store status styling and existing five-second collection polling. Submitted/queued/running/retry states display a spinner; terminal states display outcome and partial/failure reasons. Table width and empty colspan account for the new column.
- `ProductMonitoringProgress.test.ts`: uses the real collection-status composable with mocked HTTP to verify selected POST, pending state despite previous successful job, running/retry/failure/success polling and availability separation.
- Full frontend `ERP_BUILD_OUT_DIR=/tmp/product-progress-build pnpm --dir frontend quality` passed 88 files / 353 tests plus lint/typecheck/build; log `/tmp/product-progress-final.log`. `git diff --check` passed. Backend unchanged.
- Deployed through original local Vite 5173. Live selected update immediately showed 排队中 and automatically reached 部分更新 with the actual public-field reason. Checked 1280×800, 800×600 and 360×800 including far-right column access; pager/scroll controls remain usable. QA disabled, sessions removed, viewport reset and temporary browser closed. No commits, push or main merge.

### Product details layout collection repair

- ERP-099 continuation, branch `fix/ERP-099-store-pagination-repeat`; uncommitted, no push/main changes. Earlier authorized changes preserved.
- `AmazonPublicAdapter.java` now reads expandable product-detail tables within `#prodDetails` and overview dimensions/weight rows. For overview rows it reads the last value cell, avoiding the label cell. Product/item dimensions are supported; unrelated page tables are excluded. Missing date/weight are not invented.
- `AmazonPublicAdapterTest.java` adds a regression for expandable ranks, two-cell overview dimensions and absent date/weight, including an unrelated table that must not become product data.
- Full backend `./mvnw clean verify` passed: 250 tests, zero failures/errors, one existing skip; `/tmp/product-layout-back.log`. `git diff --check` passed. Frontend unchanged this round.
- Deployed on original backend 8080; health UP. Release `.local/releases/backend/product-layout-20261027/backend.jar`, SHA256 `0887c5760d3fa6d77782462dc04aa008b2645460a73551fdad3a28aab1d0af63`. Backup `.local/backups/product-layout-20261027/`. MySQL retained.
- Live acceptance: recollected the reported product through its normal collect endpoint. Baseline advanced and now contains main/subcategory ranks and dimensions. Job correctly remains PARTIAL because date/weight were not observed. Temporary QA disabled and sessions cleared. No unrelated files staged or overwritten.

### Separate discovery and 60-day novelty records

- ERP-099 continuation on `fix/ERP-099-store-pagination-repeat`; direct user authorization, uncommitted, no push or main changes. Earlier authorized dirty changes preserved; no unrelated files staged/overwritten.
- Preserve initial-baseline exclusion and historical-ASIN discovery rules. Rename 今日上新 to 今日上架 and detail 上新记录 to 上架记录, with explicit first-observed semantics. Add independent 今日新品 counter and 新品记录 tab. Both counters use the event's original Pacific discovery date.
- V54 adds automatic novelty evidence and separately versioned manual overrides to existing events. `WatchNoveltyTarget` classifies observed dates 0–60 days before discovery as CANDIDATE, older dates as OLD, missing/invalid/future dates as PENDING. No unsupported suspected inference from missing dates/review counts. Evidence dates are retained when a later page omits them. Automatic results never overwrite manual marks or event discovery timestamps.
- `WatchNoveltyController` discovers public-source events, including historical ones, and enqueues bounded detail reads using the existing collection scheduler. Successful enrichment is one-shot; missing date remains explicit; failures use existing retry/lease fencing. Paused/removed stores are fenced. Original STORE/PRODUCT jobs have priority over enrichment backlog. No added ports, seller credentials or Amazon API requirements.
- `WatchTypes/Repository/ExceptionHandler` expose evidence and versioned CONFIRMED/EXCLUDED/RESET actions, with watch.write authorization, validation, conflict protection and append-only audit. These marks are distinct from candidate-pool confirmation.
- Frontend `WatchStoresView`, `WatchStoreDetail`, `watchNovelty`, resources and collection-status composable: default today/new-candidate filter; explicit Search applies date/title/ASIN/judgment; pending/old/excluded/all views; source/date/reason/check time; queued/running/retry feedback; date retry, confirm/exclude/undo controls. Shared HorizontalTableViewport keeps all columns accessible, and short-height dialog body scrolls while pagination remains visible. Compact native select styling follows UI_STANDARD.
- Full backend `./mvnw clean verify`: 254 tests, zero failures/errors, one existing skip; `/tmp/novelty-back-release.log`. Tests cover inclusive 60-day boundary, Pacific date boundary, invalid/future/missing dates, late enrichment, permissions, validation/conflicts, manual override persistence/undo, failure isolation, one-shot completion, queue priority and pause fencing. Full frontend `ERP_BUILD_OUT_DIR=/tmp/novelty-final-build pnpm --dir frontend quality`: 89 files / 356 passing tests, lint/typecheck/build passed; `/tmp/novelty-front-final.log`. `git diff --check` passed.
- Local release `.local/releases/backend/watch-novelty-20261027-final/backend.jar`, SHA256 `e07cc6584a621d100d6a476a2d4d5563af7f15ea05e8025ff057b8dcf4cf4842`; original 8080 / 5173, backend pane `%12`, restarts between active jobs. V54 applied; backup `.local/backups/watch-novelty-20261027/`; MySQL retained.
- Live acceptance: public-page enrichment populated actual old dates and classified old links while missing dates stayed pending; discovery counts/timestamps retained. Historical backlog continues asynchronously. Browser checked desktop 1280×800, 800×600 and 360×800, filters, default novelty entry, all columns via horizontal control, fixed footer and short-window body scrolling. Exclude confirmation cancelled without modifying a real record. QA disabled, sessions cleared, viewport reset and temporary tab closed.
- Limitation: Date First Available is product-level evidence, not proof of when this seller began selling. No trustworthy additional signal currently auto-generates SUSPECTED; missing dates stay pending and can be reviewed manually. No unrelated performance work.
