import { describe, expect, it } from 'vitest'

import { capabilitiesForRole } from './capabilities'

describe('MVP role capabilities', () => {
  it('keeps the administrator mapping aligned with the approved catalog capability contract', () => {
    expect(new Set(capabilitiesForRole('ADMIN'))).toEqual(new Set([
      'system.users.manage', 'products.read', 'products.write', 'materials.read', 'materials.write',
      'inventory.read', 'inventory.write', 'selection.read', 'selection.write', 'watch.read', 'watch.write',
      'imports.read', 'imports.template.download', 'imports.validate', 'imports.commit', 'imports.cancel',
      'imports.result.download', 'audit.read.all', 'audit.read.self', 'dashboard.read',
      'dashboard.shipments.manage', 'dashboard.pending-products.manage',
      'suppliers.read', 'suppliers.write', 'purchases.read', 'purchases.write', 'purchases.receive',
    ]))
  })

  it('does not grant import mutation capabilities to selection users', () => {
    expect(capabilitiesForRole('SELECTION')).toEqual([
      'products.read', 'materials.read', 'selection.read', 'selection.write', 'watch.read', 'watch.write',
      'audit.read.self', 'dashboard.read', 'dashboard.pending-products.manage',
    ])
  })
})
