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
      { path: 'compliance/customs', name: 'customs-management', component: () => import('@/views/CustomsManagementView.vue'), meta: { title: '报关管理', group: '报关管理', capabilities: ['products.read'], breadcrumbs: [{ title: '合规' }, { title: '报关管理' }] } },
      { path: 'customers/info', name: 'customer-info', component: () => import('@/views/CustomerManagementView.vue'), props: { kind: 'info' }, meta: { title: '客户信息', group: '客户管理', capabilities: ['customers.read'], breadcrumbs: [{ title: '客户' }, { title: '客户管理' }, { title: '客户信息' }] } },
      { path: 'customers/products', name: 'customer-products', component: () => import('@/views/CustomerManagementView.vue'), props: { kind: 'products' }, meta: { title: '客户商品', group: '客户管理', capabilities: ['customers.read'], breadcrumbs: [{ title: '客户' }, { title: '客户管理' }, { title: '客户商品' }] } },
      { path: 'customers/invoices', name: 'customer-invoices', component: () => import('@/views/OrderInvoicesView.vue'), meta: { title: '发票管理', group: '客户管理', capabilities: ['customers.read'], breadcrumbs: [{ title: '客户' }, { title: '客户管理' }, { title: '发票管理' }] } },
      { path: 'orders/customers', name: 'customer-orders', component: () => import('@/views/CustomerOrdersView.vue'), meta: { title: '客户订单', group: '订单管理', capabilities: ['customers.read'], breadcrumbs: [{ title: '订单' }, { title: '订单管理' }, { title: '客户订单' }] } },
      { path: 'orders/customers/:id', name: 'customer-order-detail', component: () => import('@/views/CustomerOrdersView.vue'), meta: { title: '订单详情', hidden: true, parentPath: '/orders/customers', capabilities: ['customers.read'], breadcrumbs: [{ title: '订单' }, { title: '订单管理' }, { title: '客户订单', to: '/orders/customers' }, { title: '订单详情' }] } },
      { path: 'product-development', name: 'product-development', component: () => import('@/views/ProductDevelopmentView.vue'), meta: { title: '新品开发', group: '产品管理', icon: '新', capabilities: ['products.read'] } },
      { path: 'product-development/new', name: 'product-development-new', component: () => import('@/views/ProductDevelopmentView.vue'), meta: { title: '添加新品需求', group: '产品管理', hidden: true, parentPath: '/product-development', capabilities: ['products.write'] } },
      { path: 'product-development/:id', name: 'product-development-detail', component: () => import('@/views/ProductDevelopmentView.vue'), meta: { title: '新品开发详情', group: '产品管理', hidden: true, parentPath: '/product-development', capabilities: ['products.read'] } },
      { path: 'product-monitoring', name: 'product-monitoring', component: () => import('@/views/ProductMonitoringView.vue'), meta: { title: '产品监控', group: '选品管理', icon: '◎' } },
      { path: 'product-monitoring/:id', name: 'product-monitoring-detail', component: () => import('@/views/ProductMonitoringView.vue'), meta: { title: '监控详情', hidden: true, parentPath: '/product-monitoring' } },
      { path: 'home', name: 'home', component: () => import('@/views/DashboardView.vue'), meta: { title: '首页', group: '工作台', icon: '⌂', capabilities: ['dashboard.read'] } },
      { path: 'dashboard-maintenance/today-shipments', name: 'dashboard-today-shipments', component: () => import('@/views/DashboardMaintenanceView.vue'), props: { kind: 'today-shipments' }, meta: { title: '今日发货管理', group: '发货管理', icon: '今', capabilities: ['dashboard.shipments.manage'] } },
      { path: 'dashboard-maintenance/pending-shipments', name: 'dashboard-pending-shipments', component: () => import('@/views/DashboardMaintenanceView.vue'), props: { kind: 'pending-shipments' }, meta: { title: '待发货管理', group: '发货管理', icon: '待', capabilities: ['dashboard.shipments.manage'] } },
      { path: 'shipping/statistics', name: 'shipping-statistics', component: () => import('@/views/ShipmentStatisticsView.vue'), meta: { title: '发货统计', group: '发货管理', icon: '统', capabilities: ['dashboard.shipments.manage'] } },
      { path: 'dashboard-maintenance/pending-products', name: 'dashboard-pending-products', component: () => import('@/views/DashboardMaintenanceView.vue'), props: { kind: 'pending-products' }, meta: { title: '新品待开发管理', group: '工作台维护', icon: '新', capabilities: ['dashboard.pending-products.manage'] } },
      { path: 'products', name: 'products', component: () => import('@/views/ProductsView.vue'), meta: { title: '产品管理', group: '产品管理', icon: '◆', capabilities: ['products.read'] } },
      { path: 'products/new', name: 'product-new', component: () => import('@/views/ProductNewView.vue'), meta: { title: '新建产品', hidden: true, capabilities: ['products.write'] } },
      { path: 'products/:productId/:section(overview|variants|materials|documents|changes)?', name: 'product-detail', component: () => import('@/views/ProductDetailView.vue'), meta: { title: '产品详情', hidden: true, parentPath: '/products', breadcrumbs: [{ title: '商品' }, { title: '产品管理', to: '/products' }, { title: '产品详情' }], capabilities: ['products.read'] } },
      { path: 'materials', name: 'materials', component: () => import('@/views/MaterialsView.vue'), meta: { title: '辅料管理', group: '产品管理', icon: '◇', capabilities: ['materials.read'] } },
      { path: 'product-costs', name: 'product-costs', component: () => import('@/views/ProductCostsView.vue'), meta: { title: '产品成本', group: '产品管理', capabilities: ['costs.read'], breadcrumbs: [{ title: '商品' }, { title: '产品成本' }] } },
      { path: 'suppliers', name: 'suppliers', component: () => import('@/views/SuppliersView.vue'), meta: { title: '供应商管理', group: '产品管理', icon: '供', capabilities: ['suppliers.read'] } },
      { path: 'purchases', name: 'purchase-dashboard', redirect: '/purchases/orders', meta: { title: '采购管理', hidden: true, capabilities: ['purchases.read'] } },
      { path: 'purchases/orders', name: 'purchase-orders', component: () => import('@/views/PurchasesView.vue'), meta: { title: '采购管理', group: '产品管理', icon: '单', capabilities: ['purchases.read'] } },
      { path: 'purchases/orders/:orderId', name: 'purchase-order-detail', component: () => import('@/views/PurchaseOrderDetailView.vue'), meta: { title: '采购单详情', hidden: true, parentPath: '/purchases/orders', breadcrumbs: [{ title: '商品' }, { title: '采购管理', to: '/purchases/orders' }, { title: '采购单详情' }], capabilities: ['purchases.read'] } },
      { path: 'purchases/receipts', name: 'purchase-receipts', redirect: '/purchases/orders', meta: { title: '采购管理', hidden: true, capabilities: ['purchases.read'] } },
      { path: 'inventory', redirect: '/inventory/balances', meta: { title: '辅料库存', hidden: true, capabilities: ['inventory.read'] } },
      { path: 'inventory/balances', name: 'inventory-balances', redirect: '/materials', meta: { title: '辅料库存', hidden: true, capabilities: ['inventory.read'] } },
      { path: 'inventory/ledger', name: 'inventory-ledger', component: () => import('@/views/InventoryLedgerView.vue'), meta: { title: '库存流水', hidden: true, parentPath: '/materials', breadcrumbs: [{ title: '商品' }, { title: '辅料管理', to: '/materials' }, { title: '库存流水' }], capabilities: ['inventory.read'] } },
      { path: 'products/imports', name: 'product-imports', component: () => import('@/views/ImportsView.vue'), props: { importBasePath: '/products/imports' }, meta: { title: '产品导入', hidden: true, parentPath: '/products', breadcrumbs: [{ title: '商品' }, { title: '产品管理', to: '/products' }, { title: '导入' }], capabilities: ['imports.read'] } },
      { path: 'products/imports/:batchId', name: 'product-import-detail', component: () => import('@/views/ImportDetailView.vue'), props: { importBasePath: '/products/imports' }, meta: { title: '产品导入批次', hidden: true, parentPath: '/products', breadcrumbs: [{ title: '商品' }, { title: '产品管理', to: '/products' }, { title: '导入', to: '/products/imports' }, { title: '导入批次' }], capabilities: ['imports.read'] } },
      { path: 'imports', name: 'imports', component: () => import('@/views/ImportsView.vue'), meta: { title: '导入', hidden: true, parentPath: '/materials', breadcrumbs: [{ title: '商品' }, { title: '辅料管理', to: '/materials' }, { title: '导入' }], capabilities: ['imports.read'] } },
      { path: 'imports/:batchId', name: 'import-detail', component: () => import('@/views/ImportDetailView.vue'), meta: { title: '导入批次', hidden: true, parentPath: '/materials', breadcrumbs: [{ title: '商品' }, { title: '辅料管理', to: '/materials' }, { title: '导入', to: '/imports' }, { title: '导入批次' }], capabilities: ['imports.read'] } },
      { path: 'selection/candidates', name: 'selection-candidates', component: () => import('@/views/CandidatesView.vue'), meta: { title: '候选池', group: '选品管理', icon: '☆', capabilities: ['selection.read'] } },
      { path: 'selection/candidates/:candidateId', name: 'candidate-detail', component: () => import('@/views/CandidateDetailView.vue'), meta: { title: '候选详情', hidden: true, capabilities: ['selection.read'] } },
      { path: 'watch/stores', name: 'watch-stores', component: () => import('@/views/WatchStoresView.vue'), meta: { title: '店铺监控', group: '选品管理', icon: '◎', capabilities: ['watch.read'] } },
      { path: 'watch/events', name: 'new-events', component: () => import('@/views/WatchEventsView.vue'), meta: { title: '上新监控', group: '选品管理', icon: '↗', capabilities: ['watch.read'] } },
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
