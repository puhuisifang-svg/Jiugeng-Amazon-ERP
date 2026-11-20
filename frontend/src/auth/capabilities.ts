import type { AppRole, Capability } from '@/router/meta'

const ROLE_CAPABILITIES: Record<AppRole, Capability[]> = {
  CUSTOM: [],
  ADMIN: [
    'tools.pdf.use',
    'customers.read', 'customers.write',
    'system.users.manage', 'products.read', 'products.write', 'materials.read', 'materials.write',
    'inventory.read', 'inventory.write', 'selection.read', 'selection.write', 'watch.read', 'watch.write',
    'imports.read', 'imports.template.download', 'imports.validate', 'imports.commit', 'imports.cancel',
    'imports.result.download', 'audit.read.all', 'audit.read.self', 'dashboard.read',
    'dashboard.shipments.manage', 'dashboard.pending-products.manage',
    'suppliers.read', 'suppliers.write', 'purchases.read', 'purchases.write', 'purchases.receive',
  ],
  PRODUCT_MATERIAL: [
    'customers.read', 'customers.write',
    'products.read', 'products.write', 'materials.read', 'materials.write', 'inventory.read', 'inventory.write',
    'selection.read', 'watch.read', 'imports.read', 'imports.template.download', 'imports.validate', 'imports.commit',
    'imports.cancel', 'imports.result.download', 'audit.read.self', 'dashboard.read',
    'dashboard.shipments.manage', 'dashboard.pending-products.manage',
    'suppliers.read', 'suppliers.write', 'purchases.read', 'purchases.write', 'purchases.receive',
  ],
  SELECTION: [
    'products.read', 'materials.read', 'selection.read', 'selection.write', 'watch.read', 'watch.write',
    'audit.read.self', 'dashboard.read', 'dashboard.pending-products.manage',
  ],
}

export function capabilitiesForRole(role: AppRole): Capability[] {
  const result = new Set<Capability>(ROLE_CAPABILITIES[role])
  if (result.has('products.read')) ['materials.read', 'suppliers.read', 'inventory.read'].forEach(code => result.add(code as Capability))
  if (result.has('products.write')) ['materials.write', 'suppliers.write'].forEach(code => result.add(code as Capability))
  return [...result]
}
