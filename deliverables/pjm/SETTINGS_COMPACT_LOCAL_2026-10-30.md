# 设置页紧凑布局与账号分页

用户直接授权实施及本地部署，无新增项目经理任务号。分支 fix/ERP-099-product-detail-ui，基准 33122a4c08831689db5454320143fb6fc6235089。变更未提交，未提交 main。

修改 frontend/src/views/SettingsView.vue，新增 SettingsView.test.ts。修改密码、安全设置收为一行按钮，点击 native dialog 编辑；密码重置也进入弹窗。保留输入校验、保存失败输入保留、取消未保存确认及管理员权限。顶部约 42px，账号行约 47px。账号列表使用 ListPagination，默认20条、支持10/20/50/100、跳页、加载/空态；复用 TableViewport 和底部独立分页区域。

账号接口目前返回全量授权列表，本次采用前端分页，不改变 API 或权限；后续账号量增长时可改为服务端分页。

验证：ERP_BUILD_OUT_DIR=/tmp/settings-layout-build pnpm --dir frontend quality，通过 lint、类型检查、95 文件381测试及构建；git diff --check通过。真实浏览器检查1280×800、800×600、360×800，第二页显示剩余13条（总33条），分页bottom=800；安全设置、密码弹窗及取消验证通过。未实际修改账号密码或安全策略；验收账号已禁用并清理会话。

已在原本地5173服务通过Vite生效，未更换端口、未重启后端。保留工作区所有前序授权修改，本次只更改设置页、其测试及本记录。
