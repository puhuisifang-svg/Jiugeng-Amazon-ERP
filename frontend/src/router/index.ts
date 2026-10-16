import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'

import AdminLayout from '@/layouts/AdminLayout.vue'
import { session } from '@/state/session'
import type { Capability } from '@/router/meta'

const placeholder = () => import('@/views/PlaceholderView.vue')

export async function refreshAccessIfStale(capabilities?: Capability[]) {
  if (session.canAccess(capabilities)) return true
  try {
    await session.refresh()
  }
  catch {
    return false
  }
  return session.canAccess(capabilities)
}

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
      { path: 'product-development', name: 'product-development', component: () => import('@/views/ProductDevelopmentView.vue'), meta: { title: '新品开发', group: '产品管理', icon: '新', capabilities: ['products.read'] } },
      { path: 'product-development/new', name: 'product-development-new', component: () => import('@/views/ProductDevelopmentView.vue'), meta: { title: '添加新品需求', group: '产品管理', hidden: true, parentPath: '/product-development', capabilities: ['products.write'] } },
      { path: 'product-development/:id', name: 'product-development-detail', component: () => import('@/views/ProductDevelopmentView.vue'), meta: { title: '新品开发详情', group: '产品管理', hidden: true, parentPath: '/product-development', capabilities: ['products.read'] } },
      { path: 'product-monitoring', name: 'product-monitoring', component: () => import('@/views/ProductMonitoringView.vue'), meta: { title: '产品监控', group: '产品监控', icon: '◎' } },
      { path: 'product-monitoring/:id', name: 'product-monitoring-detail', component: () => import('@/views/ProductMonitoringView.vue'), meta: { title: '监控详情', hidden: true, parentPath: '/product-monitoring' } },
      { path: 'home', name: 'home', component: () => import('@/views/DashboardView.vue'), meta: { title: '首页', group: '工作台', icon: '⌂', capabilities: ['dashboard.read'] } },
      { path: 'dashboard-maintenance/today-shipments', name: 'dashboard-today-shipments', component: () => import('@/views/DashboardMaintenanceView.vue'), props: { kind: 'today-shipments' }, meta: { title: '今日发货管理', group: '工作台维护', icon: '今', capabilities: ['dashboard.shipments.manage'] } },
      { path: 'dashboard-maintenance/pending-shipments', name: 'dashboard-pending-shipments', component: () => import('@/views/DashboardMaintenanceView.vue'), props: { kind: 'pending-shipments' }, meta: { title: '待发货管理', group: '工作台维护', icon: '待', capabilities: ['dashboard.shipments.manage'] } },
      { path: 'dashboard-maintenance/pending-products', name: 'dashboard-pending-products', component: () => import('@/views/DashboardMaintenanceView.vue'), props: { kind: 'pending-products' }, meta: { title: '新品待开发管理', group: '工作台维护', icon: '新', capabilities: ['dashboard.pending-products.manage'] } },
      { path: 'products', name: 'products', component: () => import('@/views/ProductsView.vue'), meta: { title: '产品管理', group: '产品管理', icon: '◆', capabilities: ['products.read'] } },
      { path: 'products/new', name: 'product-new', component: () => import('@/views/ProductNewView.vue'), meta: { title: '新建产品', hidden: true, capabilities: ['products.write'] } },
      { path: 'products/:productId/:section(overview|variants|materials|changes)?', name: 'product-detail', component: () => import('@/views/ProductDetailView.vue'), meta: { title: '产品详情', hidden: true, capabilities: ['products.read'] } },
      { path: 'materials', name: 'materials', component: () => import('@/views/MaterialsView.vue'), meta: { title: '辅料管理', group: '产品管理', icon: '◇', capabilities: ['materials.read'] } },
      { path: 'suppliers', name: 'suppliers', component: () => import('@/views/SuppliersView.vue'), meta: { title: '供应商管理', group: '产品管理', icon: '供', capabilities: ['suppliers.read'] } },
      { path: 'purchases', name: 'purchase-dashboard', redirect: '/purchases/orders', meta: { title: '采购单', hidden: true, capabilities: ['purchases.read'] } },
      { path: 'purchases/orders', name: 'purchase-orders', component: () => import('@/views/PurchasesView.vue'), meta: { title: '采购单', group: '采购管理', icon: '单', capabilities: ['purchases.read'] } },
      { path: 'purchases/orders/:orderId', name: 'purchase-order-detail', component: () => import('@/views/PurchaseOrderDetailView.vue'), meta: { title: '采购单详情', hidden: true, parentPath: '/purchases/orders', capabilities: ['purchases.read'] } },
      { path: 'purchases/receipts', name: 'purchase-receipts', redirect: '/purchases/orders', meta: { title: '采购单', hidden: true, capabilities: ['purchases.read'] } },
      { path: 'inventory', redirect: '/inventory/balances', meta: { title: '辅料库存', hidden: true, capabilities: ['inventory.read'] } },
      { path: 'inventory/balances', name: 'inventory-balances', redirect: '/materials', meta: { title: '辅料库存', hidden: true, capabilities: ['inventory.read'] } },
      { path: 'inventory/ledger', name: 'inventory-ledger', component: () => import('@/views/InventoryLedgerView.vue'), meta: { title: '库存流水', hidden: true, parentPath: '/materials', breadcrumbs: [{ title: '商品' }, { title: '辅料管理', to: '/materials' }, { title: '库存流水' }], capabilities: ['inventory.read'] } },
      { path: 'imports', name: 'imports', component: () => import('@/views/ImportsView.vue'), meta: { title: '导入', hidden: true, parentPath: '/materials', breadcrumbs: [{ title: '商品' }, { title: '辅料管理', to: '/materials' }, { title: '导入' }], capabilities: ['imports.read'] } },
      { path: 'imports/:batchId', name: 'import-detail', component: () => import('@/views/ImportDetailView.vue'), meta: { title: '导入批次', hidden: true, parentPath: '/materials', breadcrumbs: [{ title: '商品' }, { title: '辅料管理', to: '/materials' }, { title: '导入', to: '/imports' }, { title: '导入批次' }], capabilities: ['imports.read'] } },
      { path: 'selection/candidates', name: 'selection-candidates', component: () => import('@/views/CandidatesView.vue'), meta: { title: '候选池', group: '选品管理', icon: '☆', capabilities: ['selection.read'] } },
      { path: 'selection/candidates/:candidateId', name: 'candidate-detail', component: () => import('@/views/CandidateDetailView.vue'), meta: { title: '候选详情', hidden: true, capabilities: ['selection.read'] } },
      { path: 'watch/stores', name: 'watch-stores', component: () => import('@/views/WatchStoresView.vue'), meta: { title: '监控店铺', group: '选品管理', icon: '◎', capabilities: ['watch.read'] } },
      { path: 'watch/events', name: 'new-events', component: () => import('@/views/WatchEventsView.vue'), meta: { title: '上新事件', group: '选品管理', icon: '↗', capabilities: ['watch.read'] } },
      { path: 'system/users', name: 'users', component: () => import('@/views/UsersView.vue'), meta: { title: '用户与角色', group: '系统管理', icon: '♙', capabilities: ['system.users.manage'] } },
      { path: 'system/audit', name: 'audit', component: () => import('@/views/AuditView.vue'), meta: { title: '审计日志', group: '系统管理', icon: '◫', capabilities: ['audit.read.self'] } },
      { path: 'settings', name: 'settings', component: () => import('@/views/SettingsView.vue'), meta: { title: '设置', group: '系统管理', icon: '⚙' } },
    ],
  },
  { path: '/:pathMatch(.*)*', name: 'not-found', component: () => import('@/views/NotFoundView.vue'), meta: { title: '页面不存在', hidden: true } },
]

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: appRoutes,
  scrollBehavior: () => ({ top: 0 }),
})

router.beforeEach(async (to) => {
  if (to.name === 'login' || to.name === 'forgot-password') {
    return session.isAuthenticated.value ? { name: 'home' } : true
  }
  if (!session.isAuthenticated.value) return { name: 'login', query: { redirect: to.fullPath } }
  if (session.user.value?.mustChangePassword && to.name !== 'change-password') return { name: 'change-password' }
  if (!await refreshAccessIfStale(to.meta.capabilities)) {
    return session.isAuthenticated.value ? { name: 'forbidden' } : { name: 'login', query: { redirect: to.fullPath } }
  }
  return true
})

router.afterEach((to) => {
  document.title = `${to.meta.title} · 九更亚马逊 ERP`
})

export default router
