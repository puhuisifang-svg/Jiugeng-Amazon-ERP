import type { AppRole, Capability } from '@/router/meta'

const ROLE_CAPABILITIES: Record<AppRole, Capability[]> = {
  ADMIN: [
    'system.users.manage', 'products.read', 'products.write', 'materials.read', 'materials.write',
    'inventory.read', 'inventory.write', 'selection.read', 'selection.write', 'watch.read', 'watch.write',
    'imports.read', 'imports.template.download', 'imports.validate', 'imports.commit', 'imports.cancel',
    'imports.result.download', 'audit.read.all', 'audit.read.self',
  ],
  PRODUCT_MATERIAL: [
    'products.read', 'products.write', 'materials.read', 'materials.write', 'inventory.read', 'inventory.write',
    'selection.read', 'watch.read', 'imports.read', 'imports.template.download', 'imports.validate', 'imports.commit',
    'imports.cancel', 'imports.result.download', 'audit.read.self',
  ],
  SELECTION: [
    'products.read', 'materials.read', 'selection.read', 'selection.write', 'watch.read', 'watch.write',
    'audit.read.self',
  ],
}

export function capabilitiesForRole(role: AppRole): Capability[] {
  return ROLE_CAPABILITIES[role]
}
