import 'vue-router'

export type AppRole = 'ADMIN' | 'PRODUCT_MATERIAL' | 'SELECTION' | 'CUSTOM'
export type Capability =
  | 'forwarders.read' | 'forwarders.write' | 'forwarders.export' | 'forwarders.delete'
  | 'factory.expenses.read' | 'factory.expenses.write' | 'factory.expenses.remove'
  | 'factory.employees.read' | 'factory.employees.write' | 'factory.rates.read' | 'factory.rates.write' | 'factory.reports.read' | 'factory.reports.write' | 'factory.reports.review' | 'factory.payroll.read' | 'factory.payroll.write' | 'factory.payroll.lock' | 'factory.payroll.correct' | 'factory.payroll.pay' | 'factory.payroll.export'
  | 'products.export' | 'customers.delete' | 'orders.read' | 'orders.write' | 'orders.transition' | 'invoices.read' | 'invoices.write' | 'costs.delete'
  | 'finance.read' | 'finance.write' | 'costs.read' | 'costs.write' | 'costs.publish' | 'costs.export' | 'system.users.manage' | 'products.read' | 'products.write' | 'materials.read' | 'materials.write' | 'inventory.read' | 'inventory.write' | 'selection.read' | 'selection.write' | 'watch.read' | 'watch.write' | 'imports.read' | 'imports.template.download' | 'imports.validate' | 'imports.commit' | 'imports.cancel' | 'imports.result.download' | 'audit.read.all' | 'audit.read.self' | 'dashboard.read' | 'dashboard.shipments.manage' | 'dashboard.pending-products.manage' | 'suppliers.read' | 'suppliers.write' | 'purchases.read' | 'purchases.write' | 'purchases.receive' | 'customers.read' | 'customers.write' | 'samples.read' | 'samples.write' | 'samples.remove' | 'development.read' | 'development.write' | 'development.review' | 'selection.review' | 'watch.collect' | 'watch.remove' | 'finance.void' | 'finance.export' | 'finance.attachment.remove' | 'suppliers.delete' | 'products.delete' | 'materials.delete' | 'purchases.submit' | 'purchases.cancel' | 'inventory.inbound' | 'inventory.outbound' | 'inventory.reverse' | 'monitoring.read' | 'monitoring.write' | 'monitoring.collect' | 'monitoring.remove'

  | 'finance.read' | 'finance.write'
  | 'costs.read' | 'costs.write' | 'costs.publish' | 'costs.export'
  | 'system.users.manage' | 'products.read' | 'products.write' | 'materials.read' | 'materials.write'
  | 'inventory.read' | 'inventory.write' | 'selection.read' | 'selection.write' | 'watch.read' | 'watch.write'
  | 'imports.read' | 'imports.template.download' | 'imports.validate' | 'imports.commit' | 'imports.cancel'
  | 'imports.result.download' | 'audit.read.all' | 'audit.read.self'
  | 'dashboard.read' | 'dashboard.shipments.manage' | 'dashboard.pending-products.manage'
  | 'suppliers.read' | 'suppliers.write' | 'purchases.read' | 'purchases.write' | 'purchases.receive'
  | 'customers.read' | 'customers.write'

declare module 'vue-router' {
  interface RouteMeta {
    title: string
    group?: string
    icon?: string
    hidden?: boolean
    parentPath?: string
    breadcrumbs?: Array<{ title: string; to?: string }>
    capabilities?: Capability[]
  }
}

export {}
