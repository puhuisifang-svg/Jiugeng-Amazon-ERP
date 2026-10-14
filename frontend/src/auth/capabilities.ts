import type { AppRole, Capability } from '@/router/meta'

const ROLE_CAPABILITIES: Record<AppRole, Capability[]> = {
  ADMIN: [
    'system.users.manage', 'products.read', 'products.write', 'materials.read', 'materials.write',
    'inventory.read', 'inventory.write', 'selection.read', 'selection.write', 'watch.read', 'watch.write',
    'imports.products', 'imports.materials', 'imports.inventory', 'imports.selection', 'imports.watch',
    'audit.read.all', 'audit.read.self', 'audit.export',
  ],
  PRODUCT_MATERIAL: [
    'products.read', 'products.write', 'materials.read', 'materials.write', 'inventory.read', 'inventory.write',
    'selection.read', 'watch.read', 'imports.products', 'imports.materials', 'imports.inventory', 'audit.read.self',
  ],
  SELECTION: [
    'products.read', 'materials.read', 'selection.read', 'selection.write', 'watch.read', 'watch.write',
    'imports.selection', 'imports.watch', 'audit.read.self',
  ],
}

export function capabilitiesForRole(role: AppRole): Capability[] {
  return ROLE_CAPABILITIES[role]
}
