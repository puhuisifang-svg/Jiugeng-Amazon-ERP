import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'

import AdminLayout from '@/layouts/AdminLayout.vue'
import { session } from '@/state/session'

const placeholder = () => import('@/views/PlaceholderView.vue')

export const appRoutes: RouteRecordRaw[] = [
  { path: '/login', name: 'login', component: () => import('@/views/LoginView.vue'), meta: { title: '登录', hidden: true } },
  { path: '/change-password', name: 'change-password', component: () => import('@/views/ChangePasswordView.vue'), meta: { title: '修改密码', hidden: true } },
  { path: '/forgot-password', name: 'forgot-password', component: () => import('@/views/ForgotPasswordView.vue'), meta: { title: '忘记密码', hidden: true } },
  { path: '/forbidden', name: 'forbidden', component: placeholder, props: { description: '当前角色无权访问此页面（403）' }, meta: { title: '权限受限', hidden: true } },
  {
    path: '/',
    component: AdminLayout,
    redirect: '/home',
    children: [
      { path: 'home', name: 'home', component: () => import('@/views/DashboardView.vue'), meta: { title: '首页', group: '工作台', icon: '⌂' } },
      { path: 'products', name: 'products', component: () => import('@/views/ProductsView.vue'), meta: { title: '产品与 SKU', group: '产品管理', icon: '◆', capabilities: ['products.read'] } },
      { path: 'products/new', name: 'product-new', component: () => import('@/views/ProductNewView.vue'), meta: { title: '新建产品', hidden: true, capabilities: ['products.write'] } },
      { path: 'products/:productId/:section(overview|variants|materials|changes)?', name: 'product-detail', component: () => import('@/views/ProductDetailView.vue'), meta: { title: '产品详情', hidden: true, capabilities: ['products.read'] } },
      { path: 'materials', name: 'materials', component: () => import('@/views/MaterialsView.vue'), meta: { title: '辅料主档', group: '产品管理', icon: '◇', capabilities: ['materials.read'] } },
      { path: 'inventory', redirect: '/inventory/balances', meta: { title: '辅料库存', hidden: true, capabilities: ['inventory.read'] } },
      { path: 'inventory/balances', name: 'inventory-balances', component: () => import('@/views/InventoryView.vue'), meta: { title: '辅料库存', group: '产品管理', icon: '▦', capabilities: ['inventory.read'] } },
      { path: 'inventory/ledger', name: 'inventory-ledger', component: placeholder, props: { description: '不可编辑、不可删除的库存流水入口' }, meta: { title: '库存流水', hidden: true, capabilities: ['inventory.read'] } },
      { path: 'imports', name: 'imports', component: placeholder, props: { description: '五类 Excel 上传、校验、预览、提交与结果入口' }, meta: { title: '导入中心', group: '产品管理', icon: '⇧', capabilities: ['imports.read'] } },
      { path: 'imports/:batchId', name: 'import-detail', component: placeholder, props: { description: '导入批次进度、错误定位与结果入口' }, meta: { title: '导入批次', hidden: true, capabilities: ['imports.read'] } },
      { path: 'selection/candidates', name: 'selection-candidates', component: () => import('@/views/CandidatesView.vue'), meta: { title: '候选池', group: '选品管理', icon: '☆', capabilities: ['selection.read'] } },
      { path: 'selection/candidates/:candidateId', name: 'candidate-detail', component: () => import('@/views/CandidateDetailView.vue'), meta: { title: '候选详情', hidden: true, capabilities: ['selection.read'] } },
      { path: 'watch/stores', name: 'watch-stores', component: () => import('@/views/WatchStoresView.vue'), meta: { title: '监控店铺', group: '选品管理', icon: '◎', capabilities: ['watch.read'] } },
      { path: 'watch/events', name: 'new-events', component: () => import('@/views/WatchEventsView.vue'), meta: { title: '上新事件', group: '选品管理', icon: '↗', capabilities: ['watch.read'] } },
      { path: 'system/users', name: 'users', component: () => import('@/views/UsersView.vue'), meta: { title: '用户与角色', group: '系统管理', icon: '♙', capabilities: ['system.users.manage'] } },
      { path: 'system/audit', name: 'audit', component: () => import('@/views/AuditView.vue'), meta: { title: '审计日志', group: '系统管理', icon: '◫', capabilities: ['audit.read.self'] } },
    ],
  },
  { path: '/:pathMatch(.*)*', name: 'not-found', component: () => import('@/views/NotFoundView.vue'), meta: { title: '页面不存在', hidden: true } },
]

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: appRoutes,
  scrollBehavior: () => ({ top: 0 }),
})

router.beforeEach((to) => {
  if (to.name === 'login' || to.name === 'forgot-password') {
    return session.isAuthenticated.value ? { name: 'home' } : true
  }
  if (!session.isAuthenticated.value) return { name: 'login', query: { redirect: to.fullPath } }
  if (session.user.value?.mustChangePassword && to.name !== 'change-password') return { name: 'change-password' }
  if (!session.canAccess(to.meta.capabilities)) return { name: 'forbidden' }
  return true
})

router.afterEach((to) => {
  document.title = `${to.meta.title} · 九更亚马逊 ERP`
})

export default router
