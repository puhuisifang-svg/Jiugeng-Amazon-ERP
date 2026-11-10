import type { RouteRecordRaw } from 'vue-router'
import type { Permission } from '@/api/roles'
import { navigationDomains } from '@/config/navigation'
export interface PermissionMenuItem { key: string; title: string; codes: string[] }
export interface PermissionMenuGroup extends PermissionMenuItem { children: PermissionMenuItem[] }
// Pages sharing a backend permission deliberately show the same permission.
// This is navigation only: selecting a page never invents a new authorization scope.
const prefixes: Record<string, string[]> = {
  'factory/expenses':['factory.expenses.'], 'factory/employees':['factory.employees.'], 'factory/rates':['factory.rates.'], 'factory/reports':['factory.reports.'], 'factory/payroll':['factory.payroll.'],
  'home':['dashboard.read'], 'dashboard-maintenance/pending-products':['dashboard.pending-products.manage'],
  'dashboard-maintenance/today-shipments':['dashboard.shipments.manage'], 'dashboard-maintenance/pending-shipments':['dashboard.shipments.manage'], 'shipping/statistics':['dashboard.shipments.manage'],
  'product-development':['development.'], samples:['samples.'], products:['products.','imports.'], materials:['materials.','inventory.','imports.'],
  'product-costs':['costs.'], suppliers:['suppliers.'], 'purchases/orders':['purchases.'],
  'customers/info':['customers.read','customers.write'], 'customers/products':['customers.'], 'customers/invoices':['invoices.'],
  'fba/forwarders':['forwarders.'], 'fba/warehouses':['products.read','products.write'],
  'orders/customers':['orders.'], 'compliance/customs':['products.read'], 'finance/bills':['finance.'],
  'product-monitoring':['monitoring.'], 'selection/candidates':['selection.'], 'watch/stores':['watch.'],
  'system/users':['system.users.manage'], 'system/roles':['system.users.manage'], 'system/audit':['audit.'], settings:['system.users.manage'],
}
export function buildPermissionMenu(routes: RouteRecordRaw[], permissions: Permission[]): PermissionMenuGroup[] {
  const entries = routes.filter(r => !r.meta?.hidden && r.meta?.group)
  const groups = navigationDomains.map(domain => {
    const children = domain.groups.flatMap(group => entries.filter(r => r.meta?.group === group).map(r => {
      const matches = prefixes[r.path] ?? r.meta?.capabilities ?? []
      const codes = permissions.filter(p => matches.some(prefix => prefix.endsWith('.') ? p.code.startsWith(prefix) : p.code === prefix)).map(p => p.code)
      return { key:r.path, title:r.meta?.title || r.path, codes }
    }))
    return {key:`domain:${domain.title}`,title:domain.title,children,codes:[...new Set(children.flatMap(c=>c.codes))]}
  }).filter(g=>g.children.length)
  const covered = new Set(groups.flatMap(g=>g.codes)), extra = permissions.filter(p=>!covered.has(p.code)).map(p=>p.code)
  if(extra.length) groups.push({key:'domain:other',title:'其他权限',codes:extra,children:[{key:'other',title:'未归类权限',codes:extra}]})
  return groups
}
