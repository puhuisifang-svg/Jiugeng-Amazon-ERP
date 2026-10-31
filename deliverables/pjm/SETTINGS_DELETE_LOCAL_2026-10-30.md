# 设置按钮配色与账号删除

用户直接授权实施及本地部署；无新增ERP编号。分支 fix/ERP-099-product-detail-ui，基准33122a4c08831689db5454320143fb6fc6235089，变更未提交，未提交main。

- SettingsView.vue：修改密码橙色、安全设置蓝色，操作列红色删除按钮；确认后删除，失败保留行，删除最后一页最后一条时自动回退；当前账号不显示删除。
- api/auth.ts：DELETE /api/v1/users/{id}。
- IamController/Service/Repository：管理员权限、自删拦截、软删除、撤销会话与找回密码挑战、追加ACCOUNT_DELETE审计；删除账号不可登录/启用，不再列出。历史记录保留，登录名仍保留不可复用。
- V60__iam_user_soft_delete.sql：增加deleted_at。
- SettingsView.test.ts、IamControllerTest.java：删除取消/失败/分页回退/自删/越权/会话失效/删除后登录与启用失败/审计验证。

验证：前端完整quality通过，95文件383测试；后端 ./mvnw clean verify 通过，274测试0失败0错误1跳过；git diff --check通过。浏览器1280×800及360×800检查配色、操作列、确认取消。仅验收模拟账号用于接口新建/删除冒烟，真实账号未删除。临时登录账号禁用并清理会话。

本地已部署到原5173/8080。后端release .local/releases/backend/settings-delete-20261030/backend.jar，旧启动脚本保存在该目录；原tmux jiugeng-mvp:backend启动，health UP，MySQL迁移60成功。部署前无RUNNING采集任务；保留caffeinate与原采集环境配置。未清空业务数据。

本次仅上述文件及本记录新增修改；此前工作区授权变更全部保留，无无关修改。
