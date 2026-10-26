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
